package ar.edu.turnos.turnos.user.infrastructure.web.controller;

import ar.edu.turnos.turnos.user.domain.ports.in.AuthenticateUserCommand;
import ar.edu.turnos.turnos.user.domain.ports.in.AuthenticateUserUseCase;
import ar.edu.turnos.turnos.user.infrastructure.web.dto.AuthenticateUserRequest;
import ar.edu.turnos.turnos.user.infrastructure.web.dto.AuthenticationResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserAuthenticationController {
    private final AuthenticateUserUseCase authenticateUserUseCase;

    public UserAuthenticationController(AuthenticateUserUseCase authenticateUserUseCase) {
        this.authenticateUserUseCase = authenticateUserUseCase;
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(@Valid @RequestBody AuthenticateUserRequest request) {
        String token = authenticateUserUseCase.authenticate(
                new AuthenticateUserCommand(request.username(), request.password(), Boolean.TRUE.equals(request.rememberMe())));
        return ResponseEntity.ok().header("Authorization", "Bearer " + token)
                .body(new AuthenticationResponse(token));
    }
}
