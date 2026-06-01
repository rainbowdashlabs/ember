/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A user-defined tag that can be assigned to station members.
 * Tags can optionally be visible as badges behind member names.
 *
 * @param id        the tag identifier
 * @param stationId the station this tag belongs to
 * @param name      the tag display name
 * @param color     optional hex color for the badge (e.g. "#3694FF"). Null means no color.
 * @param visible   whether this tag shows as a badge behind member names
 * @param position  sort priority. Higher = higher priority for badge display.
 */
public record UserTag(int id, int stationId, String name, String color, boolean visible, int position) {
    public static RowMapping<UserTag> map() {
        return row -> new UserTag(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("color"),
                row.getBoolean("visible"),
                row.getInt("position"));
    }
}
