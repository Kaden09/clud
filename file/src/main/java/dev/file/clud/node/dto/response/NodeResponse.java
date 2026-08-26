package dev.file.clud.node.dto.response;

import java.time.Instant;
import java.util.UUID;

import dev.file.clud.node.entity.FileNode;
import dev.file.clud.node.entity.NodeType;

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
