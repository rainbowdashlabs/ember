/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.members.entity.ProfileFieldChange;
import dev.chojo.ember.feature.members.entity.ProfileFieldChangeAcknowledgement;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for profile field change tracking, including acknowledgements and summaries.
 */
@Singleton
public class ProfileFieldChangeRepository {

    /**
     * Find a recent change for the same field+member+changedBy within the merge window.
     */
    public Optional<ProfileFieldChange> findRecentChange(int fieldId, int memberId, int changedBy, Instant cutoff) {
        return query("""
                SELECT c.id, c.field_id, c.member_id, c.old_value, c.new_value,
                       c.changed_by, c.changed_at, c.requires_acknowledgement,
                       a.full_name AS changed_by_name,
                       pf.name AS field_name
                FROM profile_field_change c
                JOIN station_member sm ON sm.id = c.changed_by
                JOIN account a ON a.id = sm.account_id
                JOIN profile_field pf ON pf.id = c.field_id
                WHERE c.field_id = :field_id
                  AND c.member_id = :member_id
                  AND c.changed_by = :changed_by
                  AND c.changed_at >= :cutoff
                ORDER BY c.changed_at DESC
                LIMIT 1;""")
                .single(call().bind("field_id", fieldId)
                        .bind("member_id", memberId)
                        .bind("changed_by", changedBy)
                        .bind("cutoff", cutoff, INSTANT_TIMESTAMP))
                .map(ProfileFieldChange.map())
                .first();
    }

    /**
     * Update an existing change record's new_value and timestamp (merge).
     */
    public void updateChangeNewValue(int changeId, String newValue) {
        query("""
                UPDATE profile_field_change
                SET new_value = :new_value::JSONB, changed_at = now()
                WHERE id = :id;""")
                .single(call().bind("new_value", newValue).bind("id", changeId))
                .update();
    }

    /**
     * Create a new change record.
     */
    public ProfileFieldChange create(
            int fieldId,
            int memberId,
            String oldValue,
            String newValue,
            int changedBy,
            boolean requiresAcknowledgement) {
        return query("""
                INSERT INTO profile_field_change(field_id, member_id, old_value, new_value, changed_by, requires_acknowledgement)
                VALUES (:field_id, :member_id, :old_value::JSONB, :new_value::JSONB, :changed_by, :requires_acknowledgement)
                RETURNING id, field_id, member_id, old_value, new_value, changed_by, changed_at, requires_acknowledgement,
                          '' AS changed_by_name, '' AS field_name;""")
                .single(call().bind("field_id", fieldId)
                        .bind("member_id", memberId)
                        .bind("old_value", oldValue)
                        .bind("new_value", newValue)
                        .bind("changed_by", changedBy)
                        .bind("requires_acknowledgement", requiresAcknowledgement))
                .map(ProfileFieldChange.map())
                .first()
                .orElseThrow();
    }

    /**
     * Find all changes for a member, enriched with field name, changer name.
     */
    public List<ProfileFieldChange> findByMember(int memberId) {
        return query("""
                SELECT c.id, c.field_id, c.member_id, c.old_value, c.new_value,
                       c.changed_by, c.changed_at, c.requires_acknowledgement,
                       a.full_name AS changed_by_name,
                       pf.name AS field_name
                FROM profile_field_change c
                JOIN station_member sm ON sm.id = c.changed_by
                JOIN account a ON a.id = sm.account_id
                JOIN profile_field pf ON pf.id = c.field_id
                WHERE c.member_id = :member_id
                ORDER BY c.changed_at DESC;""")
                .single(call().bind("member_id", memberId))
                .map(ProfileFieldChange.map())
                .all();
    }

    /**
     * Find all acknowledgements for a change.
     */
    public List<ProfileFieldChangeAcknowledgement> findAcknowledgements(int changeId) {
        return query("""
                SELECT ack.id, ack.change_id, ack.acknowledged_by, ack.acknowledged_at, ack.comment,
                       a.full_name AS acknowledged_by_name
                FROM profile_field_change_acknowledgement ack
                JOIN station_member sm ON sm.id = ack.acknowledged_by
                JOIN account a ON a.id = sm.account_id
                WHERE ack.change_id = :change_id
                ORDER BY ack.acknowledged_at;""")
                .single(call().bind("change_id", changeId))
                .map(ProfileFieldChangeAcknowledgement.map())
                .all();
    }

    /**
     * Find all acknowledgements for multiple changes at once.
     */
    public List<ProfileFieldChangeAcknowledgement> findAcknowledgementsForMember(int memberId) {
        return query("""
                SELECT ack.id, ack.change_id, ack.acknowledged_by, ack.acknowledged_at, ack.comment,
                       a.full_name AS acknowledged_by_name
                FROM profile_field_change_acknowledgement ack
                JOIN profile_field_change c ON c.id = ack.change_id
                JOIN station_member sm ON sm.id = ack.acknowledged_by
                JOIN account a ON a.id = sm.account_id
                WHERE c.member_id = :member_id
                ORDER BY ack.acknowledged_at;""")
                .single(call().bind("member_id", memberId))
                .map(ProfileFieldChangeAcknowledgement.map())
                .all();
    }

