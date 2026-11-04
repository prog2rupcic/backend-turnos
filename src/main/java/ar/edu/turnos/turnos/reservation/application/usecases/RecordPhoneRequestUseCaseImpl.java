package ar.edu.turnos.turnos.reservation.application.usecases;

import ar.edu.turnos.turnos.reservation.domain.model.AdditionalInformationRequest;
import ar.edu.turnos.turnos.reservation.domain.model.PhoneRequestOutcome;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.in.RecordPhoneRequestUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneExchangeRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecordPhoneRequestUseCaseImpl implements RecordPhoneRequestUseCase {
    private final ReservationProcessRepository repository;
    private final Clock clock;
    private final PhoneExchangeRepository exchange;

    public RecordPhoneRequestUseCaseImpl(ReservationProcessRepository repository, Clock clock,
                                         PhoneExchangeRepository exchange) {
        this.repository = repository;
        this.clock = clock;
        this.exchange = exchange;
    }

    @Override
    @Transactional
    public PhoneRequestOutcome record(AdditionalInformationRequest request) {
        if (request == null || blank(request.eventId()) || blank(request.reservationProcessId())) {
            throw new IllegalArgumentException("Solicitud de información inválida");
        }
        exchange.lockProcess(request.reservationProcessId());
        Optional<ReservationProcess> found = repository.findByReservationProcessId(request.reservationProcessId());
        if (found.isEmpty()) {
            return PhoneRequestOutcome.UNKNOWN_PROCESS;
        }
        ReservationProcess process = found.get();
        if (request.eventId().equals(process.requestEventId())) {
            return PhoneRequestOutcome.DUPLICATE;
        }
        if (isTerminal(process.status())) {
            return PhoneRequestOutcome.IGNORED;
        }
        if (!Objects.equals(process.holdId(), request.holdId())) {
            return PhoneRequestOutcome.HOLD_MISMATCH;
        }
        if (!acceptsPhoneRequest(process.status())) {
            return PhoneRequestOutcome.IGNORED;
        }
        if (!exchange.markEvent(request.eventId(), process.id(), clock.instant())) {
            return PhoneRequestOutcome.DUPLICATE;
        }
        repository.save(process.withPhoneRequest(request, clock.instant()));
        return PhoneRequestOutcome.APPLIED;
    }

    private static boolean acceptsPhoneRequest(ReservationProcess.Status status) {
        return status == ReservationProcess.Status.HELD
                || status == ReservationProcess.Status.CONFIRM_REQUESTING
                || status == ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN
                || status == ReservationProcess.Status.WAITING_FOR_PHONE;
    }

    private static boolean isTerminal(ReservationProcess.Status status) {
        return status == ReservationProcess.Status.EXPIRED || status == ReservationProcess.Status.FAILED
                || status == ReservationProcess.Status.CONFIRMED;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
