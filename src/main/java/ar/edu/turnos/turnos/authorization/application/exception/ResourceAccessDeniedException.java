package ar.edu.turnos.turnos.authorization.application.exception;

public class ResourceAccessDeniedException extends RuntimeException {
    public ResourceAccessDeniedException() {
        super("No tienes permiso para acceder a este recurso");
    }
}
