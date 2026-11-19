package ar.edu.turnos.turnos.user.infrastructure.persistence.repository;

import ar.edu.turnos.turnos.user.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUserRepository extends JpaRepository<UserEntity, Long> {
    boolean existsByLogin(String login);

    boolean existsByEmail(String email);

    Optional<UserEntity> findByLogin(String login);
}
