package ar.edu.turnos.turnos.reservation.infrastructure.kafka;

public class InvalidAdditionalInformationRequestException extends RuntimeException {
    public enum Reason {
        MALFORMED, UNEXPECTED_EVENT_TYPE, UNSUPPORTED_SCHEMA_VERSION, MISSING_FIELD,
        UNSUPPORTED_REQUIRED_FIELD, INVALID_FIELD, KEY_MISMATCH
    }

    private final Reason reason;

    public InvalidAdditionalInformationRequestException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
