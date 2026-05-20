/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A named group of station members, optionally associated with roles.
 *
 * @param id        the group identifier
 * @param stationId the station this group belongs to
 * @param name      the group display name
 */
public record MemberGroup(int id, int stationId, String name) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<MemberGroup> map() {
        return row -> new MemberGroup(row.getInt("id"), row.getInt("station_id"), row.getString("name"));
    }
}
