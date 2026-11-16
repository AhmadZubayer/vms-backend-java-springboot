package com.example.stark_vms_server.exception;

import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String VEHICLE_NUMBER_INDEX = "uq_vehicles_vehicle_number";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<FieldViolation> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();

        return handleExceptionInternal(
                exception, validationProblem(status, errors), headers, status, request
        );
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<FieldViolation> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                parameterName(result.getMethodParameter()), error.getDefaultMessage()
                        )))
                .toList();

        return handleExceptionInternal(
                exception, validationProblem(status, errors), headers, status, request
        );
    }

    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    public void rethrowSecurityException(RuntimeException exception) {
        throw exception;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        if (violatedConstraint(exception).equals(VEHICLE_NUMBER_INDEX)) {
            return ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT, "A vehicle with that number already exists."
            );
        }

        logger.error("Unexpected database constraint violation", exception);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "The database could not save the change."
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedError(Exception exception) {
        logger.error("Unexpected request failure", exception);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "The request could not be completed."
        );
    }

    public static ResponseStatusException vehicleNotFound(long id) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Vehicle " + id + " was not found."
        );
    }

    public static ResponseStatusException pageOffsetTooLarge() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page offset is too large.");
    }

    public static ResponseStatusException unsortableProperty(String property) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Cannot sort by '" + property + "'."
        );
    }

    private static ProblemDetail validationProblem(
            HttpStatusCode status, List<FieldViolation> errors
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status, "Check the fields listed in errors."
        );
        problem.setTitle("Request validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static String parameterName(MethodParameter parameter) {
        String name = parameter.getParameterName();
        return name != null ? name : "request";
    }

    private static String violatedConstraint(DataIntegrityViolationException exception) {
        if (exception.getCause() instanceof ConstraintViolationException violation
                && violation.getConstraintName() != null) {
            return violation.getConstraintName();
        }
        return "";
    }

    public record FieldViolation(String field, String message) {
    }
}
