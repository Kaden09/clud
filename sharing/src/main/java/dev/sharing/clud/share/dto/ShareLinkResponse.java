package dev.sharing.clud.share.dto;

import java.time.Instant;
import java.util.UUID;

public record ShareLinkResponse(
		UUID id,
		UUID fileId,
		Instant expiresAt,
		Instant createdAt) {
}
