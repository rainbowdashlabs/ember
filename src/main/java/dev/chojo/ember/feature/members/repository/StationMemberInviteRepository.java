/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.StationMemberInvite;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for station-member invites — the per-recipient email invitation flow added with the
 * station setup wizard. Each invite carries a single-use URL-safe token and a target user type
 * plus optional member group; the public accept endpoint creates the matching account and station
 * member when the recipient follows the link.
 */
@Singleton
public class StationMemberInviteRepository {

    private static final String INVITE_COLUMNS =
            "id, station_id, token, email, first_name, last_name, user_type, group_id, guardian_of_invite_id, "
                    + "invited_by_member_id, expires_at, accepted_at, accepted_account_id, created_at";

    /**
     * Returns {@code true} if the station has at least one invite row (pending or accepted).
     * Used by the setup wizard's status endpoint to mark the optional "Invites" step complete
     * once the administrator has sent at least one invitation.
     */
    public boolean existsForStation(int stationId) {
        return query("SELECT 1 FROM station_member_invite WHERE station_id = :station_id LIMIT 1;")
                .single(call().bind("station_id", stationId))
                .map(row -> true)
                .first()
                .orElse(false);
    }

    /**
     * Inserts a new invite. The caller has already generated the token and computed the expiration
     * timestamp.
     */
    public StationMemberInvite create(
            int stationId,
            String token,
            String email,
            String firstName,
            String lastName,
            StationUserType userType,
            Integer groupId,
            Integer guardianOfInviteId,
            int invitedByMemberId,
            Instant expiresAt) {
        return query("""
                INSERT INTO station_member_invite(
                    station_id, token, email, first_name, last_name, user_type, group_id,
                    guardian_of_invite_id, invited_by_member_id, expires_at)
                VALUES (
                    :station_id, :token, :email, :first_name, :last_name, :user_type, :group_id,
                    :guardian_of_invite_id, :invited_by_member_id, :expires_at)
                RETURNING %s;""", INVITE_COLUMNS)
                .single(call().bind("station_id", stationId)
                        .bind("token", token)
                        .bind("email", email)
                        .bind("first_name", firstName)
                        .bind("last_name", lastName)
                        .bind("user_type", userType)
                        .bind("group_id", groupId)
                        .bind("guardian_of_invite_id", guardianOfInviteId)
                        .bind("invited_by_member_id", invitedByMemberId)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP))
                .map(StationMemberInvite.map())
                .first()
                .orElseThrow();
    }

    /**
     * Returns the invite identified by the given token, if any.
     */
    public Optional<StationMemberInvite> findByToken(String token) {
        return query("SELECT %s FROM station_member_invite WHERE token = :token;", INVITE_COLUMNS)
                .single(call().bind("token", token))
                .map(StationMemberInvite.map())
                .first();
    }

    /**
     * Returns the invite by its primary id, if any.
     */
    public Optional<StationMemberInvite> findById(int id) {
        return query("SELECT %s FROM station_member_invite WHERE id = :id;", INVITE_COLUMNS)
                .single(call().bind("id", id))
                .map(StationMemberInvite.map())
                .first();
    }

    /**
     * Lists every invite tied to the given station, newest first.
     */
    public List<StationMemberInvite> findByStation(int stationId) {
        return query(
                        "SELECT %s FROM station_member_invite WHERE station_id = :station_id ORDER BY created_at DESC;",
                        INVITE_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(StationMemberInvite.map())
                .all();
    }

    /**
     * Lists invites that still need acceptance for the given station.
     */
    public List<StationMemberInvite> findPendingByStation(int stationId) {
        return query(
                        "SELECT %s FROM station_member_invite WHERE station_id = :station_id "
                                + "AND accepted_at IS NULL ORDER BY created_at DESC;",
                        INVITE_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(StationMemberInvite.map())
                .all();
    }

    /**
     * Lists guardian invites that point at the given accepted parent invite. Used when the parent
     * accepts after the guardian, so the guardian-of relation can be wired retroactively.
     */
    public List<StationMemberInvite> findGuardianInvitesFor(int parentInviteId) {
        return query("SELECT %s FROM station_member_invite WHERE guardian_of_invite_id = :parent_id;", INVITE_COLUMNS)
                .single(call().bind("parent_id", parentInviteId))
                .map(StationMemberInvite.map())
                .all();
    }

    /**
     * Marks an invite accepted by stamping the timestamp and recording the account row created
     * during acceptance.
     */
    public boolean markAccepted(int inviteId, int accountId) {
        return query("""
                UPDATE station_member_invite
                SET accepted_at = now(),
                    accepted_account_id = :account_id
                WHERE id = :id
                  AND accepted_at IS NULL;""")
                .single(call().bind("id", inviteId).bind("account_id", accountId))
                .update()
                .changed();
    }

    /**
     * Deletes a pending invite. Returns {@code true} if a row was removed.
     */
    public boolean delete(int id) {
        return query("DELETE FROM station_member_invite WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }
}
