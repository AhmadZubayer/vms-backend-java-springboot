package com.example.stark_vms_server.exception;

import com.example.stark_vms_server.dto.SendResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final String VEHICLE_NUMBER_INDEX = "uq_vehicles_vehicle_number";

    private static final String UNEXPECTED_FAILURE = "The request could not be completed.";

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ResponseEntity<Object> response =
                super.handleExceptionInternal(exception, body, headers, status, request);
        if (response == null) {
            return null;
        }

        Object answered = response.getBody();
        if (answered instanceof SendResponse<?> envelope) {
            Object fields = envelope.data();
            return ResponseEntity.status(response.getStatusCode())
                    .headers(json(response.getHeaders()))
                    .body(SendResponse.of(
                            envelope.statusCode(), named(request, envelope.message()), fields
                    ));
        }

        String reason = answered instanceof ProblemDetail problem
                ? describe(problem)
                : UNEXPECTED_FAILURE;

        return ResponseEntity.status(response.getStatusCode())
                .headers(json(response.getHeaders()))
                .body(SendResponse.of(
                        response.getStatusCode().value(), named(request, reason), null
                ));
    }

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
                exception, invalidFields(status, errors), headers, status, request
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
                exception, invalidFields(status, errors), headers, status, request
        );
    }

    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    public void rethrowSecurityException(RuntimeException exception) {
        throw exception;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<SendResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        if (violatedConstraint(exception).equals(VEHICLE_NUMBER_INDEX)) {
            return failure(
                    HttpStatus.CONFLICT, request, "A vehicle with that number already exists."
            );
        }

        logger.error("Unexpected database constraint violation", exception);
        return failure(
                HttpStatus.INTERNAL_SERVER_ERROR, request,
                "The database could not save the change."
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<SendResponse<Void>> handleUnexpectedError(
            Exception exception, HttpServletRequest request) {
        logger.error("Unexpected request failure", exception);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, request, UNEXPECTED_FAILURE);
    }

    public static ResponseEntity<SendResponse<Void>> failure(
            HttpStatus status, HttpServletRequest request, String reason
    ) {
        String message = FailedOperation.message(
                request.getMethod(), request.getRequestURI(), reason
        );

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(SendResponse.of(status, message, null));
    }

    private static String named(WebRequest request, String reason) {
        if (request instanceof ServletWebRequest servlet) {
            HttpServletRequest http = servlet.getRequest();
            return FailedOperation.message(http.getMethod(), http.getRequestURI(), reason);
        }
        return reason;
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

    private static SendResponse<List<FieldViolation>> invalidFields(
            HttpStatusCode status, List<FieldViolation> errors
    ) {
        return SendResponse.of(status.value(), "Some fields are invalid.", errors);
    }

    private static String describe(ProblemDetail problem) {
        String detail = problem.getDetail();
        if (detail != null && !detail.isBlank()) {
            return detail;
        }
        return problem.getTitle() == null ? UNEXPECTED_FAILURE : problem.getTitle();
    }

    private static HttpHeaders json(HttpHeaders headers) {
        HttpHeaders copy = new HttpHeaders();
        copy.putAll(headers);
        copy.setContentType(MediaType.APPLICATION_JSON);
        return copy;
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
