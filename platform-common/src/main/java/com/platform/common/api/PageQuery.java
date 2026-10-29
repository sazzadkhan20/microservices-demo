package com.platform.common.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Standard list query used by all services: page, size, sort, q (search), filter.*.
 * Tenant-agnostic — allow-listed fields enforced per resource in each service.
 */
public final class PageQuery {

    public static final int DEFAULT_SIZE = 20;
    /**
     * Hard cap for every list API (documented in QII_HRMS_PLAN: page size max 100).
     * Clients needing more rows must page; larger values are silently clamped.
     */
    public static final int MAX_SIZE = 100;

    private final int page;
    private final int size;
    private final String sort;
    private final String q;
    private final Map<String, String> filters;

    public PageQuery(int page, int size, String sort, String q, Map<String, String> filters) {
        this.page = Math.max(page, 1);
        this.size = Math.min(Math.max(size, 1), MAX_SIZE);
        this.sort = sort == null || sort.isBlank() ? null : sort.trim();
        this.q = q == null || q.isBlank() ? null : q.trim();
        this.filters = filters == null ? Map.of() : Map.copyOf(filters);
    }

    public static PageQuery fromParams(Map<String, String> allParams) {
        int page = parseInt(allParams.get("page"), 1);
        int size = parseInt(allParams.getOrDefault("size", allParams.get("pageSize")), DEFAULT_SIZE);
        String sort = allParams.get("sort");
        String q = allParams.get("q");
        if (q == null) {
            q = allParams.get("search");
        }
        Map<String, String> filters = new LinkedHashMap<>();
        allParams.forEach((k, v) -> {
            if (k.startsWith("filter.") && v != null && !v.isBlank()) {
                filters.put(k.substring("filter.".length()), v);
            }
        });
        return new PageQuery(page, size, sort, q, filters);
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }

    public int offset() {
        return (page - 1) * size;
    }

    public String sort() {
        return sort;
    }

    public String q() {
        return q;
    }

    public Map<String, String> filters() {
        return filters;
    }

    public List<SortSpec> parseSort(String defaultField) {
        List<SortSpec> specs = new ArrayList<>();
        String raw = sort == null ? defaultField + ",asc" : sort;
        for (String part : raw.split(";")) {
            String[] bits = part.split(",");
            String field = bits[0].trim();
            boolean asc = bits.length < 2 || !"desc".equalsIgnoreCase(bits[1].trim());
            if (!field.isBlank()) {
                specs.add(new SortSpec(field, asc));
            }
        }
        if (specs.isEmpty()) {
            specs.add(new SortSpec(defaultField, true));
        }
        return specs;
    }

    public record SortSpec(String field, boolean ascending) {
        public String direction() {
            return ascending ? "ASC" : "DESC";
        }

        public String fieldLower() {
            return field.toLowerCase(Locale.ROOT);
        }
    }

    private static int parseInt(String v, int def) {
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
