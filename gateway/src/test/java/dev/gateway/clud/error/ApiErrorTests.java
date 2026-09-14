package dev.gateway.clud.error;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorTests {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"BAD_REQUEST", "UNAUTHORIZED", "FORBIDDEN",
            "NOT_FOUND", "METHOD_NOT_ALLOWED", "CONFLICT", "INTERNAL_SERVER_ERROR",
            "BAD_GATEWAY", "SERVICE_UNAVAILABLE", "GATEWAY_TIMEOUT"})
    void keepsTheMinimalErrorContract(HttpStatus status) {
        var json = mapper.valueToTree(ApiError.of(status, "Description", "/example"));
        assertThat(json.get("status").asInt()).isEqualTo(status.value());
        assertThat(json.get("message").asString()).isEqualTo("Description");
        assertThat(json.get("path").asString()).isEqualTo("/example");
        assertThat(json.has("code")).isFalse();
        assertThat(json.has("timestamp")).isFalse();
        assertThat(json.has("fieldErrors")).isFalse();
    }

    @Test
    void escapesJson() {
        var error = ApiError.of(HttpStatus.BAD_REQUEST, "Invalid \"value\"\n", "/example");
        var json = mapper.readTree(mapper.writeValueAsString(error));
        assertThat(json.get("message").asString()).isEqualTo(error.message());
    }
}
