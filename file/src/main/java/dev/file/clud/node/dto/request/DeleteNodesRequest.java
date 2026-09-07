package dev.file.clud.node.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record DeleteNodesRequest(@NotEmpty List<UUID> nodeIds) {
}
