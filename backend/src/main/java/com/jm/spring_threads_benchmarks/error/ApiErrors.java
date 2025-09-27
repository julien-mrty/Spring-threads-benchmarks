package com.jm.spring_threads_benchmarks.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

@RestControllerAdvice
class ApiErrors {

    record ErrorBody(OffsetDateTime timestamp, int status, String error, String message, String path, Object details) {}

    private ResponseEntity<ErrorBody> build(HttpStatus status, String message, String path, Object details) {
        return ResponseEntity.status(status).body(
                new ErrorBody(OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message, path, details));
    }

    // 404 when repo can't find a row
    @ExceptionHandler(EmptyResultDataAccessException.class)
    ResponseEntity<ErrorBody> notFound(HttpServletRequest req, EmptyResultDataAccessException ex) {
        return build(HttpStatus.NOT_FOUND, "Resource not found", req.getRequestURI(), null);
    }

    // 400 when @Valid on @RequestBody fails
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> badRequestBody(HttpServletRequest req, MethodArgumentNotValidException ex) {
        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", req.getRequestURI(), fieldErrors);
    }

    // 400 when @Validated on params/path vars fails
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorBody> badRequestParams(HttpServletRequest req, ConstraintViolationException ex) {
        var violations = ex.getConstraintViolations().stream()
                .map(v -> Map.of("param", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Constraint violation", req.getRequestURI(), violations);
    }

    // 409 or 400 for database uniqueness/foreign key errors
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> conflict(HttpServletRequest req, DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "Data integrity violation", req.getRequestURI(), null);
    }

    // (Optional) 504 for slow downstream/DB timeouts in benchmarks
    @ExceptionHandler(org.springframework.dao.QueryTimeoutException.class)
    ResponseEntity<ErrorBody> gatewayTimeout(HttpServletRequest req, RuntimeException ex) {
        return build(HttpStatus.GATEWAY_TIMEOUT, "Operation timed out", req.getRequestURI(), null);
    }

    // Fallback 500
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> generic(HttpServletRequest req, Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", req.getRequestURI(), null);
    }
}

