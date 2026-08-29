package dev.file.clud.node.dto.response;

import java.time.Instant;
import java.util.UUID;

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
}
