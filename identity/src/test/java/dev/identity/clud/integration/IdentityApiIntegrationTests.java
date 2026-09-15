package dev.identity.clud.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.identity.clud.event.UserRegisteredEvent;
import dev.identity.clud.session.RefreshSessionRepository;
import dev.identity.clud.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "jwt.secret=Y2x1ZC1kZXZlbG9wbWVudC1qd3Qtc2VjcmV0LTMyYiE=")
class IdentityApiIntegrationTests {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @LocalServerPort
    private int port;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshSessionRepository refreshSessionRepository;

    @BeforeEach
    void cleanDatabase() {
        refreshSessionRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        clearInvocations((Object) kafkaTemplate);
    }

    @Test
    void registersUserAndPublishesEventAfterCommit() throws Exception {
        HttpResponse<String> response = register("USER@Example.com");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body())
                .contains("user@example.com", "\"id\"")
                .doesNotContain("password", "role");

        verify(kafkaTemplate).send(
                eq("identity.events.v1"),
                anyString(),
                argThat(event -> event instanceof UserRegisteredEvent registered
                        && registered.eventType().equals("UserRegistered")
                        && registered.eventVersion() == 1
                        && registered.email().equals("user@example.com")));
    }

    @Test
    void rejectsDuplicateEmailAndInvalidRegistration() throws Exception {
        register("user@example.com");

        HttpResponse<String> duplicate = register("USER@example.com");
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertSimpleError(duplicate, "CONFLICT", "Email is already registered");

        HttpResponse<String> invalid = request(
                "POST",
                "/auth/register",
                "{\"email\":\"invalid\",\"password\":\"short\"}",
                null,
                null);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertSimpleError(invalid, "BAD_REQUEST", "Request validation failed");
    }

    @Test
    void rejectsInvalidCredentialsAndUnavailableAccountsWithoutLeakingDetails() throws Exception {
        register("user@example.com");

        HttpResponse<String> wrongPassword = request(
                "POST",
                "/auth/login",
                "{\"email\":\"user@example.com\",\"password\":\"WrongPassword123\"}",
                null,
                null);
        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertSimpleError(wrongPassword, "UNAUTHORIZED", "Authentication failed");

        var user = userRepository.findByEmail("user@example.com").orElseThrow();
        user.setEnabled(false);
        userRepository.saveAndFlush(user);
        HttpResponse<String> disabled = request(
                "POST",
                "/auth/login",
                "{\"email\":\"user@example.com\",\"password\":\"Password123\"}",
                null,
                null);
        assertThat(disabled.statusCode()).isEqualTo(401);
        assertSimpleError(disabled, "UNAUTHORIZED", "Authentication failed");

        user.setEnabled(true);
        user.setAccountNonLocked(false);
        userRepository.saveAndFlush(user);
        HttpResponse<String> locked = request(
                "POST",
                "/auth/login",
                "{\"email\":\"user@example.com\",\"password\":\"Password123\"}",
                null,
                null);
        assertThat(locked.statusCode()).isEqualTo(401);
        assertSimpleError(locked, "UNAUTHORIZED", "Authentication failed");
    }

    @Test
    void logsInReadsCurrentUserAndLogsOut() throws Exception {
        register("user@example.com");
        Tokens login = login();

        HttpResponse<String> currentUser = request(
                "GET",
                "/user/me",
                null,
                login.accessToken(),
                null);
        assertThat(currentUser.statusCode()).isEqualTo(200);
        assertThat(currentUser.body())
                .contains("user@example.com", "\"id\"")
                .doesNotContain("password", "role");

        HttpResponse<String> logout = request(
                "POST",
                "/auth/logout",
                null,
                null,
                login.cookie());
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.headers().firstValue("Set-Cookie"))
                .hasValueSatisfying(cookie -> assertThat(cookie)
                        .contains("refresh_token=")
                        .contains("Max-Age=0"));
    }

    @Test
    void serializesConcurrentRefreshAndRevokesTheRotatedSessionOnReuse() throws Exception {
        register("user@example.com");
        Tokens login = login();
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var requests = List.of(
                    executor.submit(() -> refreshAfter(start, login.cookie())),
                    executor.submit(() -> refreshAfter(start, login.cookie())));
            start.countDown();

            List<HttpResponse<String>> responses = requests.stream().map(future -> {
                try {
                    return future.get();
                }
                catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).toList();
            assertThat(responses.stream().map(response -> response.statusCode()).toList())
                    .containsExactlyInAnyOrder(200, 401);

            String rotatedCookie = responses.stream()
                    .filter(response -> response.statusCode() == 200)
                    .map(this::cookie)
                    .findFirst()
                    .orElseThrow();
            assertThat(request("POST", "/auth/refresh", null, null, rotatedCookie).statusCode())
                    .isEqualTo(401);
        }
    }

    @Test
    void rotatesRefreshTokenAndRejectsReuse() throws Exception {
        register("user@example.com");
        Tokens login = login();

        HttpResponse<String> refreshed = request(
                "POST",
                "/auth/refresh",
                null,
                null,
                login.cookie());
        assertThat(refreshed.statusCode()).isEqualTo(200);
        String rotatedCookie = cookie(refreshed);
        assertThat(rotatedCookie).isNotEqualTo(login.cookie());

        HttpResponse<String> reuse = request(
                "POST",
                "/auth/refresh",
                null,
                null,
                login.cookie());
        assertThat(reuse.statusCode()).isEqualTo(401);
        assertThat(reuse.body()).contains("UNAUTHORIZED");

        HttpResponse<String> revokedRotation = request(
                "POST",
                "/auth/refresh",
                null,
                null,
                rotatedCookie);
        assertThat(revokedRotation.statusCode()).isEqualTo(401);
    }

    @Test
    void returnsJsonUnauthorizedForMissingAndInvalidAccessTokens() throws Exception {
        HttpResponse<String> missing = request("GET", "/user/me", null, null, null);
        assertThat(missing.statusCode()).isEqualTo(401);
        assertThat(missing.body()).contains("UNAUTHORIZED");

        HttpResponse<String> invalid = request("GET", "/user/me", null, "not-a-jwt", null);
        assertThat(invalid.statusCode()).isEqualTo(401);
        assertThat(invalid.body()).contains("UNAUTHORIZED");
    }

    @Test
    void exposesDefaultDocumentationPathsAndSecuritySchemes() throws Exception {
        HttpResponse<String> openApi = request("GET", "/v3/api-docs", null, null, null);
        assertThat(openApi.statusCode()).isEqualTo(200);
        assertThat(openApi.body()).contains("bearerAuth", "refreshCookie");

        HttpResponse<String> swagger = request("GET", "/swagger-ui.html", null, null, null);
        assertThat(swagger.statusCode()).isIn(200, 302);
        assertThat(request("GET", "/docs", null, null, null).statusCode()).isEqualTo(404);

        HttpResponse<String> proxiedSwagger = HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/swagger-ui.html"))
                        .header("X-Forwarded-Prefix", "/api/identity")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(proxiedSwagger.statusCode()).isEqualTo(302);
        assertThat(proxiedSwagger.headers().firstValue("Location"))
                .hasValueSatisfying(location -> assertThat(location)
                        .isEqualTo("/api/identity/swagger-ui/index.html"));

        HttpResponse<String> swaggerConfig = HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs/swagger-config"))
                        .header("X-Forwarded-Prefix", "/api/identity")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(swaggerConfig.statusCode()).isEqualTo(200);
        assertThat(swaggerConfig.body()).contains(
                "\"configUrl\":\"/api/identity/v3/api-docs/swagger-config\"",
                "\"url\":\"/api/identity/v3/api-docs\"");
    }

    @Test
    void unknownRoutesReturnNotFoundWithAndWithoutTokens() throws Exception {
        register("user@example.com");
        Tokens tokens = login();
        for (String path : new String[] {"/", "/unknown", "/user/unknown", "/auth/unknown"}) {
            for (String token : new String[] {null, "invalid-token", tokens.accessToken()}) {
                HttpResponse<String> response = request("GET", path, null, token, null);
                assertThat(response.statusCode()).as(path).isEqualTo(404);
                assertSimpleError(response, "NOT_FOUND", "The requested endpoint does not exist");
            }
        }
    }

    @Test
    void protectedMethodsStayProtectedAndAuthorizedMethodErrorsKeepAllowHeader() throws Exception {
        assertThat(request("POST", "/user/me", null, null, null).statusCode()).isEqualTo(401);
        register("user@example.com");
        HttpResponse<String> response = request("POST", "/user/me", null, login().accessToken(), null);
        assertThat(response.statusCode()).isEqualTo(405);
        assertSimpleError(response, "METHOD_NOT_ALLOWED", "The HTTP method is not supported for this endpoint");
        assertThat(response.headers().firstValue("Allow")).hasValueSatisfying(value -> assertThat(value).contains("GET"));
        assertThat(request("GET", "/actuator/info", null, null, null).statusCode()).isEqualTo(401);

        HttpResponse<String> deniedActuator = request(
                "GET", "/actuator/info", null, login().accessToken(), null);
        assertThat(deniedActuator.statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> register(String email) throws Exception {
        return request(
                "POST",
                "/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"Password123\"}",
                null,
                null);
    }

    private Tokens login() throws Exception {
        HttpResponse<String> response = request(
                "POST",
                "/auth/login",
                "{\"email\":\"user@example.com\",\"password\":\"Password123\"}",
                null,
                null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Pragma")).contains("no-cache");
        Matcher matcher = ACCESS_TOKEN.matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return new Tokens(matcher.group(1), cookie(response));
    }

    private HttpResponse<String> refreshAfter(CountDownLatch start, String cookie) throws Exception {
        start.await();
        return request("POST", "/auth/refresh", null, null, cookie);
    }

    private String cookie(HttpResponse<String> response) {
        String setCookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private void assertSimpleError(HttpResponse<String> response, String code, String message) {
        assertThat(response.body()).isEqualTo(
                "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    private HttpResponse<String> request(
            String method,
            String path,
            String body,
            String accessToken,
            String cookie) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            request.header("Content-Type", "application/json");
            publisher = HttpRequest.BodyPublishers.ofString(body);
        }
        return HTTP_CLIENT.send(
                request.method(method, publisher).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private record Tokens(String accessToken, String cookie) {
    }
}
