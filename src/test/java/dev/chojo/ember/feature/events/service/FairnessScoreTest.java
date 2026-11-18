/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.MemberRegistrationStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FairnessScoreTest {

    private static FairnessScore of(int accepted, int denied) {
        return FairnessScore.of(new MemberRegistrationStats(1, accepted + denied, accepted, denied, 0, null));
    }

    @Test
    void somebodyWithoutDecisionsScoresFiftyWithoutPriority() {
        assertEquals(new FairnessScore(50.0, 1.0, "NONE"), of(0, 0));
        assertEquals(FairnessScore.NEWCOMER, of(0, 0));
    }

    @Test
    void denialsRaiseTheScoreAndAcceptancesLowerIt() {
        assertEquals(new FairnessScore(110.0, 0.0, "HIGH"), of(0, 2));
        assertEquals(new FairnessScore(42.0, 1.0, "LOW"), of(4, 0));
    }

    @Test
    void theScoreIsRoundedToOneDecimalAndTheRateToTwo() {
        var score = of(2, 1);

        assertEquals(67.7, score.score());
        assertEquals(0.67, score.acceptRate());
        assertEquals("MEDIUM", score.priority());
    }

    @Test
    void theScoreNeverFallsBelowZero() {
        assertEquals(0.0, of(40, 0).score());
    }
}
