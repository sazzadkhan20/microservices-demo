package com.platform.common.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Platform list-query contract used by every Qii list API. */
class PageQueryTest {

    @Test
    void defaults_page1_size20() {
        PageQuery q = PageQuery.fromParams(Map.of());
        assertEquals(1, q.page());
        assertEquals(20, q.size());
        assertEquals(0, q.offset());
    }

    @Test
    void caps_size_at_100() {
        PageQuery q = PageQuery.fromParams(Map.of("size", "500"));
        assertEquals(100, q.size());
    }

    @Test
    void parses_filter_prefix_and_search_aliases() {
        PageQuery q = PageQuery.fromParams(Map.of(
                "page", "2",
                "size", "10",
                "sort", "name,desc",
                "search", "asman",
                "filter.status", "Active"
        ));
        assertEquals(2, q.page());
        assertEquals(10, q.size());
        assertEquals(10, q.offset());
        assertEquals("asman", q.q());
        assertEquals("Active", q.filters().get("status"));
        assertTrue(q.parseSort("id").get(0).field().equals("name"));
        assertEquals("DESC", q.parseSort("id").get(0).direction());
    }

    @Test
    void page_response_total_pages() {
        PageResponse<String> r = PageResponse.of(java.util.List.of("a", "b"), PageQuery.fromParams(Map.of("size", "2")), 5);
        assertEquals(3, r.totalPages());
        assertEquals(5, r.totalElements());
        assertEquals(2, r.content().size());
    }
}
