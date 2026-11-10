package com.platform.payroll.api.rest;

import com.platform.common.api.ApiError;
import com.platform.messaging.correlation.CorrelationContext;
import com.platform.payroll.application.PayrollException;
import com.platform.payroll.infrastructure.FactsClient;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Common error envelope {status, error, code, message, correlationId, details} (guide §7). */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PayrollException.class)
    public ResponseEntity<ApiError> payroll(PayrollException ex, HttpServletRequest req) {
        return body(ex.status(), ex.code(), ex.getMessage(), req, ex.details());
    }

    @ExceptionHandler(FactsClient.FactsUnavailableException.class)
    public ResponseEntity<ApiError> facts(FactsClient.FactsUnavailableException ex, HttpServletRequest req) {
        log.warn("Payroll facts unavailable: {}", ex.getMessage());
        return body(503, "FACTS_UNAVAILABLE", ex.getMessage() + " — nothing was changed; try again shortly", req, Map.of());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiError> concurrency(RuntimeException ex, HttpServletRequest req) {
        return body(409, "CONCURRENT_UPDATE", "The payroll run was changed at the same time — reload and retry", req, Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException ex, HttpServletRequest req) {
        return body(400, "BAD_REQUEST", ex.getMessage(), req, Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> status(ResponseStatusException ex, HttpServletRequest req) {
        return body(ex.getStatusCode().value(), "HTTP_" + ex.getStatusCode().value(),
                ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason(), req, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(Exception ex, HttpServletRequest req) {
        log.error("Unhandled payroll error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return body(500, "INTERNAL_ERROR", "Unexpected error — reference " + CorrelationContext.getOrCreate(), req, Map.of());
    }

    private static ResponseEntity<ApiError> body(int status, String code, String message, HttpServletRequest req,
                                                 Map<String, Object> details) {
        HttpStatus hs = HttpStatus.resolve(status);
        return ResponseEntity.status(status).body(ApiError.of(status, hs == null ? String.valueOf(status) : hs.getReasonPhrase(),
                code, message, req.getRequestURI(), CorrelationContext.getOrCreate(), details));
    }
}
