/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.CoursePartSummary;
import dev.chojo.ember.feature.events.entity.EventCourse;
import dev.chojo.ember.feature.events.entity.EventCourseDetails;
import dev.chojo.ember.feature.events.entity.EventCoursePart;
import dev.chojo.ember.feature.events.entity.EventCourseSet;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
     * What every part of a station's courses says about its course in a list, by appointment.
     *
     * @param stationId the station
     * @return the course of each part, numbered in date order within its course
     */
    public Map<Integer, CoursePartSummary> findPartSummaries(int stationId) {
        return query("""
                SELECT e.id AS event_id,
                       c.id AS course_id,
                       c.name AS course_name,
                       s.id AS set_id,
                       s.name AS set_name,
                       row_number() OVER (PARTITION BY c.id ORDER BY e.start_time, e.id) AS number,
                       count(*) OVER (PARTITION BY c.id) AS part_count
                FROM station_event e
                         JOIN event_course_set s ON s.id = e.course_set_id
                         JOIN event_course c ON c.id = s.course_id
                WHERE c.station_id = :station_id;""")
                .single(call().bind("station_id", stationId))
                .map(row -> Map.entry(
                        row.getInt("event_id"),
                        new CoursePartSummary(
                                row.getInt("course_id"),
                                row.getString("course_name"),
                                row.getInt("set_id"),
                                row.getString("set_name"),
                                row.getInt("number"),
                                row.getInt("part_count"))))
                .all()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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

    /**
     * Calls a course off.
     *
     * @param courseId the course
     * @param reason   why, or null
     * @return false where it was called off already
     */
    public boolean cancel(int courseId, @Nullable String reason) {
        return query("""
                UPDATE event_course
                SET cancelled     = TRUE,
                    cancelled_at  = now(),
                    cancel_reason = :reason
                WHERE id = :id
                  AND NOT cancelled;""")
                .single(call().bind("id", courseId).bind("reason", reason))
                .update()
                .changed();
    }

    /**
     * Courses whose sign-ups closed while some still wait for a decision, and whose managers were not
     * told yet.
     *
     * @return those courses
     */
    public List<ExpiredCourseDeadline> findExpiredDeadlines() {
        return query("""
                SELECT c.id AS course_id, c.station_id, c.name, count(r.id) AS pending_count
                FROM event_course c
                         JOIN event_course_registration r ON r.course_id = c.id AND r.status = 'PENDING'
                WHERE c.registration_deadline IS NOT NULL
                  AND c.registration_deadline < now()
                  AND NOT c.deadline_notified
                  AND NOT c.cancelled
                GROUP BY c.id, c.station_id, c.name;""")
                .single(call())
                .map(row -> new ExpiredCourseDeadline(
                        row.getInt("course_id"),
                        row.getInt("station_id"),
                        row.getString("name"),
                        row.getInt("pending_count")))
                .all();
    }

    /**
     * The courses of a station members can still answer before a deadline: not called off, a
     * deadline still ahead, and a part still to come.
     *
     * @param stationId the station
     * @return those courses, soonest deadline first
     */
    public List<EventCourse> findOpenWithDeadline(int stationId) {
        return query("""
                SELECT %s
                FROM event_course c
                WHERE c.station_id = :station_id
                  AND NOT c.cancelled
                  AND c.registration_deadline IS NOT NULL
                  AND c.registration_deadline > now()
                  AND EXISTS (SELECT 1
                              FROM station_event e
                                       JOIN event_course_set cs ON cs.id = e.course_set_id
                              WHERE cs.course_id = c.id
                                AND e.start_time > now()
                                AND NOT e.cancelled
                                AND %s)
                ORDER BY c.registration_deadline, c.id;""", "c." + EventCourse.COLUMNS.replace(", ", ", c."), CancellationSql.NO_DATE_CANCELLED)
                .single(call().bind("station_id", stationId))
                .map(EventCourse.map())
                .all();
    }

    /**
     * Notes that managers were told that a course's sign-ups closed.
     *
     * @param courseId the course
     */
    public void markDeadlineNotified(int courseId) {
        query("UPDATE event_course SET deadline_notified = TRUE WHERE id = :id;")
                .single(call().bind("id", courseId))
                .update();
    }

    /**
     * Courses whose sign-ups close within the given number of days and whose warning for that lead
     * time has not gone out yet.
     *
     * @param daysBefore the lead time warned about
     * @return those courses
     */
    public List<ClosingCourse> findClosingIn(int daysBefore) {
        return query("""
                SELECT c.id AS course_id, c.station_id, c.name
                FROM event_course c
                WHERE NOT c.cancelled
                  AND c.registration_deadline IS NOT NULL
                  AND c.registration_deadline > now()
                  AND c.registration_deadline <= now() + make_interval(days => :days_before)
                  AND EXISTS (SELECT 1
                              FROM station_event e
                                       JOIN event_course_set cs ON cs.id = e.course_set_id
                              WHERE cs.course_id = c.id
                                AND e.start_time > now()
                                AND NOT e.cancelled
                                AND %s)
                  AND NOT EXISTS (SELECT 1
                                  FROM event_course_deadline_reminder_sent s
                                  WHERE s.course_id = c.id
                                    AND s.days_before = :days_before);""", CancellationSql.NO_DATE_CANCELLED)
                .single(call().bind("days_before", daysBefore))
                .map(row -> new ClosingCourse(row.getInt("course_id"), row.getInt("station_id"), row.getString("name")))
                .all();
    }

    /**
     * Forgets which warnings went out for a course, so a moved deadline is warned about again.
     *
     * @param courseId the course
     */
    public void clearClosingWarnings(int courseId) {
        query("DELETE FROM event_course_deadline_reminder_sent WHERE course_id = :course_id;")
                .single(call().bind("course_id", courseId))
                .update();
    }

    /**
     * Notes that the warning for a lead time went out for a course.
     *
     * @param courseId   the course
     * @param daysBefore the lead time
     */
    public void markClosingWarned(int courseId, int daysBefore) {
        query("""
                INSERT INTO event_course_deadline_reminder_sent(course_id, days_before)
                VALUES (:course_id, :days_before)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("course_id", courseId).bind("days_before", daysBefore))
                .insert();
    }

    /**
     * The current members of a station who never answered a course: neither signed up nor withdrew.
     * Whether the course is meant for them is not decided here.
     *
     * @param courseId  the course
     * @param stationId the station
     * @return the members
     */
    public List<Integer> findUnansweredMemberIds(int courseId, int stationId) {
        return query("""
                SELECT sm.id AS member_id
                FROM station_member sm
                WHERE sm.station_id = :station_id
                  AND sm.former = FALSE
                  AND NOT EXISTS (SELECT 1
                                  FROM event_course_registration r
                                  WHERE r.course_id = :course_id
                                    AND r.member_id = sm.id);""")
                .single(call().bind("course_id", courseId).bind("station_id", stationId))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    /**
     * A course whose sign-ups closed with some still waiting.
     *
     * @param courseId     the course
     * @param stationId    its station
     * @param name         its name
     * @param pendingCount how many wait for a decision
     */
    public record ExpiredCourseDeadline(int courseId, int stationId, String name, int pendingCount) {}

    /**
     * A course whose sign-ups close soon.
     *
     * @param courseId  the course
     * @param stationId its station
     * @param name      its name
     */
    public record ClosingCourse(int courseId, int stationId, String name) {}
}
