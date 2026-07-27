/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for managing member groups, their memberships, and associated roles.
 */
@Singleton
public class MemberGroupRepository {
    private static final String MEMBER_GROUP_COLUMNS = "id, station_id, name, color, position";
    private static final String STATION_MEMBER_COLUMNS =
            "id, station_id, uid, account_id, former, former_at, display_name, user_type, join_date";

    /**
     * Finds a member group by its identifier.
     */
    public Optional<MemberGroup> findById(int id) {
        return SqlSupport.findById("member_group", MEMBER_GROUP_COLUMNS, id, MemberGroup.map());
    }

    /**
     * Finds all member groups for a station.
     */
    public List<MemberGroup> findByStation(int stationId) {
        return query("""
                SELECT %s FROM member_group WHERE station_id = :station_id ORDER BY position DESC, name;""", MEMBER_GROUP_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(MemberGroup.map())
                .all();
    }

    /**
     * Returns {@code true} if the station has at least one member group. Used by the setup
     * wizard's status endpoint to mark the "Member groups" step complete without materialising
     * the full list.
     */
    public boolean existsForStation(int stationId) {
        return SqlSupport.exists(
                "SELECT 1 FROM member_group WHERE station_id = :station_id LIMIT 1;",
                call().bind("station_id", stationId));
    }

    /**
     * Creates a new member group for a station.
     */
    public MemberGroup create(int stationId, String name) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO member_group(station_id, name, position)
                VALUES(:station_id, :name, coalesce((SELECT max(position) + 1 FROM member_group WHERE station_id = :station_id), 0))
                RETURNING %s;""", call().bind("station_id", stationId).bind("name", name), MemberGroup.map(), MEMBER_GROUP_COLUMNS);
    }

    /**
     * Updates a member group's name, color, and position.
     */
    public boolean update(int id, String name, String color, int position) {
        return query("UPDATE member_group SET name = :name, color = :color, position = :position WHERE id = :id;")
                .single(call().bind("name", name)
                        .bind("color", color)
                        .bind("position", position)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes a member group by its identifier.
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("member_group", id);
    }

    /**
     * Finds all members belonging to a group.
     */
    public List<StationMember> findMembers(int groupId) {
        return query("""
                SELECT
                    %s
                FROM
                    station_member sm
                        JOIN member_group_entry mge
                        ON sm.id = mge.member_id
                WHERE mge.group_id = :group_id AND NOT sm.former;""", SqlSupport.alias("sm", STATION_MEMBER_COLUMNS))
                .single(call().bind("group_id", groupId))
                .map(StationMember.map())
                .all();
    }

    /**
     * Finds all groups that a member belongs to.
     */
    public List<MemberGroup> findGroupsForMember(int memberId) {
        return query("""
                SELECT %s
                FROM member_group mg
                JOIN member_group_entry mge ON mg.id = mge.group_id
                WHERE mge.member_id = :member_id
                ORDER BY mg.position DESC, mg.name;""", SqlSupport.alias("mg", MEMBER_GROUP_COLUMNS))
                .single(call().bind("member_id", memberId))
                .map(MemberGroup.map())
                .all();
    }

    /**
     * Adds a member to a group.
     */
    public void addMember(int groupId, int memberId) {
        query("INSERT INTO member_group_entry(group_id, member_id) VALUES(:group_id, :member_id);")
                .single(call().bind("group_id", groupId).bind("member_id", memberId))
                .insert();
    }

    /**
     * Removes a member from a group.
     */
    public boolean removeMember(int groupId, int memberId) {
        return query("DELETE FROM member_group_entry WHERE group_id = :group_id AND member_id = :member_id;")
                .single(call().bind("group_id", groupId).bind("member_id", memberId))
                .delete()
                .changed();
    }

    /**
     * Finds all permissions assigned to a group.
     */
    public List<Permission> findGroupPermissions(int groupId) {
        return query("""
                SELECT sp.id, sp.name
                FROM station_permission sp
                JOIN member_group_permission mgp ON sp.id = mgp.permission_id
                WHERE mgp.group_id = :group_id;""")
                .single(call().bind("group_id", groupId))
                .map(Permission.map())
                .all();
    }

    /**
     * Assigns a permission to a group.
     */
    public void addGroupPermission(int groupId, int permissionId) {
        query(
                        "INSERT INTO member_group_permission(group_id, permission_id) VALUES(:group_id, :permission_id) ON CONFLICT DO NOTHING;")
                .single(call().bind("group_id", groupId).bind("permission_id", permissionId))
                .insert();
    }

    /**
     * Removes a permission from a group.
     */
    public void removeGroupPermission(int groupId, int permissionId) {
        query("DELETE FROM member_group_permission WHERE group_id = :group_id AND permission_id = :permission_id;")
                .single(call().bind("group_id", groupId).bind("permission_id", permissionId))
                .delete()
                .changed();
    }

    /**
     * Finds all distinct permissions a member has through their group memberships.
     */
    public List<Permission> findPermissionsForMemberViaGroups(int memberId) {
        return query("""
                SELECT DISTINCT sp.id, sp.name
                FROM station_permission sp
                JOIN member_group_permission mgp ON sp.id = mgp.permission_id
                JOIN member_group_entry mge ON mgp.group_id = mge.group_id
                WHERE mge.member_id = :member_id;""")
                .single(call().bind("member_id", memberId))
                .map(Permission.map())
                .all();
    }
}
