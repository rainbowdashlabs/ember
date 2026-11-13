/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A named group of an event group's parts with how many of them a sign-up has to choose.
 *
 * @param id       the set
 * @param eventGroupId the event group
 * @param name     the name shown to members
 * @param minimum  how many of its parts a sign-up has to choose
 * @param position its place among the event group's sets
 */
public record EventGroupSet(int id, int eventGroupId, String name, int minimum, int position) {

    /** The columns {@link #map()} reads. */
    public static final String COLUMNS = "id, group_id, name, minimum, position";

    /** Reads a set from a row holding {@link #COLUMNS}. */
    public static RowMapping<EventGroupSet> map() {
        return row -> new EventGroupSet(
                row.getInt("id"),
                row.getInt("group_id"),
                row.getString("name"),
                row.getInt("minimum"),
                row.getInt("position"));
    }
}
