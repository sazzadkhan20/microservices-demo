package com.platform.common.correlation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationIdsTest {

    @Test
    void acceptsUuidAndAlphanumericDash() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        assertTrue(CorrelationIds.isValid(uuid));
        assertEquals(uuid, CorrelationIds.sanitizeOrCreate(uuid));
        assertEquals("abcDEF12", CorrelationIds.sanitizeOrCreate("abcDEF12"));
    }

    @Test
    void rejectsShortSpacesAndInjectionThenIssuesNewUuid() {
        assertFalse(CorrelationIds.isValid("short"));
        assertFalse(CorrelationIds.isValid("has space!!"));
        assertFalse(CorrelationIds.isValid("id; DROP TABLE"));
        String generated = CorrelationIds.sanitizeOrCreate("<script>");
        assertTrue(CorrelationIds.isValid(generated));
        assertNotEquals("<script>", generated);
        assertTrue(CorrelationIds.isValid(CorrelationIds.sanitizeOrCreate(null)));
        assertTrue(CorrelationIds.isValid(CorrelationIds.sanitizeOrCreate("  ")));
    }
}
