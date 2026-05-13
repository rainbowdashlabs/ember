/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.entity.ProfileFieldChange;
import dev.chojo.ember.entity.ProfileFieldChangeAcknowledgement;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class ProfileFieldChangeRepository {

    /**
     * Find a recent change for the same field+member+changedBy within the merge window.
     */
    public Optional<ProfileFieldChange> findRecentChange(int fieldId, int memberId, int changedBy, Instant cutoff) {
        return Query.query("""
                            SELECT c.id, c.field_id, c.member_id, c.old_value, c.new_value,
                                   c.changed_by, c.changed_at, c.requires_acknowledgement,
                                   coalesce(a.first_name || ' ' || a.last_name, '') AS changed_by_name,
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
                .single(Call.of()
                        .bind("field_id", fieldId)
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
        Query.query("""
                     UPDATE profile_field_change
                     SET new_value = :new_value::JSONB, changed_at = now()
                     WHERE id = :id;""")
                .single(Call.of().bind("new_value", newValue).bind("id", changeId))
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
        return Query.query("""
                            INSERT INTO profile_field_change(field_id, member_id, old_value, new_value, changed_by, requires_acknowledgement)
                            VALUES (:field_id, :member_id, :old_value::JSONB, :new_value::JSONB, :changed_by, :requires_acknowledgement)
                            RETURNING id, field_id, member_id, old_value, new_value, changed_by, changed_at, requires_acknowledgement,
                                      '' AS changed_by_name, '' AS field_name;""")
                .single(Call.of()
                        .bind("field_id", fieldId)
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
        return Query.query("""
                            SELECT c.id, c.field_id, c.member_id, c.old_value, c.new_value,
                                   c.changed_by, c.changed_at, c.requires_acknowledgement,
                                   coalesce(a.first_name || ' ' || a.last_name, '') AS changed_by_name,
                                   pf.name AS field_name
                            FROM profile_field_change c
                            JOIN station_member sm ON sm.id = c.changed_by
                            JOIN account a ON a.id = sm.account_id
                            JOIN profile_field pf ON pf.id = c.field_id
                            WHERE c.member_id = :member_id
                            ORDER BY c.changed_at DESC;""")
                .single(Call.of().bind("member_id", memberId))
                .map(ProfileFieldChange.map())
                .all();
    }

    /**
     * Find all acknowledgements for a change.
     */
    public List<ProfileFieldChangeAcknowledgement> findAcknowledgements(int changeId) {
        return Query.query("""
                            SELECT ack.id, ack.change_id, ack.acknowledged_by, ack.acknowledged_at, ack.comment,
                                   coalesce(a.first_name || ' ' || a.last_name, '') AS acknowledged_by_name
                            FROM profile_field_change_acknowledgement ack
                            JOIN station_member sm ON sm.id = ack.acknowledged_by
                            JOIN account a ON a.id = sm.account_id
                            WHERE ack.change_id = :change_id
                            ORDER BY ack.acknowledged_at;""")
                .single(Call.of().bind("change_id", changeId))
                .map(ProfileFieldChangeAcknowledgement.map())
                .all();
    }

    /**
     * Find all acknowledgements for multiple changes at once.
     */
    public List<ProfileFieldChangeAcknowledgement> findAcknowledgementsForMember(int memberId) {
        return Query.query("""
                            SELECT ack.id, ack.change_id, ack.acknowledged_by, ack.acknowledged_at, ack.comment,
                                   coalesce(a.first_name || ' ' || a.last_name, '') AS acknowledged_by_name
                            FROM profile_field_change_acknowledgement ack
                            JOIN profile_field_change c ON c.id = ack.change_id
                            JOIN station_member sm ON sm.id = ack.acknowledged_by
                            JOIN account a ON a.id = sm.account_id
                            WHERE c.member_id = :member_id
                            ORDER BY ack.acknowledged_at;""")
                .single(Call.of().bind("member_id", memberId))
                .map(ProfileFieldChangeAcknowledgement.map())
                .all();
    }

    /**
     * Acknowledge a change with optional comment.
     */
    public ProfileFieldChangeAcknowledgement acknowledge(int changeId, int acknowledgedBy, String comment) {
        return Query.query("""
                            INSERT INTO profile_field_change_acknowledgement(change_id, acknowledged_by, comment)
                            VALUES (:change_id, :acknowledged_by, :comment)
                            ON CONFLICT (change_id, acknowledged_by) DO UPDATE SET
                                acknowledged_at = now(),
                                comment = coalesce(excluded.comment, profile_field_change_acknowledgement.comment)
                            RETURNING id, change_id, acknowledged_by, acknowledged_at, comment,
                                      '' AS acknowledged_by_name;""")
                .single(Call.of()
                        .bind("change_id", changeId)
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
        return Query.query("""
                            SELECT c.member_id,
                                   coalesce(ma.first_name || ' ' || ma.last_name, '') AS member_name,
                                   count(c.id) AS pending_count,
                                   max(c.changed_at) AS latest_change
                            FROM profile_field_change c
                            JOIN station_member sm ON sm.id = c.member_id
                            JOIN account ma ON ma.id = sm.account_id
                            WHERE sm.station_id = :station_id
                              AND c.requires_acknowledgement = TRUE
                              AND NOT exists (
                                  SELECT 1 FROM profile_field_change_acknowledgement ack
                                  WHERE ack.change_id = c.id AND ack.acknowledged_by = :acknowledged_by
                              )
                            GROUP BY c.member_id, ma.first_name, ma.last_name
                            ORDER BY latest_change DESC;""")
                .single(Call.of().bind("station_id", stationId).bind("acknowledged_by", acknowledgedBy))
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
        return Query.query("""
                            SELECT c.id
                            FROM profile_field_change c
                            WHERE c.member_id = :member_id
                              AND c.requires_acknowledgement = TRUE
                              AND NOT exists (
                                  SELECT 1 FROM profile_field_change_acknowledgement ack
                                  WHERE ack.change_id = c.id AND ack.acknowledged_by = :acknowledged_by
                              )
                            ORDER BY c.changed_at;""")
                .single(Call.of().bind("member_id", memberId).bind("acknowledged_by", acknowledgedBy))
                .map(row -> row.getInt("id"))
                .all();
    }

    public record MemberChangeSummary(int memberId, String memberName, int pendingCount, Instant latestChange) {}
}
