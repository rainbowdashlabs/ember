/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * Who sees a member tag.
 *
 * <p>A private tag is a label for the people allowed to view members and nothing else: it is never a
 * badge, the tagged member never sees it, and it cannot select people for an audience or a
 * restriction, because any feature picking people by it would show who carries it.
 */
public enum TagVisibility {
    /**
     * Shown as a badge behind member names, wherever a name appears.
     */
    BADGE,
    /**
     * Shown on the member, not behind their name.
     */
    PLAIN,
    /**
     * Shown only to readers allowed to view members.
     */
    PRIVATE;

    /**
     * Whether a tag of this visibility is worn as a badge behind a member's name.
     */
    public boolean badge() {
        return this == BADGE;
    }

    /**
     * Whether a tag of this visibility is kept to readers allowed to view members.
     */
    public boolean restricted() {
        return this == PRIVATE;
    }
}
