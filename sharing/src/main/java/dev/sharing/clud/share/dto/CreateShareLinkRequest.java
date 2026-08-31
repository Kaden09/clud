package dev.sharing.clud.share.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

public record CreateShareLinkRequest(
		@NotNull(message = "File ID is required")
		UUID fileId,
		@Future(message = "Expiration must be in the future")
		Instant expiresAt) {
}
