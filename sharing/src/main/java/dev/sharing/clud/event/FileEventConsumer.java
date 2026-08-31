package dev.sharing.clud.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import dev.sharing.clud.share.service.ShareLinkService;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileEventConsumer {

	private final ShareLinkService shareLinkService;

	@KafkaListener(topics = "${clud.sharing.file-events-topic}")
	public void consume(FileLifecycleEvent event) {
		if (!"FileDeleted".equals(event.eventType())) {
			return;
		}

		int revokedLinks = shareLinkService.revokeAllForFile(event.ownerId(), event.fileId());
		log.info("Revoked public links after FileDeleted: fileId={}, links={}", event.fileId(), revokedLinks);
	}
}
