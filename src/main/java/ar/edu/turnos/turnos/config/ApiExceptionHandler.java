package ar.edu.turnos.turnos.config;

import ar.edu.turnos.turnos.authorization.application.exception.ResourceAccessDeniedException;
import ar.edu.turnos.turnos.user.application.exception.UserAlreadyExistsException;
import ar.edu.turnos.turnos.user.application.exception.InvalidCredentialsException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceAccessDeniedException.class)
    ProblemDetail handleResourceAccessDenied(ResourceAccessDeniedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
        problem.setProperty("code", "RESOURCE_NOT_OWNED");
        return problem;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
        problem.setProperty("code", "INVALID_CREDENTIALS");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleInvalidRequest(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Datos inválidos");
        problem.setProperty("code", "VALIDATION_ERROR");
        List<String> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField()).distinct().sorted().toList();
        problem.setProperty("fields", fields);
        return problem;
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    ProblemDetail handleUserAlreadyExists(UserAlreadyExistsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setProperty("code", exception.getMessage().contains("email") ? "EMAIL_ALREADY_EXISTS" : "USERNAME_ALREADY_EXISTS");
        return problem;
    }
}
