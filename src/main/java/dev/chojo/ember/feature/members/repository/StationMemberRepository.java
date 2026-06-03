/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.api.results.writing.insertion.InsertionResult;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.api.roles.StationUserType;
import dev.chojo.ember.feature.members.entity.MemberCompletion;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.RichMember;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Repository for station members, their roles, manager relations, and avatars.
 */
@Singleton
public class StationMemberRepository {

    private final Cache<Integer, UUID> memberUidCache = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();
    private final Cache<MemberKey, Integer> memberIdCache = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();
    private final StationRepository stationRepository;

    @Inject
    public StationMemberRepository(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    /**
     * Resolves an internal member ID to its UUID. Cached.
     */
    public UUID resolveUid(int memberId) {
        return memberUidCache.get(memberId, id -> Query.query("SELECT uid FROM station_member WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(row -> row.get("uid", StandardValueConverter.UUID_STRING))
                .first()
                .orElse(null));
    }

    /**
     * Resolves a member UUID within a station to its internal ID. Cached.
     */
    public Optional<Integer> resolveId(int stationId, UUID memberUid) {
        var key = new MemberKey(stationId, memberUid);
        var cached = memberIdCache.getIfPresent(key);
        if (cached != null) return Optional.of(cached);
        return Query.query("SELECT id FROM station_member WHERE station_id = :station_id AND uid = :uid::uuid;")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("uid", memberUid, StandardValueConverter.UUID_STRING))
                .map(row -> row.getInt("id"))
                .first()
                .map(id -> {
                    memberIdCache.put(key, id);
                    memberUidCache.put(id, memberUid);
                    return id;
                });
    }

    /**
     * Resolves a member ID to a full MemberIdentity (station UID + member UID).
     */
    public MemberIdentity resolveIdentity(int memberId) {
        return Query.query(
                        "SELECT sm.uid AS member_uid, s.uid AS station_uid FROM station_member sm JOIN station s ON s.id = sm.station_id WHERE sm.id = :id;")
                .single(Call.of().bind("id", memberId))
                .map(row -> new MemberIdentity(
                        row.get("station_uid", StandardValueConverter.UUID_STRING),
                        row.get("member_uid", StandardValueConverter.UUID_STRING)))
                .first()
                .orElse(null);
    }

    /**
     * Invalidates caches for a member (on create/delete).
     */
    public void invalidateMemberCache(int memberId) {
        var uid = memberUidCache.getIfPresent(memberId);
        memberUidCache.invalidate(memberId);
        if (uid != null) {
            memberIdCache.asMap().values().remove(memberId);
        }
    }

    private record MemberKey(int stationId, UUID memberUid) {}

    // -- Members --

    /**
     * Finds a station member by its identifier.
     */
    public Optional<StationMember> findById(int id) {
        return Query.query("SELECT * FROM station_member WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(StationMember.map())
                .first();
    }

    /**
     * Finds a station member by its UUID within a station.
     */
    public Optional<StationMember> findByUid(int stationId, UUID uid) {
        return Query.query("SELECT * FROM station_member WHERE station_id = :station_id AND uid = :uid::uuid;")
                .single(Call.of().bind("station_id", stationId).bind("uid", uid, StandardValueConverter.UUID_STRING))
                .map(StationMember.map())
                .first();
    }

    /**
     * Finds all station memberships for an account across all stations.
     */
    public List<StationMember> findAllByAccountId(int accountId) {
        return Query.query("SELECT * FROM station_member WHERE account_id = :account_id;")
                .single(Call.of().bind("account_id", accountId))
                .map(StationMember.map())
                .all();
    }

    /**
     * Finds a member by their station and account combination.
     */
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

    /**
     * Finds members of a station, optionally including former members.
     *
     * @param stationId     the station identifier
     * @param includeFormer whether to include former members
     * @return the list of matching members
     */
    public List<StationMember> findByStation(int stationId, boolean includeFormer) {
        return Query.query(
                        "SELECT * FROM station_member WHERE station_id = :station_id AND (former = FALSE OR :include_former);")
                .single(Call.of().bind("station_id", stationId).bind("include_former", includeFormer))
                .map(StationMember.map())
                .all();
    }

    /**
     * Finds members of a station with all associated data (roles, groups, tags, profile values)
     * aggregated in a single query. Avoids N+1 queries when loading the member list.
     *
     * @param stationId     the station identifier
     * @param includeFormer whether to include former members
     * @return the list of rich members
     */
    public List<RichMember> findRichMembers(int stationId, boolean includeFormer) {
        return Query.query("""
                        SELECT sm.id, sm.station_id, sm.uid, sm.account_id, sm.former, sm.user_type,
                               COALESCE(NULLIF(sm.display_name, ''), TRIM(CONCAT(a.first_name, ' ', a.last_name)), '') AS name,
                               COALESCE(a.email, '') AS email,
                               COALESCE((SELECT json_agg(sp.name) FROM station_member_permission smp JOIN station_permission sp ON sp.id = smp.permission_id WHERE smp.member_id = sm.id), '[]'::json)::text AS roles,
                               COALESCE((SELECT json_agg(json_build_object('id', mg.id, 'name', mg.name)) FROM member_group_entry mge JOIN member_group mg ON mg.id = mge.group_id WHERE mge.member_id = sm.id), '[]'::json)::text AS groups,
                               COALESCE((SELECT json_agg(json_build_object('id', ut.id, 'name', ut.name)) FROM user_tag_entry ute JOIN user_tag ut ON ut.id = ute.tag_id WHERE ute.member_id = sm.id), '[]'::json)::text AS tags,
                               COALESCE((SELECT json_object_agg(pfv.field_id, pfv.value) FROM profile_field_value pfv WHERE pfv.member_id = sm.id), '{}'::json)::text AS profile_values
                        FROM station_member sm
                        LEFT JOIN account a ON a.id = sm.account_id
                        WHERE sm.station_id = :station_id AND (sm.former = FALSE OR :include_former)
                        ORDER BY a.last_name, a.first_name, sm.display_name;""")
                .single(Call.of().bind("station_id", stationId).bind("include_former", includeFormer))
                .map(RichMember.map())
                .all();
    }

    /**
     * Finds active members of a station for autocomplete, returning only id and display name.
     *
     * @param stationId the station identifier
     * @return list of member completion entries
     */
    public List<MemberCompletion> findCompletions(int stationId) {
        UUID stationUid = stationRepository.resolveUid(stationId);
        return Query.query(
                        "SELECT sm.id, sm.uid, COALESCE(NULLIF(sm.display_name, ''), TRIM(CONCAT(a.first_name, ' ', a.last_name)), 'Mitglied ' || sm.id) AS display_name FROM station_member sm LEFT JOIN account a ON sm.account_id = a.id WHERE sm.station_id = :station_id AND sm.former = FALSE ORDER BY display_name;")
                .single(Call.of().bind("station_id", stationId))
                .map(row -> new MemberCompletion(
                        row.getInt("id"),
                        row.getString("display_name"),
                        stationUid,
                        row.get("uid", StandardValueConverter.UUID_STRING),
                        null,
                        null,
                        null))
                .all();
    }

    /**
     * Find former members of a station.
     */
    public List<StationMember> findFormerByStation(int stationId) {
        return Query.query("SELECT * FROM station_member WHERE station_id = :station_id AND former = TRUE;")
                .single(Call.of().bind("station_id", stationId))
                .map(StationMember.map())
                .all();
    }

    /**
     * Finds active members of a station that have a specific user type.
     */
    public List<StationMember> findByStationAndUserType(int stationId, StationUserType userType) {
        return Query.query(
                        "SELECT * FROM station_member WHERE station_id = :station_id AND user_type = :user_type AND former = FALSE;")
                .single(Call.of().bind("station_id", stationId).bind("user_type", userType))
                .map(StationMember.map())
                .all();
    }

    /**
     * Finds all station memberships for an account.
     */
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

    // -- Permissions --

    public List<Permission> findAllPermissions() {
        return Query.query("SELECT id, name FROM station_permission ORDER BY id;")
                .single()
                .map(Permission.map())
                .all();
    }

    public boolean hasLoginPermission(int accountId) {
        return Query.query("""
                            SELECT 1
                            FROM station_member sm
                            WHERE sm.account_id = :account_id
                              AND sm.former = FALSE
                              AND (
                                sm.user_type IN ('GUARDIAN', 'TEAM', 'MANAGER')
                                OR EXISTS (
                                    SELECT 1 FROM station_member_permission smp
                                    JOIN station_permission sp ON sp.id = smp.permission_id
                                    WHERE smp.member_id = sm.id AND sp.name = 'LOGIN'
                                )
                              )
                            LIMIT 1;""")
                .single(Call.of().bind("account_id", accountId))
                .map(row -> true)
                .first()
                .isPresent();
    }

    public List<Permission> findPermissions(int memberId) {
        return Query.query("""
                            SELECT sp.id, sp.name
                            FROM station_permission sp JOIN station_member_permission smp ON sp.id = smp.permission_id
                            WHERE smp.member_id = :member_id;""")
                .single(Call.of().bind("member_id", memberId))
                .map(Permission.map())
                .all();
    }

    public Optional<Permission> findPermissionByName(StationPermission permission) {
        return Query.query("SELECT id, name FROM station_permission WHERE name = :name;")
                .single(Call.of().bind("name", permission))
                .map(Permission.map())
                .first();
    }

    public InsertionResult grantPermission(int memberId, int permissionId) {
        return Query.query(
                        "INSERT INTO station_member_permission(member_id, permission_id) VALUES(:member_id, :permission_id) ON CONFLICT DO NOTHING;")
                .single(Call.of().bind("member_id", memberId).bind("permission_id", permissionId))
                .insert();
    }

    public boolean revokePermission(int memberId, int permissionId) {
        return Query.query(
                        "DELETE FROM station_member_permission WHERE member_id = :member_id AND permission_id = :permission_id;")
                .single(Call.of().bind("member_id", memberId).bind("permission_id", permissionId))
                .delete()
                .changed();
    }

    public void revokeAllPermissions(int memberId) {
        Query.query("DELETE FROM station_member_permission WHERE member_id = :member_id;")
                .single(Call.of().bind("member_id", memberId))
                .delete();
    }

    /**
     * Find all active members of a station who have the given permission (directly or via group).
     */
    public List<StationMember> findMembersWithPermission(int stationId, StationPermission permission) {
        return Query.query("""
                            SELECT DISTINCT sm.* FROM station_member sm
                            WHERE sm.station_id = :station_id AND sm.former = FALSE
                              AND (
                                EXISTS (
                                    SELECT 1 FROM station_member_permission smp
                                    JOIN station_permission sp ON sp.id = smp.permission_id
                                    WHERE smp.member_id = sm.id AND sp.name = :permission_name
                                )
                                OR EXISTS (
                                    SELECT 1 FROM member_group_entry mge
                                    JOIN member_group_permission mgp ON mgp.group_id = mge.group_id
                                    JOIN station_permission sp ON sp.id = mgp.permission_id
                                    WHERE mge.member_id = sm.id AND sp.name = :permission_name
                                )
                              );""")
                .single(Call.of().bind("station_id", stationId).bind("permission_name", permission))
                .map(StationMember.map())
                .all();
    }

    // -- User Type --

    public boolean setUserType(int memberId, StationUserType userType) {
        return Query.query("UPDATE station_member SET user_type = :user_type WHERE id = :id;")
                .single(Call.of().bind("user_type", userType).bind("id", memberId))
                .update()
                .changed();
    }

    // -- Station User Type Permissions --

    public List<Permission> findUserTypePermissions(int stationId, StationUserType userType) {
        return Query.query("""
                SELECT sp.id, sp.name
                FROM station_permission sp
                JOIN station_user_type_permission sutp ON sp.id = sutp.permission_id
                WHERE sutp.station_id = :station_id AND sutp.user_type = :user_type;""")
                .single(Call.of().bind("station_id", stationId).bind("user_type", userType))
                .map(Permission.map())
                .all();
    }

    public void setUserTypePermissions(int stationId, StationUserType userType, List<Integer> permissionIds) {
        Query.query(
                        "DELETE FROM station_user_type_permission WHERE station_id = :station_id AND user_type = :user_type;")
                .single(Call.of().bind("station_id", stationId).bind("user_type", userType))
                .delete();
        for (int permissionId : permissionIds) {
            Query.query(
                            "INSERT INTO station_user_type_permission(station_id, user_type, permission_id) VALUES(:station_id, :user_type, :permission_id) ON CONFLICT DO NOTHING;")
                    .single(Call.of()
                            .bind("station_id", stationId)
                            .bind("user_type", userType)
                            .bind("permission_id", permissionId))
                    .insert();
        }
    }

    // -- Manager Relations --

    public List<StationMember> findManaged(int managerId) {
        return Query.query("""
                            SELECT sm.* FROM station_member sm
                            JOIN member_manager mm ON sm.id = mm.managed_id
                            WHERE mm.manager_id = :manager_id AND sm.former = FALSE;""")
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
