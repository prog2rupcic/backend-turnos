package ar.edu.turnos.turnos.user.domain.ports.in;

import ar.edu.turnos.turnos.user.domain.model.User;

public interface RegisterUserUseCase {
    User register(RegisterUserCommand command);
}
