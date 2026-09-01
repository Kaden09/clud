package dev.file.clud.node.mapper;

import dev.file.clud.node.dto.response.NodeResponse;
import dev.file.clud.node.dto.response.PageResponse;
import dev.file.clud.node.entity.FileNode;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class NodeMapper {
    public NodeResponse toResponse(FileNode node) {
        if (node == null) {
            return null;
        }
        return new NodeResponse(
                node.getId(),
                node.getParent() == null ? null : node.getParent().getId(),
                node.getType(),
                node.getName(),
                node.getContentType(),
                node.getSizeBytes(),
                node.getDeletedAt(),
                node.getCreatedAt(),
                node.getUpdatedAt(),
                node.getVersion());
    }

    public PageResponse<NodeResponse> toPageResponse(Page<FileNode> nodes) {
        return new PageResponse<>(
                nodes.map(this::toResponse).getContent(),
                nodes.getNumber(),
                nodes.getSize(),
                nodes.getTotalElements(),
                nodes.getTotalPages());
    }
}
