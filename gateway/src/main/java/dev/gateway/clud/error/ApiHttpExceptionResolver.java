package dev.gateway.clud.error;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;
import tools.jackson.databind.json.JsonMapper;

/** Handles HTTP failures before domain advice, including resource and functional handlers. */
@Component
public class ApiHttpExceptionResolver extends AbstractHandlerExceptionResolver {

    private final JsonMapper jsonMapper;

    public ApiHttpExceptionResolver(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception exception) {
        if (response.isCommitted()) {
            return null;
        }
        HttpStatusCode status;
        HttpHeaders headers = new HttpHeaders();
        if (exception instanceof ErrorResponse error) {
            status = error.getStatusCode();
            headers.putAll(error.getHeaders());
        }
        else if (exception instanceof HttpMessageNotReadableException
                || exception instanceof MethodArgumentTypeMismatchException) {
            status = HttpStatus.BAD_REQUEST;
        }
        else if (upstreamFailureStatus(exception) != null) {
            status = upstreamFailureStatus(exception);
        }
        else {
            return null; // Unhandled failures reach the servlet JSON error controller.
        }

        Map<String, String> fields = new LinkedHashMap<>();
        String message = ApiError.defaultMessage(status);
        if (exception instanceof MethodArgumentNotValidException validation) {
            validation.getBindingResult().getFieldErrors().forEach(error ->
                    fields.putIfAbsent(error.getField(), error.getDefaultMessage() == null
                            ? "Invalid value" : error.getDefaultMessage()));
            message = "Request validation failed";
        }
        if (status.is5xxServerError()) {
            logger.error("HTTP request failed at " + request.getRequestURI(), exception);
        }
        try {
            response.setStatus(status.value());
            headers.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            if (!"HEAD".equals(request.getMethod())) {
                jsonMapper.writeValue(response.getOutputStream(),
                        ApiError.of(status, message, request.getRequestURI(), fields));
            }
            return new ModelAndView();
        }
        catch (IOException writeFailure) {
            logger.warn("Could not write API error response", writeFailure);
            return null;
        }
    }

    private HttpStatus upstreamFailureStatus(Throwable exception) {
        boolean upstream = false;
        boolean timeout = false;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = exception; cause != null && visited.add(cause); cause = cause.getCause()) {
            upstream |= cause instanceof ResourceAccessException;
            timeout |= cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException
                    || cause instanceof TimeoutException;
        }
        return upstream ? (timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY) : null;
    }
}
