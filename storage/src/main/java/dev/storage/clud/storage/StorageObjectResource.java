package dev.storage.clud.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record StorageObjectResource(Resource resource, MediaType contentType, long sizeBytes) {
}
