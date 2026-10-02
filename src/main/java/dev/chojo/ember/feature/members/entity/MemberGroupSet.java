/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A set of groups a member can be in only one of, such as the levels of a training.
 *
 * @param id        the set identifier
 * @param stationId the station the set belongs to
 * @param name      the set's name, unique within the station
 */
public record MemberGroupSet(int id, int stationId, String name) {
    /** The columns {@link #map()} reads. */
    public static final String COLUMNS = "id, station_id, name";

    public static RowMapping<MemberGroupSet> map() {
        return row -> new MemberGroupSet(row.getInt("id"), row.getInt("station_id"), row.getString("name"));
    }
}
