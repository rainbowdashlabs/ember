/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.events.entity.EventCoursePartnerRegistration;
import dev.chojo.ember.feature.events.entity.EventCourseShare;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.restriction.RestrictionSql;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Courses shared with partner stations, and the sign-ups partners' members send for them.
 */
@Singleton
public class EventCoursePartnerRepository {

    /**
     * How a course is shared, where it is.
     *
     * @param courseId the course
     * @return the share, empty where the course is not shared
     */
    public Optional<EventCourseShare> findShare(int courseId) {
        return query("SELECT scope FROM event_course_share WHERE course_id = :course_id;")
                .single(call().bind("course_id", courseId))
                .map(row -> new EventCourseShare(courseId, row.getEnum("scope", ShareScope.class), targets(courseId)))
                .first();
    }

    private List<Integer> targets(int courseId) {
        return query(
                        "SELECT partner_id FROM event_course_share_target WHERE course_id = :course_id ORDER BY partner_id;")
                .single(call().bind("course_id", courseId))
                .map(row -> row.getInt("partner_id"))
                .all();
    }

    /**
     * Shares a course, replacing how it was shared before.
     *
     * @param courseId   the course
     * @param scope      every partner, or the ones named
     * @param partnerIds the partners named
     */
    public void setShare(int courseId, ShareScope scope, List<Integer> partnerIds) {
        query("""
                INSERT INTO event_course_share(course_id, scope) VALUES (:course_id, :scope)
                ON CONFLICT (course_id) DO UPDATE SET scope = :scope;""")
                .single(call().bind("course_id", courseId).bind("scope", scope))
                .insert();
        query("DELETE FROM event_course_share_target WHERE course_id = :course_id;")
                .single(call().bind("course_id", courseId))
                .delete();
        for (int partnerId : partnerIds) {
            query("INSERT INTO event_course_share_target(course_id, partner_id) VALUES (:course_id, :partner_id);")
                    .single(call().bind("course_id", courseId).bind("partner_id", partnerId))
                    .insert();
        }
    }

    /**
     * Stops sharing a course.
     *
     * @param courseId the course
     */
    public void removeShare(int courseId) {
        query("DELETE FROM event_course_share WHERE course_id = :course_id;")
                .single(call().bind("course_id", courseId))
                .delete();
    }

    /**
     * The courses of a station shared with a partner. A course not every member of the station may
     * see is never among them, whatever its share says.
     *
     * @param partnerId the station's record of the partner
     * @param stationId the station
     * @return the ids of the shared courses
     */
    public List<Integer> findSharedCourseIds(int partnerId, int stationId) {
        return query("""
                SELECT ec.id
                FROM event_course_share ecs
                    JOIN event_course ec ON ec.id = ecs.course_id
                WHERE ec.station_id = :station_id
                  AND %s
                  AND (ecs.scope = 'ALL_PARTNERS'
                       OR (ecs.scope = 'SPECIFIC'
                           AND exists (SELECT 1 FROM event_course_share_target t
                                       WHERE t.course_id = ecs.course_id AND t.partner_id = :partner_id)));""", RestrictionSql.unrestricted(RestrictionType.EVENT_COURSE_VIEW, "ec.id"))
                .single(call().bind("station_id", stationId).bind("partner_id", partnerId))
                .map(row -> row.getInt("id"))
                .all();
    }

    /**
     * A partner member's sign-up for a course.
     *
     * @param courseId       the course
     * @param partnerId      the partner
     * @param remoteMemberId the member
     * @return the sign-up, empty where the member never signed up
     */
    public Optional<EventCoursePartnerRegistration> find(int courseId, int partnerId, UUID remoteMemberId) {
        return query("""
                SELECT %s FROM event_course_partner_registration
                WHERE course_id = :course_id AND partner_id = :partner_id AND remote_member_id = :member::UUID;""", EventCoursePartnerRegistration.COLUMNS)
                .single(call().bind("course_id", courseId)
                        .bind("partner_id", partnerId)
                        .bind("member", remoteMemberId, StandardValueConverter.UUID_STRING))
                .map(EventCoursePartnerRegistration.map())
                .first();
    }

    /**
     * A partner sign-up by its id.
     *
     * @param id the sign-up
     * @return the sign-up, empty where there is none
     */
    public Optional<EventCoursePartnerRegistration> findById(int id) {
        return query(
                        "SELECT %s FROM event_course_partner_registration WHERE id = :id;",
                        EventCoursePartnerRegistration.COLUMNS)
                .single(call().bind("id", id))
                .map(EventCoursePartnerRegistration.map())
                .first();
    }

