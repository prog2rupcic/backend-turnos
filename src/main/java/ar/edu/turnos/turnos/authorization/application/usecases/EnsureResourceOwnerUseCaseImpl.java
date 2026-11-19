package ar.edu.turnos.turnos.authorization.application.usecases;

import ar.edu.turnos.turnos.authorization.application.exception.ResourceAccessDeniedException;
import ar.edu.turnos.turnos.authorization.domain.model.OwnershipPolicy;
import ar.edu.turnos.turnos.authorization.domain.ports.in.EnsureResourceOwnerUseCase;
import ar.edu.turnos.turnos.authorization.domain.ports.out.AuthenticatedUserProvider;
import org.springframework.stereotype.Component;

@Component
public class EnsureResourceOwnerUseCaseImpl implements EnsureResourceOwnerUseCase {
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public EnsureResourceOwnerUseCaseImpl(AuthenticatedUserProvider authenticatedUserProvider) {
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @Override
    public void ensureOwner(Long persistedOwnerUserId) {
        if (!OwnershipPolicy.isOwner(authenticatedUserProvider.currentUserId(), persistedOwnerUserId)) {
            throw new ResourceAccessDeniedException();
        }
    }
}
