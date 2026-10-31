package ar.edu.turnos.turnos.user.infrastructure.persistence.adapter;

import ar.edu.turnos.turnos.user.domain.model.User;
import ar.edu.turnos.turnos.user.domain.ports.out.UserRepository;
import ar.edu.turnos.turnos.user.infrastructure.persistence.mapper.UserMapper;
import ar.edu.turnos.turnos.user.infrastructure.persistence.repository.JpaUserRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JpaUserRepositoryAdapter implements UserRepository {
    private final JpaUserRepository repository;
    private final UserMapper mapper;

    public JpaUserRepositoryAdapter(JpaUserRepository repository, UserMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public boolean existsByLogin(String login) { return repository.existsByLogin(login); }

    @Override
    public boolean existsByEmail(String email) { return repository.existsByEmail(email); }

    @Override
    public User save(User user) { return mapper.toDomain(repository.save(mapper.toEntity(user))); }

    @Override
    public Optional<User> findByLogin(String login) {
        return repository.findByLogin(login).map(mapper::toDomain);
    }
}
