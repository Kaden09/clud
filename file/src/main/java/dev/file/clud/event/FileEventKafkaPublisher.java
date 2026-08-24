package dev.file.clud.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FileEventKafkaPublisher {

	private final KafkaTemplate<String, Object> kafkaTemplate;
	private final String topic;

	public FileEventKafkaPublisher(
			KafkaTemplate<String, Object> kafkaTemplate,
			@Value("${clud.file.events-topic}") String topic) {
		this.kafkaTemplate = kafkaTemplate;
		this.topic = topic;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(FileLifecycleEvent event) {
		kafkaTemplate.send(topic, event.fileId().toString(), event);
	}
}
