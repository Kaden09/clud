package dev.storage.clud.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
	@ExceptionHandler(StorageException.class)
	public ResponseEntity<ApiError> handleStorageException(StorageException exception, HttpServletRequest request) {
		Throwable cause = exception.getCause();

		if (cause != null) {
			log.error("Storage error: uri={}, message={}, cause={}",
					request.getRequestURI(),
					exception.getMessage(),
					cause.getMessage(),
					cause);
		} else {
			log.warn("Storage error: uri={}, message={}",
					request.getRequestURI(),
					exception.getMessage());
		}

		Map<String, String> details = new HashMap<>();
		if (cause != null) {
			details.put("cause", cause.getMessage());
		}

		return error(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", exception.getMessage(), request, details);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleValidation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
			fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}

		log.warn("Request validation failed: uri={}, fields={}", request.getRequestURI(), fieldErrors);

		return error(
				HttpStatus.BAD_REQUEST,
				"VALIDATION_FAILED",
				"Request validation failed",
				request,
				fieldErrors);
	}

	@ExceptionHandler({
			MethodArgumentTypeMismatchException.class,
			HttpMessageNotReadableException.class,
			MissingRequestHeaderException.class
	})
	ResponseEntity<ApiError> handleMalformedRequest(Exception exception, HttpServletRequest request) {
		log.warn("Malformed request: uri={}, type={}, message={}",
				request.getRequestURI(),
				exception.getClass().getSimpleName(),
				exception.getMessage());

		return error(
				HttpStatus.BAD_REQUEST,
				"MALFORMED_REQUEST",
				"The request contains an invalid value",
				request,
				Map.of());
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
		log.error("Unexpected error at uri={}", request.getRequestURI(), exception);

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
