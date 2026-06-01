/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record BoardComment(
        int id,
        int ticketId,
        Integer parentId,
        MemberIdentity author,
        String content,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt) {

    public static RowMapping<BoardComment> map() {
        return row -> {
            UUID authorStationUid = row.get("author_station_uid", StandardValueConverter.UUID_STRING);
            UUID authorMemberUid = row.get("author_member_uid", StandardValueConverter.UUID_STRING);
            MemberIdentity author = (authorStationUid != null && authorMemberUid != null)
                    ? new MemberIdentity(authorStationUid, authorMemberUid)
                    : null;

            return new BoardComment(
                    row.getInt("id"),
                    row.getInt("ticket_id"),
                    row.getObject("parent_id", Integer.class),
                    author,
                    row.getString("content"),
                    row.getBoolean("deleted"),
                    row.get("created_at", INSTANT_TIMESTAMP),
                    row.get("updated_at", INSTANT_TIMESTAMP));
        };
    }
}
