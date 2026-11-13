/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A message somebody reported to the people handling a station's chats.
 *
 * @param id           the report
 * @param messageId    the message reported
 * @param conversation the chat the message is in
 * @param reporter     who reported it, or null once that account is gone
 * @param reason       what they wrote about why, or null
 * @param content      the message's text as it read when it was reported
 * @param createdAt    when it was reported
 */
public record MessageReport(
        int id,
        int messageId,
        Conversation conversation,
        @Nullable Person reporter,
        @Nullable String reason,
        String content,
        Instant createdAt) {

    /**
     * Reads a row carrying the report columns, {@link Conversation#COLUMNS} and the reporter under
     * {@code reporter_}.
     *
     * @return the mapping
     */
    public static RowMapping<MessageReport> map() {
        return row -> new MessageReport(
                row.getInt("report_id"),
                row.getInt("report_message_id"),
                Conversation.map().map(row),
                Person.read(row, "reporter_"),
                row.getString("reason"),
                row.getString("reported_content"),
                row.get("reported_at", INSTANT_TIMESTAMP));
    }
}
