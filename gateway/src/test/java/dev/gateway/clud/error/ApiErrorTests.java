package dev.gateway.clud.error;

import java.util.Map;

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
    void derivesCodeFromStatusAndOmitsEmptyFieldErrors(HttpStatus status) {
        var json = mapper.valueToTree(ApiError.of(status, "Description", "/example", Map.of()));
        assertThat(json.get("status").asInt()).isEqualTo(status.value());
        assertThat(json.get("code").asText()).isEqualTo(status.name());
        assertThat(json.get("timestamp").asText()).isNotBlank();
        assertThat(json.has("fieldErrors")).isFalse();
    }

    @Test
    void keepsValidationDetailsAndEscapesJson() {
        var error = ApiError.of(HttpStatus.BAD_REQUEST, "Invalid \"value\"\n", "/example",
                Map.of("email", "Must be a valid email address"));
        var json = mapper.readTree(mapper.writeValueAsString(error));
        assertThat(json.get("message").asText()).isEqualTo(error.message());
        assertThat(json.get("fieldErrors").get("email").asText()).isEqualTo("Must be a valid email address");
    }
}
