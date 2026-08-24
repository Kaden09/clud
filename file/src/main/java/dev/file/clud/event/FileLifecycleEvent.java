package dev.file.clud.event;

import java.time.Instant;
import java.util.UUID;

import dev.file.clud.node.FileNode;

public record FileLifecycleEvent(
		UUID eventId,
		String eventType,
		int eventVersion,
		Instant occurredAt,
		UUID fileId,
		UUID ownerId,
		String storageKey) {

	public static FileLifecycleEvent uploaded(FileNode file) {
		return create("FileUploaded", file);
	}

	public static FileLifecycleEvent deleted(FileNode file) {
		return create("FileDeleted", file);
	}

	private static FileLifecycleEvent create(String eventType, FileNode file) {
		return new FileLifecycleEvent(
				UUID.randomUUID(),
				eventType,
				1,
				Instant.now(),
				file.getId(),
				file.getOwnerId(),
				file.getStorageKey());
	}
}
