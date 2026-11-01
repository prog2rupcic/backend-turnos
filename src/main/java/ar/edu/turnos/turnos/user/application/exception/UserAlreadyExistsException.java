package ar.edu.turnos.turnos.user.application.exception;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String field) {
        super("Ya existe un usuario con ese " + field);
    }
}
