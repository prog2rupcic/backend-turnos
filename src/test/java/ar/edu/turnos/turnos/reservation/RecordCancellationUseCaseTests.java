package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.turnos.turnos.reservation.application.usecases.RecordCancellationUseCaseImpl;
import ar.edu.turnos.turnos.reservation.domain.model.CancellationNotice;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneExchangeRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecordCancellationUseCaseTests {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private final ReservationProcessRepository processes = mock(ReservationProcessRepository.class);
    private final PhoneExchangeRepository exchange = mock(PhoneExchangeRepository.class);
    private final RecordCancellationUseCaseImpl useCase = new RecordCancellationUseCaseImpl(processes, exchange,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void anUnknownProcessIsIgnored() {
        when(processes.findByReservationProcessId("process-1")).thenReturn(Optional.empty());

        assertThat(useCase.record(notice())).isEqualTo("UNKNOWN_PROCESS");
    }

    @Test
    void aDifferentHoldIsNeverApplied() {
        when(processes.findByReservationProcessId("process-1")).thenReturn(Optional.of(confirmed()));
        var other = new CancellationNotice("event-1", "process-1", "other-hold", "request-1", 8251, 15,
                LocalDate.of(2026, 12, 7), LocalTime.of(9, 30), null, NOW);

        assertThat(useCase.record(other)).isEqualTo("MISMATCH");
        verify(processes, never()).save(any());
    }

    @Test
    void aDifferentRequestEventIsNeverApplied() {
        when(processes.findByReservationProcessId("process-1")).thenReturn(Optional.of(confirmed()));
        var other = new CancellationNotice("event-1", "process-1", "hold-1", "other-request", 8251, 15,
                LocalDate.of(2026, 12, 7), LocalTime.of(9, 30), null, NOW);

        assertThat(useCase.record(other)).isEqualTo("MISMATCH");
    }

    @Test
    void aDifferentReservationIdIsNeverApplied() {
        when(processes.findByReservationProcessId("process-1")).thenReturn(Optional.of(confirmed()));
        var other = new CancellationNotice("event-1", "process-1", "hold-1", "request-1", 9999, 15,
                LocalDate.of(2026, 12, 7), LocalTime.of(9, 30), null, NOW);

        assertThat(useCase.record(other)).isEqualTo("MISMATCH");
    }

    @Test
    void aDifferentSlotIsNeverApplied() {
        when(processes.findByReservationProcessId("process-1")).thenReturn(Optional.of(confirmed()));
        var other = new CancellationNotice("event-1", "process-1", "hold-1", "request-1", 8251, 15,
                LocalDate.of(2026, 12, 8), LocalTime.of(9, 30), null, NOW);

        assertThat(useCase.record(other)).isEqualTo("MISMATCH");
    }

    @Test
    void aRepeatedEventIsDeduplicated() {
        when(processes.findByReservationProcessId("process-1")).thenReturn(Optional.of(confirmed()));
        when(exchange.markEvent("event-1", "local-1", NOW)).thenReturn(false);

        assertThat(useCase.record(notice())).isEqualTo("DUPLICATE");
        verify(processes, never()).save(any());
    }

    @Test
    void aConfirmedProcessIsCancelled() {
        when(processes.findByReservationProcessId("process-1")).thenReturn(Optional.of(confirmed()));
        when(exchange.markEvent("event-1", "local-1", NOW)).thenReturn(true);

        assertThat(useCase.record(notice())).isEqualTo("APPLIED");
        verify(exchange).abandonPending("local-1");
        verify(processes).save(any(ReservationProcess.class));
    }

    @Test
    void aProcessWithoutKnownReservationCanStillBeCancelledByTheAuthoritativeEvent() {
        when(processes.findByReservationProcessId("process-1")).thenReturn(Optional.of(
                new ReservationProcess("local-1", 42, 15, LocalDate.of(2026, 12, 7), LocalTime.of(9, 30),
                        ReservationProcess.Status.PHONE_SUBMITTED, "hold-1", "process-1", NOW.plusSeconds(600),
                        "request-1", "PHONE_NUMBER", null, NOW, NOW, null)));
        when(exchange.markEvent("event-1", "local-1", NOW)).thenReturn(true);

        assertThat(useCase.record(notice())).isEqualTo("APPLIED");
        verify(processes).save(any(ReservationProcess.class));
    }

    @Test
    void aTerminalProcessIsNotReopened() {
        when(processes.findByReservationProcessId("process-1")).thenReturn(Optional.of(
                confirmed().terminal(ReservationProcess.Status.EXPIRED, "HOLD_EXPIRED", NOW)));
        when(exchange.markEvent("event-1", "local-1", NOW)).thenReturn(true);

        assertThat(useCase.record(notice())).isEqualTo("IGNORED");
        verify(processes, never()).save(any());
    }

    @Test
    void anAlreadyCancelledProcessIsNotCancelledTwice() {
        when(processes.findByReservationProcessId("process-1")).thenReturn(Optional.of(
                confirmed().cancelled(8251L, NOW, "Cambio", NOW)));
        when(exchange.markEvent("event-1", "local-1", NOW)).thenReturn(true);

        assertThat(useCase.record(notice())).isEqualTo("IGNORED");
        verify(processes, never()).save(any());
    }

    private static CancellationNotice notice() {
        return new CancellationNotice("event-1", "process-1", "hold-1", "request-1", 8251, 15,
                LocalDate.of(2026, 12, 7), LocalTime.of(9, 30), null, NOW);
    }

    private static ReservationProcess confirmed() {
        var now = Instant.parse("2026-11-10T11:00:00Z");
        return new ReservationProcess("local-1", 42, 15, LocalDate.of(2026, 12, 7), LocalTime.of(9, 30),
                ReservationProcess.Status.CONFIRMED, "hold-1", "process-1", NOW.plusSeconds(600), "request-1",
                "PHONE_NUMBER", null, now, now, null).confirmed(8251L, now, now);
    }
}
