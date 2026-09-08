package dev.sharing.clud.event;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SharingEventKafkaPublisher {

	private final KafkaTemplate<String, Object> kafkaTemplate;

	@Value("${clud.sharing.events-topic}")
	private String topic;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(FileSharedEvent event) {
		log.info("Publishing FileSharedEvent to topic='{}', fileId={}", topic, event.fileId());
		kafkaTemplate.send(topic, event.fileId().toString(), event);
	}
}
