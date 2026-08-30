package dev.sharing.clud.event;

import java.time.Instant;
import java.util.UUID;

import dev.sharing.clud.share.entity.ShareLink;

public record FileSharedEvent(
		UUID eventId,
		String eventType,
		int eventVersion,
		Instant occurredAt,
		UUID shareId,
		UUID fileId,
		UUID ownerId,
		Instant expiresAt) {

	public static FileSharedEvent from(ShareLink link) {
		return new FileSharedEvent(
				UUID.randomUUID(),
				"FileShared",
				1,
				Instant.now(),
				link.getId(),
				link.getFileId(),
				link.getOwnerId(),
				link.getExpiresAt());
	}
}
