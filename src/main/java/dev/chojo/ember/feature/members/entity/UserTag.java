/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

/**
 * A user-defined tag that can be assigned to station members.
 *
 * @param id         the tag identifier
 * @param stationId  the station this tag belongs to
 * @param name       the tag display name
 * @param color      optional hex color for the badge (e.g. "#3694FF"). Null means no color.
 * @param visibility who sees the tag, and whether it shows as a badge behind member names
 * @param position   sort priority. Higher = higher priority for badge display.
 */
public record UserTag(
        int id, int stationId, String name, @Nullable String color, TagVisibility visibility, int position) {
    public static RowMapping<UserTag> map() {
        return row -> new UserTag(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("color"),
                TagVisibility.valueOf(row.getString("visibility")),
                row.getInt("position"));
    }
}
