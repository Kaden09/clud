package dev.sharing.clud.event;

import java.time.Instant;
import java.util.UUID;

public record FileLifecycleEvent(
		UUID eventId,
		String eventType,
		int eventVersion,
		Instant occurredAt,
		UUID fileId,
		UUID ownerId,
		String storageKey) {
}
