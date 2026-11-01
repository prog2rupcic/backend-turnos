package ar.edu.turnos.turnos.user.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank @Size(min = 3, max = 50) String login,
        @NotBlank @Size(min = 4, max = 100) String password,
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @Size(max = 256) String imageUrl,
        @Size(min = 2, max = 10) String langKey) {
}
