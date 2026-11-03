package ar.edu.turnos.turnos.reservation.infrastructure.user;

import ar.edu.turnos.turnos.reservation.domain.model.PatientProfile;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationUserProfileProvider;
import ar.edu.turnos.turnos.user.domain.ports.in.GetUserProfileUseCase;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UserProfileAdapter implements ReservationUserProfileProvider {
    // Composición entre slices autorizada: consumir solo el puerto público de consulta del slice user.
    private final GetUserProfileUseCase getUserProfile;

    public UserProfileAdapter(GetUserProfileUseCase getUserProfile) {
        this.getUserProfile = getUserProfile;
    }

    @Override
    public Optional<PatientProfile> findByUserId(long userId) {
        return getUserProfile.getById(userId)
                .map(profile -> new PatientProfile(profile.firstName(), profile.lastName()));
    }
}
