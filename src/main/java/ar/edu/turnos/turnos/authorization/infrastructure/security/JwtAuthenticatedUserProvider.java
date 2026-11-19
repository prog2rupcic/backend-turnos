package ar.edu.turnos.turnos.authorization.infrastructure.security;

import ar.edu.turnos.turnos.authorization.domain.ports.out.AuthenticatedUserProvider;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticatedUserProvider implements AuthenticatedUserProvider {
    @Override
    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Autenticación requerida");
        }
        Long userId = UserJwtSubject.userId(jwtAuthentication.getToken().getSubject());
        if (userId == null) {
            throw new AuthenticationCredentialsNotFoundException("Identidad de usuario inválida");
        }
        return userId;
    }
}
