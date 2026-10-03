/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.entity;

/**
 * Which guardians the member a letter is for must have for a block to be printed, beside the block's
 * restriction. Wording such as "both guardians" stands in a block with {@link #SECOND_GUARDIAN}, and
 * its alternative for a member with a single guardian in one with {@link #NO_SECOND_GUARDIAN}.
 */
public enum GuardianCondition {
    /** Printed only for a member with a second guardian. */
    SECOND_GUARDIAN,
    /** Printed only for a member without a second guardian. */
    NO_SECOND_GUARDIAN;

    /**
     * @param guardians how many guardians the member has
     * @return whether a block under this condition is printed for that member
     */
    public boolean holds(int guardians) {
        return switch (this) {
            case SECOND_GUARDIAN -> guardians >= 2;
            case NO_SECOND_GUARDIAN -> guardians < 2;
        };
    }
}
