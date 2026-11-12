package ar.edu.turnos.turnos.availability.domain.model;

public class AvailabilitySourceException extends RuntimeException {
    private final boolean invalidResponse;

    public AvailabilitySourceException(boolean invalidResponse) {
        super("No se pudo verificar la disponibilidad");
        this.invalidResponse = invalidResponse;
    }

    public boolean invalidResponse() {
        return invalidResponse;
    }
}
