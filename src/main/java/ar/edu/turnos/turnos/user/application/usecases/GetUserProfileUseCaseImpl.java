package ar.edu.turnos.turnos.user.application.usecases;

import ar.edu.turnos.turnos.user.domain.model.UserProfile;
import ar.edu.turnos.turnos.user.domain.ports.in.GetUserProfileUseCase;
import ar.edu.turnos.turnos.user.domain.ports.out.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetUserProfileUseCaseImpl implements GetUserProfileUseCase {
    private final UserRepository userRepository;

    public GetUserProfileUseCaseImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProfile> getById(long userId) {
        return userRepository.findById(userId).map(user -> new UserProfile(user.firstName(), user.lastName()));
    }
}
