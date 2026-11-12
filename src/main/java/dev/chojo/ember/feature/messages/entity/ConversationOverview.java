/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

/**
 * A conversation as one of the accounts in it sees it in the list.
 *
 * @param conversation      the conversation
 * @param muted             whether the reader muted it
 * @param lastReadMessageId the last message the reader has read, or null
 * @param unread            how many messages of others the reader has not read yet, blocked people left out
 */
public record ConversationOverview(
        Conversation conversation, boolean muted, @Nullable Integer lastReadMessageId, int unread) {

    /**
     * Reads a row carrying {@link Conversation#COLUMNS} and the reader's member columns.
     *
     * @return the mapping
     */
    public static RowMapping<ConversationOverview> map() {
        return row -> new ConversationOverview(
                Conversation.map().map(row),
                row.getBoolean("muted"),
                row.getObject("last_read_message_id", Integer.class),
                row.getInt("unread"));
    }
}
