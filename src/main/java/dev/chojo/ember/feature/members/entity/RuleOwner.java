/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * The group or tag a member rule fills.
 *
 * @param kind whether a group or a tag
 * @param id   the id of the group or tag
 */
public record RuleOwner(Kind kind, int id) {
    /** Whether a rule fills a group or a tag. */
    public enum Kind {
        GROUP,
        TAG
    }

    /** The rule of a group. */
    public static RuleOwner group(int groupId) {
        return new RuleOwner(Kind.GROUP, groupId);
    }

    /** The rule of a tag. */
    public static RuleOwner tag(int tagId) {
        return new RuleOwner(Kind.TAG, tagId);
    }
}
