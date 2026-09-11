package dev.file.clud.event;

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
public class FileEventKafkaPublisher {
	private final KafkaTemplate<String, Object> kafkaTemplate;

	@Value("${clud.file.events-topic}")
	private String topic;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(FileLifecycleEvent event) {
		log.info("Publishing FileLifecycleEvent to topic='{}', fileId={}, eventType={}", topic, event.fileId(), event.eventType());
		kafkaTemplate.send(topic, event.fileId().toString(), event);
	}
}
