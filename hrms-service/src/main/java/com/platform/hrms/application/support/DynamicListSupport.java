package com.platform.hrms.application.support;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Config-driven list helper: q search + filter.* equality/operators + optional sort + page/size.
 * Controllers pass a filterValues map so new filter keys need no controller rewrite —
 * only expose extra keys in the map.
 *
 * Filter value operators (suffix on value):
 *   exact (default) — equality, case-insensitive
 *   value*          — starts-with
 *   *value* or *value — contains
 */
public final class DynamicListSupport {

    private DynamicListSupport() {}

    public static <T> PageResponse<T> page(
            List<T> all,
            PageQuery query,
            Predicate<T> searchMatch,
            Function<T, Map<String, Object>> filterValues) {

        List<T> filtered = all == null ? List.of() : new ArrayList<>(all);

        if (query.q() != null) {
            filtered = filtered.stream().filter(searchMatch).toList();
        }

        Map<String, String> filters = query.filters();
        if (!filters.isEmpty()) {
            filtered = filtered.stream().filter(item -> {
                Map<String, Object> values = filterValues.apply(item);
                for (Map.Entry<String, String> entry : filters.entrySet()) {
                    Object fieldValue = values.get(entry.getKey());
                    if (fieldValue == null) return false;
                    if (!matchesFilter(fieldValue.toString(), entry.getValue())) return false;
                }
                return true;
            }).toList();
        }

        if (query.sort() != null && !query.sort().isBlank()) {
            List<PageQuery.SortSpec> specs = query.parseSort("id");
            Comparator<T> cmp = null;
            for (PageQuery.SortSpec spec : specs) {
                Comparator<T> next = Comparator.comparing(
                        item -> {
                            Object v = filterValues.apply(item).get(spec.field());
                            return v == null ? "" : v.toString().toLowerCase(Locale.ROOT);
                        },
                        Comparator.nullsLast(String::compareTo)
                );
                if (!spec.ascending()) {
                    next = next.reversed();
                }
                cmp = cmp == null ? next : cmp.thenComparing(next);
            }
            if (cmp != null) {
                filtered = filtered.stream().sorted(cmp).toList();
            }
        }

        long total = filtered.size();
        int from = Math.min(query.offset(), filtered.size());
        int to = Math.min(from + query.size(), filtered.size());
        List<T> page = filtered.subList(from, to);

        return PageResponse.of(page, query, total);
    }

    public static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static boolean matchesFilter(String fieldValue, String rawFilter) {
        if (rawFilter == null) return true;
        String fv = fieldValue.toLowerCase(Locale.ROOT);
        String f = rawFilter.trim();
        if (f.startsWith("*") && f.endsWith("*") && f.length() > 1) {
            return fv.contains(f.substring(1, f.length() - 1).toLowerCase(Locale.ROOT));
        }
        if (f.startsWith("*")) {
            return fv.endsWith(f.substring(1).toLowerCase(Locale.ROOT));
        }
        if (f.endsWith("*")) {
            return fv.startsWith(f.substring(0, f.length() - 1).toLowerCase(Locale.ROOT));
        }
        return fv.equalsIgnoreCase(f);
    }
}
