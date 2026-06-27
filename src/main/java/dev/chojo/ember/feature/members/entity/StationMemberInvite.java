/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.auth.StationUserType;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Persistent state of a single-use email invite a station administrator sent to bring a new
 * member onto the station.
 *
 * @param id                 the internal database identifier
 * @param stationId          the station that issued the invite
 * @param token              URL-safe random token included in the invite email link
 * @param email              recipient email address
 * @param firstName          recipient first name (prefilled on the registration page)
 * @param lastName           recipient last name (prefilled on the registration page)
 * @param userType           the {@link StationUserType} the new station member will be assigned
 * @param groupId            optional member group the new station member will be added to
 * @param guardianOfInviteId when non-null, this invite is for a guardian whose managed member
 *                           is the account that accepts the referenced invite
 * @param invitedByMemberId  the station member who issued the invite
 * @param expiresAt          timestamp at which the invite link stops working
 * @param acceptedAt         timestamp at which the invite was accepted, or {@code null} while pending
 * @param acceptedAccountId  account row created or chosen at acceptance time
 * @param createdAt          insertion timestamp
 */
public record StationMemberInvite(
        int id,
        int stationId,
        String token,
        String email,
        String firstName,
        String lastName,
        StationUserType userType,
        Integer groupId,
        Integer guardianOfInviteId,
        int invitedByMemberId,
        Instant expiresAt,
        Instant acceptedAt,
        Integer acceptedAccountId,
        Instant createdAt) {

    public static RowMapping<StationMemberInvite> map() {
        return row -> new StationMemberInvite(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("token"),
                row.getString("email"),
                row.getString("first_name"),
                row.getString("last_name"),
                row.getEnum("user_type", StationUserType.class),
                row.getObject("group_id") != null ? row.getInt("group_id") : null,
                row.getObject("guardian_of_invite_id") != null ? row.getInt("guardian_of_invite_id") : null,
                row.getInt("invited_by_member_id"),
                row.get("expires_at", INSTANT_TIMESTAMP),
                row.get("accepted_at", INSTANT_TIMESTAMP),
                row.getObject("accepted_account_id") != null ? row.getInt("accepted_account_id") : null,
                row.get("created_at", INSTANT_TIMESTAMP));
    }

    /**
     * Returns {@code true} if the invite is past its expiration timestamp.
     */
    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    /**
     * Returns {@code true} if the invite has been accepted.
     */
    public boolean isAccepted() {
        return acceptedAt != null;
    }
}
