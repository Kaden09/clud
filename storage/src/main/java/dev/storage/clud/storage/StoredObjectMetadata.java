package dev.storage.clud.storage;

import org.springframework.http.MediaType;

public record StoredObjectMetadata(MediaType contentType, long sizeBytes) {
}
