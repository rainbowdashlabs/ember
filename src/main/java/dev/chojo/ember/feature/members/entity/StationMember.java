/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Represents a member's association with a station.
 *
 * @param id          the station member identifier
 * @param stationId   the station this member belongs to
 * @param accountId   the linked account identifier, or null for decoupled former members
 * @param former      whether this member has been marked as a former member
 * @param displayName the cached display name, used for former members after account decoupling
 */
public record StationMember(int id, int stationId, Integer accountId, boolean former, String displayName) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<StationMember> map() {
        return row -> new StationMember(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getObject("account_id", Integer.class),
                row.getBoolean("former"),
                row.getString("display_name"));
    }
}
