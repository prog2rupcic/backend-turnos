package ar.edu.turnos.turnos.authorization.domain.model;

public final class OwnershipPolicy {
    private OwnershipPolicy() {
    }

    public static boolean isOwner(Long authenticatedUserId, Long ownerUserId) {
        return authenticatedUserId != null && ownerUserId != null && authenticatedUserId.equals(ownerUserId);
    }
}
