/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import jakarta.inject.Singleton;

import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * This station's own word about which of its members it signed up for a course a partner holds, so
 * a partner naming members of this station is believed only for those.
 */
@Singleton
public class PartnerCourseSignupRepository {

    /**
     * Notes that this station signed a member up for a partner's course.
     *
     * @param memberId          the member
     * @param partnerStationUid the partner holding the course
     * @param remoteCourseId    the course there
     */
    public void record(int memberId, UUID partnerStationUid, int remoteCourseId) {
        query("""
                INSERT INTO partner_course_signup(member_id, partner_station_uid, remote_course_id)
                VALUES (:member_id, :partner_uid::UUID, :course_id)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("member_id", memberId)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("course_id", remoteCourseId))
                .insert();
    }

    /**
     * Forgets a sign-up the member withdrew.
     *
     * @param memberId          the member
     * @param partnerStationUid the partner holding the course
     * @param remoteCourseId    the course there
     */
    public void forget(int memberId, UUID partnerStationUid, int remoteCourseId) {
        query("""
                DELETE FROM partner_course_signup
                WHERE member_id = :member_id AND partner_station_uid = :partner_uid::UUID
                  AND remote_course_id = :course_id;""")
                .single(call().bind("member_id", memberId)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("course_id", remoteCourseId))
                .delete();
    }

    /**
     * Whether this station signed the member up for the partner's course.
     *
     * @param memberId          the member
     * @param partnerStationUid the partner holding the course
     * @param remoteCourseId    the course there
     * @return true where it did
     */
    public boolean signedUp(int memberId, UUID partnerStationUid, int remoteCourseId) {
        return query("""
                SELECT 1 FROM partner_course_signup
                WHERE member_id = :member_id AND partner_station_uid = :partner_uid::UUID
                  AND remote_course_id = :course_id
                LIMIT 1;""")
                .single(call().bind("member_id", memberId)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("course_id", remoteCourseId))
                .map(row -> true)
                .first()
                .orElse(false);
    }
}
