package ar.edu.turnos.turnos.reservation.application.usecases;

import ar.edu.turnos.turnos.reservation.domain.model.PhoneExchangeException;
import ar.edu.turnos.turnos.reservation.domain.model.PhoneSubmission;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcessView;
import ar.edu.turnos.turnos.reservation.domain.ports.in.GetReservationProcessUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.in.SubmitPhoneUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneEventPublisher;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneExchangeRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SubmitPhoneUseCaseImpl implements SubmitPhoneUseCase {
    private final GetReservationProcessUseCase query;
    private final ReservationProcessRepository processes;
    private final PhoneExchangeRepository exchange;
    private final PhoneEventPublisher publisher;
    private final Clock clock;

    public SubmitPhoneUseCaseImpl(GetReservationProcessUseCase query, ReservationProcessRepository processes,
                                  PhoneExchangeRepository exchange, PhoneEventPublisher publisher, Clock clock) {
        this.query = query;
        this.processes = processes;
        this.exchange = exchange;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReservationProcessView submit(String processId, long ownerUserId, String phoneNumber) {
        exchange.lockProcess(processId);
        var view = query.get(processId, ownerUserId);
        var process = view.process();
        String phone = PhoneSubmission.normalize(phoneNumber);
        if (process.requestEventId() == null) {
            throw error(PhoneExchangeException.Reason.REQUEST_NOT_RECEIVED);
        }
        if (process.expiresAt() == null || !clock.instant().isBefore(process.expiresAt())) {
            throw error(PhoneExchangeException.Reason.DEADLINE_PASSED);
        }
        if (process.status() == ReservationProcess.Status.PHONE_SUBMISSION_PENDING
                || process.status() == ReservationProcess.Status.PHONE_SUBMITTED) {
            var existing = exchange.latest(processId).orElseThrow(() -> error(PhoneExchangeException.Reason.INVALID_STATE));
            if (!existing.phoneNumber().equals(phone)) {
                throw error(PhoneExchangeException.Reason.SUBMISSION_IN_PROGRESS);
            }
            if (existing.deliveryStatus() == PhoneSubmission.DeliveryStatus.RETRY_EXHAUSTED) {
                exchange.retry(existing.eventId(), clock.instant());
            }
            return query.get(processId, ownerUserId);
        }
        if (!view.canSubmitPhone()) {
            throw error(PhoneExchangeException.Reason.INVALID_STATE);
        }
        String group = publisher.groupId();
        if (group == null || group.isBlank()) {
            throw error(PhoneExchangeException.Reason.INTEGRATION_UNAVAILABLE);
        }
        var now = clock.instant();
        exchange.enqueue(new PhoneSubmission(UUID.randomUUID().toString(), processId, group,
                process.reservationProcessId(), process.holdId(), process.requestEventId(), phone, now,
                process.expiresAt(), PhoneSubmission.DeliveryStatus.PENDING, 0));
        processes.save(process.withStatus(ReservationProcess.Status.PHONE_SUBMISSION_PENDING, now));
        return query.get(processId, ownerUserId);
    }

    private static PhoneExchangeException error(PhoneExchangeException.Reason reason) {
        return new PhoneExchangeException(reason);
    }
}
