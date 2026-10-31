package ar.edu.turnos.turnos.user.application.usecases;

import ar.edu.turnos.turnos.user.application.exception.UserAlreadyExistsException;
import ar.edu.turnos.turnos.user.domain.model.User;
import ar.edu.turnos.turnos.user.domain.ports.in.RegisterUserCommand;
import ar.edu.turnos.turnos.user.domain.ports.in.RegisterUserUseCase;
import ar.edu.turnos.turnos.user.domain.ports.out.PasswordHasher;
import ar.edu.turnos.turnos.user.domain.ports.out.UserRepository;
import java.time.Instant;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Component;

@Component
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public RegisterUserUseCaseImpl(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional
    public User register(RegisterUserCommand command) {
        if (userRepository.existsByLogin(command.login())) {
            throw new UserAlreadyExistsException("login");
        }
        if (command.email() != null && userRepository.existsByEmail(command.email())) {
            throw new UserAlreadyExistsException("email");
        }
        Instant now = Instant.now();
        User user = new User(null, command.login(), passwordHasher.hash(command.password()),
                command.firstName(), command.lastName(), command.email(), command.imageUrl(),
                command.langKey() == null ? "es" : command.langKey(), true, Set.of("ROLE_USER"), now, now);
        return userRepository.save(user);
    }
}
