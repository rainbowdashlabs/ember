/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import dev.chojo.ember.feature.restriction.RestrictionMember;

import java.util.List;

/**
 * The preparation and follow-up time one person is credited with on one sheet.
 *
 * <p>Where several rules name the same person they get the largest preparation and the largest
 * follow-up among them, never the sum: two rules describe who does the work, not two pieces of it,
 * and adding a rule must not count anybody twice.
 *
 * @param preparationMinutes minutes credited before the appointment
 * @param followUpMinutes    minutes credited after it
 */
public record ExtraTime(int preparationMinutes, int followUpMinutes) {

    /** No extra time at all. */
    public static final ExtraTime NONE = new ExtraTime(0, 0);

    /**
     * The extra time a sheet's rules credit to a member.
     *
     * @param rules  the sheet's rules
     * @param member the member and what a restriction can name of them
     * @return the largest preparation and the largest follow-up among the rules naming the member,
     *     {@link #NONE} where none does
     */
    public static ExtraTime of(List<TimeRule> rules, RestrictionMember member) {
        int preparation = 0;
        int followUp = 0;
        for (TimeRule rule : rules) {
            if (!rule.appliesTo(member)) continue;
            preparation = Math.max(preparation, rule.preparationMinutes());
            followUp = Math.max(followUp, rule.followUpMinutes());
        }
        return preparation == 0 && followUp == 0 ? NONE : new ExtraTime(preparation, followUp);
    }

    /** @return the preparation and follow-up together, in minutes */
    public int totalMinutes() {
        return preparationMinutes + followUpMinutes;
    }
}
