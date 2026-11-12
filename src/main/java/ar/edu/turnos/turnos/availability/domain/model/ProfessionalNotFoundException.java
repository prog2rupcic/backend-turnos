package ar.edu.turnos.turnos.availability.domain.model;

public class ProfessionalNotFoundException extends RuntimeException {
    public ProfessionalNotFoundException() {
        super("Profesional inexistente");
    }
}
