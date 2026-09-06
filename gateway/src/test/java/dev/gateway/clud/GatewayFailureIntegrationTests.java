package dev.gateway.clud;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.server.mvc.handler.ProxyExchange;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "IDENTITY_SERVICE_URL=http://localhost:1", "FILE_SERVICE_URL=http://localhost:1",
        "SHARING_SERVICE_URL=http://localhost:1",
        "JWT_SECRET=Y2x1ZC1kZXZlbG9wbWVudC1qd3Qtc2VjcmV0LTMyYiE="
})
class GatewayFailureIntegrationTests {
    @LocalServerPort
    int port;

    // Mock only the network exchange: requests still traverse real security and functional routes.
    @MockitoBean
    ProxyExchange proxyExchange;

    @Test
    void connectionFailureReturnsBadGateway() throws Exception {
        assertFailure(new ConnectException("private backend address"), 502, "BAD_GATEWAY");
    }

    @Test
    void timeoutReturnsGatewayTimeout() throws Exception {
        assertFailure(new SocketTimeoutException("private backend address"), 504, "GATEWAY_TIMEOUT");
    }

    @Test
    void requestFactoryConnectionFailureReturnsBadGateway() throws Exception {
        assertExchangeFailure(new UncheckedIOException(new ConnectException("private backend address")),
                502, "BAD_GATEWAY");
    }

    @Test
    void requestFactoryTimeoutReturnsGatewayTimeout() throws Exception {
        assertExchangeFailure(new UncheckedIOException(new SocketTimeoutException("private backend address")),
                504, "GATEWAY_TIMEOUT");
    }

    private void assertFailure(IOException cause, int status, String code) throws Exception {
        assertExchangeFailure(new ResourceAccessException("private backend address", cause), status, code);
    }

    private void assertExchangeFailure(RuntimeException failure, int status, String code) throws Exception {
        // Request construction belongs to the exchange API and is independent of the network.
        when(proxyExchange.request(any())).thenCallRealMethod();
        when(proxyExchange.exchange(any())).thenThrow(failure);
        String path = "/api/identity/auth/login";
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(status);
        var json = JsonMapper.builder().build().readTree(response.body());
        assertThat(json.get("code").asText()).isEqualTo(code);
        assertThat(json.get("path").asText()).isEqualTo(path);
        assertThat(json.has("fieldErrors")).isFalse();
        assertThat(response.body()).doesNotContain("private backend address");
    }
}
