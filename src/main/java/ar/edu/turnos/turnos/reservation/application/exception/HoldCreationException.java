package ar.edu.turnos.turnos.reservation.application.exception;

public class HoldCreationException extends RuntimeException {
    public enum Reason { SLOT_UNAVAILABLE, ALREADY_IN_PROGRESS, PROFESSIONAL_NOT_FOUND,
        INTEGRATION_UNAVAILABLE, INVALID_RESPONSE, OUTCOME_UNKNOWN }

    private final Reason reason;
    private final String processId;

    public HoldCreationException(Reason reason) {
        this(reason, null);
    }

    public HoldCreationException(Reason reason, String processId) {
        super(reason.name());
        this.reason = reason;
        this.processId = processId;
    }

    public Reason reason() { return reason; }
    public String processId() { return processId; }
}
