package dev.gateway.clud;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class GatewayIntegrationTests {

	private static final HttpServer UPSTREAM = startUpstream();
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	@LocalServerPort
	private int gatewayPort;

	@DynamicPropertySource
	static void configureServiceUrls(DynamicPropertyRegistry registry) {
		String upstreamUrl = "http://localhost:" + UPSTREAM.getAddress().getPort();
		registry.add("IDENTITY_SERVICE_URL", () -> upstreamUrl);
		registry.add("FILE_SERVICE_URL", () -> upstreamUrl);
		registry.add("STORAGE_SERVICE_URL", () -> upstreamUrl);
		registry.add("SHARING_SERVICE_URL", () -> upstreamUrl);
	}

	@AfterAll
	static void stopUpstream() {
		UPSTREAM.stop(0);
	}

	@ParameterizedTest
	@MethodSource("routes")
	void routesRequestsAndStripsServicePrefix(String publicPath, String upstreamPath) throws Exception {
		HttpResponse<String> response = send(HttpRequest.newBuilder(gatewayUri(publicPath)).GET().build());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("method=GET", "uri=" + upstreamPath);
	}

	@Test
	void preservesMethodQueryBodyHeadersAndUpstreamResponse() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(gatewayUri("/api/files/folders?parent=root"))
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
				"customHeader=custom-value");
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
	void preservesIncomingRequestId() throws Exception {
		String requestId = "client-request-id";
		HttpRequest request = HttpRequest.newBuilder(gatewayUri("/api/storage/objects/1"))
				.header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
				.GET()
				.build();

		HttpResponse<String> response = send(request);

		assertThat(response.headers().allValues(RequestIdFilter.REQUEST_ID_HEADER)).containsExactly(requestId);
		assertThat(response.body()).contains("requestId=" + requestId);
	}

	@Test
	void generatesRequestIdWhenMissing() throws Exception {
		HttpResponse<String> response = send(
				HttpRequest.newBuilder(gatewayUri("/api/sharing/links/1")).GET().build());

		String requestId = response.headers().firstValue(RequestIdFilter.REQUEST_ID_HEADER).orElseThrow();
		assertThat(UUID.fromString(requestId)).isNotNull();
		assertThat(response.headers().allValues(RequestIdFilter.REQUEST_ID_HEADER)).containsExactly(requestId);
		assertThat(response.body()).contains("requestId=" + requestId);
	}

	@Test
	void logsCompletedRequestWithCorrelationData(CapturedOutput output) throws Exception {
		String requestId = "logging-test-" + UUID.randomUUID();
		HttpRequest request = HttpRequest.newBuilder(gatewayUri("/api/files/folders/1"))
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
	void returnsNotFoundForUnknownRoute() throws Exception {
		HttpResponse<String> response = send(
				HttpRequest.newBuilder(gatewayUri("/api/unknown/resource")).GET().build());

		assertThat(response.statusCode()).isEqualTo(404);
	}

	@Test
	void keepsActuatorEndpointsLocal() throws Exception {
		HttpResponse<String> response = send(
				HttpRequest.newBuilder(gatewayUri("/actuator/health/readiness")).GET().build());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"status\":\"UP\"");
	}

	private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
		return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
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
				"customHeader=" + exchange.getRequestHeaders().getFirst("X-Custom-Header"));
		byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
		int status = "POST".equals(exchange.getRequestMethod()) ? 201 : 200;
		exchange.getResponseHeaders().set("Content-Type", "text/plain");
		exchange.getResponseHeaders().set("X-Upstream", "true");
		exchange.getResponseHeaders().set(RequestIdFilter.REQUEST_ID_HEADER, "upstream-request-id");
		if ("/auth/login".equals(exchange.getRequestURI().getPath())) {
			// Cookie Path остаётся публичным: его проверяет браузер до маршрутизации Gateway.
			exchange.getResponseHeaders().set(
					"Set-Cookie",
					"refresh_token=test; Path=/api/identity/auth; HttpOnly");
		}
		exchange.sendResponseHeaders(status, responseBytes.length);
		exchange.getResponseBody().write(responseBytes);
		exchange.close();
	}
}
