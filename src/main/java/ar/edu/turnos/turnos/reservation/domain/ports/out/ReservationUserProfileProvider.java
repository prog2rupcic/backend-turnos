package ar.edu.turnos.turnos.reservation.domain.ports.out;

import ar.edu.turnos.turnos.reservation.domain.model.PatientProfile;
import java.util.Optional;

public interface ReservationUserProfileProvider {
    Optional<PatientProfile> findByUserId(long userId);
}
