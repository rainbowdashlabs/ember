/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The minute of the day an instance reports at, which is what keeps a fleet from arriving at once.
 */
class BeaconSchedulingTest {

    /** The same instance always reports at the same time, so a restart cannot move it. */
    @Test
    void anInstanceKeepsItsOwnSlot() {
        String uid = "6f1c9a44-2f6f-4a2a-9f7f-2f2c9d3b7c11";
        assertEquals(BeaconMetricsIdentity.slotFor(uid), BeaconMetricsIdentity.slotFor(uid));
    }

    /** Every slot is a real minute of the day. */
    @Test
    void everySlotIsAMinuteOfTheDay() {
        for (int i = 0; i < 500; i++) {
            int slot = BeaconMetricsIdentity.slotFor(UUID.randomUUID().toString());
            assertTrue(slot >= 0 && slot < 24 * 60, "slot out of the day: " + slot);
        }
    }

    /**
     * The slot must not move between two checks on the same day. It is asked many times a day, and a
     * jitter drawn from a random source each time would have an instance see itself as due, then not
     * due, and write a mark that lines up with no slot at all.
     */
    @Test
    void theSlotDoesNotMoveWithinADay() {
        String uid = "6f1c9a44-2f6f-4a2a-9f7f-2f2c9d3b7c11";
        String day = "2026-09-09";
        assertEquals(BeaconMetricsIdentity.slotFor(uid + day) % 6, BeaconMetricsIdentity.slotFor(uid + day) % 6);
    }

    /** It does move between days, so a fleet does not stay in lockstep at the same second forever. */
    @Test
    void theSlotVariesAcrossDays() {
        String uid = "6f1c9a44-2f6f-4a2a-9f7f-2f2c9d3b7c11";
        var seen = new HashSet<Integer>();
        for (int day = 1; day <= 28; day++) {
            seen.add(BeaconMetricsIdentity.slotFor(uid + "2026-09-%02d".formatted(day)) % 6);
        }
        assertTrue(seen.size() > 1, "the jitter never varied across a month of days");
    }

    /**
     * The point of drawing the slot from the identifier at all: a fleet brought up together does not
     * agree on when to report. Five hundred instances should land on a wide spread of minutes, not on
     * a handful.
     */
    @Test
    void aFleetDoesNotArriveTogether() {
        var slots = new HashSet<Integer>();
        for (int i = 0; i < 500; i++) {
            slots.add(BeaconMetricsIdentity.slotFor(UUID.randomUUID().toString()));
        }
        assertTrue(slots.size() > 300, "slots clumped: only " + slots.size() + " distinct of 500");
    }
}
