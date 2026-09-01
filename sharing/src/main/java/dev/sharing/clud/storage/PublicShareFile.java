package dev.sharing.clud.storage;

import java.util.UUID;
import java.time.Instant;

public record PublicShareFile(
		UUID fileId,
		String storageKey,
		String name,
		String contentType,
		Long sizeBytes,
		Instant expiresAt) {
}
