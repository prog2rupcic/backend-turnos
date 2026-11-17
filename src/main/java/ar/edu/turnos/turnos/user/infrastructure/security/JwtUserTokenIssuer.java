package ar.edu.turnos.turnos.user.infrastructure.security;

import ar.edu.turnos.turnos.user.domain.model.User;
import ar.edu.turnos.turnos.user.domain.ports.out.UserTokenIssuer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class JwtUserTokenIssuer implements UserTokenIssuer {
    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration regularTtl;
    private final Duration rememberMeTtl;

    public JwtUserTokenIssuer(JwtEncoder encoder, @Value("${turnos.security.issuer}") String issuer,
                              @Value("${turnos.security.session-ttl}") Duration regularTtl,
                              @Value("${turnos.security.remember-me-ttl}") Duration rememberMeTtl) {
        if (regularTtl.isNegative() || regularTtl.isZero() || rememberMeTtl.isNegative() || rememberMeTtl.isZero()) {
            throw new IllegalArgumentException("La duración de los JWT debe ser positiva");
        }
        this.encoder = encoder;
        this.issuer = issuer;
        this.regularTtl = regularTtl;
        this.rememberMeTtl = rememberMeTtl;
    }

    @Override
    public String issue(User user, boolean rememberMe) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("user-" + user.id())
                .audience(List.of("backend-turnos", "backend-catalogo"))
                .issuedAt(now)
                .expiresAt(now.plus(rememberMe ? rememberMeTtl : regularTtl))
                .claim("authorities", user.authorities())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
