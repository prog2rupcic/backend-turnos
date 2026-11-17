package ar.edu.turnos.turnos.reservation.domain.ports.in;

import ar.edu.turnos.turnos.reservation.domain.model.CancellationNotice;

public interface RecordCancellationUseCase {
    String record(CancellationNotice notice);
}
