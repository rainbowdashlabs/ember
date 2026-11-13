/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * A conversation between accounts.
 *
 * @param id             the conversation
 * @param uid            the identity it is addressed by
 * @param kind           direct, group or station chat
 * @param name           the name a group or station chat was given, or null
 * @param ownerAccountId the owner of a group, or null for a direct conversation and once the owner is gone
 * @param createdAt      when it was started
 * @param lastActivityAt when anything was last written in it
 * @param station        what makes it a station chat, or null for a direct conversation or group
 */
public record Conversation(
        int id,
        UUID uid,
        ConversationKind kind,
        @Nullable String name,
        @Nullable Integer ownerAccountId,
        Instant createdAt,
        Instant lastActivityAt,
        @Nullable StationScope station) {

    /** The columns {@link #map()} reads, for a query over the conversation table aliased {@code c}. */
    public static final String COLUMNS =
            "c.id, c.uid, c.kind, c.name, c.owner_account_id, c.created_at, c.last_activity_at, "
                    + StationScope.COLUMNS;

    /**
     * Reads a conversation row.
     *
     * @return the mapping
     */
    public static RowMapping<Conversation> map() {
        return row -> new Conversation(
                row.getInt("id"),
                row.get("uid", UUID_STRING),
                row.getEnum("kind", ConversationKind.class),
                row.getString("name"),
                row.getObject("owner_account_id", Integer.class),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("last_activity_at", INSTANT_TIMESTAMP),
                StationScope.read(row));
    }

    /** Whether this is a group, the only kind that has an owner and that people add each other to. */
    public boolean isGroup() {
        return kind == ConversationKind.GROUP;
    }

    /** Whether this is a direct conversation between exactly two accounts. */
    public boolean isDirect() {
        return kind == ConversationKind.DIRECT;
    }

    /** Whether this is a station chat, whose members follow its audience. */
    public boolean isStation() {
        return kind == ConversationKind.STATION;
    }
}
