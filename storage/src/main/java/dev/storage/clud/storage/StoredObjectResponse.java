package dev.storage.clud.storage;

public record StoredObjectResponse(String storageKey, String contentType, long sizeBytes) {
}
