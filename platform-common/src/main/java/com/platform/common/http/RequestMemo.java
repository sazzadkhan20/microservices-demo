package com.platform.common.http;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.function.Supplier;

/**
 * Memoise a remote lookup for the lifetime of the current HTTP request only.
 *
 * <p>Removes request-local N+1 fan-out (e.g. one Time&amp;Work schedule call per leave type) without
 * introducing any cross-request staleness: the next request always fetches again. Outside a request
 * (jobs, message listeners) the loader runs every time, exactly as before.
 */
public final class RequestMemo {

    private static final String PREFIX = RequestMemo.class.getName() + ":";

    private RequestMemo() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Supplier<T> loader) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return loader.get();
        }
        String k = PREFIX + key;
        Object hit = attrs.getAttribute(k, RequestAttributes.SCOPE_REQUEST);
        if (hit != null) {
            return (T) hit;
        }
        T value = loader.get();
        if (value != null) {
            attrs.setAttribute(k, value, RequestAttributes.SCOPE_REQUEST);
        }
        return value;
    }
}
