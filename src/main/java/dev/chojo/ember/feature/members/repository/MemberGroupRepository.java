/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.results.writing.insertion.InsertionResult;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for managing member groups, their memberships, and associated roles.
 */
@Singleton
public class MemberGroupRepository {

    /**
     * Finds a member group by its identifier.
     */
    public Optional<MemberGroup> findById(int id) {
        return query("SELECT * FROM member_group WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(MemberGroup.map())
                .first();
    }

    /**
     * Finds all member groups for a station.
     */
    public List<MemberGroup> findByStation(int stationId) {
        return query("SELECT * FROM member_group WHERE station_id = :station_id ORDER BY position DESC, name;")
                .single(Call.of().bind("station_id", stationId))
                .map(MemberGroup.map())
                .all();
    }

    /**
     * Creates a new member group for a station.
     */
    public MemberGroup create(int stationId, String name) {
        return query("""
                        INSERT INTO member_group(station_id, name, position)
                        VALUES(:station_id, :name, COALESCE((SELECT MAX(position) + 1 FROM member_group WHERE station_id = :station_id), 0))
                        RETURNING *;""")
                .single(Call.of().bind("station_id", stationId).bind("name", name))
                .map(MemberGroup.map())
                .first()
                .orElseThrow();
    }

    /**
     * Updates a member group's name, color, and position.
     */
    public boolean update(int id, String name, String color, int position) {
        return query("UPDATE member_group SET name = :name, color = :color, position = :position WHERE id = :id;")
                .single(Call.of()
                        .bind("name", name)
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
        return query("DELETE FROM member_group WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Group Entries --

    /**
     * Finds all members belonging to a group.
     */
    public List<StationMember> findMembers(int groupId) {
        return query("""
                SELECT
                    sm.*
                FROM
                    station_member sm
                        JOIN member_group_entry mge
                        ON sm.id = mge.member_id
                WHERE mge.group_id = :group_id AND NOT sm.former;""")
                .single(Call.of().bind("group_id", groupId))
                .map(StationMember.map())
                .all();
    }

    /**
     * Finds all groups that a member belongs to.
     */
    public List<MemberGroup> findGroupsForMember(int memberId) {
        return query("""
                SELECT mg.*
                FROM member_group mg
                JOIN member_group_entry mge ON mg.id = mge.group_id
                WHERE mge.member_id = :member_id
                ORDER BY mg.position DESC, mg.name;""")
                .single(Call.of().bind("member_id", memberId))
                .map(MemberGroup.map())
                .all();
    }

    /**
     * Adds a member to a group.
     */
    public InsertionResult addMember(int groupId, int memberId) {
        return query("INSERT INTO member_group_entry(group_id, member_id) VALUES(:group_id, :member_id);")
                .single(Call.of().bind("group_id", groupId).bind("member_id", memberId))
                .insert();
    }

    /**
     * Removes a member from a group.
     */
    public boolean removeMember(int groupId, int memberId) {
        return query("DELETE FROM member_group_entry WHERE group_id = :group_id AND member_id = :member_id;")
                .single(Call.of().bind("group_id", groupId).bind("member_id", memberId))
                .delete()
                .changed();
    }

    // -- Group Permissions --

    /**
     * Finds all permissions assigned to a group.
     */
    public List<Permission> findGroupPermissions(int groupId) {
        return query("""
                SELECT sp.id, sp.name
                FROM station_permission sp
                JOIN member_group_permission mgp ON sp.id = mgp.permission_id
                WHERE mgp.group_id = :group_id;""")
                .single(Call.of().bind("group_id", groupId))
                .map(Permission.map())
                .all();
    }

    /**
     * Assigns a permission to a group.
     */
    public InsertionResult addGroupPermission(int groupId, int permissionId) {
        return query(
                        "INSERT INTO member_group_permission(group_id, permission_id) VALUES(:group_id, :permission_id) ON CONFLICT DO NOTHING;")
                .single(Call.of().bind("group_id", groupId).bind("permission_id", permissionId))
                .insert();
    }

    /**
     * Removes a permission from a group.
     */
    public boolean removeGroupPermission(int groupId, int permissionId) {
        return query(
                        "DELETE FROM member_group_permission WHERE group_id = :group_id AND permission_id = :permission_id;")
                .single(Call.of().bind("group_id", groupId).bind("permission_id", permissionId))
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
                .single(Call.of().bind("member_id", memberId))
                .map(Permission.map())
                .all();
    }
}
