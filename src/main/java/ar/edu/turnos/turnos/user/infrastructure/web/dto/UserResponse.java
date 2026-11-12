package ar.edu.turnos.turnos.user.infrastructure.web.dto;

import java.time.Instant;
import java.util.Set;

public record UserResponse(
        Long id,
        String login,
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
