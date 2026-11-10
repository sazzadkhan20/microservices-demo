package com.platform.payroll.application;

import java.util.Map;

/**
 * Business-rule refusal or state conflict with a stable code for the error envelope (guide §7).
 * 409 = state/revision conflict, 422 = rule refusal, 404 = not found, 403 = scope.
 */
public class PayrollException extends RuntimeException {

    private final int status;
    private final String code;
    private final Map<String, Object> details;

    public PayrollException(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public PayrollException(int status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public static PayrollException conflict(String code, String message) {
        return new PayrollException(409, code, message);
    }

    public static PayrollException rule(String code, String message) {
        return new PayrollException(422, code, message);
    }

    public static PayrollException rule(String code, String message, Map<String, Object> details) {
        return new PayrollException(422, code, message, details);
    }

    public static PayrollException notFound(String message) {
        return new PayrollException(404, "NOT_FOUND", message);
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
