/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

/**
 * A conversation together with the reader's active membership of it, which is what every action
 * inside a conversation starts from.
 *
 * @param conversation the conversation
 * @param membership   the reader's membership
 */
public record Participation(Conversation conversation, Membership membership) {

    /** The reader's account. */
    public int accountId() {
        return membership.accountId();
    }
}
