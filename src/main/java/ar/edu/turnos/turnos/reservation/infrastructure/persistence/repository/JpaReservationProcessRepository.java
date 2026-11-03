package ar.edu.turnos.turnos.reservation.infrastructure.persistence.repository;

import ar.edu.turnos.turnos.reservation.infrastructure.persistence.entity.ReservationProcessEntity;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaReservationProcessRepository extends JpaRepository<ReservationProcessEntity, String> {
    Optional<ReservationProcessEntity> findByReservationProcessId(String reservationProcessId);

    Optional<ReservationProcessEntity> findFirstByOwnerUserIdAndProfessionalIdAndDateAndStartTimeAndStatusInOrderByCreatedAtDesc(
            long ownerUserId, long professionalId, LocalDate date, LocalTime startTime,
            Collection<ReservationProcess.Status> statuses);
}
