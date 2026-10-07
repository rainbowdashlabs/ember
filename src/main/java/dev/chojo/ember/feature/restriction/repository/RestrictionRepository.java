/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction.repository;

import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.restriction.Restriction;
import dev.chojo.ember.feature.restriction.RestrictionMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSql;
import dev.chojo.ember.feature.restriction.RestrictionType;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Data access for the unified restriction tables. Resolving the member identity and the manager
 * bypass happens in {@link dev.chojo.ember.feature.restriction.service.RestrictionService}.
 *
 * <p>A member's private tags never take part in a restriction. A restriction cannot be saved naming
 * one, and an audience kept inside some other configuration that names one lets nobody through,
 * because answering for it would show who carries it.
 */
@Singleton
public class RestrictionRepository {
    private static final String OPEN_TAG_IDS = """
            ARRAY(SELECT ute.tag_id FROM user_tag_entry ute JOIN user_tag ut ON ut.id = ute.tag_id
                  WHERE ute.member_id = sm.id AND ut.visibility <> 'PRIVATE')""";

    /**
     * Loads every restriction row of an entity, ordered by id.
     */
    public List<Restriction> findRestrictions(RestrictionType type, int entityId) {
        return RestrictionSql.findRows(type.table(), type.fkColumn(), entityId);
    }

    /**
     * Replaces all restrictions of an entity with the given selection.
     */
    public void setRestrictions(RestrictionType type, int entityId, RestrictionSelection selection) {
        RestrictionSql.replace(type.table(), type.fkColumn(), entityId, selection);
    }

    /**
     * Whether an entity carries any restriction at all.
     */
    public boolean hasRestrictions(RestrictionType type, int entityId) {
        return RestrictionSql.hasAny(type.table(), type.fkColumn(), entityId);
    }

    /**
     * Reads the restriction mode stored on the owning entity, falling back to
     * {@link RestrictionMode#AND} when no such entity exists.
     */
    public RestrictionMode findMode(RestrictionType type, int entityId) {
        return query(
                        "SELECT %s AS mode FROM %s WHERE %s = :id;",
                        type.modeColumn(), type.entityTable(), type.entityIdColumn())
                .single(call().bind("id", entityId))
                .map(row -> RestrictionMode.valueOf(row.getString("mode")))
                .first()
                .orElse(RestrictionMode.AND);
    }

    /**
     * The current members of a station who pass the restrictions of an entity, decided per member by
     * the same {@code check_restriction} database function as {@link #matches}, with no manager
     * bypass.
     *
     * @param type      the restricted entity type
     * @param entityId  the entity
     * @param mode      how the entity's restrictions combine
     * @param stationId the station whose members are tested
     * @return the IDs of the members who pass
     */
    public Set<Integer> findMatchingMembers(RestrictionType type, int entityId, RestrictionMode mode, int stationId) {
        return query("""
                SELECT sm.id
                FROM station_member sm
                WHERE sm.station_id = :station_id
                  AND sm.former = FALSE
                  AND check_restriction(
                        :rtable, :fk_column, :entity_id, :mode, sm.id, sm.user_type,
                        ARRAY(SELECT mge.group_id FROM member_group_entry mge WHERE mge.member_id = sm.id),
                        %s);""", OPEN_TAG_IDS)
                .single(call().bind("station_id", stationId)
                        .bind("rtable", type.table())
                        .bind("fk_column", type.fkColumn())
                        .bind("entity_id", entityId)
                        .bind("mode", mode.name()))
                .map(row -> row.getInt("id"))
                .all()
                .stream()
                .collect(Collectors.toSet());
    }

    /**
     * The current members of a station with the user type, groups and tags a restriction can name,
     * for deciding many members against one entity at once.
     *
     * @param stationId the station
     * @return one identity per current member
     */
    public List<RestrictionMember> findStationMembers(int stationId) {
        return query("""
                SELECT sm.id,
                       sm.user_type,
                       ARRAY(SELECT mge.group_id FROM member_group_entry mge WHERE mge.member_id = sm.id) AS group_ids,
                       %s AS tag_ids
                FROM station_member sm
                WHERE sm.station_id = :station_id
                  AND sm.former = FALSE;""", OPEN_TAG_IDS)
                .single(call().bind("station_id", stationId))
                .map(RestrictionRepository::identityOf)
                .all();
    }

    /**
     * What a restriction can name of several members, former ones included, in one read.
     *
     * @param memberIds the members
     * @return one identity per member that exists
     */
    public List<RestrictionMember> findMembers(Collection<Integer> memberIds) {
        if (memberIds.isEmpty()) return List.of();
        return query("""
                SELECT sm.id,
                       sm.user_type,
                       ARRAY(SELECT mge.group_id FROM member_group_entry mge WHERE mge.member_id = sm.id) AS group_ids,
                       %s AS tag_ids
                FROM station_member sm
                WHERE sm.id = ANY(:member_ids);""", OPEN_TAG_IDS)
                .single(call().bind("member_ids", List.copyOf(memberIds), PostgreSqlTypes.INTEGER))
                .map(RestrictionRepository::identityOf)
                .all();
    }

    private static RestrictionMember identityOf(Row row) throws SQLException {
        return new RestrictionMember(
                row.getInt("id"),
                row.getEnum("user_type", StationUserType.class),
                List.of((Integer[]) row.getArray("group_ids").getArray()),
                List.of((Integer[]) row.getArray("tag_ids").getArray()));
    }

    /**
     * Evaluates the entity's restrictions against a member identity through the
     * {@code check_restriction} database function. Entities without restrictions match everyone.
     */
    public boolean matches(RestrictionType type, int entityId, RestrictionMode mode, RestrictionMember member) {
        return query(
                        "SELECT check_restriction(:rtable, :fk_column, :entity_id, :mode, :member_id, :user_type, :group_ids, :tag_ids) AS result;")
                .single(call().bind("rtable", type.table())
                        .bind("fk_column", type.fkColumn())
                        .bind("entity_id", entityId)
                        .bind("mode", mode.name())
                        .bind("member_id", member.memberId())
                        .bind("user_type", member.userType())
                        .bind("group_ids", member.groupIds(), PostgreSqlTypes.INTEGER)
                        .bind("tag_ids", member.tagIds(), PostgreSqlTypes.INTEGER))
                .map(row -> row.getBoolean("result"))
                .first()
                .orElse(true);
    }
}
