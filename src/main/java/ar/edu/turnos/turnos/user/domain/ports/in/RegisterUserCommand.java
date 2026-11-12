package ar.edu.turnos.turnos.user.domain.ports.in;

public record RegisterUserCommand(
        String login,
        String password,
        String firstName,
        String lastName,
        String email,
        String imageUrl,
        String langKey) {
}
