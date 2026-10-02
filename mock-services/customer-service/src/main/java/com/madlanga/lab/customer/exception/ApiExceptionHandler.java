package com.madlanga.lab.customer.exception;

import com.madlanga.lab.customer.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> invalidInput(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Supply valid synthetic references and required fields", null, null);
    }

    @ExceptionHandler(DownstreamException.class)
    ResponseEntity<ApiError> downstreamFailure(DownstreamException ex) {
        log.warn("event=dependency_failed dependency=document-service downstreamStatus={}", ex.downstreamStatus());
        return error(HttpStatus.BAD_GATEWAY, "DOWNSTREAM_FAILURE", "Required downstream request failed", "document-service", ex.downstreamStatus());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message,
                                           String dependency, Integer downstreamStatus) {
        return ResponseEntity.status(status).body(new ApiError(code, message, "customer-service",
                dependency, downstreamStatus, MDC.get("correlationId")));
    }
}
