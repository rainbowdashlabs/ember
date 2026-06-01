/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;

import java.util.UUID;

public record BoardTicketWatcher(int ticketId, MemberIdentity watcher) {

    public static RowMapping<BoardTicketWatcher> map() {
        return row -> {
            UUID watcherStationUid = row.get("watcher_station_uid", StandardValueConverter.UUID_STRING);
            UUID watcherMemberUid = row.get("watcher_member_uid", StandardValueConverter.UUID_STRING);
            MemberIdentity watcher = new MemberIdentity(watcherStationUid, watcherMemberUid);
            return new BoardTicketWatcher(row.getInt("ticket_id"), watcher);
        };
    }
}
