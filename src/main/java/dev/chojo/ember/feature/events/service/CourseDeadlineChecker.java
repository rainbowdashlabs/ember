/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CourseDeadlineExpired;
import dev.chojo.ember.feature.events.repository.EventCourseRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Watches the deadlines of courses, once per course rather than once per part.
 *
 * <p>A few days and one day before sign-ups close, members the course is meant for who never answered
 * it are reminded. Once they closed with sign-ups still waiting for a decision, whoever runs events
 * is told. The parts' own deadlines are left out of the appointment checks, so nobody hears of a
 * course once for every part.
 */
@Singleton
public class CourseDeadlineChecker implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(CourseDeadlineChecker.class);
    /** The lead times warned about, nearest first: a deadline inside several gets the nearest one only. */
    private static final int[] CLOSING_WARNINGS = {1, 3};

    private final EventCourseRepository courses;
    private final DomainEventBus eventBus;
    private final Notifier notifier;
    private final MemberNameResolver names;
    private final RestrictionService restrictions;
    private final StationReadOnlyGuard readOnlyGuard;

    @Inject
    public CourseDeadlineChecker(
            EventCourseRepository courses,
            DomainEventBus eventBus,
            Notifier notifier,
            MemberNameResolver names,
            RestrictionService restrictions,
            StationReadOnlyGuard readOnlyGuard) {
        this.courses = courses;
        this.eventBus = eventBus;
        this.notifier = notifier;
        this.names = names;
        this.restrictions = restrictions;
        this.readOnlyGuard = readOnlyGuard;
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "course-deadline-check",
                Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofMinutes(5)),
                this::check));
    }

    /** Runs one sweep: warnings for deadlines coming, notices for deadlines passed. */
    void check() {
        try {
            warnAboutClosingCourses();
            tellAboutExpiredCourses();
        } catch (RuntimeException e) {
            log.error("Error checking course deadlines", e);
        }
    }

    private void warnAboutClosingCourses() {
        for (int daysBefore : CLOSING_WARNINGS) {
            for (var course : courses.findClosingIn(daysBefore)) {
                if (!readOnlyGuard.isWritable(course.stationId())) continue;
                for (int memberId : courses.findUnansweredMemberIds(course.courseId(), course.stationId())) {
                    if (!meantFor(course.courseId(), memberId)) continue;
                    notifier.notify(
                            StationAudience.household(List.of(memberId)),
                            NotificationType.REGISTRATION_CLOSING,
                            NotificationData.of(
                                    new NotificationParams.RegistrationClosing(
                                            course.name(), daysBefore, names.called(memberId)),
                                    NotificationLinks.course(course.courseId())),
                            Delivery.EVERY_TIME);
                }
                for (int wider : CLOSING_WARNINGS) {
                    if (wider >= daysBefore) courses.markClosingWarned(course.courseId(), wider);
                }
            }
        }
    }

    private void tellAboutExpiredCourses() {
        for (var course : courses.findExpiredDeadlines()) {
            if (!readOnlyGuard.isWritable(course.stationId())) continue;
            courses.markDeadlineNotified(course.courseId());
            eventBus.publish(new CourseDeadlineExpired(
                    course.stationId(), course.courseId(), course.name(), course.pendingCount()));
        }
    }

    private boolean meantFor(int courseId, int memberId) {
        var member = restrictions.memberOf(memberId).orElse(null);
        return member != null
                && restrictions.includes(RestrictionType.EVENT_COURSE_VIEW, courseId, member)
                && restrictions.includes(RestrictionType.EVENT_COURSE, courseId, member);
    }
}
