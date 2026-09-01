package dev.gateway.clud.security.handler;

import java.time.Instant;

public record SecurityApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path) {
}
