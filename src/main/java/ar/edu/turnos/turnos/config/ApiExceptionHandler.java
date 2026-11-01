package ar.edu.turnos.turnos.config;

import ar.edu.turnos.turnos.user.application.exception.UserAlreadyExistsException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(UserAlreadyExistsException.class)
    ProblemDetail handleUserAlreadyExists(UserAlreadyExistsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setProperty("code", exception.getMessage().contains("email") ? "EMAIL_ALREADY_EXISTS" : "USERNAME_ALREADY_EXISTS");
        return problem;
    }
}
