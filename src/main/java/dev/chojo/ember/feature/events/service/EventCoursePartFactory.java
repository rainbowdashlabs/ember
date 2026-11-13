/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventCoursePart;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Creates a new appointment straight into a course.
 *
 * <p>An appointment created on its own tells the people it is meant for that it exists. A part
 * created from the course does not: the course is what people sign up for, and a course with four
 * days would otherwise announce itself four times, each time as a loose appointment.
 */
@Singleton
public class EventCoursePartFactory {
    private final EventCrudService crud;
    private final EventCourseService courses;

    @Inject
    public EventCoursePartFactory(EventCrudService crud, EventCourseService courses) {
        this.crud = crud;
        this.courses = courses;
    }

    /**
     * Creates a one-time appointment and makes it a part of a course in one go.
     *
     * @param stationId   the station
     * @param courseId    the course
     * @param setId       the set it goes into
     * @param name        the appointment's name
     * @param description what it is about, as Markdown, or null
     * @param startTime   when it begins
     * @param endTime     when it ends
     * @return the part
     */
    public EventCoursePart create(
            int stationId,
            int courseId,
            int setId,
            String name,
            @Nullable String description,
            Instant startTime,
            Instant endTime) {
        courses.requireCourse(stationId, courseId);
        return Transactions.call(() -> {
            var event = crud.createWithoutEvent(
                    stationId,
                    name,
                    description,
                    StationEvent.EventType.ONE_TIME,
                    null,
                    startTime,
                    endTime,
                    null,
                    true,
                    null,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null);
            return courses.addPart(stationId, courseId, event.id(), setId);
        });
    }
}
