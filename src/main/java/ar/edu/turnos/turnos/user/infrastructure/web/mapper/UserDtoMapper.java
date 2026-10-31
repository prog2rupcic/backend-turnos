package ar.edu.turnos.turnos.user.infrastructure.web.mapper;

import ar.edu.turnos.turnos.user.domain.model.User;
import ar.edu.turnos.turnos.user.infrastructure.web.dto.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserDtoMapper {
    public UserResponse toResponse(User user) {
        return new UserResponse(user.id(), user.login(), user.firstName(), user.lastName(), user.email(),
                user.imageUrl(), user.langKey(), user.activated(), user.authorities(), user.createdAt(), user.updatedAt());
    }
}
