package ar.edu.turnos.turnos.user.domain.ports.in;

import ar.edu.turnos.turnos.user.domain.model.UserProfile;
import java.util.Optional;

public interface GetUserProfileUseCase {
    Optional<UserProfile> getById(long userId);
}
