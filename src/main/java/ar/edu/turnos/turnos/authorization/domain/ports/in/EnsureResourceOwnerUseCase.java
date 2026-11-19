package ar.edu.turnos.turnos.authorization.domain.ports.in;

public interface EnsureResourceOwnerUseCase {
    void ensureOwner(Long persistedOwnerUserId);
}
