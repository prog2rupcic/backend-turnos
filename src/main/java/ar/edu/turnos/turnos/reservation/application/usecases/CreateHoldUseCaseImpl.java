package ar.edu.turnos.turnos.reservation.application.usecases;

import ar.edu.turnos.turnos.reservation.application.exception.HoldCreationException;
import ar.edu.turnos.turnos.reservation.domain.model.CreatedHold;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.in.CreateHoldUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.HoldGateway;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.SlotAvailability;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CreateHoldUseCaseImpl implements CreateHoldUseCase {
    private final SlotAvailability availability;
    private final HoldGateway gateway;
    private final ReservationProcessRepository repository;
    private final Clock clock;

    public CreateHoldUseCaseImpl(SlotAvailability availability, HoldGateway gateway,
                                 ReservationProcessRepository repository, Clock clock) {
        this.availability = availability;
        this.gateway = gateway;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public ReservationProcess create(long ownerUserId, long professionalId, LocalDate date, LocalTime startTime) {
        if (ownerUserId <= 0 || professionalId <= 0 || date == null || startTime == null) {
            throw new IllegalArgumentException("Datos del turno inválidos");
        }
        repository.findUnresolved(ownerUserId, professionalId, date, startTime).ifPresent(previous -> {
            throw new HoldCreationException(HoldCreationException.Reason.ALREADY_IN_PROGRESS, previous.id());
        });
        if (!availability.isAvailable(professionalId, date, startTime)) {
            throw new HoldCreationException(HoldCreationException.Reason.SLOT_UNAVAILABLE);
        }
        Instant now = clock.instant();
        // Persistir el intento ANTES del POST: no hay clave de idempotencia ni GET por hold en cátedra.
        ReservationProcess intent = repository.save(new ReservationProcess(UUID.randomUUID().toString(), ownerUserId,
                professionalId, date, startTime, ReservationProcess.Status.HOLD_REQUESTING, null, null, null,
                null, null, null, now, now, null));
        try {
            CreatedHold hold = gateway.create(professionalId, date, startTime, "user-" + ownerUserId);
            return repository.save(intent.withHold(hold.holdId(), hold.reservationProcessId(), hold.expiresAt(),
                    clock.instant()));
        } catch (HoldCreationException exception) {
            if (exception.reason() == HoldCreationException.Reason.OUTCOME_UNKNOWN) {
                repository.save(intent.withStatus(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN, clock.instant()));
                // Consultar ocupaciones nuevamente, sin deducir propiedad ni repetir el POST.
                HoldCreationException unknown = new HoldCreationException(HoldCreationException.Reason.OUTCOME_UNKNOWN,
                        intent.id());
                try {
                    availability.isAvailable(professionalId, date, startTime);
                } catch (RuntimeException unavailable) {
                    // No perder la causa de la reconsulta fallida; el intento queda pendiente de TUR-14.
                    unknown.addSuppressed(unavailable);
                }
                throw unknown;
            }
            repository.save(intent.withStatus(ReservationProcess.Status.FAILED, clock.instant()));
            throw exception;
        }
    }
}
