/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.feature.members.entity.FieldSetPlacement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A bundle stands together on a form, in its own order, where its first question was put.
 */
class FieldSetsTest {
    private static final Map<Integer, FieldSetPlacement> ADDRESS = Map.of(
            10, new FieldSetPlacement(1, 0),
            11, new FieldSetPlacement(1, 1),
            12, new FieldSetPlacement(1, 2));

    @Test
    void aBundleIsPulledTogetherWhereItsFirstQuestionStands() {
        assertEquals(List.of(5, 10, 11, 12, 6), FieldSets.keepTogether(List.of(5, 11, 6, 12, 10), ADDRESS));
    }

    @Test
    void questionsOnTheirOwnKeepTheirOrder() {
        assertEquals(List.of(3, 2, 1), FieldSets.keepTogether(List.of(3, 2, 1), ADDRESS));
    }

    @Test
    void aBundleOnlyPartlyOnAFormKeepsWhatIsThere() {
        assertEquals(List.of(10, 12, 7), FieldSets.keepTogether(List.of(12, 7, 10), ADDRESS));
    }
}
