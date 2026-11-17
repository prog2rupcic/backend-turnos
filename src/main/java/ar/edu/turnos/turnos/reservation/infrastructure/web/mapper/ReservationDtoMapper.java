package ar.edu.turnos.turnos.reservation.infrastructure.web.mapper;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.infrastructure.web.dto.CreateHoldResponse;
import org.springframework.stereotype.Component;

@Component
public class ReservationDtoMapper {
    public CreateHoldResponse toResponse(ReservationProcess process) {
        return new CreateHoldResponse(process.id(), process.professionalId(), process.date(), process.startTime(),
                process.status().name(), process.holdId(), process.reservationProcessId(), process.expiresAt());
    }
}
