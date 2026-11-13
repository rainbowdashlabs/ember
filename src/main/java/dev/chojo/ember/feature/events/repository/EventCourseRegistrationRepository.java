/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.EventCourseRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/** Sign-ups for courses, one per member and course. */
@Singleton
public class EventCourseRegistrationRepository {

    /**
     * A member's sign-up for a course.
     *
     * @param courseId the course
     * @param memberId the member
     * @return the sign-up, or empty where the member never signed up
     */
    public Optional<EventCourseRegistration> find(int courseId, int memberId) {
        return query(
                        "SELECT %s FROM event_course_registration WHERE course_id = :course_id AND member_id = :member_id;",
                        EventCourseRegistration.COLUMNS)
                .single(call().bind("course_id", courseId).bind("member_id", memberId))
                .map(EventCourseRegistration.map())
                .first();
    }

    /**
     * The sign-ups of a course, oldest first.
     *
     * @param courseId the course
     * @return its sign-ups in every status
     */
    public List<EventCourseRegistration> findByCourse(int courseId) {
        return query(
                        "SELECT %s FROM event_course_registration WHERE course_id = :course_id ORDER BY registered_at, id;",
                        EventCourseRegistration.COLUMNS)
                .single(call().bind("course_id", courseId))
                .map(EventCourseRegistration.map())
                .all();
    }

    /**
     * Signs a member up for a course, or signs them up again after they withdrew or were turned
     * down.
     *
     * @param courseId  the course
     * @param memberId  the member
     * @param status    the status the sign-up starts with
     * @param createdBy the member who signed somebody else up, or null
     * @return the sign-up
     */
    public EventCourseRegistration signUp(
            int courseId, int memberId, RegistrationStatus status, @Nullable Integer createdBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO event_course_registration(course_id, member_id, status, created_by)
                VALUES (:course_id, :member_id, :status, :created_by)
                ON CONFLICT (course_id, member_id) DO UPDATE
                    SET status        = :status,
                        registered_at = now(),
                        created_by    = :created_by
                RETURNING %s;""",
                call().bind("course_id", courseId)
                        .bind("member_id", memberId)
                        .bind("status", status)
                        .bind("created_by", createdBy),
                EventCourseRegistration.map(),
                EventCourseRegistration.COLUMNS);
    }

    /**
     * Moves a sign-up to a new status.
     *
     * @param id     the sign-up
     * @param status the new status
     */
    public void updateStatus(int id, RegistrationStatus status) {
        query("UPDATE event_course_registration SET status = :status WHERE id = :id;")
                .single(call().bind("id", id).bind("status", status))
                .update();
    }

    /**
     * How many sign-ups of a course hold or wait for a place.
     *
     * @param courseId the course
     * @return the pending and accepted sign-ups
     */
    public int countHolding(int courseId) {
        return SqlSupport.count("""
                SELECT count(*) FROM event_course_registration
                WHERE course_id = :course_id AND status IN ('PENDING', 'ACCEPTED');""", call().bind("course_id", courseId));
    }
}
