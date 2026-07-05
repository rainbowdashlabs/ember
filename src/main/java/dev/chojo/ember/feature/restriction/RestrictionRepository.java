/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for unified restriction CRUD operations.
 * Manager bypass is handled in Java before calling DB functions.
 */
@Singleton
public class RestrictionRepository {

    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final UserTagRepository userTagRepository;

    @Inject
    public RestrictionRepository(
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            UserTagRepository userTagRepository) {
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.userTagRepository = userTagRepository;
    }

    public List<Restriction> findRestrictions(String table, String fkColumn, int entityId) {
        return query("SELECT * FROM %s WHERE %s = :entity_id ORDER BY id;", table, fkColumn)
                .single(call().bind("entity_id", entityId))
                .map(Restriction.map())
                .all();
    }

    /**
     * Replaces all restrictions for an entity.
     */
    public void setRestrictions(
            String table,
            String fkColumn,
            int entityId,
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds) {
        query("DELETE FROM %s WHERE %s = :entity_id;", table, fkColumn)
                .single(call().bind("entity_id", entityId))
                .delete();

        for (StationUserType userType : userTypes) {
            query("INSERT INTO %s (%s, user_type) VALUES (:entity_id, :user_type);", table, fkColumn)
                    .single(call().bind("entity_id", entityId).bind("user_type", userType))
                    .insert();
        }
        for (int groupId : groupIds) {
            query("INSERT INTO %s (%s, group_id) VALUES (:entity_id, :group_id);", table, fkColumn)
                    .single(call().bind("entity_id", entityId).bind("group_id", groupId))
                    .insert();
        }
        for (int tagId : tagIds) {
            query("INSERT INTO %s (%s, tag_id) VALUES (:entity_id, :tag_id);", table, fkColumn)
                    .single(call().bind("entity_id", entityId).bind("tag_id", tagId))
                    .insert();
        }
        for (int memberId : memberIds) {
            query("INSERT INTO %s (%s, member_id) VALUES (:entity_id, :member_id);", table, fkColumn)
                    .single(call().bind("entity_id", entityId).bind("member_id", memberId))
                    .insert();
        }
    }

    public RestrictionSet findRestrictionSet(String table, String fkColumn, int entityId, RestrictionMode mode) {
        return new RestrictionSet(findRestrictions(table, fkColumn, entityId), mode);
    }

    /**
     * Returns the set of member IDs from the given station that pass the restrictions for an entity,
     * including members with the manager permission for the entity type.
     */
    public Set<Integer> findMembersPassingRestriction(RestrictionType type, int entityId, int stationId) {
        var restrictions = findRestrictions(type.table(), type.fkColumn(), entityId);
        if (restrictions.isEmpty()) return Set.of();

        var groupIds = nonNullValues(restrictions, Restriction::groupId);
        var tagIds = nonNullValues(restrictions, Restriction::tagId);
        var userTypes = nonNullValues(restrictions, Restriction::userType);
        var memberIds = restrictions.stream()
                .map(Restriction::memberId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        if (!groupIds.isEmpty()) {
            for (int gid : groupIds) {
                memberGroupRepository.findMembers(gid).forEach(m -> memberIds.add(m.id()));
            }
        }
        if (!tagIds.isEmpty()) {
            for (int tid : tagIds) {
                userTagRepository.findMembers(tid).forEach(m -> memberIds.add(m.id()));
            }
        }
        if (!userTypes.isEmpty()) {
            stationMemberRepository.findByStation(stationId, false).stream()
                    .filter(m -> userTypes.contains(m.userType()))
                    .forEach(m -> memberIds.add(m.id()));
        }
        // Include managers who bypass restrictions
        stationMemberRepository
                .findMembersWithPermission(stationId, type.managerPermission())
                .forEach(m -> memberIds.add(m.id()));

        return memberIds;
    }

    /**
     * Collects the non-null results of applying an extractor to each restriction.
     *
     * @param extractor selects a nullable value from a restriction
     */
    private static <R> List<R> nonNullValues(List<Restriction> restrictions, Function<Restriction, R> extractor) {
        return restrictions.stream().map(extractor).filter(Objects::nonNull).toList();
    }

    /**
     * Checks if a member passes the restrictions for an entity.
     * Manager bypass is checked first in Java, then DB function handles matching.
     */
    public boolean checkRestriction(
            RestrictionType type, int entityId, int memberId, Set<StationPermission> memberPermissions) {
        // Manager bypass in Java
        if (memberPermissions.contains(type.managerPermission())) {
            return true;
        }

        // Resolve member identity for DB function
        StationMember member = stationMemberRepository.findById(memberId).orElse(null);
        if (member == null) return false;

        StationUserType userType = member.userType();
        List<Integer> groupIds = memberGroupRepository.findGroupsForMember(memberId).stream()
                .map(MemberGroup::id)
                .toList();
        List<Integer> tagIds = userTagRepository.findTagsForMember(memberId).stream()
                .map(UserTag::id)
                .toList();

        // Resolve mode from entity table
        String mode = query(
                        "SELECT restriction_mode FROM %s WHERE %s = :id;", type.entityTable(), type.entityIdColumn())
                .single(call().bind("id", entityId))
                .map(row -> row.getString("restriction_mode"))
                .first()
                .orElse("AND");

        return query(
                        "SELECT check_restriction(:rtable, :fk_column, :entity_id, :mode, :member_id, :user_type, :group_ids, :tag_ids) AS result;")
                .single(call().bind("rtable", type.table())
                        .bind("fk_column", type.fkColumn())
                        .bind("entity_id", entityId)
                        .bind("mode", mode)
                        .bind("member_id", memberId)
                        .bind("user_type", userType)
                        .bind("group_ids", groupIds, PostgreSqlTypes.INTEGER)
                        .bind("tag_ids", tagIds, PostgreSqlTypes.INTEGER))
                .map(row -> row.getBoolean("result"))
                .first()
                .orElse(true);
    }
}
