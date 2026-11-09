package com.platform.gateway.error;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamUnavailableExceptionHandlerTest {

    @Test
    void detectsConnectException() {
        assertTrue(UpstreamUnavailableExceptionHandler.isUpstreamUnavailable(
                new ConnectException("Connection refused")));
    }

    @Test
    void detectsWrappedConnectException() {
        RuntimeException wrapped = new RuntimeException(
                "proxy failed",
                new ConnectException("Connection refused: getsockopt: /127.0.0.1:8082"));
        assertTrue(UpstreamUnavailableExceptionHandler.isUpstreamUnavailable(wrapped));
    }

    @Test
    void detectsAnnotatedConnectExceptionMessage() {
        assertTrue(UpstreamUnavailableExceptionHandler.isUpstreamUnavailable(
                new RuntimeException("Connection refused: getsockopt")));
    }

    @Test
    void detectsUnknownHostAndTimeout() {
        assertTrue(UpstreamUnavailableExceptionHandler.isUpstreamUnavailable(new UnknownHostException("x")));
        assertTrue(UpstreamUnavailableExceptionHandler.isUpstreamUnavailable(new TimeoutException("t")));
    }

    @Test
    void ignoresUnrelatedErrors() {
        assertFalse(UpstreamUnavailableExceptionHandler.isUpstreamUnavailable(
                new IllegalArgumentException("bad request")));
        assertFalse(UpstreamUnavailableExceptionHandler.isUpstreamUnavailable(null));
    }
}
