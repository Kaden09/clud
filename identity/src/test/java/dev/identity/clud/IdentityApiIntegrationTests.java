package dev.identity.clud;

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
import org.testcontainers.containers.PostgreSQLContainer;
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
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

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
        clearInvocations(kafkaTemplate);
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
        assertThat(duplicate.body()).contains("EMAIL_ALREADY_EXISTS");

        HttpResponse<String> invalid = request(
                "POST",
                "/auth/register",
                "{\"email\":\"invalid\",\"password\":\"short\"}",
                null,
                null);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.body()).contains("VALIDATION_FAILED", "email", "password");
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
        assertThat(reuse.body()).contains("AUTHENTICATION_FAILED");

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
        assertThat(invalid.body()).contains("INVALID_ACCESS_TOKEN");
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
        Matcher matcher = ACCESS_TOKEN.matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return new Tokens(matcher.group(1), cookie(response));
    }

    private String cookie(HttpResponse<String> response) {
        String setCookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
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
