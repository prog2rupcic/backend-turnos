package ar.edu.turnos.turnos.reservation.domain.ports.out;

import ar.edu.turnos.turnos.reservation.domain.model.RecoveryProgress;
import java.time.Instant;
import java.util.Optional;

public interface RecoveryRepository {
    void discover(Instant now);
    Optional<RecoveryProgress> claim(Instant now);
    Optional<RecoveryProgress> get(String processId);
    void reschedule(RecoveryProgress claim, Instant when, String code);
    void review(RecoveryProgress claim, String code);
    void done(RecoveryProgress claim);
}
