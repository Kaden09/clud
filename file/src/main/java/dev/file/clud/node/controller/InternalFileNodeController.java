package dev.file.clud.node.controller;

import java.util.UUID;

import dev.file.clud.node.dto.response.InternalNodeResponse;
import dev.file.clud.node.entity.FileNode;
import dev.file.clud.node.service.FileNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/nodes")
@RequiredArgsConstructor
public class InternalFileNodeController {

    private static final String USER_ID_HEADER = "X-User-ID";

    private final FileNodeService service;

    @GetMapping("/{nodeId}")
    InternalNodeResponse getNode(
            @RequestHeader(USER_ID_HEADER) UUID ownerId,
            @PathVariable UUID nodeId) {
        FileNode node = service.get(ownerId, nodeId);
        return new InternalNodeResponse(
                node.getId(),
                node.getParent() == null ? null : node.getParent().getId(),
                node.getType(),
                node.getName(),
                node.getStorageKey(),
                node.getContentType(),
                node.getSizeBytes(),
                node.getDeletedAt(),
                node.getCreatedAt(),
                node.getUpdatedAt(),
                node.getVersion());
    }
}
