package ar.edu.turnos.turnos.reservation.domain.ports.out;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Collection;
import java.util.Map;

public interface ReservationProcessRepository {
    ReservationProcess save(ReservationProcess process);

    Optional<ReservationProcess> findById(String id);

    Optional<ReservationProcess> findByReservationProcessId(String reservationProcessId);

    Map<String, ReservationProcess> findByReservationProcessIds(Collection<String> reservationProcessIds);

    Optional<ReservationProcess> findUnresolved(long ownerUserId, long professionalId, LocalDate date,
                                                LocalTime startTime);
}
