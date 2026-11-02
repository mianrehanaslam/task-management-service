package com.servicemarket.taskmanagement.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps every exception raised while handling an API request to an {@link ApiErrorResponse}.
 * Extending {@link ResponseEntityExceptionHandler} routes Spring MVC's own errors (405, 415, 404 for
 * unknown routes, ...) through the same body. Messages are fixed or built from safe values only, so
 * stack traces and internal details never reach the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<Object> handleTaskNotFound(TaskNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(InvalidTaskStatusException.class)
    public ResponseEntity<Object> handleInvalidTaskStatus(InvalidTaskStatusException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI(),
                List.of(new ApiErrorResponse.FieldError("status", ex.getMessage())));
    }

    /** The task was deleted or changed by another request between our read and our write. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleConcurrentModification(OptimisticLockingFailureException ex,
                                                               HttpServletRequest request) {
        log.warn("Concurrent modification processing {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.CONFLICT, "Task was modified or deleted by another request; please retry",
                request.getRequestURI(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred",
                request.getRequestURI(), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        List<ApiErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", path(request), fieldErrors);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        // Well-formed JSON with a value of the wrong type for a field, e.g. {"title": {}}.
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            ApiErrorResponse.FieldError fieldError =
                    new ApiErrorResponse.FieldError(fieldName(mismatch), "has an invalid type");
            return build(HttpStatus.BAD_REQUEST, "Validation failed", path(request), List.of(fieldError));
        }
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", path(request), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            @NonNull TypeMismatchException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getPropertyName() + "'",
                path(request), List.of());
    }

    /** Fallback for the remaining Spring MVC exceptions (405, 415, unknown route, ...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            @NonNull Exception ex, Object body, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode, @NonNull WebRequest request) {
        // Fixed message: Spring's own detail text echoes client input and internal resource names.
        ApiErrorResponse error = new ApiErrorResponse(Instant.now(clock), statusCode.value(),
                reasonPhrase(statusCode), "Request could not be processed: " + reasonPhrase(statusCode),
                path(request), List.of());
        return ResponseEntity.status(statusCode).headers(headers).contentType(MediaType.APPLICATION_JSON).body(error);
    }

    // Content type is fixed so the error body is written as JSON whatever the client's Accept header says.
    private ResponseEntity<Object> build(HttpStatus status, String message, String path,
                                         List<ApiErrorResponse.FieldError> fieldErrors) {
        ApiErrorResponse error = new ApiErrorResponse(Instant.now(clock), status.value(),
                status.getReasonPhrase(), message, path, fieldErrors);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(error);
    }

    private static String fieldName(JsonMappingException ex) {
        return ex.getPath().stream()
                .map(reference -> reference.getFieldName() != null
                        ? reference.getFieldName()
                        : "[" + reference.getIndex() + "]")
                .collect(Collectors.joining("."));
    }

    private static String reasonPhrase(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.getReasonPhrase() : String.valueOf(statusCode.value());
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : "";
    }
}
