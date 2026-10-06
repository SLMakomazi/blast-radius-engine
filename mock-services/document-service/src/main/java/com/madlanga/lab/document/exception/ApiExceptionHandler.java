package com.madlanga.lab.document.exception;

import com.madlanga.lab.document.dto.ApiError;
import com.madlanga.lab.document.fault.SyntheticFaultException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> invalidInput(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Supply valid synthetic references and required fields", null, null);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<ApiError> databaseFailure(Exception ex) {
        log.warn("event=dependency_failed dependency=postgres code=DATABASE_UNAVAILABLE");
        return error(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "Document storage is unavailable", "postgres", null);
    }

    @ExceptionHandler(SyntheticFaultException.class)
    ResponseEntity<ApiError> syntheticApplicationFailure(SyntheticFaultException ex) {
        log.error("event=request_failed code=SYNTHETIC_APPLICATION_FAILURE");
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "SYNTHETIC_APPLICATION_FAILURE",
                "Synthetic local application failure", null, null);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message,
                                           String dependency, Integer downstreamStatus) {
        return ResponseEntity.status(status).body(new ApiError(code, message, "document-service",
                dependency, downstreamStatus, MDC.get("correlationId")));
    }
}
