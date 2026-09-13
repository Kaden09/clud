package dev.gateway.clud.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public record ApiError(
        int status,
        String message,
        String path) {

    public static ApiError of(HttpStatusCode status, String message, String path) {
        return new ApiError(status.value(), message, path);
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
