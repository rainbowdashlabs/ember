/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventCourse;
import dev.chojo.ember.feature.events.entity.EventCourseDetails;
import dev.chojo.ember.feature.events.entity.EventCoursePart;
import dev.chojo.ember.feature.events.entity.EventCourseSet;
import dev.chojo.ember.feature.events.entity.EventCourseSetDraft;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventCourseRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates and shapes courses: their details, sets, parts and who may see and join them.
 *
 * <p>A course hands its sign-up and its audiences down to every part. The parts take sign-ups,
 * confirm and close as the course does and carry no limit or minimum of their own, and they are seen
 * and joined by exactly the people the course names. That keeps every place that reads a single
 * appointment (lists, the calendar, reminders, attendance) right without knowing about courses.
 *
 * <p>Only a one-time appointment of the same station can become a part, and only while it is not
 * called off, nobody is signed up for it on its own, it is not shared with partners on its own, and
 * it is meant either for everybody or for the same people as the course. Handing the course's
 * audience down therefore never shows an appointment to anybody it was kept from.
 *
 * <p>Every change runs in one transaction that locks the course first, and adding a part also locks
 * the appointment, so two managers working on the same course or appointment take turns. Deleting a
 * course makes its parts appointments of their own again; who was signed up for one stays signed up.
 */
@Singleton
public class EventCourseService {
    private final EventCourseRepository courses;
    private final EventRepository events;
    private final RestrictionService restrictions;

    @Inject
    public EventCourseService(EventCourseRepository courses, EventRepository events, RestrictionService restrictions) {
        this.courses = courses;
        this.events = events;
        this.restrictions = restrictions;
    }

    /**
     * Creates a course with its sets and audiences.
     *
     * @param stationId    the station
     * @param details      what the course holds
     * @param sets         its sets in order, at least one
     * @param registration who may sign up
     * @param view         who may see it
     * @return the course
     */
    public EventCourse create(
            int stationId,
            EventCourseDetails details,
            List<EventCourseSetDraft> sets,
            RestrictionAudience registration,
            RestrictionAudience view) {
        var checked = checked(details);
        requireValidSets(sets);
        if (sets.stream().anyMatch(set -> set.id() != null)) throw EventRefusal.COURSE_SET_NOT_IN_COURSE.raise();
        requireOfStation(stationId, registration, view);
        return Transactions.call(() -> {
            var course = courses.create(stationId, checked);
            for (int position = 0; position < sets.size(); position++) {
                var set = sets.get(position);
                courses.createSet(course.id(), set.name().strip(), set.minimum(), position);
            }
            restrictions.setAudience(RestrictionType.EVENT_COURSE, course.id(), registration);
            restrictions.setAudience(RestrictionType.EVENT_COURSE_VIEW, course.id(), view);
            return courses.findById(course.id()).orElseThrow();
        });
    }

    /**
     * Changes what a course holds and hands the sign-up down to its parts.
     *
     * @param stationId the station asking
     * @param courseId  the course
     * @param details   what it holds now
     * @return the course as it is now
     */
    public EventCourse update(int stationId, int courseId, EventCourseDetails details) {
        var checked = checked(details);
        return Transactions.call(() -> {
            lockCourse(stationId, courseId);
            courses.update(courseId, checked);
            courses.alignParts(courseId, checked.requiresConfirmation(), checked.registrationDeadline());
            return courses.findById(courseId).orElseThrow();
        });
    }

    /**
     * Replaces the sets of a course: drafts with an id change that set, drafts without one add a
     * set, and sets left out are removed, which a set still holding parts refuses.
     *
     * @param stationId the station asking
     * @param courseId  the course
     * @param drafts    the sets in their new order, at least one
     * @return the sets as they are now
     */
    public List<EventCourseSet> replaceSets(int stationId, int courseId, List<EventCourseSetDraft> drafts) {
        requireValidSets(drafts);
        return Transactions.call(() -> {
            lockCourse(stationId, courseId);
            var existing =
                    courses.findSets(courseId).stream().map(EventCourseSet::id).collect(Collectors.toSet());
            var kept = new HashSet<Integer>();
            for (var draft : drafts) {
                if (draft.id() == null) continue;
                if (!existing.contains(draft.id()) || !kept.add(draft.id())) {
                    throw EventRefusal.COURSE_SET_NOT_IN_COURSE.raise();
                }
            }
            var removed = existing.stream().filter(id -> !kept.contains(id)).toList();
            if (removed.stream().anyMatch(id -> courses.countPartsInSet(id) > 0)) {
                throw EventRefusal.COURSE_SET_HOLDS_PARTS.raise();
            }
            removed.forEach(courses::deleteSet);
            for (int position = 0; position < drafts.size(); position++) {
                var draft = drafts.get(position);
                if (draft.id() == null) {
                    courses.createSet(courseId, draft.name().strip(), draft.minimum(), position);
                } else {
                    courses.updateSet(draft.id(), draft.name().strip(), draft.minimum(), position);
                }
            }
            return courses.findSets(courseId);
        });
    }

