/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.entity;

import java.time.Instant;
import java.util.List;

/**
 * One line of what an appointment needs, answered for one evening.
 *
 * <p>The need is the question and where the gear comes from is part of the answer, not a second
 * question. Fourteen needed, ten of our own, four borrowed from station A, none outstanding: counting
 * only the station's own pieces would report a shortfall that was solved a week ago, and asking at
 * the time the line is written how many will be borrowed puts the decision before the information it
 * needs.
 *
 * @param need        the line
 * @param label       what the line asks for, in words
 * @param from        when the gear goes, the evening's start less the lead
 * @param to          when it is back, the evening's end plus the trail
 * @param own         how many of the station's own pieces are free for that window
 * @param borrowed    how many pieces are already here on loan against this line
 * @param outstanding how many pieces have been asked of a partner and not yet arrived
 * @param stock       how many pieces of this kind the station holds at all
 * @param claimed     how much of that stock is spoken for over the window
 * @param overClaim   the appointments that have promised the same stock to somebody else
 */
public record NeedCoverage(
        EquipmentNeed need,
        String label,
        Instant from,
        Instant to,
        int own,
        int borrowed,
        int outstanding,
        int stock,
        int claimed,
        List<EquipmentClaim> overClaim) {

    /**
     * How many pieces the line still has no answer for.
     *
     * @return the missing count
     */
    public int missing() {
        return Math.max(0, need.quantity() - own - borrowed - outstanding);
    }

    /**
     * Whether the line is answered, from wherever.
     *
     * @return {@code true} when nothing is missing
     */
    public boolean covered() {
        return missing() == 0;
    }
}
