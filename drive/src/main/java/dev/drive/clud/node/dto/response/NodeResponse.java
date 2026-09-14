package dev.drive.clud.node.dto.response;

import java.time.Instant;
import java.util.UUID;

import dev.drive.clud.node.entity.NodeType;

public record NodeResponse(
        UUID id,
        UUID parentId,
        NodeType type,
        String name,
        String contentType,
        Long sizeBytes,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
