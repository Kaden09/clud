package dev.file.clud.node.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFolderRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        UUID parentId) {
}
