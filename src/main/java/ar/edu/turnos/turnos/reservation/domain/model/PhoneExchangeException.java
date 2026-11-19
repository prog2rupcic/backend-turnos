package ar.edu.turnos.turnos.reservation.domain.model;

public class PhoneExchangeException extends RuntimeException {
    public enum Reason { PROCESS_NOT_FOUND, PROCESS_NOT_OWNED, REQUEST_NOT_RECEIVED, INVALID_STATE,
        DEADLINE_PASSED, SUBMISSION_IN_PROGRESS, INTEGRATION_UNAVAILABLE }
    private final Reason reason;

    public PhoneExchangeException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
