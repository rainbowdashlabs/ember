/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CourseCancelled;
import dev.chojo.ember.feature.events.repository.EventCourseRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventCourseRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calls a whole course off.
 *
 * <p>Every part still to come is called off with it, the way a single date is, so lists, the
 * calendar, attendance and reminders know. The people signed up are told once, for the course; the
 * parts tell nobody on their own. Parts that already took place or were called off before are left
 * as they are. Calling a course off is final: it cannot be taken back, and none of its parts comes
 * back on its own.
 */
@Singleton
public class EventCourseCancellationService {
    private static final Logger log = LoggerFactory.getLogger(EventCourseCancellationService.class);

    private final EventCourseRepository courses;
    private final EventCourseRegistrationRepository signUps;
    private final EventRepository events;
    private final EventCancellationService cancellation;
    private final OccurrenceCalendar calendar;
    private final DomainEventBus eventBus;

    @Inject
    public EventCourseCancellationService(
            EventCourseRepository courses,
            EventCourseRegistrationRepository signUps,
            EventRepository events,
            EventCancellationService cancellation,
            OccurrenceCalendar calendar,
            DomainEventBus eventBus) {
        this.courses = courses;
        this.signUps = signUps;
        this.events = events;
        this.cancellation = cancellation;
        this.calendar = calendar;
        this.eventBus = eventBus;
    }

    /**
     * Calls a course off with every part still to come.
     *
     * @param stationId   the station asking
     * @param courseId    the course
     * @param reason      why, as told to the people signed up, or null
     * @param cancelledBy the member calling it off, or null
     */
    public void cancel(int stationId, int courseId, @Nullable String reason, @Nullable Integer cancelledBy) {
        var course = courses.findById(courseId)
                .filter(found -> found.stationId() == stationId)
                .orElseThrow(EventRefusal.COURSE_NOT_HERE::raise);
        boolean calledOff = Transactions.call(() -> {
            courses.lockCourse(courseId);
            return courses.cancel(courseId, reason);
        });
        if (!calledOff) throw EventRefusal.COURSE_CANCELLED.raise();
        try {
            for (var part : courses.findParts(courseId)) {
                callOff(stationId, part.eventId(), reason, cancelledBy);
            }
        } finally {
            eventBus.publish(new CourseCancelled(
                    stationId, courseId, course.name(), reason, signUps.findHoldingMemberIds(courseId)));
        }
    }

    /**
     * Calls one part off unless it is off already or past. A part that cannot be called off is left
     * as it is and the others still follow, so one part never keeps the course or its notice back.
     */
    private void callOff(int stationId, int eventId, @Nullable String reason, @Nullable Integer cancelledBy) {
        var stationCalendar = calendar.forStation(stationId);
        try {
            events.findById(eventId)
                    .filter(event -> !stationCalendar.cancelledAltogether(event))
                    .ifPresent(event -> stationCalendar
                            .ruleOf(event)
                            .filter(rule -> !rule.first().isBefore(stationCalendar.today()))
                            .ifPresent(rule -> cancellation.cancelDate(event, rule.first(), reason, cancelledBy)));
        } catch (RuntimeException e) {
            log.warn("Could not call off part {} with its course", eventId, e);
        }
    }
}
