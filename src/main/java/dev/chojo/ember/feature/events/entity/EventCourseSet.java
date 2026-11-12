/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A named group of a course's parts with how many of them a sign-up has to choose.
 *
 * @param id       the set
 * @param courseId the course
 * @param name     the name shown to members
 * @param minimum  how many of its parts a sign-up has to choose
 * @param position its place among the course's sets
 */
public record EventCourseSet(int id, int courseId, String name, int minimum, int position) {

    /** The columns {@link #map()} reads. */
    public static final String COLUMNS = "id, course_id, name, minimum, position";

    /** Reads a set from a row holding {@link #COLUMNS}. */
    public static RowMapping<EventCourseSet> map() {
        return row -> new EventCourseSet(
                row.getInt("id"),
                row.getInt("course_id"),
                row.getString("name"),
                row.getInt("minimum"),
                row.getInt("position"));
    }
}
