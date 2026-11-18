/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/** What one node of a member rule is: a block joining its children, or a single check. */
public enum RuleNodeKind {
    /** Every child holds. */
    ALL,
    /** At least one child holds. */
    ANY,
    /** No child holds. */
    NONE,
    /** A single check on member data. */
    CHECK;

    /** Whether this node joins children rather than checking something itself. */
    public boolean isBlock() {
        return this != CHECK;
    }
}
