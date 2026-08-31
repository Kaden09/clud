package dev.sharing.clud.storage;

import java.util.UUID;

public record PublicShareFile(
		UUID fileId,
		String storageKey,
		String name,
		String contentType,
		Long sizeBytes) {
}
