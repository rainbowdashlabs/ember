/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.results.writing.insertion.InsertionResult;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Role;
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
                WHERE mge.group_id = :group_id;""")
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

    // -- Group Roles --

    /**
     * Finds all roles assigned to a group.
     */
    public List<Role> findGroupRoles(int groupId) {
        return query("""
                SELECT
                    r.id,
                    r.name
                FROM
                    role r
                        JOIN member_group_role mgr
                        ON r.id = mgr.role_id
                WHERE mgr.group_id = :group_id;""")
                .single(Call.of().bind("group_id", groupId))
                .map(Role.map())
                .all();
    }

    /**
     * Assigns a role to a group.
     */
    public InsertionResult addGroupRole(int groupId, int roleId) {
        return query("INSERT INTO member_group_role(group_id, role_id) VALUES(:group_id, :role_id);")
                .single(Call.of().bind("group_id", groupId).bind("role_id", roleId))
                .insert();
    }

    /**
     * Removes a role from a group.
     */
    public boolean removeGroupRole(int groupId, int roleId) {
        return query("DELETE FROM member_group_role WHERE group_id = :group_id AND role_id = :role_id;")
                .single(Call.of().bind("group_id", groupId).bind("role_id", roleId))
                .delete()
                .changed();
    }

    /**
     * Finds all distinct roles a member has through their group memberships.
     */
    public List<Role> findRolesForMemberViaGroups(int memberId) {
        return query("""
                SELECT DISTINCT
                    r.id,
                    r.name
                FROM
                    role r
                        JOIN member_group_role mgr ON r.id = mgr.role_id
                        JOIN member_group_entry mge ON mgr.group_id = mge.group_id
                WHERE mge.member_id = :member_id;""")
                .single(Call.of().bind("member_id", memberId))
                .map(Role.map())
                .all();
    }
}
