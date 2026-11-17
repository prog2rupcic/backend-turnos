package ar.edu.turnos.turnos.reservation.infrastructure.kafka;

import ar.edu.turnos.turnos.reservation.domain.model.PhoneSubmission;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneEventPublisher;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class KafkaPhoneEventPublisher implements PhoneEventPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final String groupId;
    private final String topic;
    private final boolean enabled;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public KafkaPhoneEventPublisher(KafkaTemplate<String, String> kafka,
                                   @Value("${turnos.integration.group-id:}") String groupId,
                                   @Value("${turnos.kafka.phone-topic:}") String topic,
                                   @Value("${turnos.kafka.enabled:false}") boolean enabled) {
        this.kafka = kafka;
        this.groupId = groupId;
        this.topic = topic;
        this.enabled = enabled;
    }

    @Override
    public String groupId() { return enabled && !topic.isBlank() ? groupId : ""; }

    @Override
    public void publish(PhoneSubmission submission) {
        if (groupId().isBlank()) { throw new IllegalStateException("Publicación Kafka no configurada"); }
        String payload = JSON.writeValueAsString(Map.of("eventId", submission.eventId(),
                "eventType", "AdditionalInformationSubmitted", "groupId", submission.groupId(),
                "reservationProcessId", submission.reservationProcessId(), "holdId", submission.holdId(),
                "requestEventId", submission.requestEventId(), "information", Map.of("phoneNumber", submission.phoneNumber()),
                "occurredAt", submission.occurredAt().toString(), "schemaVersion", 1));
        try {
            kafka.send(topic, submission.reservationProcessId(), payload).get(20, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicación interrumpida");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            // Do not propagate a Kafka exception containing the record or phone number to logs/HTTP.
            throw new IllegalStateException("Resultado de publicación incierto");
        }
    }
}
