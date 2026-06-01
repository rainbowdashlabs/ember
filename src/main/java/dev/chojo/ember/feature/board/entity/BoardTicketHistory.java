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

public record BoardTicketHistory(
        int id, int ticketId, String action, String detail, MemberIdentity actor, Instant createdAt) {

    public static RowMapping<BoardTicketHistory> map() {
        return row -> {
            UUID actorStationUid = row.get("actor_station_uid", StandardValueConverter.UUID_STRING);
            UUID actorMemberUid = row.get("actor_member_uid", StandardValueConverter.UUID_STRING);
            MemberIdentity actor = (actorStationUid != null && actorMemberUid != null)
                    ? new MemberIdentity(actorStationUid, actorMemberUid)
                    : null;

            return new BoardTicketHistory(
                    row.getInt("id"),
                    row.getInt("ticket_id"),
                    row.getString("action"),
                    row.getString("detail"),
                    actor,
                    row.get("created_at", INSTANT_TIMESTAMP));
        };
    }
}