    /**
     * Makes an appointment a part of a course, in one of its sets. The part takes the course's
     * sign-up and audiences from then on.
     *
     * @param stationId the station asking
     * @param courseId  the course
     * @param eventId   the appointment
     * @param setId     the set it goes into
     * @return the part
     */
    public EventCoursePart addPart(int stationId, int courseId, int eventId, int setId) {
        return Transactions.call(() -> {
            var course = lockCourse(stationId, courseId);
            courses.lockEvent(eventId);
            var event = requireEvent(stationId, eventId);
            requireSetOf(courseId, setId);
            if (event.eventType() != StationEvent.EventType.ONE_TIME) {
                throw EventRefusal.COURSE_PART_NOT_ONE_TIME.raise();
            }
            if (event.cancelled()) throw EventRefusal.COURSE_PART_CANCELLED.raise();
            if (courses.findPart(eventId).isPresent()) throw EventRefusal.COURSE_PART_TAKEN.raise();
            if (courses.hasOpenSignUps(eventId)) throw EventRefusal.COURSE_PART_HAS_SIGN_UPS.raise();
            if (courses.isSharedWithPartners(eventId)) throw EventRefusal.COURSE_PART_SHARED.raise();
            requireCompatibleAudiences(courseId, eventId);
            if (!courses.claimPart(eventId, setId)) throw EventRefusal.COURSE_PART_TAKEN.raise();
            courses.alignParts(courseId, course.requiresConfirmation(), course.registrationDeadline());
            handDownAudiences(courseId, List.of(eventId));
            return courses.findPart(eventId).orElseThrow();
        });
    }

    /**
     * Moves a part into another set of its course.
     *
     * @param stationId the station asking
     * @param courseId  the course
     * @param eventId   the part
     * @param setId     the set it moves into
     * @return the part
     */
    public EventCoursePart movePart(int stationId, int courseId, int eventId, int setId) {
        return Transactions.call(() -> {
            lockCourse(stationId, courseId);
            requirePartOf(courseId, eventId);
            requireSetOf(courseId, setId);
            courses.setPartSet(eventId, setId);
            return courses.findPart(eventId).orElseThrow();
        });
    }

    /**
     * Makes a part an appointment of its own again. Who was signed up for it stays signed up, and
     * it keeps the sign-up settings and audiences it had as a part until somebody changes them.
     *
     * @param stationId the station asking
     * @param courseId  the course
     * @param eventId   the part
     */
    public void removePart(int stationId, int courseId, int eventId) {
        Transactions.run(() -> {
            lockCourse(stationId, courseId);
            requirePartOf(courseId, eventId);
            courses.setPartSet(eventId, null);
        });
    }

    /**
     * Sets who may see and who may sign up for a course, and hands both down to every part.
     *
     * @param stationId    the station asking
     * @param courseId     the course
     * @param registration who may sign up
     * @param view         who may see it
     */
    public void setAudiences(int stationId, int courseId, RestrictionAudience registration, RestrictionAudience view) {
        requireOfStation(stationId, registration, view);
        Transactions.run(() -> {
            lockCourse(stationId, courseId);
            restrictions.setAudience(RestrictionType.EVENT_COURSE, courseId, registration);
            restrictions.setAudience(RestrictionType.EVENT_COURSE_VIEW, courseId, view);
            handDownAudiences(
                    courseId,
                    courses.findParts(courseId).stream()
                            .map(EventCoursePart::eventId)
                            .toList());
        });
    }

    /**
     * Who may sign up for a course.
     *
     * @param courseId the course
     * @return the audience, one naming nobody where everybody who sees it may
     */
    public RestrictionAudience registrationAudience(int courseId) {
        return restrictions.audienceOf(RestrictionType.EVENT_COURSE, courseId);
    }

    /**
     * Who may see a course.
     *
     * @param courseId the course
     * @return the audience, one naming nobody where everybody may
     */
    public RestrictionAudience viewAudience(int courseId) {
        return restrictions.audienceOf(RestrictionType.EVENT_COURSE_VIEW, courseId);
    }

    /**
     * Deletes a course with its sets and sign-ups. Its parts become appointments of their own again,
     * and who was signed up for one of them stays signed up for it.
     *
     * @param stationId the station asking
     * @param courseId  the course
     */
    public void delete(int stationId, int courseId) {
        Transactions.run(() -> {
            lockCourse(stationId, courseId);
            courses.releaseParts(courseId);
            courses.delete(courseId);
        });
    }

    /**
     * A course of the station.
     *
     * @param stationId the station asking
     * @param courseId  the course
     * @return the course
     */
    public EventCourse requireCourse(int stationId, int courseId) {
        return courses.findById(courseId)
                .filter(course -> course.stationId() == stationId)
                .orElseThrow(EventRefusal.COURSE_NOT_HERE::raise);
    }

