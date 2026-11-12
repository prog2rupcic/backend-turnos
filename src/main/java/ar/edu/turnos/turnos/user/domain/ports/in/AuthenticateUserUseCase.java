package ar.edu.turnos.turnos.user.domain.ports.in;

public interface AuthenticateUserUseCase {
    String authenticate(AuthenticateUserCommand command);
}
