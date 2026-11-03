package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.turnos.turnos.reservation.application.exception.HoldConfirmationException;
import ar.edu.turnos.turnos.reservation.application.usecases.ConfirmHoldUseCaseImpl;
import ar.edu.turnos.turnos.reservation.domain.model.ConfirmedHold;
import ar.edu.turnos.turnos.reservation.domain.model.CreatedHold;
import ar.edu.turnos.turnos.reservation.domain.model.PatientProfile;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.out.HoldGateway;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationUserProfileProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfirmHoldUseCaseTests {
    private static final Instant NOW = Instant.parse("2026-11-03T17:00:00Z");
    private static final Instant EXPIRES_AT = NOW.plusSeconds(300);
    private static final long OWNER_ID = 42;

    @Test
    void rejectsInvalidIdentifiersAndUnknownProcessesBeforeAccessingDependencies() {
        var useCase = useCase(new FakeRepository(null), userId -> Optional.empty(),
                successfulGateway());

        assertThatThrownBy(() -> useCase.confirm(null, OWNER_ID)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.confirm(" ", OWNER_ID)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.confirm("local-id", 0)).isInstanceOf(IllegalArgumentException.class);
        assertReason(() -> useCase.confirm("local-id", OWNER_ID), HoldConfirmationException.Reason.PROCESS_NOT_FOUND);
    }

    @Test
    void requiresConfirmableStateAndPersistedExternalIdentifiers() {
        var confirming = new FakeRepository(process(ReservationProcess.Status.CONFIRM_REQUESTING,
                "hold-id", "external-id"));
        assertReason(() -> useCase(confirming, validProfile(), successfulGateway()).confirm("local-id", OWNER_ID),
                HoldConfirmationException.Reason.CONFIRMATION_IN_PROGRESS);

        var failed = new FakeRepository(process(ReservationProcess.Status.FAILED, "hold-id", "external-id"));
        assertReason(() -> useCase(failed, validProfile(), successfulGateway()).confirm("local-id", OWNER_ID),
                HoldConfirmationException.Reason.INVALID_STATE);

        var noHoldId = new FakeRepository(process(ReservationProcess.Status.HELD, null, "external-id"));
        assertReason(() -> useCase(noHoldId, validProfile(), successfulGateway()).confirm("local-id", OWNER_ID),
                HoldConfirmationException.Reason.INVALID_STATE);

        var noExternalId = new FakeRepository(process(ReservationProcess.Status.HELD, "hold-id", null));
        assertReason(() -> useCase(noExternalId, validProfile(), successfulGateway()).confirm("local-id", OWNER_ID),
                HoldConfirmationException.Reason.INVALID_STATE);
    }

    @Test
    void missingOrMalformedStoredProfilePreventsTheExternalPost() {
        var missing = new FakeRepository(heldProcess());
        assertReason(() -> useCase(missing, userId -> Optional.empty(), successfulGateway())
                        .confirm("local-id", OWNER_ID),
                HoldConfirmationException.Reason.USER_PROFILE_UNAVAILABLE);
        assertThat(missing.saved).isEmpty();

        for (PatientProfile profile : new PatientProfile[] {
                new PatientProfile(null, "Garcia"), new PatientProfile(" ", "Garcia"),
                new PatientProfile("A", "Garcia"), new PatientProfile("A".repeat(101), "Garcia"),
                new PatientProfile("Ana", null), new PatientProfile("Ana", " "),
                new PatientProfile("Ana", "A"), new PatientProfile("Ana", "G".repeat(101))
        }) {
            var repository = new FakeRepository(heldProcess());
            assertReason(() -> useCase(repository, userId -> Optional.of(profile), successfulGateway())
                            .confirm("local-id", OWNER_ID),
                    HoldConfirmationException.Reason.USER_PROFILE_UNAVAILABLE);
            assertThat(repository.saved).isEmpty();
        }
    }

    @Test
    void knownPermanentExternalRejectionMarksTheProcessFailed() {
        var repository = new FakeRepository(heldProcess());
        HoldGateway gateway = gateway((holdId, externalId, first, last) -> {
            throw new HoldConfirmationException(HoldConfirmationException.Reason.HOLD_NOT_FOUND);
        });

        assertReason(() -> useCase(repository, validProfile(), gateway).confirm("local-id", OWNER_ID),
                HoldConfirmationException.Reason.HOLD_NOT_FOUND);
        assertThat(repository.current.status()).isEqualTo(ReservationProcess.Status.FAILED);
        assertThat(repository.saved).hasSize(2);
    }

    @Test
    void externalSuccessUsesTheStableOwnerIdAndStoresTheReturnedExpiry() {
        var repository = new FakeRepository(heldProcess());
        var useCase = useCase(repository, validProfile(), successfulGateway());

        var result = useCase.confirm("local-id", OWNER_ID);

        assertThat(result.status()).isEqualTo(ReservationProcess.Status.WAITING_FOR_PHONE);
        assertThat(result.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(repository.saved).extracting(ReservationProcess::status)
                .containsExactly(ReservationProcess.Status.CONFIRM_REQUESTING,
                        ReservationProcess.Status.WAITING_FOR_PHONE);
    }

    private static ConfirmHoldUseCaseImpl useCase(FakeRepository repository,
                                                   ReservationUserProfileProvider profiles,
                                                   HoldGateway gateway) {
        return new ConfirmHoldUseCaseImpl(repository, profiles, gateway, Clock.fixed(NOW, java.time.ZoneOffset.UTC));
    }

    private static ReservationUserProfileProvider validProfile() {
        return userId -> Optional.of(new PatientProfile("Ana", "Garcia"));
    }

    private static HoldGateway successfulGateway() {
        return gateway((holdId, externalId, first, last) -> {
            assertThat(holdId).isEqualTo("hold-id");
            assertThat(externalId).isEqualTo("user-" + OWNER_ID);
            assertThat(first).isEqualTo("Ana");
            assertThat(last).isEqualTo("Garcia");
            return new ConfirmedHold("external-id", EXPIRES_AT);
        });
    }

    private static HoldGateway gateway(Confirmation confirmation) {
        return new HoldGateway() {
            @Override
            public CreatedHold create(long professionalId, LocalDate date, LocalTime startTime,
                                      String externalPatientId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ConfirmedHold confirm(String holdId, String externalPatientId, String patientFirstName,
                                         String patientLastName) {
                return confirmation.confirm(holdId, externalPatientId, patientFirstName, patientLastName);
            }
        };
    }

    private static ReservationProcess heldProcess() {
        return process(ReservationProcess.Status.HELD, "hold-id", "external-id");
    }

    private static ReservationProcess process(ReservationProcess.Status status, String holdId, String externalId) {
        return new ReservationProcess("local-id", OWNER_ID, 15, LocalDate.of(2026, 11, 9),
                LocalTime.of(9, 30), status, holdId, externalId, EXPIRES_AT, NOW, NOW, 0L);
    }

    private static void assertReason(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
                                     HoldConfirmationException.Reason expected) {
        assertThatThrownBy(action).isInstanceOfSatisfying(HoldConfirmationException.class,
                exception -> assertThat(exception.reason()).isEqualTo(expected));
    }

    @FunctionalInterface
    private interface Confirmation {
        ConfirmedHold confirm(String holdId, String externalId, String firstName, String lastName);
    }

    private static final class FakeRepository implements ReservationProcessRepository {
        private ReservationProcess current;
        private final java.util.List<ReservationProcess> saved = new java.util.ArrayList<>();

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
        public Optional<ReservationProcess> findUnresolved(long ownerUserId, long professionalId, LocalDate date,
                                                           LocalTime startTime) {
            return Optional.empty();
        }
    }
}
