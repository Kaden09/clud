package dev.sharing.clud.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler({ShareLinkNotFoundException.class, SharedFileNotFoundException.class})
	ResponseEntity<ApiError> handleNotFound(RuntimeException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(ShareLinkExpiredException.class)
	ResponseEntity<ApiError> handleExpired(ShareLinkExpiredException exception, HttpServletRequest request) {
		return error(HttpStatus.GONE, "SHARE_LINK_EXPIRED", exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(InvalidShareOperationException.class)
	ResponseEntity<ApiError> handleInvalidOperation(
			InvalidShareOperationException exception,
			HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_SHARE_OPERATION", exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(PreviewNotSupportedException.class)
	ResponseEntity<ApiError> handlePreviewNotSupported(
			PreviewNotSupportedException exception,
			HttpServletRequest request) {
		return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PREVIEW_NOT_SUPPORTED", exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(UpstreamServiceException.class)
	ResponseEntity<ApiError> handleUnavailable(UpstreamServiceException exception, HttpServletRequest request) {
		log.error("Upstream service failure: uri={}, message={}", request.getRequestURI(), exception.getMessage(), exception);
		return error(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE", exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
		log.warn("Sharing database conflict: uri={}, message={}", request.getRequestURI(), exception.getMessage());
		return error(
				HttpStatus.CONFLICT,
				"SHARE_LINK_CONFLICT",
				"A concurrent request changed the active public link",
				request,
				Map.of());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleValidation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
			fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return error(
				HttpStatus.BAD_REQUEST,
				"VALIDATION_FAILED",
				"Request validation failed",
				request,
				fieldErrors);
	}

	@ExceptionHandler({
			ConstraintViolationException.class,
			MethodArgumentTypeMismatchException.class,
			HttpMessageNotReadableException.class,
			MissingRequestHeaderException.class
	})
	ResponseEntity<ApiError> handleMalformedRequest(Exception exception, HttpServletRequest request) {
		return error(
				HttpStatus.BAD_REQUEST,
				"MALFORMED_REQUEST",
				"The request contains an invalid value",
				request,
				Map.of());
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
		log.error("Unexpected sharing error at uri={}", request.getRequestURI(), exception);
		return error(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"INTERNAL_ERROR",
				"An unexpected error occurred. Please try again later.",
				request,
				Map.of());
	}

	private ResponseEntity<ApiError> error(
			HttpStatus status,
			String code,
			String message,
			HttpServletRequest request,
			Map<String, String> fieldErrors) {
		return ResponseEntity.status(status).body(new ApiError(
				Instant.now(),
				status.value(),
				code,
				message,
				request.getRequestURI(),
				fieldErrors));
	}
}
