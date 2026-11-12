package com.platform.gateway.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRevocationStoreTest {

    @Test
    void employeeRevocationRefusesSessionsIssuedAtOrBeforeIt() {
        assertTrue(SessionRevocationStore.revokedByEmployeeEntry("1000", 999L));
        assertTrue(SessionRevocationStore.revokedByEmployeeEntry("1000", 1000L));
        assertFalse(SessionRevocationStore.revokedByEmployeeEntry("1000", 1001L), "re-login after revocation is allowed");
    }

    @Test
    void legacyAndUnknownEntriesFailClosed() {
        assertTrue(SessionRevocationStore.revokedByEmployeeEntry("1", 5_000_000_000L));
        assertTrue(SessionRevocationStore.revokedByEmployeeEntry("garbage", 5L));
        assertTrue(SessionRevocationStore.revokedByEmployeeEntry("1000", null));
        assertFalse(SessionRevocationStore.revokedByEmployeeEntry(null, 5L));
    }
}
