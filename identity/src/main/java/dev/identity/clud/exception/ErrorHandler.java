package dev.identity.clud.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.nio.file.AccessDeniedException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, WebRequest request) {
        Map<String, String> details = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> Objects.requireNonNullElse(fieldError.getDefaultMessage(), "Invalid value")
                ));

        log.warn("Validation failed on {}: {}", getPath(request), details);

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                "Request validation failed",
                getPath(request),
                LocalDateTime.now(),
                details
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailAlreadyExistsException e, WebRequest request) {
        log.warn("Email conflict on {}: {}", getPath(request), e.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "Conflict", e.getMessage(), request);
    }

    @ExceptionHandler({InvalidTokenException.class, UsernameNotFoundException.class, BadCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleAuth(RuntimeException e, WebRequest request) {
        log.warn("Authentication error on {}: {}", getPath(request), e.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", e.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, WebRequest request) {
        log.warn("Access denied on {}: {}", getPath(request), e.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "Forbidden", e.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e, WebRequest request) {
        log.warn("Malformed request on {}: {}", getPath(request), e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", "Malformed JSON or missing request body", request);
    }

    @ExceptionHandler(DateTimeException.class)
    public ResponseEntity<ErrorResponse> handleDateTime(DateTimeException e, WebRequest request) {
        log.warn("Date/time parse error on {}: {}", getPath(request), e.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, WebRequest request) {
        log.error("Unexpected error on {}: {}", getPath(request), e.getMessage(), e);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred",
                request
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String message, WebRequest request) {
        ErrorResponse body = new ErrorResponse(
                status.value(),
                error,
                message,
                getPath(request),
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.status(status).body(body);
    }

    private String getPath(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            return swr.getRequest().getRequestURI();
        }
        return "unknown";
    }
}