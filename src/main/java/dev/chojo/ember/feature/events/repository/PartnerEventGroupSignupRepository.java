/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * This station's own word about which of its members it signed up for an event group a partner holds, so
 * a partner naming members of this station is believed only for those.
 */
@Singleton
public class PartnerEventGroupSignupRepository {

    /**
     * Notes that this station signed a member up for a partner's event group.
     *
     * @param memberId          the member
     * @param partnerStationUid the partner holding the event group
     * @param remoteEventGroupId    the event group there
     */
    public void record(int memberId, UUID partnerStationUid, int remoteEventGroupId) {
        query("""
                INSERT INTO partner_event_group_signup(member_id, partner_station_uid, remote_event_group_id)
                VALUES (:member_id, :partner_uid::UUID, :remote_event_group_id)
                ON CONFLICT (member_id, partner_station_uid, remote_event_group_id)
                    DO UPDATE SET status = CASE WHEN partner_event_group_signup.status = 'WITHDRAWN' THEN NULL
                                                ELSE partner_event_group_signup.status END;""")
                .single(call().bind("member_id", memberId)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("remote_event_group_id", remoteEventGroupId))
                .insert();
    }

    /**
     * Notes the status the partner gave this station's member for its event group, noting the sign-up
     * where it was not noted yet. The partner's instant is left as it was.
     *
     * @param memberId           the member
     * @param partnerStationUid  the partner holding the event group
     * @param remoteEventGroupId the event group there
     * @param status             the status the partner gave
     */
    public void recordStatus(int memberId, UUID partnerStationUid, int remoteEventGroupId, RegistrationStatus status) {
        query("""
                INSERT INTO partner_event_group_signup(member_id, partner_station_uid, remote_event_group_id, status)
                VALUES (:member_id, :partner_uid::UUID, :remote_event_group_id, :status)
                ON CONFLICT (member_id, partner_station_uid, remote_event_group_id)
                    DO UPDATE SET status = EXCLUDED.status;""")
                .single(call().bind("member_id", memberId)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("remote_event_group_id", remoteEventGroupId)
                        .bind("status", status))
                .insert();
    }

    /**
     * The status the partner last gave this station's member for its event group.
     *
     * @param memberId           the member
     * @param partnerStationUid  the partner holding the event group
     * @param remoteEventGroupId the event group there
     * @return the status, empty where none is known or the member was not signed up from here
     */
    public Optional<RegistrationStatus> knownStatus(int memberId, UUID partnerStationUid, int remoteEventGroupId) {
        return query("""
                SELECT status FROM partner_event_group_signup
                WHERE member_id = :member_id AND partner_station_uid = :partner_uid::UUID
                  AND remote_event_group_id = :remote_event_group_id AND status IS NOT NULL;""")
                .single(call().bind("member_id", memberId)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("remote_event_group_id", remoteEventGroupId))
                .map(row -> row.getEnum("status", RegistrationStatus.class))
                .first();
    }

    /**
     * Takes in a decision the partner told about this station's member, unless it is not newer than the
     * one already taken in.
     *
     * @param memberId           the member
     * @param partnerStationUid  the partner holding the event group
     * @param remoteEventGroupId the event group there
     * @param status             the status the partner gave
     * @param changedAt          when the partner gave it, on its own clock
     * @return whether the decision was taken in
     */
    public boolean learnStatus(
            int memberId,
            UUID partnerStationUid,
            int remoteEventGroupId,
            RegistrationStatus status,
            Instant changedAt) {
        return query("""
                UPDATE partner_event_group_signup
                SET status = :status, status_changed_at = :changed_at
                WHERE member_id = :member_id AND partner_station_uid = :partner_uid::UUID
                  AND remote_event_group_id = :remote_event_group_id
                  AND (status_changed_at IS NULL OR status_changed_at < :changed_at);""")
                .single(call().bind("status", status)
                        .bind("changed_at", changedAt, INSTANT_TIMESTAMP)
                        .bind("member_id", memberId)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("remote_event_group_id", remoteEventGroupId))
                .update()
                .changed();
    }

    /**
     * Notes that the member withdrew a sign-up. The row stays with the partner's last instant, so a notice
     * the partner sent before the withdrawal cannot apply to a sign-up given again later.
     *
     * @param memberId          the member
     * @param partnerStationUid the partner holding the event group
     * @param remoteEventGroupId    the event group there
     */
    public void forget(int memberId, UUID partnerStationUid, int remoteEventGroupId) {
        query("""
                UPDATE partner_event_group_signup SET status = 'WITHDRAWN'
                WHERE member_id = :member_id AND partner_station_uid = :partner_uid::UUID
                  AND remote_event_group_id = :remote_event_group_id;""")
                .single(call().bind("member_id", memberId)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("remote_event_group_id", remoteEventGroupId))
                .update();
    }

    /**
     * Whether this station signed the member up for the partner's event group, and the member has not
     * withdrawn.
     *
     * @param memberId          the member
     * @param partnerStationUid the partner holding the event group
     * @param remoteEventGroupId    the event group there
     * @return true where it did
     */
    public boolean signedUp(int memberId, UUID partnerStationUid, int remoteEventGroupId) {
        return query("""
                SELECT 1 FROM partner_event_group_signup
                WHERE member_id = :member_id AND partner_station_uid = :partner_uid::UUID
                  AND remote_event_group_id = :remote_event_group_id
                  AND status IS DISTINCT FROM 'WITHDRAWN'
                LIMIT 1;""")
                .single(call().bind("member_id", memberId)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("remote_event_group_id", remoteEventGroupId))
                .map(row -> true)
                .first()
                .orElse(false);
    }
}
