package dev.file.clud.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

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

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(NodeNotFoundException.class)
	ResponseEntity<ApiError> handleNotFound(NodeNotFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "NODE_NOT_FOUND", exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(NodeConflictException.class)
	ResponseEntity<ApiError> handleConflict(NodeConflictException exception, HttpServletRequest request) {
		return error(HttpStatus.CONFLICT, "NODE_CONFLICT", exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ApiError> handleDatabaseConflict(
			DataIntegrityViolationException exception,
			HttpServletRequest request) {
		return error(
				HttpStatus.CONFLICT,
				"NODE_CONFLICT",
				"A node with this name already exists in the destination folder",
				request,
				Map.of());
	}

	@ExceptionHandler(InvalidNodeOperationException.class)
	ResponseEntity<ApiError> handleInvalidOperation(
			InvalidNodeOperationException exception,
			HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "INVALID_NODE_OPERATION", exception.getMessage(), request, Map.of());
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

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ApiError> handleConstraintViolation(
			ConstraintViolationException exception,
			HttpServletRequest request) {
		return error(
				HttpStatus.BAD_REQUEST,
				"VALIDATION_FAILED",
				"Request validation failed",
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
