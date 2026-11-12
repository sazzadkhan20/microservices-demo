package com.platform.messaging.web;

import com.platform.common.http.DependencyUnavailableException;
import com.platform.common.security.PermissionLookupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/**
 * Platform-level HTTP errors for every servlet service, evaluated before each service's own advice.
 * Only framework / infrastructure exceptions are mapped here; business mappings stay in the services.
 *
 * <ul>
 *   <li>client mistakes (unreadable JSON, wrong method or media type, missing/invalid params) → 4xx, never 500;</li>
 *   <li>overload or dependency failure (DB pool exhausted, query timeout, People unavailable)
 *       → 503 + {@code Retry-After}, so callers back off instead of seeing a misleading 409/500.</li>
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlatformErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(PlatformErrorAdvice.class);
    private static final String RETRY_AFTER_SECONDS = "2";

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException ex) {
        return PlatformErrors.of(HttpStatus.BAD_REQUEST, "MALFORMED_BODY", "Malformed or unreadable request body");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> missingParam(MissingServletRequestParameterException ex) {
        return PlatformErrors.of(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Missing required parameter: " + ex.getParameterName());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, String>> missingHeader(MissingRequestHeaderException ex) {
        return PlatformErrors.of(HttpStatus.BAD_REQUEST, "MISSING_HEADER", "Missing required header: " + ex.getHeaderName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> typeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName() == null ? "parameter" : ex.getName();
        return PlatformErrors.of(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid value for " + name);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> method(HttpRequestMethodNotSupportedException ex) {
        return PlatformErrors.of(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> mediaType(HttpMediaTypeNotSupportedException ex) {
        return PlatformErrors.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported content type");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, String>> notAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return PlatformErrors.of(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "Requested representation not available");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NoResourceFoundException ex) {
        return PlatformErrors.of(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotGetJdbcConnectionException.class, CannotCreateTransactionException.class,
            QueryTimeoutException.class, AsyncRequestTimeoutException.class})
    public ResponseEntity<Map<String, String>> overloaded(Exception ex) {
        log.warn("Service saturated, shedding request: {}", ex.toString());
        return unavailable("SERVICE_BUSY", "The service is busy, please retry shortly");
    }

    @ExceptionHandler({DependencyUnavailableException.class, PermissionLookupException.class})
    public ResponseEntity<Map<String, String>> dependency(RuntimeException ex) {
        log.warn("Dependency unavailable: {}", ex.getMessage());
        return unavailable("DEPENDENCY_UNAVAILABLE", "A required service is temporarily unavailable, please retry shortly");
    }

    private static ResponseEntity<Map<String, String>> unavailable(String code, String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(PlatformErrors.body(503, code, message));
    }
}
