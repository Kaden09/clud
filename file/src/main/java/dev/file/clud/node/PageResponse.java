package dev.file.clud.node;

import java.util.List;

import org.springframework.data.domain.Page;

public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public static PageResponse<NodeResponse> fromNodes(Page<FileNode> nodes) {
		return new PageResponse<>(
				nodes.map(NodeResponse::from).getContent(),
				nodes.getNumber(),
				nodes.getSize(),
				nodes.getTotalElements(),
				nodes.getTotalPages());
	}
}
