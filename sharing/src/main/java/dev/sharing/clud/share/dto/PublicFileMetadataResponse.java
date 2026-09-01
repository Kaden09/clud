package dev.sharing.clud.share.dto;

import java.time.Instant;
import java.util.UUID;

public record PublicFileMetadataResponse(
        UUID fileId,
        String name,
        String contentType,
        long sizeBytes,
        boolean previewAvailable,
        String previewUrl,
        String downloadUrl,
        Instant expiresAt) {
}
