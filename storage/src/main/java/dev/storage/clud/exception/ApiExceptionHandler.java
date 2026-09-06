package dev.storage.clud.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<ApiError> handleNotFound(NotFoundException exception, HttpServletRequest request) {
		log.warn("Object not found: uri={}, message={}", request.getRequestURI(), exception.getMessage());

		return error(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(InvalidStorageObjectException.class)
	ResponseEntity<ApiError> handleInvalidObject(
			InvalidStorageObjectException exception,
			HttpServletRequest request) {
		log.warn("Invalid storage object: uri={}, message={}", request.getRequestURI(), exception.getMessage());

		return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(StorageException.class)
	public ResponseEntity<ApiError> handleStorage(StorageException exception, HttpServletRequest request) {
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

		return error(HttpStatus.INTERNAL_SERVER_ERROR,
				"The storage operation could not be completed", request, Map.of());
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
		log.error("Unexpected error at uri={}", request.getRequestURI(), exception);

		return error(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"An unexpected error occurred. Please try again later.",
				request,
				Map.of());
	}

	private ResponseEntity<ApiError> error(
			HttpStatus status,
			String message,
			HttpServletRequest request,
			Map<String, String> fieldErrors) {
		return ResponseEntity.status(status).body(ApiError.of(status,
				message,
				request.getRequestURI(),
				fieldErrors));
	}
}
