package ar.edu.turnos.turnos.user.domain.ports.out;

import ar.edu.turnos.turnos.user.domain.model.User;
import java.util.Optional;

public interface UserRepository {
    boolean existsByLogin(String login);

    boolean existsByEmail(String email);

    User save(User user);

    Optional<User> findByLogin(String login);
}
