package dev.file.clud.error;

import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON fallback for servlet error dispatches that do not reach exception advice. */
@Slf4j
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ApiError> error(HttpServletRequest request) {
        Object originalStatus = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatusCode status = originalStatus instanceof Integer value
                ? HttpStatusCode.valueOf(value) : HttpStatus.NOT_FOUND;
        Object originalPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = originalPath instanceof String value ? value : request.getRequestURI();
        if (status.is5xxServerError()) {
            Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            if (exception instanceof Throwable cause) {
                log.error("Unhandled request failure at {}", path, cause);
            }
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(ApiError.of(status, ApiError.defaultMessage(status), path, Map.of()));
    }
}
