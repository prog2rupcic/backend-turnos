package ar.edu.turnos.turnos.reservation.domain.ports.out;

import ar.edu.turnos.turnos.reservation.domain.model.PhoneSubmission;

public interface PhoneEventPublisher {
    String groupId();
    void publish(PhoneSubmission submission);
}
