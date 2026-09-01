package dev.gateway.clud;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.gateway.clud.security.jwt.TrustedUserHeaderFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class GatewayIntegrationTests {

    private static final String JWT_SECRET =
            "Y2x1ZC1kZXZlbG9wbWVudC1qd3Qtc2VjcmV0LTMyYiE=";
    private static final String OTHER_JWT_SECRET = Base64.getEncoder().encodeToString(
            "another-development-jwt-secret-32-bytes".getBytes(StandardCharsets.UTF_8));
    private static final UUID USER_ID = UUID.fromString("7b22152f-93c5-48f1-b1c8-a60647fb7d86");
    private static final UUID SPOOFED_USER_ID = UUID.fromString("4443f9df-3b75-41a9-8981-e02cc1abdc99");
    private static final HttpServer UPSTREAM = startUpstream();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final JwtEncoder JWT_ENCODER = jwtEncoder(JWT_SECRET);
    private static final JwtEncoder OTHER_JWT_ENCODER = jwtEncoder(OTHER_JWT_SECRET);

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void configureApplication(DynamicPropertyRegistry registry) {
        String upstreamUrl = "http://localhost:" + UPSTREAM.getAddress().getPort();
        registry.add("IDENTITY_SERVICE_URL", () -> upstreamUrl);
        registry.add("FILE_SERVICE_URL", () -> upstreamUrl);
        registry.add("STORAGE_SERVICE_URL", () -> upstreamUrl);
        registry.add("SHARING_SERVICE_URL", () -> upstreamUrl);
        registry.add("JWT_SECRET", () -> JWT_SECRET);
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.stop(0);
    }

    @ParameterizedTest
    @MethodSource("routes")
    void routesAuthenticatedRequestsAndStripsServicePrefix(String publicPath, String upstreamPath) throws Exception {
        HttpResponse<String> response = send(authorizedGet(publicPath));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "method=GET",
                "uri=" + upstreamPath,
                "userId=" + USER_ID);
    }

    @Test
    void preservesMethodQueryBodyHeadersAndUpstreamResponse() throws Exception {
        HttpRequest request = authorized(HttpRequest.newBuilder(gatewayUri("/api/files/folders?parent=root")))
                .header("Content-Type", "text/plain")
                .header("X-Custom-Header", "custom-value")
                .POST(HttpRequest.BodyPublishers.ofString("request-body"))
                .build();

        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("X-Upstream")).contains("true");
        assertThat(response.body()).contains(
                "method=POST",
                "uri=/folders?parent=root",
                "body=request-body",
                "customHeader=custom-value",
                "userId=" + USER_ID);
    }

    @Test
    void preservesIdentityCookiePublicPath() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(gatewayUri("/api/identity/auth/login"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Set-Cookie"))
                .hasValueSatisfying(cookie -> assertThat(cookie).contains("Path=/api/identity/auth"));
    }

    @Test
    void allowsPublicSharingWithoutAccessToken() throws Exception {
        HttpResponse<String> response = send(
                HttpRequest.newBuilder(gatewayUri("/api/sharing/public/a-valid-public-token-value-123456"))
                        .GET()
                        .build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("uri=/public/a-valid-public-token-value-123456");
    }

    @Test
    void rejectsProtectedRouteWithoutAccessToken() throws Exception {
        HttpResponse<String> response = send(
                HttpRequest.newBuilder(gatewayUri("/api/files/nodes")).GET().build());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains(
                "\"code\":\"UNAUTHORIZED\"",
                "\"path\":\"/api/files/nodes\"");
        assertThat(response.headers().firstValue(RequestIdFilter.REQUEST_ID_HEADER)).isPresent();
    }

    @Test
    void rejectsRefreshToken() throws Exception {
        HttpRequest request = bearer(
                HttpRequest.newBuilder(gatewayUri("/api/files/nodes")),
                token(JWT_ENCODER, USER_ID.toString(), "refresh", Instant.now().plusSeconds(300)))
                .GET()
                .build();

        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsExpiredAccessToken() throws Exception {
        HttpRequest request = bearer(
                HttpRequest.newBuilder(gatewayUri("/api/files/nodes")),
                token(JWT_ENCODER, USER_ID.toString(), "access", Instant.now().minusSeconds(60)))
                .GET()
                .build();

        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsAccessTokenWithInvalidSignature() throws Exception {
        HttpRequest request = bearer(
                HttpRequest.newBuilder(gatewayUri("/api/files/nodes")),
                token(OTHER_JWT_ENCODER, USER_ID.toString(), "access", Instant.now().plusSeconds(300)))
                .GET()
                .build();

        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsAccessTokenWithInvalidUserIdSubject() throws Exception {
        HttpRequest request = bearer(
                HttpRequest.newBuilder(gatewayUri("/api/files/nodes")),
                token(JWT_ENCODER, "not-a-uuid", "access", Instant.now().plusSeconds(300)))
                .GET()
                .build();

        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void replacesClientProvidedUserIdWithAuthenticatedSubject() throws Exception {
        HttpRequest request = authorized(HttpRequest.newBuilder(gatewayUri("/api/files/nodes")))
                .header(TrustedUserHeaderFilter.USER_ID_HEADER, SPOOFED_USER_ID.toString())
                .GET()
                .build();

        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("userId=" + USER_ID)
                .doesNotContain("userId=" + SPOOFED_USER_ID);
    }

    @Test
    void removesClientProvidedUserIdFromPublicRequests() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(gatewayUri("/api/identity/auth/login"))
                .header(TrustedUserHeaderFilter.USER_ID_HEADER, SPOOFED_USER_ID.toString())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("userId=null");
    }

    @Test
    void allowsCorsPreflightWithoutAccessToken() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(gatewayUri("/api/files/nodes"))
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .contains("http://localhost:3000");
    }

    @Test
    void preservesIncomingRequestId() throws Exception {
        String requestId = "client-request-id";
        HttpRequest request = authorized(HttpRequest.newBuilder(gatewayUri("/api/storage/objects/1")))
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .GET()
                .build();

        HttpResponse<String> response = send(request);

        assertThat(response.headers().allValues(RequestIdFilter.REQUEST_ID_HEADER)).containsExactly(requestId);
        assertThat(response.body()).contains("requestId=" + requestId);
    }

    @Test
    void generatesRequestIdWhenMissing() throws Exception {
        HttpResponse<String> response = send(authorizedGet("/api/sharing/links/1"));

        String requestId = response.headers().firstValue(RequestIdFilter.REQUEST_ID_HEADER).orElseThrow();
        assertThat(UUID.fromString(requestId)).isNotNull();
        assertThat(response.headers().allValues(RequestIdFilter.REQUEST_ID_HEADER)).containsExactly(requestId);
        assertThat(response.body()).contains("requestId=" + requestId);
    }

    @Test
    void logsCompletedRequestWithCorrelationData(CapturedOutput output) throws Exception {
        String requestId = "logging-test-" + UUID.randomUUID();
        HttpRequest request = authorized(HttpRequest.newBuilder(gatewayUri("/api/files/folders/1")))
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .GET()
                .build();

        HttpResponse<String> response = send(request);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(output).contains(
                "Gateway request completed:",
                "requestId=" + requestId,
                "method=GET",
                "path=/api/files/folders/1",
                "status=200",
                "durationMs=");
    }

    @Test
    void returnsNotFoundForUnknownAuthenticatedRoute() throws Exception {
        HttpResponse<String> response = send(authorizedGet("/api/unknown/resource"));

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void keepsActuatorHealthPublicAndLocal() throws Exception {
        HttpResponse<String> response = send(
                HttpRequest.newBuilder(gatewayUri("/actuator/health/readiness")).GET().build());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest authorizedGet(String path) {
        return authorized(HttpRequest.newBuilder(gatewayUri(path))).GET().build();
    }

    private HttpRequest.Builder authorized(HttpRequest.Builder request) {
        return bearer(
                request,
                token(JWT_ENCODER, USER_ID.toString(), "access", Instant.now().plusSeconds(300)));
    }

    private static HttpRequest.Builder bearer(HttpRequest.Builder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private URI gatewayUri(String path) {
        return URI.create("http://localhost:" + gatewayPort + path);
    }

    private static Stream<Arguments> routes() {
        return Stream.of(
                Arguments.of("/api/identity/auth/login", "/auth/login"),
                Arguments.of("/api/files/folders/1", "/folders/1"),
                Arguments.of("/api/storage/objects/1", "/objects/1"),
                Arguments.of("/api/sharing/links/1", "/links/1"));
    }

    private static JwtEncoder jwtEncoder(String encodedSecret) {
        SecretKey key = new SecretKeySpec(
                Base64.getDecoder().decode(encodedSecret),
                "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
    }

    private static String token(
            JwtEncoder encoder,
            String subject,
            String type,
            Instant expiresAt) {
        Instant issuedAt = expiresAt.isAfter(Instant.now())
                ? Instant.now().minusSeconds(1)
                : expiresAt.minusSeconds(60);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .claim("email", "user@example.com")
                .claim("type", type)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static HttpServer startUpstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", GatewayIntegrationTests::handleUpstreamRequest);
            server.start();
            return server;
        }
        catch (IOException exception) {
            throw new IllegalStateException("Could not start test upstream server", exception);
        }
    }

    private static void handleUpstreamRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String responseBody = String.join("\n",
                "method=" + exchange.getRequestMethod(),
                "uri=" + exchange.getRequestURI(),
                "body=" + body,
                "requestId=" + exchange.getRequestHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER),
                "customHeader=" + exchange.getRequestHeaders().getFirst("X-Custom-Header"),
                "userId=" + exchange.getRequestHeaders().getFirst(TrustedUserHeaderFilter.USER_ID_HEADER));
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        int status = "POST".equals(exchange.getRequestMethod()) ? 201 : 200;
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.getResponseHeaders().set("X-Upstream", "true");
        exchange.getResponseHeaders().set(RequestIdFilter.REQUEST_ID_HEADER, "upstream-request-id");
        if ("/auth/login".equals(exchange.getRequestURI().getPath())) {
            exchange.getResponseHeaders().set(
                    "Set-Cookie",
                    "refresh_token=test; Path=/api/identity/auth; HttpOnly");
        }
        exchange.sendResponseHeaders(status, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }
}
