package ar.edu.turnos.turnos.user.domain.ports.out;

import ar.edu.turnos.turnos.user.domain.model.User;

public interface UserTokenIssuer {
    String issue(User user, boolean rememberMe);
}
