package ar.edu.turnos.turnos.authorization.domain.ports.out;

public interface AuthenticatedUserProvider {
    Long currentUserId();
}
