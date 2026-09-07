package dev.file.clud.error;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(NodeNotFoundException.class)
	ResponseEntity<ApiError> handleNotFound(NodeNotFoundException exception, HttpServletRequest request) {
		log.warn("Node not found: uri={}, message={}", request.getRequestURI(), exception.getMessage());

		return error(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(NodeConflictException.class)
	ResponseEntity<ApiError> handleConflict(NodeConflictException exception, HttpServletRequest request) {
		log.warn("Node conflict: uri={}, message={}", request.getRequestURI(), exception.getMessage());

		return error(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ApiError> handleDatabaseConflict(
			DataIntegrityViolationException exception,
			HttpServletRequest request) {
		log.warn("Database conflict (possible duplicate): uri={}, rootCause={}",
				request.getRequestURI(),
				exception.getMostSpecificCause().getMessage());

		return error(
				HttpStatus.CONFLICT,
				"A node with this name already exists in the destination folder",
				request,
				Map.of());
	}

	@ExceptionHandler(InvalidNodeOperationException.class)
	ResponseEntity<ApiError> handleInvalidOperation(
			InvalidNodeOperationException exception,
			HttpServletRequest request) {
		log.warn("Invalid node operation: uri={}, message={}", request.getRequestURI(), exception.getMessage());

		return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(UpstreamStorageException.class)
	ResponseEntity<ApiError> handleStorageUnavailable(
			UpstreamStorageException exception,
			HttpServletRequest request) {
		log.error("Storage upstream error: uri={}, message={}", request.getRequestURI(), exception.getMessage());

		return error(HttpStatus.BAD_GATEWAY, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ApiError> handleConstraintViolation(
			ConstraintViolationException exception,
			HttpServletRequest request) {
		log.warn("Constraint violation: uri={}, violations={}",
				request.getRequestURI(),
				exception.getConstraintViolations());

		return error(
				HttpStatus.BAD_REQUEST,
				"Request validation failed",
				request,
				Map.of());
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