    /**
     * The courses of a station.
     *
     * @param stationId the station
     * @return its courses, by name
     */
    public List<EventCourse> findByStation(int stationId) {
        return courses.findByStation(stationId);
    }

    /**
     * The sets of a course in order.
     *
     * @param courseId the course
     * @return its sets
     */
    public List<EventCourseSet> findSets(int courseId) {
        return courses.findSets(courseId);
    }

    /**
     * The parts of a course in date order.
     *
     * @param courseId the course
     * @return its parts
     */
    public List<EventCoursePart> findParts(int courseId) {
        return courses.findParts(courseId);
    }

    /** Locks a course of the station for the running transaction and reads it. */
    private EventCourse lockCourse(int stationId, int courseId) {
        courses.lockCourse(courseId);
        return requireCourse(stationId, courseId);
    }

    private void handDownAudiences(int courseId, List<Integer> eventIds) {
        var registration = registrationAudience(courseId);
        var view = viewAudience(courseId);
        for (int eventId : eventIds) {
            restrictions.setAudience(RestrictionType.EVENT, eventId, registration);
            restrictions.setAudience(RestrictionType.EVENT_VIEW, eventId, view);
        }
    }

    /**
     * Refuses an appointment whose own audiences would be replaced by different ones: an appointment
     * meant for everybody takes the course's audience, one meant for somebody only the same.
     */
    private void requireCompatibleAudiences(int courseId, int eventId) {
        var ownRegistration = restrictions.audienceOf(RestrictionType.EVENT, eventId);
        var ownView = restrictions.audienceOf(RestrictionType.EVENT_VIEW, eventId);
        if (!fits(ownRegistration, registrationAudience(courseId)) || !fits(ownView, viewAudience(courseId))) {
            throw EventRefusal.COURSE_PART_AUDIENCE_DIFFERS.raise();
        }
    }

    private static boolean fits(RestrictionAudience own, RestrictionAudience course) {
        return own.namesNobody() || sameAudience(own, course);
    }

    private static boolean sameAudience(RestrictionAudience a, RestrictionAudience b) {
        return Set.copyOf(a.userTypes()).equals(Set.copyOf(b.userTypes()))
                && Set.copyOf(a.groupIds()).equals(Set.copyOf(b.groupIds()))
                && Set.copyOf(a.tagIds()).equals(Set.copyOf(b.tagIds()))
                && Set.copyOf(a.memberIds()).equals(Set.copyOf(b.memberIds()))
                && (combinedEntries(a) < 2 || a.mode() == b.mode());
    }

    /** How many entries an audience combines by its mode; named members always count on their own. */
    private static int combinedEntries(RestrictionAudience audience) {
        return audience.userTypes().size()
                + audience.groupIds().size()
                + audience.tagIds().size();
    }

    private StationEvent requireEvent(int stationId, int eventId) {
        return events.findById(eventId)
                .filter(event -> event.stationId() == stationId)
                .orElseThrow(EventRefusal.COURSE_PART_NOT_HERE::raise);
    }

    private void requireSetOf(int courseId, int setId) {
        if (courses.findSets(courseId).stream().noneMatch(set -> set.id() == setId)) {
            throw EventRefusal.COURSE_SET_NOT_IN_COURSE.raise();
        }
    }

    private void requirePartOf(int courseId, int eventId) {
        courses.findPart(eventId)
                .filter(part -> part.courseId() == courseId)
                .orElseThrow(EventRefusal.COURSE_PART_NOT_IN_COURSE::raise);
    }

    private void requireOfStation(int stationId, RestrictionAudience registration, RestrictionAudience view) {
        if (!restrictions.ofStation(stationId, registration) || !restrictions.ofStation(stationId, view)) {
            throw EventRefusal.COURSE_AUDIENCE_NOT_OF_STATION.raise();
        }
    }

    private static EventCourseDetails checked(EventCourseDetails details) {
        if (details.name() == null || details.name().isBlank()) throw EventRefusal.COURSE_NAME_MISSING.raise();
        if (details.registrationLimit() != null && details.registrationLimit() < 1) {
            throw EventRefusal.COURSE_LIMIT_NOT_POSITIVE.raise();
        }
        return new EventCourseDetails(
                details.name().strip(),
                details.description(),
                details.registrationLimit(),
                details.registrationDeadline(),
                details.requiresConfirmation());
    }

    private static void requireValidSets(List<EventCourseSetDraft> sets) {
        if (sets.isEmpty()) throw EventRefusal.COURSE_NEEDS_A_SET.raise();
        for (var set : sets) {
            if (set.name() == null || set.name().isBlank()) throw EventRefusal.COURSE_NAME_MISSING.raise();
            if (set.minimum() < 0) throw EventRefusal.COURSE_SET_MINIMUM_NEGATIVE.raise();
        }
    }
}
