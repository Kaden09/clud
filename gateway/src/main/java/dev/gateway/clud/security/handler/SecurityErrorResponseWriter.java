package dev.gateway.clud.security.handler;

import java.io.IOException;
import java.util.Map;

import dev.gateway.clud.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

    private final JsonMapper jsonMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(),
                ApiError.of(HttpStatusCode.valueOf(status), message, request.getRequestURI(), Map.of()));
    }
}