    /**
     * Acknowledge a change with optional comment.
     */
    public ProfileFieldChangeAcknowledgement acknowledge(int changeId, int acknowledgedBy, String comment) {
        return query("""
                INSERT INTO profile_field_change_acknowledgement(change_id, acknowledged_by, comment)
                VALUES (:change_id, :acknowledged_by, :comment)
                ON CONFLICT (change_id, acknowledged_by) DO UPDATE SET
                    acknowledged_at = now(),
                    comment = coalesce(excluded.comment, profile_field_change_acknowledgement.comment)
                RETURNING id, change_id, acknowledged_by, acknowledged_at, comment,
                          '' AS acknowledged_by_name;""")
                .single(call().bind("change_id", changeId)
                        .bind("acknowledged_by", acknowledgedBy)
                        .bind("comment", comment))
                .map(ProfileFieldChangeAcknowledgement.map())
                .first()
                .orElseThrow();
    }

    /**
     * Find members in a station that have unacknowledged changes, with count per member.
     */
    public List<MemberChangeSummary> findUnacknowledgedSummary(int stationId, int acknowledgedBy) {
        return query("""
                SELECT c.member_id,
                       ma.full_name AS member_name,
                       count(c.id) AS pending_count,
                       max(c.changed_at) AS latest_change
                FROM profile_field_change c
                JOIN station_member sm ON sm.id = c.member_id
                JOIN account ma ON ma.id = sm.account_id
                WHERE sm.station_id = :station_id
                  AND c.requires_acknowledgement
                  AND NOT exists (
                      SELECT 1 FROM profile_field_change_acknowledgement ack
                      WHERE ack.change_id = c.id AND ack.acknowledged_by = :acknowledged_by
                  )
                GROUP BY c.member_id, ma.full_name
                ORDER BY latest_change DESC;""")
                .single(call().bind("station_id", stationId).bind("acknowledged_by", acknowledgedBy))
                .map(row -> new MemberChangeSummary(
                        row.getInt("member_id"),
                        row.getString("member_name"),
                        row.getInt("pending_count"),
                        row.get("latest_change", INSTANT_TIMESTAMP)))
                .all();
    }

    /**
     * Find all unacknowledged change IDs for a member where requires_acknowledgement is true.
     */
    public List<Integer> findUnacknowledgedChangeIds(int memberId, int acknowledgedBy) {
        return query("""
                SELECT c.id
                FROM profile_field_change c
                WHERE c.member_id = :member_id
                  AND c.requires_acknowledgement
                  AND NOT exists (
                      SELECT 1 FROM profile_field_change_acknowledgement ack
                      WHERE ack.change_id = c.id AND ack.acknowledged_by = :acknowledged_by
                  )
                ORDER BY c.changed_at;""")
                .single(call().bind("member_id", memberId).bind("acknowledged_by", acknowledgedBy))
                .map(row -> row.getInt("id"))
                .all();
    }

    /**
     * Finds all profile field changes for a station with pagination.
     *
     * @param stationId the station identifier
     * @param limit     the maximum number of results
     * @param offset    the number of results to skip
     * @return the paginated list of changes ordered by most recent first
     */
    public List<ProfileFieldChange> findByStation(int stationId, int limit, int offset) {
        return query("""
                SELECT c.id, c.field_id, c.member_id, c.old_value, c.new_value,
                       c.changed_by, c.changed_at, c.requires_acknowledgement,
                       a.full_name AS changed_by_name,
                       pf.name AS field_name
                FROM profile_field_change c
                JOIN station_member sm ON sm.id = c.member_id
                JOIN station_member sm2 ON sm2.id = c.changed_by
                JOIN account a ON a.id = sm2.account_id
                JOIN profile_field pf ON pf.id = c.field_id
                WHERE sm.station_id = :station_id
                ORDER BY c.changed_at DESC
                LIMIT :limit OFFSET :offset;""")
                .single(call().bind("station_id", stationId)
                        .bind("limit", limit)
                        .bind("offset", offset))
                .map(ProfileFieldChange.map())
                .all();
    }

    /**
     * Counts the total number of profile field changes for a station.
     *
     * @param stationId the station identifier
     * @return the total change count
     */
    public int countByStation(int stationId) {
        return query("""
                SELECT count(*) AS cnt
                FROM profile_field_change c
                JOIN station_member sm ON sm.id = c.member_id
                WHERE sm.station_id = :station_id;""")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    public int countPendingChanges(int stationId, int acknowledgedBy) {
        return query("""
                SELECT count(*) AS cnt FROM profile_field_change c
                JOIN station_member sm ON sm.id = c.member_id
                WHERE sm.station_id = :station_id
                  AND c.requires_acknowledgement
                  AND NOT exists (
                      SELECT 1 FROM profile_field_change_acknowledgement ack
                      WHERE ack.change_id = c.id AND ack.acknowledged_by = :acknowledged_by
                  );""")
                .single(call().bind("station_id", stationId).bind("acknowledged_by", acknowledgedBy))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    /**
     * Summary of unacknowledged changes for a single member.
     *
     * @param memberId     the member identifier
     * @param memberName   the member's display name
     * @param pendingCount the number of unacknowledged changes
     * @param latestChange the timestamp of the most recent change
     */
    public record MemberChangeSummary(int memberId, String memberName, int pendingCount, Instant latestChange) {}
}
