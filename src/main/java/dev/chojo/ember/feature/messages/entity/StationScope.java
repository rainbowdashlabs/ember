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
 * What makes a conversation a station chat: the station it belongs to, whether guardians come along,
 * whether its audience is gone, and whether it is closed. Whom it is for lives in its restriction
 * rows ({@code RestrictionType.STATION_CHAT}).
 *
 * @param stationId         the station
 * @param includeGuardians  whether the guardians of everybody its audience takes in are members too
 * @param audienceGone      whether its audience lost part of what it named: a user type, group or
 *                          tag, or every member it named
 * @param closedAt          when the chat was closed, or null while it is open
 * @param closedByAccountId who closed it, or null while open, when nobody did and once that account is gone
 */
public record StationScope(
        int stationId,
        boolean includeGuardians,
        boolean audienceGone,
        @Nullable Instant closedAt,
        @Nullable Integer closedByAccountId) {

    /** The columns {@link #read} expects, for a conversation table aliased {@code c}. */
    public static final String COLUMNS = "c.station_id, c.include_guardians, " + audienceGone("c")
            + " AS audience_gone, c.closed_at, c.closed_by_account_id";

    /**
     * Whether the audience of the station chat aliased as given lost part of what it named: a user
     * type, group or tag entry is gone, or nothing it named is left at the station. Such a chat takes
     * nobody in, since what is left of its audience could take in people it was never for.
     *
     * @param alias the alias of the conversation table
     * @return the predicate
     */
    public static String audienceGone(String alias) {
        return """
                (%1$s.audience_named AND (
                    (SELECT count(*) FROM conversation_restriction kinds
                     WHERE kinds.conversation_id = %1$s.id AND kinds.member_id IS NULL) < %1$s.audience_kinds
                    OR NOT EXISTS (
                        SELECT 1 FROM conversation_restriction left_over
                        LEFT JOIN station_member named ON named.id = left_over.member_id
                        WHERE left_over.conversation_id = %1$s.id
                        AND (left_over.member_id IS NULL OR named.former = FALSE))))""".formatted(alias);
    }

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
                row.getBoolean("include_guardians"),
                row.getBoolean("audience_gone"),
                row.get("closed_at", INSTANT_TIMESTAMP),
                row.getObject("closed_by_account_id", Integer.class));
    }

    /** Whether the chat is closed: readable, but taking nothing new. */
    public boolean closed() {
        return closedAt != null;
    }

    /**
     * Whether the chat's audience lost part of what it named, which leaves it without an audience. An
     * audience that named nobody is the whole station and is never gone.
     */
    public boolean orphaned() {
        return audienceGone;
    }
}
