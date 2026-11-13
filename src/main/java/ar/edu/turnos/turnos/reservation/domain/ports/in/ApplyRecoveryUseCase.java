package ar.edu.turnos.turnos.reservation.domain.ports.in;

import ar.edu.turnos.turnos.reservation.domain.model.OwnReservation;
import ar.edu.turnos.turnos.reservation.domain.model.RecoveryProgress;
import ar.edu.turnos.turnos.reservation.domain.model.CreatedHold;

public interface ApplyRecoveryUseCase {
    String remote(RecoveryProgress claim, OwnReservation reservation);
    String hold(RecoveryProgress claim, CreatedHold hold);
}
