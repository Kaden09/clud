package dev.file.clud.error;

import java.util.UUID;

public class NodeNotFoundException extends RuntimeException {

	public NodeNotFoundException(UUID nodeId) {
		super("Node %s was not found".formatted(nodeId));
	}
}
