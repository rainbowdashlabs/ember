/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetDiffTest {
    @Test
    void whatJoinsAndWhatLeavesKeepTheirOrder() {
        var diff = SetDiff.of(List.of(1, 2, 3, 4), List.of(5, 3, 1, 6));

        assertEquals(List.of(5, 6), diff.added());
        assertEquals(List.of(2, 4), diff.removed());
        assertEquals(List.of(5, 6, 2, 4), diff.touched());
        assertFalse(diff.isEmpty());
    }

    @Test
    void aValueNamedTwiceCountsOnce() {
        var diff = SetDiff.of(List.of(1, 1), List.of(2, 2, 1));

        assertEquals(List.of(2), diff.added());
        assertEquals(List.of(), diff.removed());
    }

    @Test
    void theSameSetOnBothSidesChangesNothing() {
        var diff = SetDiff.of(Set.of("a", "b"), List.of("b", "a"));

        assertTrue(diff.isEmpty());
        assertTrue(diff.touched().isEmpty());
    }
}
