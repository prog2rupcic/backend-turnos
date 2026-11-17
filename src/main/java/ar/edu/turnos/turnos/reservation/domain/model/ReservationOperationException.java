package ar.edu.turnos.turnos.reservation.domain.model;

public class ReservationOperationException extends RuntimeException {
    public enum Reason { PROCESS_NOT_FOUND, NOT_OWNER, INVALID_STATE, CANCELLATION_IN_PROGRESS,
        OUTCOME_UNKNOWN, INTEGRATION_UNAVAILABLE, INVALID_RESPONSE }

    private final Reason reason;

    public ReservationOperationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
