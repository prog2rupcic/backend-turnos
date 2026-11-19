package ar.edu.turnos.turnos.user.domain.ports.in;

public record AuthenticateUserCommand(String username, String password, boolean rememberMe) {
}
