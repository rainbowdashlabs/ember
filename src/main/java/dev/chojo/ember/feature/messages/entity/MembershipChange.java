/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import java.util.Set;

/**
 * What one reconciliation changed about a station chat.
 *
 * @param conversation the chat, as it was read before the change
 * @param added        the accounts taken in
 * @param removed      the accounts taken out
 * @param closed       whether the chat was closed because its group or tag is gone
 */
public record MembershipChange(Conversation conversation, Set<Integer> added, Set<Integer> removed, boolean closed) {

    /** Whether anything changed at all. */
    public boolean any() {
        return closed || !added.isEmpty() || !removed.isEmpty();
    }
}
