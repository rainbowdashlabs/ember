/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * What an assignment names: a kind of member, or one group of them.
 *
 * <p>Two targets rather than one list, because they are answered differently. A role reaches whoever
 * is that kind of member today, and a group reaches whoever is in it.
 */
public enum ProfileFieldTarget {
    /** Everybody of one kind of member. */
    ROLE,
    /** Everybody in one group, whatever kind of member they are. */
    GROUP
}
