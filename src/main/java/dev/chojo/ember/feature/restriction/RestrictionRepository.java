/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

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
        return Query.query("SELECT * FROM " + table + " WHERE " + fkColumn + " = :entity_id ORDER BY id;")
                .single(Call.of().bind("entity_id", entityId))
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
            List<String> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds) {
        Query.query("DELETE FROM " + table + " WHERE " + fkColumn + " = :entity_id;")
                .single(Call.of().bind("entity_id", entityId))
                .delete();

        for (String userType : userTypes) {
            Query.query("INSERT INTO " + table + "(" + fkColumn + ", user_type) VALUES (:entity_id, :user_type);")
                    .single(Call.of().bind("entity_id", entityId).bind("user_type", userType))
                    .insert();
        }
        for (int groupId : groupIds) {
            Query.query("INSERT INTO " + table + "(" + fkColumn + ", group_id) VALUES (:entity_id, :group_id);")
                    .single(Call.of().bind("entity_id", entityId).bind("group_id", groupId))
                    .insert();
        }
        for (int tagId : tagIds) {
            Query.query("INSERT INTO " + table + "(" + fkColumn + ", tag_id) VALUES (:entity_id, :tag_id);")
                    .single(Call.of().bind("entity_id", entityId).bind("tag_id", tagId))
                    .insert();
        }
        for (int memberId : memberIds) {
            Query.query("INSERT INTO " + table + "(" + fkColumn + ", member_id) VALUES (:entity_id, :member_id);")
                    .single(Call.of().bind("entity_id", entityId).bind("member_id", memberId))
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

        var groupIds = restrictions.stream()
                .filter(r -> r.groupId() != null)
                .map(Restriction::groupId)
                .toList();
        var tagIds = restrictions.stream()
                .filter(r -> r.tagId() != null)
                .map(Restriction::tagId)
                .toList();
        var userTypes = restrictions.stream()
                .filter(r -> r.userType() != null)
                .map(Restriction::userType)
                .toList();
        var memberIds = restrictions.stream()
                .filter(r -> r.memberId() != null)
                .map(Restriction::memberId)
                .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));

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
                    .filter(m -> userTypes.contains(m.userType().name()))
                    .forEach(m -> memberIds.add(m.id()));
        }
        // Include managers who bypass restrictions
        stationMemberRepository
                .findMembersWithPermission(stationId, type.managerPermission())
                .forEach(m -> memberIds.add(m.id()));

        return memberIds;
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

        String userType = member.userType().name();
        List<Integer> groupIds = memberGroupRepository.findGroupsForMember(memberId).stream()
                .map(g -> g.id())
                .toList();
        List<Integer> tagIds = userTagRepository.findTagsForMember(memberId).stream()
                .map(t -> t.id())
                .toList();

        // Resolve mode from entity table
        String mode = Query.query("SELECT restriction_mode FROM " + type.entityTable() + " WHERE "
                        + type.entityIdColumn() + " = :id;")
                .single(Call.of().bind("id", entityId))
                .map(row -> row.getString("restriction_mode"))
                .first()
                .orElse("AND");

        return Query.query(
                        "SELECT check_restriction(:rtable, :fk_column, :entity_id, :mode, :member_id, :user_type, :group_ids, :tag_ids) AS result;")
                .single(Call.of()
                        .bind("rtable", type.table())
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
