/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A user-defined tag that can be assigned to station members for categorization.
 *
 * @param id        the tag identifier
 * @param stationId the station this tag belongs to
 * @param name      the tag display name
 */
public record UserTag(int id, int stationId, String name) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<UserTag> map() {
        return row -> new UserTag(row.getInt("id"), row.getInt("station_id"), row.getString("name"));
    }
}
