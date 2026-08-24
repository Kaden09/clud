package dev.file.clud.node;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameNodeRequest(
		@NotBlank
		@Size(max = 255)
		String name) {
}
