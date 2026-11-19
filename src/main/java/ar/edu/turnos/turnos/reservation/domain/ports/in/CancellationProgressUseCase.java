package ar.edu.turnos.turnos.reservation.domain.ports.in;

import ar.edu.turnos.turnos.reservation.domain.model.CancellationReceipt;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;

public interface CancellationProgressUseCase {
    ReservationProcess begin(String localProcessId, long ownerUserId, String reason);
    ReservationProcess complete(String localProcessId, CancellationReceipt receipt, String reason);
    void uncertain(String localProcessId);
    void rejected(String localProcessId);
}
