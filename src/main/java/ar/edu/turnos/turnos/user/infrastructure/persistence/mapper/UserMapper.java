package ar.edu.turnos.turnos.user.infrastructure.persistence.mapper;

import ar.edu.turnos.turnos.user.domain.model.User;
import ar.edu.turnos.turnos.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserEntity toEntity(User user) {
        return new UserEntity(user.id(), user.login(), user.passwordHash(), user.firstName(), user.lastName(),
                user.email(), user.imageUrl(), user.langKey(), user.activated(), user.authorities(),
                user.createdAt(), user.updatedAt());
    }

    public User toDomain(UserEntity entity) {
        return new User(entity.getId(), entity.getLogin(), entity.getPasswordHash(), entity.getFirstName(),
                entity.getLastName(), entity.getEmail(), entity.getImageUrl(), entity.getLangKey(),
                entity.isActivated(), entity.getAuthorities(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
