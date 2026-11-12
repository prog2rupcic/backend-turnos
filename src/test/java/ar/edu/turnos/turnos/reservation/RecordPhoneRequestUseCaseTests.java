package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.turnos.turnos.reservation.application.usecases.RecordPhoneRequestUseCaseImpl;
import ar.edu.turnos.turnos.reservation.domain.model.AdditionalInformationRequest;
import ar.edu.turnos.turnos.reservation.domain.model.PhoneRequestOutcome;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecordPhoneRequestUseCaseTests {
    private static final Instant NOW = Instant.parse("2026-11-03T17:00:00Z");
    private static final Instant REQUEST_EXPIRES_AT = NOW.plusSeconds(600);
    private static final String PROCESS_ID = "process-1";
    private static final String HOLD_ID = "hold-1";

    @Test
    void rejectsRequestsWithoutFunctionalIdentifiers() {
        var useCase = useCase(new FakeRepository(held()));

        assertThatThrownBy(() -> useCase.record(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.record(request(null, PROCESS_ID, HOLD_ID)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.record(request("event-1", " ", HOLD_ID)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownProcessIsDiscardedWithoutPersisting() {
        var repository = new FakeRepository(held());

        assertThat(useCase(repository).record(request("event-1", "other-process", HOLD_ID)))
                .isEqualTo(PhoneRequestOutcome.UNKNOWN_PROCESS);
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void requestIsAppliedFromHeldAndStoresTheContractData() {
        var repository = new FakeRepository(held());

        assertThat(useCase(repository).record(request("event-1", PROCESS_ID, HOLD_ID)))
                .isEqualTo(PhoneRequestOutcome.APPLIED);

        ReservationProcess saved = repository.current;
        assertThat(saved.status()).isEqualTo(ReservationProcess.Status.WAITING_FOR_PHONE);
        assertThat(saved.requestEventId()).isEqualTo("event-1");
        assertThat(saved.requestRequiredField()).isEqualTo("PHONE_NUMBER");
        assertThat(saved.requestMessage()).isEqualTo("Ingrese un teléfono");
        assertThat(saved.expiresAt()).isEqualTo(REQUEST_EXPIRES_AT);
        assertThat(saved.ownerUserId()).isEqualTo(42);
        assertThat(saved.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void requestMayArriveBeforeTheRestConfirmationResultIsStored() {
        for (ReservationProcess.Status status : List.of(ReservationProcess.Status.CONFIRM_REQUESTING,
                ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN, ReservationProcess.Status.WAITING_FOR_PHONE)) {
            var repository = new FakeRepository(process(status, null));

            assertThat(useCase(repository).record(request("event-1", PROCESS_ID, HOLD_ID)))
                    .isEqualTo(PhoneRequestOutcome.APPLIED);
            assertThat(repository.current.status()).isEqualTo(ReservationProcess.Status.WAITING_FOR_PHONE);
            assertThat(repository.current.requestEventId()).isEqualTo("event-1");
        }
    }

    @Test
    void repeatedEventIdHasNoEffect() {
        var repository = new FakeRepository(process(ReservationProcess.Status.WAITING_FOR_PHONE, "event-1"));

        assertThat(useCase(repository).record(request("event-1", PROCESS_ID, HOLD_ID)))
                .isEqualTo(PhoneRequestOutcome.DUPLICATE);
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void terminalProcessIsNotReopened() {
        for (ReservationProcess.Status status : List.of(ReservationProcess.Status.EXPIRED,
                ReservationProcess.Status.FAILED)) {
            var repository = new FakeRepository(process(status, null));

            assertThat(useCase(repository).record(request("event-1", PROCESS_ID, HOLD_ID)))
                    .isEqualTo(PhoneRequestOutcome.IGNORED);
            assertThat(repository.current.status()).isEqualTo(status);
            assertThat(repository.current.requestEventId()).isNull();
            assertThat(repository.saved).isEmpty();
        }
    }

    @Test
    void requestForAnotherHoldIsDiscarded() {
        var repository = new FakeRepository(held());

        assertThat(useCase(repository).record(request("event-1", PROCESS_ID, "another-hold")))
                .isEqualTo(PhoneRequestOutcome.HOLD_MISMATCH);
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void processWithoutHoldCannotAcceptTheRequest() {
        var repository = new FakeRepository(new ReservationProcess("local-id", 42, 15,
                LocalDate.of(2026, 11, 9), LocalTime.of(9, 30), ReservationProcess.Status.HOLD_REQUESTING,
                null, PROCESS_ID, NOW.plusSeconds(300), null, null, null, NOW, NOW, 1L));

        assertThat(useCase(repository).record(request("event-1", PROCESS_ID, HOLD_ID)))
                .isEqualTo(PhoneRequestOutcome.HOLD_MISMATCH);
        assertThat(repository.saved).isEmpty();
    }

    private static RecordPhoneRequestUseCaseImpl useCase(FakeRepository repository) {
        var exchange = org.mockito.Mockito.mock(
                ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneExchangeRepository.class);
        org.mockito.Mockito.when(exchange.markEvent(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        return new RecordPhoneRequestUseCaseImpl(repository, Clock.fixed(NOW, ZoneOffset.UTC), exchange);
    }

    private static AdditionalInformationRequest request(String eventId, String processId, String holdId) {
        return new AdditionalInformationRequest(eventId, processId, holdId, "PHONE_NUMBER",
                "Ingrese un teléfono", REQUEST_EXPIRES_AT, NOW);
    }

    private static ReservationProcess held() {
        return process(ReservationProcess.Status.HELD, null);
    }

    private static ReservationProcess process(ReservationProcess.Status status, String requestEventId) {
        return new ReservationProcess("local-id", 42, 15, LocalDate.of(2026, 11, 9), LocalTime.of(9, 30),
                status, HOLD_ID, PROCESS_ID, NOW.plusSeconds(300), requestEventId, null, null, NOW, NOW, 1L);
    }

    private static final class FakeRepository implements ReservationProcessRepository {
        private ReservationProcess current;
        private final List<ReservationProcess> saved = new ArrayList<>();

        private FakeRepository(ReservationProcess initial) {
            current = initial;
        }

        @Override
        public ReservationProcess save(ReservationProcess process) {
            current = process;
            saved.add(process);
            return process;
        }

        @Override
        public Optional<ReservationProcess> findById(String id) {
            return current != null && current.id().equals(id) ? Optional.of(current) : Optional.empty();
        }

        @Override
        public Optional<ReservationProcess> findByReservationProcessId(String reservationProcessId) {
            return current != null && reservationProcessId.equals(current.reservationProcessId())
                    ? Optional.of(current) : Optional.empty();
        }

        @Override
        public java.util.Map<String, ReservationProcess> findByReservationProcessIds(
                java.util.Collection<String> ids) {
            return current != null && ids.contains(current.reservationProcessId())
                    ? java.util.Map.of(current.reservationProcessId(), current) : java.util.Map.of();
        }

        @Override
        public Optional<ReservationProcess> findUnresolved(long ownerUserId, long professionalId, LocalDate date,
                                                           LocalTime startTime) {
            return Optional.empty();
        }
    }
}
