/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

/**
 * A conversation whose unread part has waited long enough for its reader to be mailed about it.
 *
 * @param accountId         the reader
 * @param conversation      the conversation
 * @param lastReadMessageId the last message the reader has read, or null
 */
public record UnreadMailDue(
        int accountId, Conversation conversation, @Nullable Integer lastReadMessageId) {

    /**
     * Reads a row carrying {@link Conversation#COLUMNS}, the reader's account id and read marker.
     *
     * @return the mapping
     */
    public static RowMapping<UnreadMailDue> map() {
        return row -> new UnreadMailDue(
                row.getInt("reader_account_id"),
                Conversation.map().map(row),
                row.getObject("last_read_message_id", Integer.class));
    }
}
