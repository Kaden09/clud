package dev.file.clud.content;

public record FileDownload(
        String name,
        String storageKey,
        String contentType,
        long sizeBytes) {
}
