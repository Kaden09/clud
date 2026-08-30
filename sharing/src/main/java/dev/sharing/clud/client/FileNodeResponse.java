package dev.sharing.clud.client;

import java.time.Instant;
import java.util.UUID;

public record FileNodeResponse(
		UUID id,
		UUID parentId,
		NodeType type,
		String name,
		String storageKey,
		String contentType,
		Long sizeBytes,
		Instant deletedAt,
		Instant createdAt,
		Instant updatedAt,
		long version) {

	public enum NodeType {
		FILE,
		FOLDER
	}
}
