package com.platform.messaging.outbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxRelayBackoffTest {

    @Test
    void exponentialBaseWithMidJitter() {
        assertEquals(1_000L, OutboxRelay.backoffMs(1, 0.5));
        assertEquals(2_000L, OutboxRelay.backoffMs(2, 0.5));
        assertEquals(4_000L, OutboxRelay.backoffMs(3, 0.5));
        assertEquals(8_000L, OutboxRelay.backoffMs(4, 0.5));
    }

    @Test
    void jitterIsPlusMinusTwentyPercentAndCapped() {
        assertEquals(800L, OutboxRelay.backoffMs(1, 0.0));
        assertEquals(1_200L, OutboxRelay.backoffMs(1, 1.0));
        assertEquals(900_000L, OutboxRelay.backoffMs(12, 0.5));
        assertEquals(900_000L, OutboxRelay.backoffMs(99, 0.5));
        assertEquals(1_000L, OutboxRelay.backoffMs(0, 0.5));
        long random = OutboxRelay.backoffMs(5);
        assertTrue(random >= 12_800L && random <= 19_200L, "attempt 5 base 16s ±20%");
    }
}
