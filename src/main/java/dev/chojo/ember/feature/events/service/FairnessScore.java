/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.MemberRegistrationStats;

/**
 * How strongly a member's sign-up history suggests they should be let in next.
 *
 * <p>The score rises with denials and falls with acceptances: {@code denialRatio * 50 + denied * 5 -
 * accepted * 2 + 50}, rounded to one decimal and never below zero. Somebody without any decision yet
 * scores 50. The priority band summarises the accept rate: below half is HIGH, below three quarters
 * MEDIUM, anything else LOW, and NONE without any decision.
 *
 * @param score      the fairness score
 * @param acceptRate the share of decisions that accepted, rounded to two decimals; 1 without any
 * @param priority   HIGH, MEDIUM, LOW or NONE
 */
public record FairnessScore(double score, double acceptRate, String priority) {
    /** The score of somebody without any history at this station, such as a partner's member. */
    public static final FairnessScore NEWCOMER = of(new MemberRegistrationStats(0, 0, 0, 0, 0, null));

    /**
     * Scores a member's history.
     *
     * @param stats the member's sign-ups, acceptances, denials and declines
     * @return their score
     */
    public static FairnessScore of(MemberRegistrationStats stats) {
        int decisions = stats.accepted() + stats.denied();
        double acceptRate = decisions > 0 ? (double) stats.accepted() / decisions : 1.0;
        String priority;
        if (decisions == 0) priority = "NONE";
        else if (acceptRate < 0.5) priority = "HIGH";
        else if (acceptRate < 0.75) priority = "MEDIUM";
        else priority = "LOW";
        double denialRatio = decisions > 0 ? (double) stats.denied() / decisions : 0;
        double score = Math.round((denialRatio * 50 + stats.denied() * 5 - stats.accepted() * 2 + 50) * 10) / 10.0;
        return new FairnessScore(Math.max(0, score), Math.round(acceptRate * 100) / 100.0, priority);
    }
}
