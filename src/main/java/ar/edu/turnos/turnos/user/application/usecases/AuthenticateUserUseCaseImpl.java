package ar.edu.turnos.turnos.user.application.usecases;

import ar.edu.turnos.turnos.user.application.exception.InvalidCredentialsException;
import ar.edu.turnos.turnos.user.domain.ports.in.AuthenticateUserCommand;
import ar.edu.turnos.turnos.user.domain.ports.in.AuthenticateUserUseCase;
import ar.edu.turnos.turnos.user.domain.ports.out.PasswordHasher;
import ar.edu.turnos.turnos.user.domain.ports.out.UserRepository;
import ar.edu.turnos.turnos.user.domain.ports.out.UserTokenIssuer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuthenticateUserUseCaseImpl implements AuthenticateUserUseCase {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final UserTokenIssuer tokenIssuer;

    public AuthenticateUserUseCaseImpl(UserRepository userRepository, PasswordHasher passwordHasher,
                                       UserTokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    @Transactional(readOnly = true)
    public String authenticate(AuthenticateUserCommand command) {
        var user = userRepository.findByLogin(command.username()).orElseThrow(InvalidCredentialsException::new);
        if (!user.activated() || !passwordHasher.matches(command.password(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return tokenIssuer.issue(user, command.rememberMe());
    }
}
