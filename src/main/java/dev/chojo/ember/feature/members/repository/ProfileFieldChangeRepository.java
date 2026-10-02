/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.MemberChangeSummary;
import dev.chojo.ember.feature.members.entity.ProfileAuthor;
import dev.chojo.ember.feature.members.entity.ProfileFieldChange;
import dev.chojo.ember.feature.members.entity.ProfileFieldChangeAcknowledgement;
import dev.chojo.ember.util.sql.MemberNameSql;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

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
    private static final String ENRICHED_CHANGE_COLUMNS = """
            c.id, c.field_id, c.cluster_field_id, c.member_id, c.old_value, c.new_value,
            c.changed_by, c.changed_at, c.requires_acknowledgement,
            coalesce(%s, '') AS changed_by_name,
            coalesce(pf.name, cpf.name) AS field_name,
            coalesce(pf.field_type, cpf.field_type) AS field_type""".formatted(MemberNameSql.ofAccount("a"));
    /**
     * Who made a change, as {@code a}: their account through their membership, or the account kept beside
     * the change where they had no membership at the member's station.
     */
    private static final String AUTHOR_JOIN = """
            LEFT JOIN station_member sm ON sm.id = c.changed_by
            LEFT JOIN account a ON a.id = coalesce(sm.account_id, c.changed_by_account_id)""";

    private static final String ENRICHED_ACKNOWLEDGEMENT_COLUMNS = """
            ack.id, ack.change_id, ack.acknowledged_by, ack.acknowledged_at, ack.comment,
            %s AS acknowledged_by_name""".formatted(MemberNameSql.ofAccount("a"));

    /**
     * The newest change to one answer by one author since the cutoff, which a further change by the same
     * author merges into.
     *
     * @param origin   who asked the question, which says which column names it
     * @param fieldId  the question
     * @param memberId whose answer it is
     * @param author   who changed it
     * @param cutoff   how far back a change still counts as the same one
     * @return the change's id, or empty where there is none that recent
     */
    public Optional<Integer> findRecentChange(
            FieldOrigin origin, int fieldId, int memberId, ProfileAuthor author, Instant cutoff) {
        return query("""
                SELECT id
                FROM profile_field_change
                WHERE %s = :field_id
                  AND member_id = :member_id
                  AND changed_by IS NOT DISTINCT FROM :changed_by
                  AND changed_by_account_id IS NOT DISTINCT FROM :changed_by_account_id
                  AND changed_at >= :cutoff
                ORDER BY changed_at DESC
                LIMIT 1;""", targetColumn(origin))
                .single(call().bind("field_id", fieldId)
                        .bind("member_id", memberId)
                        .bind("changed_by", author.memberId())
                        .bind("changed_by_account_id", accountColumnOf(author))
                        .bind("cutoff", cutoff, INSTANT_TIMESTAMP))
                .map(row -> row.getInt("id"))
                .first();
    }

    /**
     * The column naming the question a change belongs to.
     *
     * @param origin who asked the question
     * @return {@code field_id} for a station's own, {@code cluster_field_id} for an association's
     */
    private static String targetColumn(FieldOrigin origin) {
        return switch (origin) {
            case STATION -> "field_id";
            case CLUSTER -> "cluster_field_id";
        };
    }

    /**
     * The account recorded beside a change, which is only kept where the author has no membership at the
     * member's station. The membership names them otherwise, and keeping both would let them disagree.
     */
    private static @Nullable Integer accountColumnOf(ProfileAuthor author) {
        return author.memberId() == null ? author.accountId() : null;
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
     * Records a change to one answer, whoever asked the question.
     *
     * <p>One history for both owners, because a member's profile reads as one story: what changed, when and
     * by whom, whoever asked the question. Which of the two columns is filled says whose question it was.
     *
     * @param origin                  who asked the question
     * @param fieldId                 the question
     * @param memberId                the member whose answer changed
     * @param oldValue                what it was
     * @param newValue                what it now is
     * @param author                  who changed it
     * @param requiresAcknowledgement whether somebody at the station has to confirm they saw it
     * @return the change
     */
    public ProfileFieldChange create(
            FieldOrigin origin,
            int fieldId,
            int memberId,
            String oldValue,
            String newValue,
            ProfileAuthor author,
            boolean requiresAcknowledgement) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO profile_field_change(%s, member_id, old_value, new_value, changed_by,
                                                 changed_by_account_id, requires_acknowledgement)
                VALUES (:field_id, :member_id, :old_value::JSONB, :new_value::JSONB, :changed_by,
                        :changed_by_account_id, :requires_acknowledgement)
                RETURNING id, field_id, cluster_field_id, member_id, old_value, new_value, changed_by, changed_at,
                          requires_acknowledgement, '' AS changed_by_name, '' AS field_name,
                          NULL AS field_type;""".formatted(targetColumn(origin)),
                call().bind("field_id", fieldId)
                        .bind("member_id", memberId)
                        .bind("old_value", oldValue)
                        .bind("new_value", newValue)
                        .bind("changed_by", author.memberId())
                        .bind("changed_by_account_id", accountColumnOf(author))
                        .bind("requires_acknowledgement", requiresAcknowledgement),
                ProfileFieldChange.map());
    }

    /**
     * Records a change a member of the station made to one of its own questions.
     *
     * @param fieldId                 the station's question
     * @param memberId                the member whose answer changed
     * @param oldValue                what it was
     * @param newValue                what it now is
     * @param changedBy               the member who changed it
     * @param requiresAcknowledgement whether somebody at the station has to confirm they saw it
     * @return the change
     */
    public ProfileFieldChange create(
            int fieldId,
            int memberId,
            String oldValue,
            String newValue,
            int changedBy,
            boolean requiresAcknowledgement) {
        return create(
                FieldOrigin.STATION,
                fieldId,
                memberId,
                oldValue,
                newValue,
                new ProfileAuthor(changedBy, null),
                requiresAcknowledgement);
    }

    /**
     * Find all changes for a member, enriched with field name, changer name.
     */
    public List<ProfileFieldChange> findByMember(int memberId) {
        return query("""
                SELECT %s
                FROM profile_field_change c
                %s
                LEFT JOIN profile_field pf ON pf.id = c.field_id
                LEFT JOIN cluster_profile_field cpf ON cpf.id = c.cluster_field_id
                WHERE c.member_id = :member_id
                ORDER BY c.changed_at DESC;""", ENRICHED_CHANGE_COLUMNS, AUTHOR_JOIN)
                .single(call().bind("member_id", memberId))
                .map(ProfileFieldChange.map())
                .all();
    }

    /**
     * Find all acknowledgements for a change.
     */
    public List<ProfileFieldChangeAcknowledgement> findAcknowledgements(int changeId) {
        return query("""
                SELECT %s
                FROM profile_field_change_acknowledgement ack
                JOIN station_member sm ON sm.id = ack.acknowledged_by
                JOIN account a ON a.id = sm.account_id
                WHERE ack.change_id = :change_id
                ORDER BY ack.acknowledged_at;""", ENRICHED_ACKNOWLEDGEMENT_COLUMNS)
                .single(call().bind("change_id", changeId))
                .map(ProfileFieldChangeAcknowledgement.map())
                .all();
    }

    /**
     * Find all acknowledgements for multiple changes at once.
     */
    public List<ProfileFieldChangeAcknowledgement> findAcknowledgementsForMember(int memberId) {
        return query("""
                SELECT %s
                FROM profile_field_change_acknowledgement ack
                JOIN profile_field_change c ON c.id = ack.change_id
                JOIN station_member sm ON sm.id = ack.acknowledged_by
                JOIN account a ON a.id = sm.account_id
                WHERE c.member_id = :member_id
                ORDER BY ack.acknowledged_at;""", ENRICHED_ACKNOWLEDGEMENT_COLUMNS)
                .single(call().bind("member_id", memberId))
                .map(ProfileFieldChangeAcknowledgement.map())
                .all();
    }

    /**
     * Acknowledge a change with optional comment.
     */
    public ProfileFieldChangeAcknowledgement acknowledge(int changeId, int acknowledgedBy, @Nullable String comment) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO profile_field_change_acknowledgement(change_id, acknowledged_by, comment)
                VALUES (:change_id, :acknowledged_by, :comment)
                ON CONFLICT (change_id, acknowledged_by) DO UPDATE SET
                    acknowledged_at = now(),
                    comment = coalesce(excluded.comment, profile_field_change_acknowledgement.comment)
                RETURNING id, change_id, acknowledged_by, acknowledged_at, comment,
                          '' AS acknowledged_by_name;""",
                call().bind("change_id", changeId)
                        .bind("acknowledged_by", acknowledgedBy)
                        .bind("comment", comment),
                ProfileFieldChangeAcknowledgement.map());
    }

    /**
     * Find members in a station that have unacknowledged changes, with count per member.
     */
    public List<MemberChangeSummary> findUnacknowledgedSummary(int stationId, int acknowledgedBy) {
        return query("""
                SELECT c.member_id,
                       %1$s AS member_name,
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
                GROUP BY c.member_id, %1$s
                ORDER BY latest_change DESC;""".formatted(MemberNameSql.ofAccount("ma")))
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
                SELECT %s
                FROM profile_field_change c
                JOIN station_member whose ON whose.id = c.member_id
                %s
                LEFT JOIN profile_field pf ON pf.id = c.field_id
                LEFT JOIN cluster_profile_field cpf ON cpf.id = c.cluster_field_id
                WHERE whose.station_id = :station_id
                ORDER BY c.changed_at DESC
                LIMIT :limit OFFSET :offset;""", ENRICHED_CHANGE_COLUMNS, AUTHOR_JOIN)
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
    /**
     * Lists the changes of the given members, newest first. Used where the caller may not see the
     * whole station: a guardian sees the members they manage and nobody else.
     *
     * @param memberIds the members whose changes may be seen
     * @param limit     page size
     * @param offset    page offset
     * @return the changes of those members
     */
    public List<ProfileFieldChange> findByMembers(List<Integer> memberIds, int limit, int offset) {
        if (memberIds.isEmpty()) return List.of();
        return query("""
                SELECT %s
                FROM profile_field_change c
                %s
                LEFT JOIN profile_field pf ON pf.id = c.field_id
                LEFT JOIN cluster_profile_field cpf ON cpf.id = c.cluster_field_id
                WHERE c.member_id = ANY(:member_ids)
                ORDER BY c.changed_at DESC
                LIMIT :limit OFFSET :offset;""", ENRICHED_CHANGE_COLUMNS, AUTHOR_JOIN)
                .single(call().bind("member_ids", memberIds, PostgreSqlTypes.INTEGER)
                        .bind("limit", limit)
                        .bind("offset", offset))
                .map(ProfileFieldChange.map())
                .all();
    }

    /**
     * Counts the changes of the given members.
     *
     * @param memberIds the members whose changes may be seen
     * @return the total change count for them
     */
    public int countByMembers(List<Integer> memberIds) {
        if (memberIds.isEmpty()) return 0;
        return SqlSupport.count("""
                SELECT count(*) AS cnt
                FROM profile_field_change
                WHERE member_id = ANY(:member_ids);""", call().bind("member_ids", memberIds, PostgreSqlTypes.INTEGER));
    }

    /**
     * Returns the member a change belongs to, so a caller can be checked against it before the
     * change is acknowledged.
     *
     * @param changeId the change identifier
     * @return the member the change was recorded for, empty if there is no such change
     */
    public Optional<Integer> findMemberOfChange(int changeId) {
        return query("SELECT member_id FROM profile_field_change WHERE id = :id;")
                .single(call().bind("id", changeId))
                .map(row -> row.getInt("member_id"))
                .first();
    }

    public int countByStation(int stationId) {
        return SqlSupport.count("""
                SELECT count(*) AS cnt
                FROM profile_field_change c
                JOIN station_member sm ON sm.id = c.member_id
                WHERE sm.station_id = :station_id;""", call().bind("station_id", stationId));
    }

    public int countPendingChanges(int stationId, int acknowledgedBy) {
        return SqlSupport.count("""
                SELECT count(*) AS cnt FROM profile_field_change c
                JOIN station_member sm ON sm.id = c.member_id
                WHERE sm.station_id = :station_id
                  AND c.requires_acknowledgement
                  AND NOT exists (
                      SELECT 1 FROM profile_field_change_acknowledgement ack
                      WHERE ack.change_id = c.id AND ack.acknowledged_by = :acknowledged_by
                  );""", call().bind("station_id", stationId).bind("acknowledged_by", acknowledgedBy));
    }
}
