package dev.identity.clud.user.dto;

import java.time.Instant;
import java.util.UUID;

import dev.identity.clud.security.principal.AuthenticatedUser;
import dev.identity.clud.user.User;

public record UserResponse(
        UUID id,
        String email,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getCreatedAt(), user.getUpdatedAt());
    }

    public static UserResponse from(AuthenticatedUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
