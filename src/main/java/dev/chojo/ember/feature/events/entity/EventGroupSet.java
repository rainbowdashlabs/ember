/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

/**
 * A named group of an event group's parts with how many of them a sign-up has to choose.
 *
 * @param id       the set
 * @param eventGroupId the event group
 * @param name     the name shown to members
 * @param minimum  how many of its parts a sign-up has to choose
 * @param position its place among the event group's sets
 * @param maximum  how many of its parts a sign-up may choose at most, or null for no maximum
 */
public record EventGroupSet(
        int id,
        int eventGroupId,
        String name,
        int minimum,
        int position,
        @Nullable Integer maximum) {
    /**
     * A set without a maximum.
     *
     * @param id           the set
     * @param eventGroupId its event group
     * @param name         its name
     * @param minimum      how many of its parts a sign-up has to choose
     * @param position     its place among the sets
     */
    public EventGroupSet(int id, int eventGroupId, String name, int minimum, int position) {
        this(id, eventGroupId, name, minimum, position, null);
    }

    /** The columns {@link #map()} reads. */
    public static final String COLUMNS = "id, event_group_id, name, minimum, position, maximum";

    /** Reads a set from a row holding {@link #COLUMNS}. */
    public static RowMapping<EventGroupSet> map() {
        return row -> new EventGroupSet(
                row.getInt("id"),
                row.getInt("event_group_id"),
                row.getString("name"),
                row.getInt("minimum"),
                row.getInt("position"),
                row.getObject("maximum", Integer.class));
    }
}