    /**
     * Every partner sign-up of a course, oldest first.
     *
     * @param courseId the course
     * @return its partner sign-ups
     */
    public List<EventCoursePartnerRegistration> findByCourse(int courseId) {
        return query("""
                SELECT %s FROM event_course_partner_registration
                WHERE course_id = :course_id
                ORDER BY registered_at, id;""", EventCoursePartnerRegistration.COLUMNS)
                .single(call().bind("course_id", courseId))
                .map(EventCoursePartnerRegistration.map())
                .all();
    }

    /**
     * Signs a partner's member up, or takes them back after they withdrew.
     *
     * @param courseId       the course
     * @param partnerId      the partner
     * @param remoteMemberId the member
     * @param status         the status the sign-up starts with
     * @return the sign-up
     */
    public EventCoursePartnerRegistration signUp(
            int courseId, int partnerId, UUID remoteMemberId, RegistrationStatus status) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO event_course_partner_registration(course_id, partner_id, remote_member_id, status)
                VALUES (:course_id, :partner_id, :member::UUID, :status)
                ON CONFLICT (course_id, partner_id, remote_member_id) DO UPDATE
                    SET status = :status, registered_at = now()
                RETURNING %s;""",
                call().bind("course_id", courseId)
                        .bind("partner_id", partnerId)
                        .bind("member", remoteMemberId, StandardValueConverter.UUID_STRING)
                        .bind("status", status),
                EventCoursePartnerRegistration.map(),
                EventCoursePartnerRegistration.COLUMNS);
    }

    /**
     * Moves a partner sign-up to a new status.
     *
     * @param id     the sign-up
     * @param status the new status
     */
    public void updateStatus(int id, RegistrationStatus status) {
        query("UPDATE event_course_partner_registration SET status = :status WHERE id = :id;")
                .single(call().bind("status", status).bind("id", id))
                .update();
    }

    /**
     * How many partner sign-ups of a course hold or wait for a place.
     *
     * @param courseId the course
     * @return the pending and accepted partner sign-ups
     */
    public int countHolding(int courseId) {
        return query("""
                SELECT count(*) AS holding FROM event_course_partner_registration
                WHERE course_id = :course_id AND status IN ('PENDING', 'ACCEPTED');""")
                .single(call().bind("course_id", courseId))
                .map(row -> row.getInt("holding"))
                .first()
                .orElse(0);
    }

    /**
     * Ties a partner registration for a part to the course sign-up it was chosen with.
     *
     * @param registrationId               the partner registration
     * @param coursePartnerRegistrationId  the partner's course sign-up
     */
    public void link(int registrationId, int coursePartnerRegistrationId) {
        query("""
                UPDATE event_federation_registration
                SET course_partner_registration_id = :course_registration
                WHERE id = :id;""")
                .single(call().bind("course_registration", coursePartnerRegistrationId)
                        .bind("id", registrationId))
                .update();
    }

    /**
     * Unties a partner registration from its course sign-up, so it is a registration of its own.
     *
     * @param registrationId the partner registration
     */
    public void unlink(int registrationId) {
        query("UPDATE event_federation_registration SET course_partner_registration_id = NULL WHERE id = :id;")
                .single(call().bind("id", registrationId))
                .update();
    }

    /**
     * The partner registrations still tied to a partner's course sign-up.
     *
     * @param coursePartnerRegistrationId the partner's course sign-up
     * @return its registrations for parts
     */
    public List<EventFederationRegistration> findLinked(int coursePartnerRegistrationId) {
        return query("""
                SELECT id, event_id, partner_id, remote_member_id, event_date, status, created_at, agreement_withdrawn_at
                FROM event_federation_registration
                WHERE course_partner_registration_id = :course_registration;""")
                .single(call().bind("course_registration", coursePartnerRegistrationId))
                .map(EventFederationRegistration.map())
                .all();
    }

    /**
     * Sets the status of a partner registration, keeping the one it had before.
     *
     * @param registrationId the partner registration
     * @param status         the new status
     */
    public void restate(int registrationId, RegistrationStatus status) {
        query("""
                UPDATE event_federation_registration
                SET previous_status = status, status = :status, status_changed_at = now()
                WHERE id = :id;""")
                .single(call().bind("status", status).bind("id", registrationId))
                .update();
    }
}
