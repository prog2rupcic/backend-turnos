package ar.edu.turnos.turnos.user.domain.model;

import java.time.Instant;
import java.util.Set;

public record User(
        Long id,
        String login,
        String passwordHash,
        String firstName,
        String lastName,
        String email,
        String imageUrl,
        String langKey,
        boolean activated,
        Set<String> authorities,
        Instant createdAt,
        Instant updatedAt) {
}
