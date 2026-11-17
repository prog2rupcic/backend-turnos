package ar.edu.turnos.turnos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.turnos.turnos.reservation.domain.model.PhoneExchangeException;
import ar.edu.turnos.turnos.reservation.domain.model.CancellationNotice;
import ar.edu.turnos.turnos.reservation.domain.model.PhoneResult;
import ar.edu.turnos.turnos.reservation.domain.model.PhoneSubmission;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.in.GetReservationProcessUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.in.PublishPhoneOutboxUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.in.RecordPhoneResultUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.in.RecordCancellationUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.in.SubmitPhoneUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneExchangeRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import ar.edu.turnos.turnos.reservation.infrastructure.persistence.repository.JpaReservationProcessRepository;
import ar.edu.turnos.turnos.user.infrastructure.persistence.entity.UserEntity;
import ar.edu.turnos.turnos.user.infrastructure.persistence.repository.JpaUserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "turnos.kafka.enabled=true", "turnos.kafka.actions-topic=phone-exchange-actions",
        "turnos.kafka.phone-topic=phone-exchange-outgoing", "turnos.integration.group-id=test-integration",
        "turnos.outbox.interval=3600000", "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=phone-exchange-group"
})
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {"phone-exchange-actions", "phone-exchange-outgoing"})
class PhoneExchangeIntegrationTests extends AbstractIntegrationTest {
    @Autowired SubmitPhoneUseCase submit;
    @Autowired GetReservationProcessUseCase query;
    @Autowired PublishPhoneOutboxUseCase outbox;
    @Autowired RecordPhoneResultUseCase results;
    @Autowired RecordCancellationUseCase cancellations;
    @Autowired PhoneExchangeRepository exchange;
    @Autowired ReservationProcessRepository processes;
    @Autowired JpaReservationProcessRepository entities;
    @Autowired JpaUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired PlatformTransactionManager transactions;
    private String id;
    private String external;
    private long owner;
    private Instant expiration;

    @BeforeEach
    void prepare() {
        entities.deleteAll();
        users.deleteAll();
        var user = users.save(new UserEntity(null, "phone-" + UUID.randomUUID().toString().substring(0, 8),
                "{argon2}not-used", "Ana", "Garcia", UUID.randomUUID() + "@example.com", null,
                "es", true, Set.of("ROLE_USER"), Instant.now(), Instant.now()));
        owner = user.getId();
        id = UUID.randomUUID().toString();
        external = "process-" + UUID.randomUUID();
        expiration = Instant.now().plusSeconds(600);
        processes.save(new ReservationProcess(id, owner, 15, LocalDate.of(2026, 11, 9), LocalTime.of(9, 30),
                ReservationProcess.Status.WAITING_FOR_PHONE, "hold-test", external, expiration,
                "request-test", "PHONE_NUMBER", "Ingrese un teléfono", Instant.now(), Instant.now(), null));
    }

