package ar.edu.turnos.turnos.reservation.domain.ports.out;

import ar.edu.turnos.turnos.reservation.domain.model.PhoneSubmission;
import java.time.Instant;
import java.util.Optional;

public interface PhoneExchangeRepository {
    void lockProcess(String processId);
    boolean markEvent(String eventId, String processId, Instant now);
    Optional<PhoneSubmission> latest(String processId);
    void enqueue(PhoneSubmission submission);
    void retry(String eventId, Instant now);
    Optional<PhoneSubmission> claim(Instant now);
    void published(PhoneSubmission submission, Instant now);
    void failed(PhoneSubmission submission, Instant now);
    void resolve(String processId, PhoneSubmission.DeliveryStatus status);
    void abandonPending(String processId);
}
