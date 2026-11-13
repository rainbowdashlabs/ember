/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * A signal on the message stream: which conversation has news and what kind. It carries no text of
 * any message, so the stream gives away nothing the normal routes would not.
 *
 * @param kind             what changed
 * @param conversationUid  the conversation it changed in
 * @param messageId        the message that changed, for {@link MessageEventKind#MESSAGE}; null otherwise
 */
public record MessageEvent(
        MessageEventKind kind,
        UUID conversationUid,
        @Nullable Integer messageId) {

    /**
     * A message was written, changed, deleted or reacted to.
     *
     * @param conversationUid the conversation
     * @param messageId       the message
     * @return the signal
     */
    public static MessageEvent message(UUID conversationUid, int messageId) {
        return new MessageEvent(MessageEventKind.MESSAGE, conversationUid, messageId);
    }

    /**
     * Who takes part in a conversation, or what it is called, changed.
     *
     * @param conversationUid the conversation
     * @return the signal
     */
    public static MessageEvent conversation(UUID conversationUid) {
        return new MessageEvent(MessageEventKind.CONVERSATION, conversationUid, null);
    }

    /**
     * The reader's own read state changed.
     *
     * @param conversationUid the conversation it changed for
     * @return the signal
     */
    public static MessageEvent read(UUID conversationUid) {
        return new MessageEvent(MessageEventKind.READ, conversationUid, null);
    }
}
