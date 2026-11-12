/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * An appointment that is a part of a course.
 *
 * @param eventId  the appointment
 * @param setId    the set it belongs to
 * @param courseId the course, through the set
 */
public record EventCoursePart(int eventId, int setId, int courseId) {

    /** Reads a part from a row holding {@code event_id}, {@code set_id} and {@code course_id}. */
    public static RowMapping<EventCoursePart> map() {
        return row -> new EventCoursePart(row.getInt("event_id"), row.getInt("set_id"), row.getInt("course_id"));
    }
}
