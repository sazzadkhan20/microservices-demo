package com.platform.leave.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovalChainSupportTest {

    @Test
    void defaultIsManagerThenAdmin() {
        List<Map<String, Object>> chain = ApprovalChainSupport.defaultChain();
        assertEquals(2, chain.size());
        assertEquals("MANAGER", chain.get(0).get("role"));
        assertEquals("ADMIN", chain.get(1).get("role"));
    }

    @Test
    void rejectsMoreThanFourSteps() {
        assertThrows(IllegalArgumentException.class, () ->
                ApprovalChainSupport.normalizeAndValidate(List.of(
                        Map.of("role", "MANAGER"),
                        Map.of("role", "HR"),
                        Map.of("role", "ADMIN"),
                        Map.of("role", "SUPERADMIN"),
                        Map.of("role", "HR")
                )));
    }

    @Test
    void rejectsConsecutiveDuplicateRoles() {
        assertThrows(IllegalArgumentException.class, () ->
                ApprovalChainSupport.normalizeAndValidate(List.of(
                        Map.of("role", "MANAGER"),
                        Map.of("role", "MANAGER")
                )));
    }

    @Test
    void namedRequiresEmployeeId() {
        assertThrows(IllegalArgumentException.class, () ->
                ApprovalChainSupport.normalizeAndValidate(List.of(
                        Map.of("role", "NAMED_EMPLOYEE")
                )));
    }

    @Test
    void namedShorthandParsed() {
        List<Map<String, Object>> chain = ApprovalChainSupport.normalizeAndValidate(List.of(
                Map.of("role", "MANAGER"),
                Map.of("role", "NAMED:42")
        ));
        assertEquals(2, chain.size());
        assertEquals("NAMED_EMPLOYEE", chain.get(1).get("role"));
        assertEquals(42L, ((Number) chain.get(1).get("namedEmployeeId")).longValue());
    }

    @Test
    void emptyFallsBackToDefault() {
        List<Map<String, Object>> chain = ApprovalChainSupport.normalizeAndValidate(List.of());
        assertEquals("MANAGER", chain.get(0).get("role"));
        assertTrue(ApprovalChainSupport.parseAndValidateJson(null).size() >= 2);
    }
}
