package com.platform.common.api;

import java.util.List;

/**
 * Standard paginated envelope for all list APIs across microservices.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort
) {
    public static <T> PageResponse<T> of(List<T> content, PageQuery query, long total) {
        int totalPages = query.size() == 0 ? 0 : (int) Math.ceil((double) total / query.size());
        return new PageResponse<>(content, query.page(), query.size(), total, totalPages, query.sort());
    }
}
