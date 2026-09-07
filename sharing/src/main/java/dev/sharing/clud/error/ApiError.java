package dev.sharing.clud.error;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Schema(description = "Consistent API error. Code is the uppercase HTTP status name; fieldErrors is present only for validation failures.")
public record ApiError(
        Instant timestamp,
        int status,
        @Schema(example = "NOT_FOUND") String code,
        String message,
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> fieldErrors) {

    public static ApiError of(HttpStatusCode status, String message, String path, Map<String, String> fieldErrors) {
        HttpStatus standard = HttpStatus.resolve(status.value());
        return new ApiError(Instant.now(), status.value(),
                standard == null ? "HTTP_" + status.value() : standard.name(),
                message, path, fieldErrors == null ? Map.of() : fieldErrors);
    }

    public static String defaultMessage(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "The request contains an invalid value";
            case 401 -> "Authentication is required";
            case 403 -> "Access is denied";
            case 404 -> "The requested endpoint does not exist";
            case 405 -> "The HTTP method is not supported for this endpoint";
            case 406 -> "The requested response media type is not supported";
            case 413 -> "The request exceeds the maximum allowed size";
            case 415 -> "The request media type is not supported";
            case 502 -> "The upstream service could not be reached";
            case 503 -> "The service is temporarily unavailable";
            case 504 -> "The upstream service did not respond in time";
            default -> {
                HttpStatus standard = HttpStatus.resolve(status.value());
                yield status.is5xxServerError() || standard == null
                        ? "An unexpected error occurred. Please try again later."
                        : standard.getReasonPhrase();
            }
        };
    }
}
