package ar.edu.turnos.turnos.reservation.application.exception;

public class HoldConfirmationException extends RuntimeException {
    public enum Reason {
        PROCESS_NOT_FOUND, PROCESS_NOT_OWNED, INVALID_STATE, CONFIRMATION_IN_PROGRESS,
        USER_PROFILE_UNAVAILABLE, HOLD_EXPIRED, HOLD_NOT_FOUND, PROFESSIONAL_NOT_FOUND,
        SLOT_UNAVAILABLE, ALREADY_CONFIRMED, INTEGRATION_UNAVAILABLE, INVALID_RESPONSE, OUTCOME_UNKNOWN
    }

    private final Reason reason;
    private final String processId;

    public HoldConfirmationException(Reason reason) {
        this(reason, null);
    }

    public HoldConfirmationException(Reason reason, String processId) {
        super(reason.name());
        this.reason = reason;
        this.processId = processId;
    }

    public Reason reason() { return reason; }
    public String processId() { return processId; }
}
