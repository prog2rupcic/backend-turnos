package ar.edu.turnos.turnos;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import ar.edu.turnos.turnos.reservation.infrastructure.persistence.repository.JpaReservationProcessRepository;
import ar.edu.turnos.turnos.user.infrastructure.persistence.entity.UserEntity;
import ar.edu.turnos.turnos.user.infrastructure.persistence.repository.JpaUserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest(properties = {
        "turnos.kafka.enabled=true",
        "turnos.kafka.actions-topic=test-appointment-actions",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=test-phone-request-group",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"
})
@EmbeddedKafka(partitions = 1, topics = "test-appointment-actions")
class AdditionalInformationRequestKafkaTests extends AbstractIntegrationTest {
    private static final String TOPIC = "test-appointment-actions";
    private static final String PROCESS_ID = "process-kafka-1";
    private static final String HOLD_ID = "hold-kafka-1";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ReservationProcessRepository processes;
    @Autowired
    private JpaReservationProcessRepository processEntities;
    @Autowired
    private JpaUserRepository users;

    @BeforeEach
    void prepare() {
        processEntities.deleteAll();
        users.deleteAll();
        var user = users.save(new UserEntity(null, "kafka-" + UUID.randomUUID().toString().substring(0, 8),
                "{argon2}not-used", "Ana", "Garcia", "kafka-" + UUID.randomUUID() + "@example.com", null,
                "es", true, Set.of("ROLE_USER"), Instant.now(), Instant.now()));
        processes.save(new ReservationProcess(UUID.randomUUID().toString(), user.getId(), 15,
                LocalDate.of(2026, 11, 9), LocalTime.of(9, 30), ReservationProcess.Status.HELD, HOLD_ID,
                PROCESS_ID, Instant.now().plusSeconds(600), null, null, null, Instant.now(), Instant.now(), null));
    }

    @Test
    void appliesTheRequestAndIgnoresADuplicateDelivery() throws Exception {
        send(PROCESS_ID, payload("event-1", PROCESS_ID, HOLD_ID));
        ReservationProcess applied = awaitRequest("event-1");
        Instant appliedUpdatedAt = applied.updatedAt();

        send(PROCESS_ID, payload("event-1", PROCESS_ID, HOLD_ID));
        Awaitility.await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            ReservationProcess process = processes.findByReservationProcessId(PROCESS_ID).orElseThrow();
            assertThat(process.requestEventId()).isEqualTo("event-1");
            assertThat(process.updatedAt()).isEqualTo(appliedUpdatedAt);
        });
    }

    @Test
    void invalidMessageIsDiscardedWithoutBlockingThePartition() throws Exception {
        send(PROCESS_ID, "{\"eventType\":\"AdditionalInformationRequested\"}");

        send(PROCESS_ID, payload("event-2", PROCESS_ID, HOLD_ID));
        awaitRequest("event-2");
    }

    @Test
    void unknownProcessAndAnotherHoldDoNotChangeLocalState() throws Exception {
        send("unknown-process", payload("event-3", "unknown-process", HOLD_ID));
        send(PROCESS_ID, payload("event-4", PROCESS_ID, "other-hold"));

        send(PROCESS_ID, payload("event-5", PROCESS_ID, HOLD_ID));
        awaitRequest("event-5");

        assertThat(processes.findByReservationProcessId("unknown-process")).isEmpty();
        assertThat(processes.findByReservationProcessId(PROCESS_ID).orElseThrow().requestEventId())
                .isEqualTo("event-5");
    }

    private ReservationProcess awaitRequest(String eventId) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            ReservationProcess process = processes.findByReservationProcessId(PROCESS_ID).orElseThrow();
            assertThat(process.status()).isEqualTo(ReservationProcess.Status.WAITING_FOR_PHONE);
            assertThat(process.requestEventId()).isEqualTo(eventId);
            assertThat(process.requestRequiredField()).isEqualTo("PHONE_NUMBER");
            assertThat(process.requestMessage()).isEqualTo("Ingrese un teléfono");
        });
        return processes.findByReservationProcessId(PROCESS_ID).orElseThrow();
    }

    private void send(String key, String payload) throws Exception {
        kafkaTemplate.send(TOPIC, key, payload).get(5, TimeUnit.SECONDS);
    }

    private static String payload(String eventId, String processId, String holdId) {
        return """
                {"eventId":"%s","eventType":"AdditionalInformationRequested","schemaVersion":1,
                 "reservationProcessId":"%s","holdId":"%s","requiredField":"PHONE_NUMBER",
                 "message":"Ingrese un teléfono","expiresAt":"%s","occurredAt":"%s","extra":"ignored"}
                """.formatted(eventId, processId, holdId, Instant.now().plusSeconds(600), Instant.now());
    }
}
