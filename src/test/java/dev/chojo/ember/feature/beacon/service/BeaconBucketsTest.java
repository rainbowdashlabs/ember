/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Counts as they leave an instance, which is never as exact numbers.
 */
class BeaconBucketsTest {

    /**
     * The whole point: a number never travels. An exact figure reported day after day can be matched
     * against publicly listed stations, which would undo the one promise the metrics identifier makes.
     */
    @Test
    void noCountLeavesAsANumber() {
        for (int i = 0; i < 2000; i += 7) {
            assertFalse(BeaconBuckets.members(i).matches("\\d+"), "members leaked an exact " + i);
            assertFalse(BeaconBuckets.size(i).matches("\\d+") && i != 0, "size leaked an exact " + i);
        }
    }

    /** Member counts use the buckets the public station directory already publishes. */
    @Test
    void membersUseTheBucketsTheDirectoryAlreadyPublishes() {
        assertEquals("<10", BeaconBuckets.members(3));
        assertEquals("10-50", BeaconBuckets.members(20));
        assertEquals("50-200", BeaconBuckets.members(100));
        assertEquals("200+", BeaconBuckets.members(5000));
    }

    /** Nothing is its own answer, because an empty store is not a secret. */
    @Test
    void nothingIsSaidPlainly() {
        assertEquals("0", BeaconBuckets.size(0));
    }

    /** The buckets widen as they grow, because a difference of one stops meaning anything. */
    @Test
    void bucketsWidenAsTheyGrow() {
        assertEquals("<10", BeaconBuckets.size(9));
        assertEquals("10-50", BeaconBuckets.size(49));
        assertEquals("50-200", BeaconBuckets.size(199));
        assertEquals("200-1000", BeaconBuckets.size(999));
        assertEquals("1000+", BeaconBuckets.size(1000));
    }
}
