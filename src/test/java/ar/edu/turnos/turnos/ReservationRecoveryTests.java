package ar.edu.turnos.turnos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.turnos.turnos.reservation.domain.model.CreatedHold;
import ar.edu.turnos.turnos.reservation.domain.model.OwnReservation;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.model.SlotOccupancy;
import ar.edu.turnos.turnos.reservation.domain.ports.in.RecoverPendingUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.CourseReservationsGateway;
import ar.edu.turnos.turnos.reservation.domain.ports.out.HoldGateway;
import ar.edu.turnos.turnos.reservation.domain.ports.out.RecoveryRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.SlotAvailability;
import ar.edu.turnos.turnos.reservation.domain.ports.out.SlotOccupancyLookup;
import ar.edu.turnos.turnos.reservation.infrastructure.persistence.repository.JpaReservationProcessRepository;
import ar.edu.turnos.turnos.user.infrastructure.persistence.entity.UserEntity;
import ar.edu.turnos.turnos.user.infrastructure.persistence.repository.JpaUserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationRecoveryTests.Stubs.class)
class ReservationRecoveryTests extends AbstractIntegrationTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class Stubs {
        @Bean @Primary CourseReservationsGateway courseStub() { return org.mockito.Mockito.mock(CourseReservationsGateway.class); }
        @Bean @Primary SlotOccupancyLookup occupancyStub() { return org.mockito.Mockito.mock(SlotOccupancyLookup.class); }
        @Bean @Primary SlotAvailability availabilityStub() { return org.mockito.Mockito.mock(SlotAvailability.class); }
        @Bean @Primary HoldGateway holdStub() { return org.mockito.Mockito.mock(HoldGateway.class); }
    }

    @Autowired RecoverPendingUseCase useCase;
    @Autowired RecoveryRepository recovery;
    @Autowired ReservationProcessRepository processes;
    @Autowired JpaReservationProcessRepository entities;
    @Autowired JpaUserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired CourseReservationsGateway course;
    @Autowired SlotOccupancyLookup occupancies;
    @Autowired SlotAvailability availability;
    @Autowired HoldGateway holds;
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    private long userId;
    private String local;
    private static final LocalDate DATE = LocalDate.of(2026, 12, 7);
    private static final LocalTime TIME = LocalTime.of(9, 30);

    @BeforeEach
    void prepare() {
        entities.deleteAll();
        users.deleteAll();
        reset(course, occupancies, availability, holds);
        userId = users.save(new UserEntity(null, "recovery-" + UUID.randomUUID().toString().substring(0, 8),
                "{argon2}not-used", "Ana", "Garcia", UUID.randomUUID() + "@example.com", null,
                "es", true, Set.of("ROLE_USER"), Instant.now(), Instant.now())).getId();
        local = UUID.randomUUID().toString();
    }

    @Test
    void reconcilesUnknownConfirmationWithoutInventingRequestEventId() {
        create(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN, "hold-1", "process-1", null, 60);
        when(course.list("user-" + userId, 0, 100)).thenReturn(new CourseReservationsGateway.Page(
                List.of(remote("process-1", "PHONE_PENDING", null)), 1, 1));
        useCase.recover();
        var process = processes.findById(local).orElseThrow();
        assertThat(process.status()).isEqualTo(ReservationProcess.Status.WAITING_FOR_PHONE);
        assertThat(process.requestEventId()).isNull();
        assertThat(recovery.get(local).orElseThrow().state().name()).isEqualTo("DONE");
    }

    @Test
    void recoversAuthoritativeConfirmationAndCancellationWithoutReplayingEffects() {
        create(ReservationProcess.Status.PHONE_SUBMITTED, "hold-1", "process-1", "request-1", 800);
        when(course.list("user-" + userId, 0, 100)).thenReturn(new CourseReservationsGateway.Page(
                List.of(remote("process-1", "CONFIRMED", 8251L)), 1, 1));
        useCase.recover();
        assertThat(processes.findById(local).orElseThrow().status()).isEqualTo(ReservationProcess.Status.CONFIRMED);
        assertThat(processes.findById(local).orElseThrow().reservationId()).isEqualTo(8251L);
        verify(holds, never()).create(anyLong(), any(), any(), anyString());
        verify(course, never()).cancel(anyString(), any());
    }

    @Test
    void uncertainCancellationWithStillConfirmedRemoteNeedsReviewWithoutReposting() {
        create(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN, "hold-1", "process-1", "request-1", 60);
        jdbc.update("UPDATE reservation_process SET reservation_id=8251 WHERE id=?", local);
        when(course.list("user-" + userId, 0, 100)).thenReturn(new CourseReservationsGateway.Page(
                List.of(remote("process-1", "CONFIRMED", 8251L)), 1, 1));
        useCase.recover();
        assertThat(processes.findById(local).orElseThrow().status())
                .isEqualTo(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN);
        assertThat(recovery.get(local).orElseThrow().state().name()).isEqualTo("REVIEW");
        assertThat(recovery.get(local).orElseThrow().lastErrorCode()).isEqualTo("CANCEL_OUTCOME_UNKNOWN");
        verify(course, never()).cancel(anyString(), any());
    }

    @Test
    void uncertainCancellationConvergesWhenCourseReportsCancelled() {
        create(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN, "hold-1", "process-1", "request-1", 60);
        jdbc.update("UPDATE reservation_process SET reservation_id=8251 WHERE id=?", local);
        var cancelled = new OwnReservation("process-1", 8251, 15, DATE, TIME, LocalTime.of(10, 0),
                "Ana", "Garcia", "CANCELLED", Instant.now(), Instant.parse("2026-11-10T14:00:00Z"),
                "Solicitud del paciente");
        when(course.list("user-" + userId, 0, 100)).thenReturn(new CourseReservationsGateway.Page(
                List.of(cancelled), 1, 1));
        useCase.recover();
        var process = processes.findById(local).orElseThrow();
        assertThat(process.status()).isEqualTo(ReservationProcess.Status.CANCELLED);
        assertThat(process.cancelledAt()).isEqualTo(Instant.parse("2026-11-10T14:00:00Z"));
        assertThat(recovery.get(local).orElseThrow().state().name()).isEqualTo("DONE");
        verify(course, never()).cancel(anyString(), any());
    }

    @Test
    void remoteSlotMismatchNeverChangesOwnershipOrClaimsConfirmation() {
        create(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN, "hold-1", "process-1", null, 60);
        var mismatched = new OwnReservation("process-1", 8251, 999, DATE, TIME, LocalTime.of(10, 0),
                "Ana", "Garcia", "CONFIRMED", Instant.now(), null, null);
        when(course.list("user-" + userId, 0, 100)).thenReturn(new CourseReservationsGateway.Page(
                List.of(mismatched), 1, 1));
        useCase.recover();
        assertThat(processes.findById(local).orElseThrow().status())
                .isEqualTo(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN);
        assertThat(recovery.get(local).orElseThrow().lastErrorCode()).isEqualTo("REMOTE_MISMATCH");
    }

    @Test
    void courseOutageSchedulesAnotherAttemptWithoutChangingTheProcess() {
        create(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN, "hold-1", "process-1", null, 60);
        when(course.list("user-" + userId, 0, 100)).thenThrow(new IllegalStateException("test connection lost"));
        useCase.recover();
        assertThat(processes.findById(local).orElseThrow().status())
                .isEqualTo(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN);
        assertThat(recovery.get(local).orElseThrow().state().name()).isEqualTo("READY");
        assertThat(recovery.get(local).orElseThrow().lastErrorCode()).isEqualTo("SOURCE_UNAVAILABLE");
    }

    @Test
    void missingHoldIdentifiersWaitForTheObservedHoldExpiryWithoutAttributingOwnership() {
        create(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN, null, null, null, 60);
        Instant expiresAt = Instant.now().plusSeconds(120);
        when(occupancies.get(15, DATE, TIME)).thenReturn(new SlotOccupancy(SlotOccupancy.Status.HELD, expiresAt));
        useCase.recover();
        assertThat(recovery.get(local).orElseThrow().nextAttemptAt()).isAfterOrEqualTo(expiresAt);
        assertThat(processes.findById(local).orElseThrow().holdId()).isNull();
        verify(holds, never()).create(anyLong(), any(), any(), anyString());
    }

    @Test
    void freeSlotAfterGraceAndTwoChecksReissuesOneBoundedHold() {
        create(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN, null, null, null, 750);
        when(occupancies.get(15, DATE, TIME)).thenReturn(new SlotOccupancy(SlotOccupancy.Status.FREE, null));
        useCase.recover();
        assertThat(recovery.get(local).orElseThrow().lastErrorCode()).isEqualTo("SLOT_FREE");
        jdbc.update("UPDATE reservation_recovery SET next_attempt_at=?, last_checked_at=? WHERE process_id=?",
                Timestamp.from(Instant.now().minusSeconds(1)), Timestamp.from(Instant.now().minusSeconds(20)), local);
        when(availability.isAvailable(15, DATE, TIME)).thenReturn(true);
        when(holds.create(15, DATE, TIME, "user-" + userId)).thenReturn(
                new CreatedHold("hold-new", "process-new", Instant.now().plusSeconds(600)));
        useCase.recover();
        var result = processes.findById(local).orElseThrow();
        assertThat(result.status()).isEqualTo(ReservationProcess.Status.HELD);
        assertThat(result.reservationProcessId()).isEqualTo("process-new");
        verify(holds).create(15, DATE, TIME, "user-" + userId);
    }

    @Test
    void confirmedOccupancyWithoutIdentifiersNeverClaimsTheReservation() {
        create(ReservationProcess.Status.HOLD_REQUESTING, null, null, null, 60);
        when(occupancies.get(15, DATE, TIME)).thenReturn(new SlotOccupancy(SlotOccupancy.Status.CONFIRMED, null));
        useCase.recover();
        assertThat(recovery.get(local).orElseThrow().state().name()).isEqualTo("REVIEW");
        assertThat(processes.findById(local).orElseThrow().status()).isEqualTo(ReservationProcess.Status.HOLD_REQUESTING);
        verify(holds, never()).create(anyLong(), any(), any(), anyString());
    }

    @Test
    void expiredProcessIsNotDeclaredExpiredFromTheClockAlone() {
        create(ReservationProcess.Status.WAITING_FOR_PHONE, "hold-1", "process-1", "request-1", 750);
        when(course.list("user-" + userId, 0, 100)).thenReturn(new CourseReservationsGateway.Page(List.of(), 0, 0));
        useCase.recover();
        assertThat(processes.findById(local).orElseThrow().status())
                .isEqualTo(ReservationProcess.Status.WAITING_FOR_PHONE);
        assertThat(recovery.get(local).orElseThrow().state().name()).isEqualTo("READY");
    }

    @Test
    void lostLeaseIsRecoveredAndAttemptsStopAfterTheBound() {
        create(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN, "hold-1", "process-1", null, 60);
        Instant first = Instant.now();
        recovery.discover(first);
        var claimed = recovery.claim(first).orElseThrow();
        assertThat(claimed.attempts()).isEqualTo(1);
        assertThat(recovery.claim(first.plusSeconds(1))).isEmpty();
        for (int attempt = 2; attempt <= 5; attempt++) {
            var recovered = recovery.claim(first.plusSeconds(601L * (attempt - 1))).orElseThrow();
            assertThat(recovered.processId()).isEqualTo(local);
            assertThat(recovered.attempts()).isEqualTo(attempt);
        }
        assertThat(recovery.claim(first.plusSeconds(601L * 5))).isEmpty();
        assertThat(recovery.get(local).orElseThrow().state().name()).isEqualTo("REVIEW");
        assertThat(recovery.get(local).orElseThrow().lastErrorCode()).isEqualTo("ATTEMPTS_EXHAUSTED");
    }

    @Test
    void recoveryDiagnosticIsVisibleOnlyToTheOwner() throws Exception {
        create(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN, "hold-1", "process-1", "request-1", 60);
        jdbc.update("UPDATE reservation_process SET reservation_id=8251 WHERE id=?", local);
        when(course.list("user-" + userId, 0, 100)).thenReturn(new CourseReservationsGateway.Page(
                List.of(remote("process-1", "CONFIRMED", 8251L)), 1, 1));
        useCase.recover();
        mvc.perform(get("/api/reservation-processes/{id}", local))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reservation-processes/{id}", local).header("Authorization", bearer(userId + 1)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reservation-processes/{id}", local).header("Authorization", bearer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryState").value("REVIEW"))
                .andExpect(jsonPath("$.recoveryAttempts").value(1))
                .andExpect(jsonPath("$.lastRecoveryErrorCode").value("CANCEL_OUTCOME_UNKNOWN"));
    }

    private String bearer(long id) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("backend-turnos").subject("user-" + id)
                .audience(List.of("backend-turnos", "backend-catalogo")).issuedAt(now)
                .expiresAt(now.plusSeconds(600)).claim("authorities", List.of("ROLE_USER")).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private void create(ReservationProcess.Status status, String holdId, String externalId, String requestId,
                        long ageSeconds) {
        Instant createdAt = Instant.now().minusSeconds(ageSeconds);
        processes.save(new ReservationProcess(local, userId, 15, DATE, TIME, status, holdId,
                externalId, Instant.now().minusSeconds(1), requestId, requestId == null ? null : "PHONE_NUMBER",
                null, createdAt, createdAt, null));
    }

    private static OwnReservation remote(String externalId, String status, Long reservationId) {
        return new OwnReservation(externalId, reservationId == null ? 8251 : reservationId, 15, DATE, TIME,
                LocalTime.of(10, 0), "Ana", "Garcia", status, Instant.now(), null, null);
    }
}
