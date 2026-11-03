package ar.edu.turnos.turnos.reservation.application.usecases;

import ar.edu.turnos.turnos.reservation.application.exception.HoldConfirmationException;
import ar.edu.turnos.turnos.reservation.domain.model.ConfirmedHold;
import ar.edu.turnos.turnos.reservation.domain.model.PatientProfile;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.in.ConfirmHoldUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.HoldGateway;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationUserProfileProvider;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ConfirmHoldUseCaseImpl implements ConfirmHoldUseCase {
    private final ReservationProcessRepository processRepository;
    private final ReservationUserProfileProvider userProfileProvider;
    private final HoldGateway holdGateway;
    private final Clock clock;

    public ConfirmHoldUseCaseImpl(ReservationProcessRepository processRepository,
                                 ReservationUserProfileProvider userProfileProvider,
                                 HoldGateway holdGateway, Clock clock) {
        this.processRepository = processRepository;
        this.userProfileProvider = userProfileProvider;
        this.holdGateway = holdGateway;
        this.clock = clock;
    }

    @Override
    public ReservationProcess confirm(String processId, long ownerUserId) {
        if (processId == null || processId.isBlank() || ownerUserId <= 0) {
            throw new IllegalArgumentException("Identificador del proceso inválido");
        }
        ReservationProcess process = processRepository.findById(processId)
                .orElseThrow(() -> new HoldConfirmationException(HoldConfirmationException.Reason.PROCESS_NOT_FOUND));
        if (process.ownerUserId() != ownerUserId) {
            throw new HoldConfirmationException(HoldConfirmationException.Reason.PROCESS_NOT_OWNED);
        }
        if (process.status() == ReservationProcess.Status.WAITING_FOR_PHONE) {
            return process;
        }
        if (process.status() == ReservationProcess.Status.CONFIRM_REQUESTING) {
            throw new HoldConfirmationException(HoldConfirmationException.Reason.CONFIRMATION_IN_PROGRESS,
                    process.id());
        }
        if (process.status() == ReservationProcess.Status.EXPIRED) {
            throw new HoldConfirmationException(HoldConfirmationException.Reason.HOLD_EXPIRED, process.id());
        }
        if (process.status() != ReservationProcess.Status.HELD
                && process.status() != ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN) {
            throw new HoldConfirmationException(HoldConfirmationException.Reason.INVALID_STATE, process.id());
        }
        if (process.holdId() == null || process.reservationProcessId() == null) {
            throw new HoldConfirmationException(HoldConfirmationException.Reason.INVALID_STATE, process.id());
        }

        PatientProfile profile = userProfileProvider.findByUserId(ownerUserId)
                .orElseThrow(() -> new HoldConfirmationException(
                        HoldConfirmationException.Reason.USER_PROFILE_UNAVAILABLE, process.id()));
        validateProfile(profile, process.id());

        ReservationProcess requesting = processRepository.save(
                process.withStatus(ReservationProcess.Status.CONFIRM_REQUESTING, clock.instant()));
        ConfirmedHold confirmation;
        try {
            confirmation = holdGateway.confirm(requesting.holdId(), "user-" + ownerUserId,
                    profile.firstName(), profile.lastName());
        } catch (HoldConfirmationException exception) {
            return handleConfirmationFailure(requesting, exception);
        }
        if (!requesting.reservationProcessId().equals(confirmation.reservationProcessId())) {
            processRepository.save(requesting.withStatus(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN,
                    clock.instant()));
            throw new HoldConfirmationException(HoldConfirmationException.Reason.OUTCOME_UNKNOWN, requesting.id());
        }
        return processRepository.save(requesting.waitingForPhone(confirmation.expiresAt(), clock.instant()));
    }

    private ReservationProcess handleConfirmationFailure(ReservationProcess requesting,
                                                           HoldConfirmationException exception) {
        Instant now = clock.instant();
        return switch (exception.reason()) {
            case ALREADY_CONFIRMED -> processRepository.save(
                    requesting.waitingForPhone(requesting.expiresAt(), now));
            case OUTCOME_UNKNOWN -> {
                processRepository.save(requesting.withStatus(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN, now));
                throw new HoldConfirmationException(HoldConfirmationException.Reason.OUTCOME_UNKNOWN,
                        requesting.id());
            }
            case HOLD_EXPIRED -> {
                processRepository.save(requesting.withStatus(ReservationProcess.Status.EXPIRED, now));
                throw new HoldConfirmationException(exception.reason(), requesting.id());
            }
            case INTEGRATION_UNAVAILABLE -> {
                processRepository.save(requesting.withStatus(ReservationProcess.Status.HELD, now));
                throw new HoldConfirmationException(exception.reason(), requesting.id());
            }
            default -> {
                processRepository.save(requesting.withStatus(ReservationProcess.Status.FAILED, now));
                throw new HoldConfirmationException(exception.reason(), requesting.id());
            }
        };
    }

    private static void validateProfile(PatientProfile profile, String processId) {
        if (!validName(profile.firstName()) || !validName(profile.lastName())) {
            throw new HoldConfirmationException(HoldConfirmationException.Reason.USER_PROFILE_UNAVAILABLE, processId);
        }
    }

    private static boolean validName(String name) {
        return name != null && !name.isBlank() && name.length() >= 2 && name.length() <= 100;
    }
}
