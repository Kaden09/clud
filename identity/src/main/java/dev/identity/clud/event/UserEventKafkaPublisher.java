package dev.identity.clud.event;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserEventKafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${clud.identity.events-topic}")
    private String topic;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(UserRegisteredEvent event) {
        kafkaTemplate.send(topic, event.userId().toString(), event);
    }
}
