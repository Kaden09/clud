package dev.drive.clud.node.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameNodeRequest(
        @NotBlank
        @Size(max = 255)
        String name
) {
}
