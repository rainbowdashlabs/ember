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
 * One account taking part in a conversation.
 *
 * @param conversationId    the conversation
 * @param person            the account taking part
 * @param joinedAt          when it started or was added, the last time if it came back
 * @param leftAt            when it left or was removed, or null while it takes part
 * @param lastReadMessageId the last message it has read, or null before anything was read
 * @param muted             whether it muted the conversation
 */
public record Membership(
        int conversationId,
        Person person,
        Instant joinedAt,
        @Nullable Instant leftAt,
        @Nullable Integer lastReadMessageId,
        boolean muted) {

    /** The columns {@link #map()} reads, for a member table aliased {@code m} joined to an account {@code a}. */
    public static final String COLUMNS =
            "m.conversation_id, m.joined_at, m.left_at, m.last_read_message_id, m.muted, %s"
                    .formatted(Person.columns("a", "member_"));

    /**
     * Reads a membership row.
     *
     * @return the mapping
     */
    public static RowMapping<Membership> map() {
        return row -> new Membership(
                row.getInt("conversation_id"),
                Person.require(row, "member_"),
                row.get("joined_at", INSTANT_TIMESTAMP),
                row.get("left_at", INSTANT_TIMESTAMP),
                row.getObject("last_read_message_id", Integer.class),
                row.getBoolean("muted"));
    }

    /** Whether the account takes part right now, rather than having left. */
    public boolean active() {
        return leftAt == null;
    }

    /** The account taking part. */
    public int accountId() {
        return person.accountId();
    }
}
