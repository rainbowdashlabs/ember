/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A named group of station members, optionally with a color and associated roles.
 *
 * @param id        the group identifier
 * @param stationId the station this group belongs to
 * @param name      the group display name
 * @param color     optional hex color (e.g. "#FF6421"). Null means no color.
 * @param position  sort priority. Higher = higher priority for name color resolution.
 */
public record MemberGroup(int id, int stationId, String name, String color, int position) {
    public static RowMapping<MemberGroup> map() {
        return row -> new MemberGroup(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("color"),
                row.getInt("position"));
    }
}
