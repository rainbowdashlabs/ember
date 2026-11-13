/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

import de.chojo.sadu.mapper.wrapper.Row;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * What makes a conversation a station chat: the station it belongs to, whom it is for, and whether
 * it is closed.
 *
 * @param stationId         the station
 * @param audience          whom the chat is for
 * @param groupId           the group of a group chat, or null for another audience and once the group is gone
 * @param tagId             the tag of a tag chat, or null for another audience and once the tag is gone
 * @param includeGuardians  whether the guardians of the people in a group or tag chat are members too
 * @param closedAt          when the chat was closed, or null while it is open
 * @param closedByAccountId who closed it, or null while open, when nobody did and once that account is gone
 */
public record StationScope(
        int stationId,
        ChatAudience audience,
        @Nullable Integer groupId,
        @Nullable Integer tagId,
        boolean includeGuardians,
        @Nullable Instant closedAt,
        @Nullable Integer closedByAccountId) {

    /** The columns {@link #read} expects, for a conversation table aliased {@code c}. */
    public static final String COLUMNS =
            "c.station_id, c.audience, c.audience_group_id, c.audience_tag_id, c.include_guardians, c.closed_at, c.closed_by_account_id";

    /**
     * The station chat part of a conversation row.
     *
     * @param row the row
     * @return the scope, or null for a conversation that is no station chat
     * @throws SQLException when the row cannot be read
     */
    public static @Nullable StationScope read(Row row) throws SQLException {
        Integer stationId = row.getObject("station_id", Integer.class);
        if (stationId == null) return null;
        return new StationScope(
                stationId,
                row.getEnum("audience", ChatAudience.class),
                row.getObject("audience_group_id", Integer.class),
                row.getObject("audience_tag_id", Integer.class),
                row.getBoolean("include_guardians"),
                row.get("closed_at", INSTANT_TIMESTAMP),
                row.getObject("closed_by_account_id", Integer.class));
    }

    /** Whether the chat is closed: readable, but taking nothing new. */
    public boolean closed() {
        return closedAt != null;
    }

    /**
     * Whether the group or tag the chat is for is gone, which leaves it without an audience. A
     * station-wide chat always has its station.
     */
    public boolean orphaned() {
        return switch (audience) {
            case STATION -> false;
            case GROUP -> groupId == null;
            case TAG -> tagId == null;
        };
    }
}
