/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMember;
import org.jspecify.annotations.Nullable;

/**
 * Preparation and follow-up time a template or a sheet credits to the people it names.
 *
 * <p>A rule never credits more people than it named. One that names nobody applies to nobody, where
 * everywhere else an empty restriction lets everybody through. One that lost a user type, group or
 * tag it named, because the group or tag was deleted, applies to nobody either: dropping one part of
 * a rule that needs all of them would hand the time to everybody matching the rest.
 *
 * @param id                 unique rule identifier
 * @param position           where the rule stands in its list, starting at 0
 * @param label              what the rule is for, such as a role name, null where nobody named it
 * @param preparationMinutes minutes credited for preparing the appointment
 * @param followUpMinutes    minutes credited for what comes after it
 * @param audience           whom the rule names
 * @param intact             false where a group or tag the rule named has been deleted since
 */
public record TimeRule(
        int id,
        int position,
        @Nullable String label,
        int preparationMinutes,
        int followUpMinutes,
        RestrictionAudience audience,
        boolean intact) {

    /**
     * Whether this rule credits a member.
     *
     * @param member the member and what a restriction can name of them
     * @return true where the rule is intact, names somebody and the member is among them
     */
    public boolean appliesTo(RestrictionMember member) {
        return intact && !audience.namesNobody() && audience.includes(member);
    }

    /** This rule as the shape a list of rules is written with, which is how a sheet copies it. */
    public TimeRuleInput toInput() {
        return new TimeRuleInput(label, preparationMinutes, followUpMinutes, audience);
    }
}
