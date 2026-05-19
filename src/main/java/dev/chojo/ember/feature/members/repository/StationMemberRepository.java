/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.api.results.writing.insertion.InsertionResult;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.StationMember;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class StationMemberRepository {

    // -- Members --

    public Optional<StationMember> findById(int id) {
        return Query.query("SELECT * FROM station_member WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(StationMember.map())
                .first();
    }

    public List<StationMember> findAllByAccountId(int accountId) {
        return Query.query("SELECT * FROM station_member WHERE account_id = :account_id;")
                .single(Call.of().bind("account_id", accountId))
                .map(StationMember.map())
                .all();
    }

    public Optional<StationMember> findByStationAndAccount(int stationId, int accountId) {
        return Query.query("SELECT * FROM station_member WHERE station_id = :station_id AND account_id = :account_id;")
                .single(Call.of().bind("station_id", stationId).bind("account_id", accountId))
                .map(StationMember.map())
                .first();
    }

    /**
     * Find active (non-former) members of a station.
     */
    public List<StationMember> findByStation(int stationId) {
        return findByStation(stationId, false);
    }

    public List<StationMember> findByStation(int stationId, boolean includeFormer) {
        return Query.query(
                        "SELECT * FROM station_member WHERE station_id = :station_id AND (former = false OR :include_former);")
                .single(Call.of().bind("station_id", stationId).bind("include_former", includeFormer))
                .map(StationMember.map())
                .all();
    }

    /**
     * Find former members of a station.
     */
    public List<StationMember> findFormerByStation(int stationId) {
        return Query.query("SELECT * FROM station_member WHERE station_id = :station_id AND former = true;")
                .single(Call.of().bind("station_id", stationId))
                .map(StationMember.map())
                .all();
    }

    public List<StationMember> findByStationAndRole(int stationId, String roleName) {
        return Query.query(
                        "SELECT sm.* FROM station_member sm JOIN station_member_role smr ON smr.member_id = sm.id JOIN role r ON r.id = smr.role_id WHERE sm.station_id = :station_id AND LOWER(r.name) = LOWER(:role) AND sm.former = false;")
                .single(Call.of().bind("station_id", stationId).bind("role", roleName))
                .map(StationMember.map())
                .all();
    }

    public List<StationMember> findByAccount(int accountId) {
        return Query.query("SELECT * FROM station_member WHERE account_id = :account_id;")
                .single(Call.of().bind("account_id", accountId))
                .map(StationMember.map())
                .all();
    }

    public StationMember create(int stationId, int accountId) {
        return Query.query(
                        "INSERT INTO station_member(station_id, account_id) VALUES(:station_id, :account_id) RETURNING *;")
                .single(Call.of().bind("station_id", stationId).bind("account_id", accountId))
                .map(StationMember.map())
                .first()
                .orElseThrow();
    }

    public boolean delete(int id) {
        return Query.query("DELETE FROM station_member WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public boolean setFormer(int id, boolean former) {
        return Query.query("UPDATE station_member SET former = :former WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("former", former))
                .update()
                .changed();
    }

    public void setDisplayNameAndClearAccount(int id, String displayName) {
        Query.query("UPDATE station_member SET display_name = :display_name, account_id = NULL WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("display_name", displayName))
                .update();
    }

    // -- Roles --

    public List<Role> findAllRoles() {
        return Query.query("SELECT id, name FROM role ORDER BY id;")
                .single()
                .map(Role.map())
                .all();
    }

    public boolean hasLoginRole(int accountId) {
        return Query.query("""
                            SELECT 1
                            FROM station_member sm
                                JOIN station_member_role smr ON sm.id = smr.member_id
                                JOIN role r ON r.id = smr.role_id
                            WHERE sm.account_id = :account_id
                              AND sm.former = false
                              AND r.name IN ('LOGIN', 'MANAGER')
                            LIMIT 1;""")
                .single(Call.of().bind("account_id", accountId))
                .map(row -> true)
                .first()
                .isPresent();
    }

    public List<Role> findRoles(int memberId) {
        return Query.query("""
                            SELECT r.id, r.name
                            FROM role r JOIN station_member_role smr ON r.id = smr.role_id
                            WHERE smr.member_id = :member_id;""")
                .single(Call.of().bind("member_id", memberId))
                .map(Role.map())
                .all();
    }

    public Optional<Role> findRoleByName(Roles role) {
        return Query.query("SELECT id, name FROM role WHERE name = :name;")
                .single(Call.of().bind("name", role))
                .map(Role.map())
                .first();
    }

    public InsertionResult addRole(int memberId, int roleId) {
        return Query.query("INSERT INTO station_member_role(member_id, role_id) VALUES(:member_id, :role_id);")
                .single(Call.of().bind("member_id", memberId).bind("role_id", roleId))
                .insert();
    }

    public boolean removeRole(int memberId, int roleId) {
        return Query.query("DELETE FROM station_member_role WHERE member_id = :member_id AND role_id = :role_id;")
                .single(Call.of().bind("member_id", memberId).bind("role_id", roleId))
                .delete()
                .changed();
    }

    public void removeAllRoles(int memberId) {
        Query.query("DELETE FROM station_member_role WHERE member_id = :member_id;")
                .single(Call.of().bind("member_id", memberId))
                .delete();
    }

    /**
     * Find all active members of a station who have the given role (directly or via group).
     */
    public List<StationMember> findMembersWithRole(int stationId, Roles role) {
        return Query.query("""
                            SELECT DISTINCT sm.* FROM station_member sm
                            WHERE sm.station_id = :station_id AND sm.former = false
                              AND (
                                EXISTS (
                                    SELECT 1 FROM station_member_role smr
                                    JOIN role r ON r.id = smr.role_id
                                    WHERE smr.member_id = sm.id AND r.name = :role_name
                                )
                                OR EXISTS (
                                    SELECT 1 FROM member_group_entry mge
                                    JOIN member_group_role mgr ON mgr.group_id = mge.group_id
                                    JOIN role r ON r.id = mgr.role_id
                                    WHERE mge.member_id = sm.id AND r.name = :role_name
                                )
                              );""")
                .single(Call.of().bind("station_id", stationId).bind("role_name", role))
                .map(StationMember.map())
                .all();
    }

    // -- Manager Relations --

    public List<StationMember> findManaged(int managerId) {
        return Query.query("""
                            SELECT sm.* FROM station_member sm
                            JOIN member_manager mm ON sm.id = mm.managed_id
                            WHERE mm.manager_id = :manager_id AND sm.former = false;""")
                .single(Call.of().bind("manager_id", managerId))
                .map(StationMember.map())
                .all();
    }

    public List<StationMember> findManagers(int managedId) {
        return Query.query("""
                            SELECT sm.* FROM station_member sm
                            JOIN member_manager mm ON sm.id = mm.manager_id
                            WHERE mm.managed_id = :managed_id;""")
                .single(Call.of().bind("managed_id", managedId))
                .map(StationMember.map())
                .all();
    }

    public InsertionResult addManager(int managerId, int managedId) {
        return Query.query("INSERT INTO member_manager(manager_id, managed_id) VALUES(:manager_id, :managed_id);")
                .single(Call.of().bind("manager_id", managerId).bind("managed_id", managedId))
                .insert();
    }

    public boolean removeManager(int managerId, int managedId) {
        return Query.query("DELETE FROM member_manager WHERE manager_id = :manager_id AND managed_id = :managed_id;")
                .single(Call.of().bind("manager_id", managerId).bind("managed_id", managedId))
                .delete()
                .changed();
    }

    public void removeAllManagers(int managedId) {
        Query.query("DELETE FROM member_manager WHERE managed_id = :managed_id;")
                .single(Call.of().bind("managed_id", managedId))
                .delete();
    }

    public void removeAllManaged(int managerId) {
        Query.query("DELETE FROM member_manager WHERE manager_id = :manager_id;")
                .single(Call.of().bind("manager_id", managerId))
                .delete();
    }
}
