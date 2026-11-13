/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.EventCourse;
import dev.chojo.ember.feature.events.entity.EventCourseDetails;
import dev.chojo.ember.feature.events.entity.EventCoursePart;
import dev.chojo.ember.feature.events.entity.EventCourseSet;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Courses, their sets and which appointments are their parts.
 *
 * <p>A part is an ordinary appointment that names its set, so whatever reads appointments keeps
 * working; the course only adds the grouping on top.
 */
@Singleton
public class EventCourseRepository {
    private static final String PART_COLUMNS = "e.id AS event_id, s.id AS set_id, s.course_id";

    /**
     * Creates a course.
     *
     * @param stationId the station
     * @param details   what the course holds
     * @return the course
     */
    public EventCourse create(int stationId, EventCourseDetails details) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO event_course(station_id, name, description, registration_limit, registration_deadline, requires_confirmation)
                VALUES (:station_id, :name, :description, :registration_limit, :registration_deadline, :requires_confirmation)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("name", details.name())
                        .bind("description", details.description())
                        .bind("registration_limit", details.registrationLimit())
                        .bind("registration_deadline", details.registrationDeadline(), INSTANT_TIMESTAMP)
                        .bind("requires_confirmation", details.requiresConfirmation()),
                EventCourse.map(),
                EventCourse.COLUMNS);
    }

    /**
     * Replaces what a course holds besides its sets, parts and audiences. A deadline moved re-arms
     * the notice that it passed.
     *
     * @param id      the course
     * @param details what it holds now
     * @return true where the course exists
     */
    public boolean update(int id, EventCourseDetails details) {
        return query("""
                UPDATE event_course
                SET name                  = :name,
                    description           = :description,
                    registration_limit    = :registration_limit,
                    deadline_notified     = deadline_notified AND registration_deadline IS NOT DISTINCT FROM :registration_deadline,
                    registration_deadline = :registration_deadline,
                    requires_confirmation = :requires_confirmation
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", details.name())
                        .bind("description", details.description())
                        .bind("registration_limit", details.registrationLimit())
                        .bind("registration_deadline", details.registrationDeadline(), INSTANT_TIMESTAMP)
                        .bind("requires_confirmation", details.requiresConfirmation()))
                .update()
                .changed();
    }

    /**
     * A course by its id.
     *
     * @param id the course
     * @return the course, or empty where there is none
     */
    public Optional<EventCourse> findById(int id) {
        return SqlSupport.findById("event_course", EventCourse.COLUMNS, id, EventCourse.map());
    }

    /**
     * The courses of a station, by name.
     *
     * @param stationId the station
     * @return its courses
     */
    public List<EventCourse> findByStation(int stationId) {
        return query(
                        "SELECT %s FROM event_course WHERE station_id = :station_id ORDER BY lower(name), id;",
                        EventCourse.COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(EventCourse.map())
                .all();
    }

    /**
     * Deletes a course with its sets, sign-ups and audiences. Its parts have to be let go first.
     *
     * @param id the course
     * @return true where it existed
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("event_course", id);
    }

    /**
     * The sets of a course in their order.
     *
     * @param courseId the course
     * @return its sets
     */
    public List<EventCourseSet> findSets(int courseId) {
        return query(
                        "SELECT %s FROM event_course_set WHERE course_id = :course_id ORDER BY position, id;",
                        EventCourseSet.COLUMNS)
                .single(call().bind("course_id", courseId))
                .map(EventCourseSet.map())
                .all();
    }

    /**
     * Adds a set to a course.
     *
     * @param courseId the course
     * @param name     its name
     * @param minimum  how many of its parts a sign-up has to choose
     * @param position its place among the sets
     * @return the set
     */
    public EventCourseSet createSet(int courseId, String name, int minimum, int position) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO event_course_set(course_id, name, minimum, position)
                VALUES (:course_id, :name, :minimum, :position)
                RETURNING %s;""",
                call().bind("course_id", courseId)
                        .bind("name", name)
                        .bind("minimum", minimum)
                        .bind("position", position),
                EventCourseSet.map(),
                EventCourseSet.COLUMNS);
    }

    /**
     * Changes a set of a course.
     *
     * @param setId    the set
     * @param name     its name
     * @param minimum  how many of its parts a sign-up has to choose
     * @param position its place among the sets
     */
    public void updateSet(int setId, String name, int minimum, int position) {
        query("UPDATE event_course_set SET name = :name, minimum = :minimum, position = :position WHERE id = :id;")
                .single(call().bind("id", setId)
                        .bind("name", name)
                        .bind("minimum", minimum)
                        .bind("position", position))
                .update();
    }

    /**
     * Removes a set that holds no parts.
     *
     * @param setId the set
     */
    public void deleteSet(int setId) {
        SqlSupport.deleteById("event_course_set", setId);
    }

    /**
     * The parts of a course, in date order.
     *
     * @param courseId the course
     * @return its parts
     */
    public List<EventCoursePart> findParts(int courseId) {
        return query("""
                SELECT %s
                FROM station_event e
                         JOIN event_course_set s ON s.id = e.course_set_id
                WHERE s.course_id = :course_id
                ORDER BY e.start_time, e.id;""", PART_COLUMNS)
                .single(call().bind("course_id", courseId))
                .map(EventCoursePart.map())
                .all();
    }

    /**
     * The course an appointment is a part of.
     *
     * @param eventId the appointment
     * @return the part, or empty for an appointment of its own
     */
    public Optional<EventCoursePart> findPart(int eventId) {
        return query("""
                SELECT %s
                FROM station_event e
                         JOIN event_course_set s ON s.id = e.course_set_id
                WHERE e.id = :event_id;""", PART_COLUMNS)
                .single(call().bind("event_id", eventId))
                .map(EventCoursePart.map())
                .first();
    }

    /**
     * Locks a course for the rest of the running transaction, so changes to its sets and parts
     * happen one after another.
     *
     * @param courseId the course
     */
    public void lockCourse(int courseId) {
        SqlSupport.exists("SELECT 1 FROM event_course WHERE id = :id FOR UPDATE;", call().bind("id", courseId));
    }

    /**
     * Locks an appointment for the rest of the running transaction, so it cannot become a part of
     * two courses, or be signed up for on its own, while it is being added.
     *
     * @param eventId the appointment
     */
    public void lockEvent(int eventId) {
        SqlSupport.exists("SELECT 1 FROM station_event WHERE id = :id FOR UPDATE;", call().bind("id", eventId));
    }

    /**
     * Puts an appointment that is a part of no course into a set.
     *
     * @param eventId the appointment
     * @param setId   the set
     * @return false where the appointment already is a part of a course
     */
    public boolean claimPart(int eventId, int setId) {
        return query("""
                UPDATE station_event
                SET course_set_id = :set_id,
                    updated_at    = now()
                WHERE id = :event_id
                  AND course_set_id IS NULL;""")
                .single(call().bind("event_id", eventId).bind("set_id", setId))
                .update()
                .changed();
    }

    /**
     * Whether an appointment is shared with partner stations on its own: shared, given places for
     * partners, or holding partner sign-ups that still stand.
     *
     * @param eventId the appointment
     * @return true where partners take part in it
     */
    public boolean isSharedWithPartners(int eventId) {
        return SqlSupport.exists("""
                SELECT 1 FROM event_federation_share WHERE event_id = :event_id
                UNION ALL
                SELECT 1 FROM event_partner_places WHERE event_id = :event_id
                UNION ALL
                SELECT 1 FROM event_federation_registration
                WHERE event_id = :event_id AND status IN ('PENDING', 'ACCEPTED')
                LIMIT 1;""", call().bind("event_id", eventId));
    }

    /**
     * Puts an appointment into a set, or takes it out of its course.
     *
     * @param eventId the appointment
     * @param setId   the set, or null to make it an appointment of its own again
     */
    public void setPartSet(int eventId, @Nullable Integer setId) {
        query("UPDATE station_event SET course_set_id = :set_id, updated_at = now() WHERE id = :event_id;")
                .single(call().bind("event_id", eventId).bind("set_id", setId))
                .update();
    }

    /**
     * Unties the registrations of an appointment from the course sign-ups they were chosen with, so
     * the appointment's sign-ups stand on their own once it is no part of a course.
     *
     * @param eventId the appointment
     */
    public void untieRegistrations(int eventId) {
        query("UPDATE event_registration SET course_registration_id = NULL WHERE event_id = :event_id;")
                .single(call().bind("event_id", eventId))
                .update();
    }

    /**
     * Makes every part of a course an appointment of its own again.
     *
     * @param courseId the course
     */
    public void releaseParts(int courseId) {
        query("""
                UPDATE station_event
                SET course_set_id = NULL,
                    updated_at    = now()
                WHERE course_set_id IN (SELECT id FROM event_course_set WHERE course_id = :course_id);""").single(call().bind("course_id", courseId)).update();
    }

    /**
     * How many parts a set holds.
     *
     * @param setId the set
     * @return its parts
     */
    public int countPartsInSet(int setId) {
        return SqlSupport.count(
                "SELECT count(*) AS count FROM station_event WHERE course_set_id = :set_id;",
                call().bind("set_id", setId));
    }

    /**
     * Gives every part of a course the sign-up of the course: parts take sign-ups, confirm them as
     * the course does and close with it, and carry no limit, closing days or minimum of their own.
     *
     * @param courseId             the course
     * @param requiresConfirmation whether the course confirms sign-ups
     * @param deadline             when the course closes, or null
     */
    public void alignParts(int courseId, boolean requiresConfirmation, @Nullable Instant deadline) {
        query("""
                UPDATE station_event
                SET requires_registration   = TRUE,
                    requires_confirmation   = :requires_confirmation,
                    registration_deadline   = :registration_deadline,
                    registration_limit      = NULL,
                    registration_close_days = NULL,
                    min_registrations       = NULL,
                    threshold_days          = NULL,
                    updated_at              = now()
                WHERE course_set_id IN (SELECT id FROM event_course_set WHERE course_id = :course_id);""")
                .single(call().bind("course_id", courseId)
                        .bind("requires_confirmation", requiresConfirmation)
                        .bind("registration_deadline", deadline, INSTANT_TIMESTAMP))
                .update();
    }

    /**
     * Whether an appointment holds sign-ups of its own that still stand: waiting or accepted, and
     * not placed by an appointment field.
     *
     * @param eventId the appointment
     * @return true where somebody is signed up for it
     */
    public boolean hasOpenSignUps(int eventId) {
        return SqlSupport.exists("""
                SELECT 1
                FROM event_registration
                WHERE event_id = :event_id
                  AND status IN ('PENDING', 'ACCEPTED')
                  AND NOT from_field
                LIMIT 1;""", call().bind("event_id", eventId));
    }
}
