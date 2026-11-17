package ar.edu.turnos.turnos.authorization.infrastructure.security;

public final class UserJwtSubject {
    private UserJwtSubject() {
    }

    public static Long userId(String subject) {
        if (subject == null || !subject.startsWith("user-")) {
            return null;
        }
        String id = subject.substring("user-".length());
        if (!id.matches("[1-9][0-9]*")) {
            return null;
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
