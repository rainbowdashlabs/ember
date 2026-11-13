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
                INSERT INTO partner_event_group_signup(member_id, partner_station_uid, remote_group_id)
                VALUES (:member_id, :partner_uid::UUID, :group_id)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("member_id", memberId)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("group_id", remoteEventGroupId))
                .insert();
    }

    /**
     * Forgets a sign-up the member withdrew.
     *
     * @param memberId          the member
     * @param partnerStationUid the partner holding the event group
     * @param remoteEventGroupId    the event group there
     */
    public void forget(int memberId, UUID partnerStationUid, int remoteEventGroupId) {
        query("""
                DELETE FROM partner_event_group_signup
                WHERE member_id = :member_id AND partner_station_uid = :partner_uid::UUID
                  AND remote_group_id = :group_id;""")
                .single(call().bind("member_id", memberId)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("group_id", remoteEventGroupId))
                .delete();
    }

    /**
     * Whether this station signed the member up for the partner's event group.
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
                  AND remote_group_id = :group_id
                LIMIT 1;""")
                .single(call().bind("member_id", memberId)
                        .bind("partner_uid", partnerStationUid, UUID_STRING)
                        .bind("group_id", remoteEventGroupId))
                .map(row -> true)
                .first()
                .orElse(false);
    }
}
