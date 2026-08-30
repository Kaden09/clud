package dev.sharing.clud.share.dto;

import java.time.Instant;
import java.util.UUID;

public record CreatedShareLinkResponse(
		UUID id,
		UUID fileId,
		String publicUrl,
		Instant expiresAt,
		Instant createdAt) {
}
