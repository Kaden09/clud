package dev.identity.clud.security;

import java.io.IOException;
import java.time.Instant;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorResponseWriter {

    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"timestamp":"%s","status":%d,"code":"%s","message":"%s"}
                """.formatted(Instant.now(), status, escape(code), escape(message)).trim());
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace(""", "\\"");
    }
}
