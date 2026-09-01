package dev.storage.clud.storage;

public record StoredObject(String storageKey, String contentType, long sizeBytes) {
}
