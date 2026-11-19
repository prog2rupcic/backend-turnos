package ar.edu.turnos.turnos.user.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthenticateUserRequest(@NotBlank String username, @NotBlank String password, Boolean rememberMe) {
}
