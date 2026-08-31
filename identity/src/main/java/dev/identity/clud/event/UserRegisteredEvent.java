package dev.identity.clud.event;

import java.time.Instant;
import java.util.UUID;

import dev.identity.clud.user.User;

public record UserRegisteredEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID userId,
        String email) {

    public static UserRegisteredEvent from(User user) {
        return new UserRegisteredEvent(
                UUID.randomUUID(),
                "UserRegistered",
                1,
                Instant.now(),
                user.getId(),
                user.getEmail());
    }
}
