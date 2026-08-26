package dev.file.clud.node.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateFileRequest(
		@NotBlank
		@Size(max = 255)
		String name,
		UUID parentId,
		@NotBlank
		@Size(max = 512)
		String storageKey,
		@NotBlank
		@Size(max = 255)
		String contentType,
		@PositiveOrZero
		long sizeBytes) {
}
