package com.jm.runner.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
class ApiErrors {

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ProblemDetail handle(ResponseStatusException ex, HttpServletRequest req) {
        var pd = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        pd.setTitle(ex.getStatusCode().toString());
        pd.setInstance(java.net.URI.create(req.getRequestURI()));

        return pd; // JSON body even for browsers
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail notFound(ResponseStatusException ex, HttpServletRequest req) {
        var pd = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        pd.setInstance(URI.create(req.getRequestURI()));

        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException ex, HttpServletRequest req) {
        var pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setInstance(URI.create(req.getRequestURI()));

        return pd;
    }
}
