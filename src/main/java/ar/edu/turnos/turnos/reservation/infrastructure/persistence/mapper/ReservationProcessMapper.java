package ar.edu.turnos.turnos.reservation.infrastructure.persistence.mapper;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.infrastructure.persistence.entity.ReservationProcessEntity;
import org.springframework.stereotype.Component;

@Component
public class ReservationProcessMapper {
    public ReservationProcessEntity toEntity(ReservationProcess process) {
        return ReservationProcessEntity.from(process);
    }

    public ReservationProcess toDomain(ReservationProcessEntity entity) {
        return new ReservationProcess(entity.getId(), entity.getOwnerUserId(), entity.getProfessionalId(),
                entity.getDate(), entity.getStartTime(), entity.getStatus(), entity.getHoldId(),
                entity.getReservationProcessId(), entity.getExpiresAt(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getVersion());
    }
}
