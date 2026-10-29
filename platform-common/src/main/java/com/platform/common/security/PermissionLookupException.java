package com.platform.common.security;

/** HRMS could not be reached / returned a server error while resolving permissions (fail closed → 503). */
public class PermissionLookupException extends RuntimeException {

    public PermissionLookupException(String message, Throwable cause) {
        super(message, cause);
    }

    public PermissionLookupException(String message) {
        super(message);
    }
}
