package dev.gateway.clud.error;

import java.time.Instant;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleRouteNotFound(
            NoResourceFoundException exception,
            HttpServletRequest request) {
        log.warn("Gateway route not found: uri={}", request.getRequestURI());

        return error(
                HttpStatus.NOT_FOUND,
                "ROUTE_NOT_FOUND",
                "No route found for the requested path",
                request);
    }

    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<ApiError> handleUpstreamUnavailable(
            ResourceAccessException exception,
            HttpServletRequest request) {
        log.error("Upstream service unavailable: uri={}, message={}",
                request.getRequestURI(), exception.getMessage());

        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "UPSTREAM_UNAVAILABLE",
                "The upstream service is unavailable",
                request);
    }

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<byte[]> preserveUpstreamError(RestClientResponseException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .headers(exception.getResponseHeaders())
                .body(exception.getResponseBodyAsByteArray());
    }

    @ExceptionHandler(RestClientException.class)
    ResponseEntity<ApiError> handleRoutingError(
            RestClientException exception,
            HttpServletRequest request) {
        log.error("Gateway routing error: uri={}, message={}",
                request.getRequestURI(), exception.getMessage());

        return error(
                HttpStatus.BAD_GATEWAY,
                "GATEWAY_ROUTING_ERROR",
                "The gateway could not route the request",
                request);
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                Map.of()));
    }
}