    @Test
    void durablePublicationUsesTheExactContractAndKey() {
        var props = KafkaTestUtils.consumerProps("outgoing-" + UUID.randomUUID(), "false", broker);
        try (var consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "phone-exchange-outgoing");
            submit.submit(id, owner, "+54 (261) 555-1234");
            var saved = exchange.latest(id).orElseThrow();
            assertThat(saved.deliveryStatus()).isEqualTo(PhoneSubmission.DeliveryStatus.PENDING);
            outbox.publishPending();
            var record = KafkaTestUtils.getSingleRecord(consumer, "phone-exchange-outgoing", Duration.ofSeconds(15));
            var json = JsonMapper.builder().build().readTree(record.value());
            assertThat(record.key()).isEqualTo(external);
            assertThat(json.path("eventId").asText()).isEqualTo(saved.eventId());
            assertThat(json.path("eventType").asText()).isEqualTo("AdditionalInformationSubmitted");
            assertThat(json.path("schemaVersion").asInt()).isEqualTo(1);
            assertThat(json.path("groupId").asText()).isEqualTo("test-integration");
            assertThat(json.path("holdId").asText()).isEqualTo("hold-test");
            assertThat(json.path("requestEventId").asText()).isEqualTo("request-test");
            assertThat(json.path("information").path("phoneNumber").asText()).isEqualTo("+542615551234");
            assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.PHONE_SUBMITTED);
        }
    }

    @Test
    void httpRequiresRoleAndOwnershipAndNeverExposesThePhone() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(post(path() + "/phone").contentType("application/json").content("{\"phoneNumber\":\"1234567\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path()).header("Authorization", token(owner + 100))).andExpect(status().isForbidden());
        mvc.perform(post(path() + "/phone").header("Authorization", token(owner + 100))
                .contentType("application/json").content("{\"phoneNumber\":\"1234567\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(path()).header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("canSubmitPhone").value(true));
        mvc.perform(get("/api/reservation-processes/missing").header("Authorization", token(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(post(path() + "/phone").header("Authorization", token(owner))
                .contentType("application/json").content("{\"phoneNumber\":\"bad-number\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path() + "/phone").header("Authorization", token(owner))
                .contentType("application/json").content("{\"phoneNumber\":\"1234567\",\"ownerUserId\":999}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("status").value("PHONE_SUBMISSION_PENDING"))
                .andExpect(jsonPath("phoneNumber").doesNotExist());
    }

    @Test
    void restWaitingStateWithoutKafkaRequestDoesNotEnableSubmission() {
        jdbc.update("UPDATE reservation_process SET request_event_id=null WHERE id=?", id);
        assertThat(query.get(id, owner).canSubmitPhone()).isFalse();
        assertThatThrownBy(() -> submit.submit(id, owner, "1234567"))
                .isInstanceOf(PhoneExchangeException.class).hasMessage("REQUEST_NOT_RECEIVED");
        assertThat(exchange.latest(id)).isEmpty();
    }

    @Test
    void rollbackCannotLeaveAnOrphanOutboxOrAnAdvancedProcess() {
        var transaction = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.executeWithoutResult(_ -> {
            submit.submit(id, owner, "1234567");
            throw new IllegalStateException("simulate crash before commit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(exchange.latest(id)).isEmpty();
        assertThat(query.get(id, owner).canSubmitPhone()).isTrue();
    }

    @Test
    void concurrentSubmissionsAndHttpRetriesKeepOneDurableEvent() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> submit.submit(id, owner, "1234567"));
            var b = executor.submit(() -> submit.submit(id, owner, "1234567"));
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation_phone_submission WHERE process_id=?",
                Integer.class, id)).isEqualTo(1);
        assertThatThrownBy(() -> submit.submit(id, owner, "7654321"))
                .hasMessage("SUBMISSION_IN_PROGRESS");
    }

    @Test
    void expiredDeadlineBlocksNewSubmissionsWithoutInventingATerminalState() {
        jdbc.update("UPDATE reservation_process SET expires_at=now()-interval '1 second' WHERE id=?", id);
        assertThat(query.get(id, owner).canSubmitPhone()).isFalse();
        assertThatThrownBy(() -> submit.submit(id, owner, "1234567")).hasMessage("DEADLINE_PASSED");
        assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.WAITING_FOR_PHONE);
    }

    @Test
    void aCrashAfterClaimRecoversTheSameEventAndPayloadAfterTheLease() {
        submit.submit(id, owner, "1234567");
        var first = exchange.claim(Instant.now()).orElseThrow();
        assertThat(exchange.claim(Instant.now())).isEmpty();
        var recovered = exchange.claim(Instant.now().plusSeconds(61)).orElseThrow();
        assertThat(recovered.eventId()).isEqualTo(first.eventId());
        assertThat(recovered.phoneNumber()).isEqualTo(first.phoneNumber());
        assertThat(recovered.occurredAt()).isEqualTo(first.occurredAt());
        assertThat(recovered.attempts()).isEqualTo(2);
    }

    @Test
    void exhaustedDeliveryCanBeExplicitlyRetriedWithoutChangingItsEventId() {
        submit.submit(id, owner, "1234567");
        var pending = exchange.latest(id).orElseThrow();
        for (int attempt = 0; attempt < 5; attempt++) {
            var now = Instant.now().plusSeconds(attempt * 40L);
            var claimed = exchange.claim(now).orElseThrow();
            exchange.failed(claimed, now);
        }
        assertThat(exchange.latest(id).orElseThrow().deliveryStatus())
                .isEqualTo(PhoneSubmission.DeliveryStatus.RETRY_EXHAUSTED);
        submit.submit(id, owner, "1234567");
        var retried = exchange.latest(id).orElseThrow();
        assertThat(retried.eventId()).isEqualTo(pending.eventId());
        assertThat(retried.deliveryStatus()).isEqualTo(PhoneSubmission.DeliveryStatus.PENDING);
    }

    @Test
    void unsentExpiredOutboxIsAbandonedWithoutExpiringTheProcess() {
        submit.submit(id, owner, "1234567");
        assertThat(exchange.claim(expiration.plusSeconds(1))).isEmpty();
        assertThat(exchange.latest(id).orElseThrow().deliveryStatus()).isEqualTo(PhoneSubmission.DeliveryStatus.ABANDONED);
        assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.PHONE_SUBMISSION_PENDING);
    }

    @Test
    void confirmationBeforeTheProducerCallbackNeverMovesBackwards() {
        submit.submit(id, owner, "1234567");
        var claimed = exchange.claim(Instant.now()).orElseThrow();
        assertThat(results.record(confirmed("confirmation-race"))).isEqualTo("APPLIED");
        exchange.published(claimed, Instant.now());
        exchange.failed(claimed, Instant.now());
        assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.CONFIRMED);
        assertThat(query.get(id, owner).process().reservationId()).isEqualTo(8251);
        assertThat(exchange.latest(id).orElseThrow().deliveryStatus()).isEqualTo(PhoneSubmission.DeliveryStatus.COMPLETED);
    }

    @Test
    void rejectionAllowsANewEventAndOldDuplicatesCannotRejectTheNewAttempt() {
        submit.submit(id, owner, "1234567");
        var first = exchange.latest(id).orElseThrow();
        var rejection = rejected("rejected-1");
        assertThat(results.record(rejection)).isEqualTo("APPLIED");
        assertThat(query.get(id, owner).canSubmitPhone()).isTrue();
        assertThat(query.get(id, owner).process().rejectionReason()).isEqualTo("INVALID_PHONE_NUMBER");
        submit.submit(id, owner, "7654321");
        var second = exchange.latest(id).orElseThrow();
        assertThat(second.eventId()).isNotEqualTo(first.eventId());
        assertThat(results.record(rejection)).isEqualTo("DUPLICATE");
        assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.PHONE_SUBMISSION_PENDING);
    }

    @Test
    void kafkaDispatchesRejectionAndConfirmationAndSkipsInvalidMessages() throws Exception {
        submit.submit(id, owner, "1234567");
        send("{\"eventType\":\"AppointmentConfirmed\"}");
        String rejection = """
                {"eventId":"kafka-reject","eventType":"AdditionalInformationRejected","schemaVersion":1,
                 "groupId":"test-integration","reservationProcessId":"%s","holdId":"hold-test",
                 "requestEventId":"request-test","reason":"INVALID_PHONE_NUMBER","message":"Corrija el teléfono",
                 "expiresAt":"%s","occurredAt":"%s","unknown":"ignored"}
                """.formatted(external, expiration, Instant.now());
        send(rejection);
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(query.get(id, owner).process().rejectionReason()).isEqualTo("INVALID_PHONE_NUMBER"));
        submit.submit(id, owner, "7654321");
        send("""
                {"eventId":"kafka-confirm","eventType":"AppointmentConfirmed","schemaVersion":1,
                 "groupId":"test-integration","reservationProcessId":"%s","holdId":"hold-test",
                 "requestEventId":"request-test","reservationId":8251,"professionalId":15,
                 "date":"2026-11-09","startTime":"09:30","status":"CONFIRMED","occurredAt":"%s"}
                """.formatted(external, Instant.now()));
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.CONFIRMED));
        send(rejection);
        assertThat(results.record(confirmed("another-confirm"))).isEqualTo("IGNORED");
    }

    @Test
    void unknownOrMismatchedResultsDoNotChangeTheProcess() {
        var c = confirmed("bad-hold");
        var wrong = new PhoneResult(c.eventId(), c.groupId(), external, "other-hold", c.requestEventId(),
                c.reservationId(), c.professionalId(), c.date(), c.startTime(), null, null, null, c.occurredAt());
        assertThat(results.record(wrong)).isEqualTo("MISMATCH");
        assertThat(query.get(id, owner).canSubmitPhone()).isTrue();
    }

    @Test
    void kafkaCancellationConvergesWithoutReopeningOrDuplicatingTheProcess() throws Exception {
        var current = processes.findById(id).orElseThrow();
        processes.save(current.confirmed(8251, Instant.now(), Instant.now()));
        var event = """
                {"eventId":"cancel-kafka","eventType":"AppointmentCancelled","schemaVersion":1,
                 "groupId":"test-integration","reservationProcessId":"%s","holdId":"hold-test",
                 "requestEventId":"request-test","reservationId":8251,"professionalId":15,
                 "date":"2026-11-09","startTime":"09:30","status":"CANCELLED",
                 "reason":null,"occurredAt":"2026-11-10T14:00:00Z","extra":"ignored"}
                """.formatted(external);
        send(event);
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.CANCELLED);
            assertThat(query.get(id, owner).process().cancelledAt())
                    .isEqualTo(Instant.parse("2026-11-10T14:00:00Z"));
        });
        var notice = new CancellationNotice("cancel-kafka", external, "hold-test", "request-test", 8251, 15,
                LocalDate.of(2026, 11, 9), LocalTime.of(9, 30), null, Instant.parse("2026-11-10T14:00:00Z"));
        assertThat(cancellations.record(notice)).isEqualTo("DUPLICATE");
        assertThat(results.record(confirmed("late-confirm-after-cancel"))).isEqualTo("IGNORED");
        assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.CANCELLED);
        mvc.perform(get(path()).header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("CANCELLED"));
        mvc.perform(get(path()).header("Authorization", token(owner + 1))).andExpect(status().isForbidden());
    }

    @Test
    void mismatchedKafkaCancellationNeverAffectsAnotherReservation() {
        var current = processes.findById(id).orElseThrow();
        processes.save(current.confirmed(8251, Instant.now(), Instant.now()));
        var wrong = new CancellationNotice("cancel-wrong", external, "hold-test", "request-test", 9999, 15,
                LocalDate.of(2026, 11, 9), LocalTime.of(9, 30), null, Instant.now());
        assertThat(cancellations.record(wrong)).isEqualTo("MISMATCH");
        assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.CONFIRMED);
    }

    @Test
    void cancellationConvergesWhenConfirmationKafkaNotificationWasLost() {
        var notice = new CancellationNotice("cancel-without-confirmation", external, "hold-test", "request-test",
                8251, 15, LocalDate.of(2026, 11, 9), LocalTime.of(9, 30), null, Instant.now());
        assertThat(cancellations.record(notice)).isEqualTo("APPLIED");
        var updated = query.get(id, owner).process();
        assertThat(updated.status()).isEqualTo(ReservationProcess.Status.CANCELLED);
        assertThat(updated.reservationId()).isEqualTo(8251);
        assertThat(results.record(confirmed("late-confirmation-after-cancellation"))).isEqualTo("IGNORED");
    }

    @Test
    void expirationIsIdempotentTerminalAndAbandonsUnsentPhoneOutbox() throws Exception {
        submit.submit(id, owner, "1234567");
        var pending = exchange.latest(id).orElseThrow();
        var expired = terminal("expired-event", PhoneResult.Type.EXPIRED, "HOLD_EXPIRED");

        assertThat(results.record(expired)).isEqualTo("APPLIED");
        assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.EXPIRED);
        assertThat(query.get(id, owner).process().terminalReason()).isEqualTo("HOLD_EXPIRED");
        assertThat(query.get(id, owner).canSubmitPhone()).isFalse();
        assertThat(exchange.latest(id).orElseThrow().deliveryStatus())
                .isEqualTo(PhoneSubmission.DeliveryStatus.ABANDONED);
        assertThat(exchange.claim(Instant.now())).isEmpty();
        assertThat(results.record(expired)).isEqualTo("DUPLICATE");
        assertThat(results.record(confirmed("late-confirmation"))).isEqualTo("IGNORED");
        assertThat(query.get(id, owner).process().terminalReason()).isEqualTo("HOLD_EXPIRED");
        assertThat(exchange.latest(id).orElseThrow().eventId()).isEqualTo(pending.eventId());

        mvc.perform(get(path()).header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("status").value("EXPIRED"))
                .andExpect(jsonPath("terminalReason").value("HOLD_EXPIRED"))
                .andExpect(jsonPath("canSubmitPhone").value(false));
    }

    @Test
    void invalidationIsDistinctFromFailureAndCannotBeReopened() {
        var invalid = terminal("invalid-event", PhoneResult.Type.INVALID, "REQUEST_EVENT_MISMATCH");
        assertThat(results.record(invalid)).isEqualTo("APPLIED");
        assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.INVALID);
        assertThat(query.get(id, owner).process().terminalReason()).isEqualTo("REQUEST_EVENT_MISMATCH");
        assertThatThrownBy(() -> submit.submit(id, owner, "1234567"))
                .isInstanceOf(PhoneExchangeException.class).hasMessage("INVALID_STATE");
        assertThat(results.record(invalid)).isEqualTo("DUPLICATE");
        assertThat(results.record(rejected("late-rejection"))).isEqualTo("IGNORED");
        assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.INVALID);
    }

    @Test
    void requestEventMismatchInvalidationCanCorrelateByProcessAndHold() {
        var mismatchedRequestEvent = new PhoneResult("invalid-request-event", "test-integration", external,
                "hold-test", "request-id-rejected-by-course", null, null, null, null,
                "REQUEST_EVENT_MISMATCH", null, null, Instant.now(), PhoneResult.Type.INVALID);
        assertThat(results.record(mismatchedRequestEvent)).isEqualTo("APPLIED");
        assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.INVALID);
        assertThat(query.get(id, owner).process().terminalReason()).isEqualTo("REQUEST_EVENT_MISMATCH");
    }

    @Test
    void kafkaExpirationAndInvalidationEventsAreDispatchedAndPersisted() throws Exception {
        send(terminalPayload("kafka-expired", "AppointmentProcessExpired", "HOLD_EXPIRED"));
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.EXPIRED);
            assertThat(query.get(id, owner).process().terminalReason()).isEqualTo("HOLD_EXPIRED");
        });
        send(terminalPayload("kafka-invalid", "AppointmentProcessInvalid", "REQUEST_EVENT_MISMATCH"));
        assertThat(results.record(terminal("other-expiry", PhoneResult.Type.EXPIRED, "LATE")))
                .isEqualTo("IGNORED");
        assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.EXPIRED);
    }

    @Test
    void kafkaInvalidationStoresReasonAndIgnoresDuplicates() throws Exception {
        String payload = terminalPayload("kafka-invalid", "AppointmentProcessInvalid", "REQUEST_EVENT_MISMATCH");
        send(payload);
        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.INVALID);
            assertThat(query.get(id, owner).process().terminalReason()).isEqualTo("REQUEST_EVENT_MISMATCH");
        });
        send(payload);
        Awaitility.await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(query.get(id, owner).process().status()).isEqualTo(ReservationProcess.Status.INVALID));
    }

    private String path() { return "/api/reservation-processes/" + id; }

    private PhoneResult confirmed(String eventId) {
        return new PhoneResult(eventId, "test-integration", external, "hold-test", "request-test", 8251L, 15L,
                LocalDate.of(2026, 11, 9), LocalTime.of(9, 30), null, null, null, Instant.now());
    }

    private PhoneResult rejected(String eventId) {
        return new PhoneResult(eventId, "test-integration", external, "hold-test", "request-test", null, null,
                null, null, "INVALID_PHONE_NUMBER", "Corrija el teléfono", expiration, Instant.now());
    }

    private PhoneResult terminal(String eventId, PhoneResult.Type type, String reason) {
        return new PhoneResult(eventId, "test-integration", external, "hold-test", "request-test", null, null,
                null, null, reason, null, null, Instant.now(), type);
    }

    private String terminalPayload(String eventId, String type, String reason) {
        return """
                {"eventId":"%s","eventType":"%s","schemaVersion":1,"groupId":"test-integration",
                 "reservationProcessId":"%s","holdId":"hold-test","requestEventId":"request-test",
                 "reason":"%s","occurredAt":"%s","futureField":"ignored"}
                """.formatted(eventId, type, external, reason, Instant.now());
    }

    private void send(String payload) throws Exception {
        kafka.send("phone-exchange-actions", external, payload).get(10, TimeUnit.SECONDS);
    }

    private String token(long userId) {
        var claims = JwtClaimsSet.builder().issuer("backend-turnos").subject("user-" + userId)
                .audience(List.of("backend-turnos", "backend-catalogo")).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600)).claim("authorities", List.of("ROLE_USER")).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
