package dev.file.clud.node;

import java.time.Instant;
import java.util.UUID;

public record NodeResponse(
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

	public static NodeResponse from(FileNode node) {
		return new NodeResponse(
				node.getId(),
				node.getParent() == null ? null : node.getParent().getId(),
				node.getType(),
				node.getName(),
				node.getStorageKey(),
				node.getContentType(),
				node.getSizeBytes(),
				node.getDeletedAt(),
				node.getCreatedAt(),
				node.getUpdatedAt(),
				node.getVersion());
	}
}
