package ar.edu.turnos.turnos.reservation.domain.ports.out;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

public interface ReservationProcessRepository {
    ReservationProcess save(ReservationProcess process);

    Optional<ReservationProcess> findById(String id);

    Optional<ReservationProcess> findUnresolved(long ownerUserId, long professionalId, LocalDate date,
                                                LocalTime startTime);
}
