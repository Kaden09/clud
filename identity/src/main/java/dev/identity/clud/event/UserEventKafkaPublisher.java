package dev.identity.clud.event;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventKafkaPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final IdentityEventProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(UserRegisteredEvent event) {
        log.info("Publishing UserRegisteredEvent to topic='{}', userId={}", properties.eventsTopic(), event.userId());
        kafkaTemplate.send(properties.eventsTopic(), event.userId().toString(), event);
    }
}
