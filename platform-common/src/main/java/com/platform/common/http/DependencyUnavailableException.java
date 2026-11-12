package com.platform.common.http;

/**
 * A synchronous dependency (People directory, Time&amp;Work, …) failed, timed out or its circuit is
 * open. Subclass of {@link IllegalStateException} so existing callers that already handle
 * {@code IllegalStateException} keep their behaviour; the shared web error advice maps it to
 * {@code 503 Service Unavailable} with {@code Retry-After} instead of a misleading 409/500.
 */
public class DependencyUnavailableException extends IllegalStateException {

    public DependencyUnavailableException(String message) {
        super(message);
    }

    public DependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
