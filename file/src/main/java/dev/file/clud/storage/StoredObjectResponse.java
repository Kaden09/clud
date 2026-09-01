package dev.file.clud.storage;

public record StoredObjectResponse(String storageKey, String contentType, long sizeBytes) {
}
