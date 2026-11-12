/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

/** What a signal on the message stream says has news, so the page knows what to fetch again. */
public enum MessageEventKind {
    /** A message was written, changed, deleted or reacted to: fetch that one message. */
    MESSAGE,
    /** Who takes part in a conversation or what it is called changed: fetch the conversation. */
    CONVERSATION,
    /** The reader's own read state changed, in this tab or another: fetch the unread counts. */
    READ
}
