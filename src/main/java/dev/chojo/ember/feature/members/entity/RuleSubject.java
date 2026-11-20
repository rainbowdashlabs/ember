/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/** What a check of a member rule looks at. */
public enum RuleSubject {
    /** Membership in a group, which may itself be kept by a rule. */
    GROUP,
    /** Holding a tag, which may itself be kept by a rule. */
    TAG,
    /** The member's user type. */
    USER_TYPE,
    /** How long somebody has been a member, counted from the join date. */
    MEMBER_SINCE,
    /** The age in whole years, counted from a date field, usually the birth date. */
    AGE,
    /** The answer to one of the station's profile fields. */
    FIELD,
    /** The answer to one of the association's profile fields. */
    CLUSTER_FIELD;

    /** Whether the check names a field it reads. */
    public boolean readsField() {
        return this == AGE || this == FIELD || this == CLUSTER_FIELD;
    }
}
