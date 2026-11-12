package ar.edu.turnos.turnos.user.infrastructure.web.controller;

import ar.edu.turnos.turnos.user.domain.ports.in.RegisterUserCommand;
import ar.edu.turnos.turnos.user.domain.ports.in.RegisterUserUseCase;
import ar.edu.turnos.turnos.user.infrastructure.web.dto.RegisterUserRequest;
import ar.edu.turnos.turnos.user.infrastructure.web.dto.UserResponse;
import ar.edu.turnos.turnos.user.infrastructure.web.mapper.UserDtoMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserRegistrationController {
    private final RegisterUserUseCase registerUserUseCase;
    private final UserDtoMapper mapper;

    public UserRegistrationController(RegisterUserUseCase registerUserUseCase, UserDtoMapper mapper) {
        this.registerUserUseCase = registerUserUseCase;
        this.mapper = mapper;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        var command = new RegisterUserCommand(request.login(), request.password(), request.firstName(),
                request.lastName(), request.email(), request.imageUrl(), request.langKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(registerUserUseCase.register(command)));
    }
}
