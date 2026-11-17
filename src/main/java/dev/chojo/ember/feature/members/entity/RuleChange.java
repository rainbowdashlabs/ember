/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import java.util.List;

/**
 * Who a rule took into its group or tag, and who it let go, in one run.
 *
 * @param owner  the group or tag the rule fills
 * @param joined the members that came in
 * @param left   the members that went out
 */
public record RuleChange(RuleOwner owner, List<Integer> joined, List<Integer> left) {

    /** A run of a rule that changed nobody. */
    public static RuleChange none(RuleOwner owner) {
        return new RuleChange(owner, List.of(), List.of());
    }

    /** Whether anybody came in or went out. */
    public boolean isEmpty() {
        return joined.isEmpty() && left.isEmpty();
    }
}
