/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.MemberCompletion;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.RichMember;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for station members, their roles, manager relations, and avatars.
 */
@Singleton
public class StationMemberRepository {
    private static final String STATION_MEMBER_COLUMNS =
            "id, station_id, uid, account_id, former, former_at, display_name, user_type, join_date";

    private static final String PRIMARY_TAG_NAME_SUBQUERY = """
            (SELECT ut.name FROM user_tag_entry ute JOIN user_tag ut ON ut.id = ute.tag_id
             WHERE ute.member_id = sm.id AND ut.visible = TRUE
             ORDER BY ut.position DESC LIMIT 1)""";
    private static final String PRIMARY_TAG_COLOR_SUBQUERY = """
            (SELECT ut.color FROM user_tag_entry ute JOIN user_tag ut ON ut.id = ute.tag_id
             WHERE ute.member_id = sm.id AND ut.visible = TRUE
             ORDER BY ut.position DESC LIMIT 1)""";
    private static final String PICKER_MEMBER_COLUMNS =
            """
            sm.uid AS member_uid,
            a.uid AS account_uid,
            coalesce(a.full_name, sm.display_name, 'Mitglied ' || sm.id) AS display_name,
            sm.user_type,
            %s AS display_tag,
            %s AS display_tag_color,
            sm.join_date AS join_date""".formatted(PRIMARY_TAG_NAME_SUBQUERY, PRIMARY_TAG_COLOR_SUBQUERY);
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
        return memberUidCache.get(memberId, id -> query("SELECT uid FROM station_member WHERE id = :id;")
                .single(call().bind("id", id))
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
        return query("SELECT id FROM station_member WHERE station_id = :station_id AND uid = :uid::UUID;")
                .single(call().bind("station_id", stationId).bind("uid", memberUid, StandardValueConverter.UUID_STRING))
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
        return query(
                        "SELECT sm.uid AS member_uid, s.uid AS station_uid FROM station_member sm JOIN station s ON s.id = sm.station_id WHERE sm.id = :id;")
                .single(call().bind("id", memberId))
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

    /**
     * Finds a station member by its identifier.
     */
    public Optional<StationMember> findById(int id) {
        return SqlSupport.findById("station_member", STATION_MEMBER_COLUMNS, id, StationMember.map());
    }

    /**
     * Finds a station member by its UUID within a station.
     */
    public Optional<StationMember> findByUid(int stationId, UUID uid) {
        return query("""
                SELECT %s FROM station_member WHERE station_id = :station_id AND uid = :uid::uuid;""", STATION_MEMBER_COLUMNS)
                .single(call().bind("station_id", stationId).bind("uid", uid, StandardValueConverter.UUID_STRING))
                .map(StationMember.map())
                .first();
    }

    /**
     * Finds all station memberships for an account across all stations.
     */
    public List<StationMember> findAllByAccountId(int accountId) {
        return query("""
                SELECT %s FROM station_member WHERE account_id = :account_id;""", STATION_MEMBER_COLUMNS)
                .single(call().bind("account_id", accountId))
                .map(StationMember.map())
                .all();
    }

    /**
     * Finds a member by their station and account combination.
     */
    public Optional<StationMember> findByStationAndAccount(int stationId, int accountId) {
        return query("""
                SELECT %s FROM station_member WHERE station_id = :station_id AND account_id = :account_id;""", STATION_MEMBER_COLUMNS)
                .single(call().bind("station_id", stationId).bind("account_id", accountId))
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
        return query("""
                SELECT %s FROM station_member WHERE station_id = :station_id AND (former = FALSE OR :include_former);""", STATION_MEMBER_COLUMNS)
                .single(call().bind("station_id", stationId).bind("include_former", includeFormer))
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
        return query("""
                SELECT sm.id, sm.station_id, sm.uid, sm.account_id, sm.former, sm.user_type, sm.join_date,
                       coalesce(a.full_name, sm.display_name, '') AS name,
                       coalesce(a.email, '') AS email,
                       (a.id IS NOT NULL AND a.setup_completed_at IS NULL) AS account_setup_pending,
                       (SELECT max(at.expires_at) FROM account_token at WHERE at.account_id = a.id AND at.token_type = 'SET_PASSWORD') AS setup_mail_expires_at,
                       coalesce((SELECT json_agg(sp.name) FROM station_member_permission smp JOIN station_permission sp ON sp.id = smp.permission_id WHERE smp.member_id = sm.id), '[]'::JSON)::TEXT AS roles,
                       coalesce((SELECT json_agg(json_build_object('id', mg.id, 'name', mg.name)) FROM member_group_entry mge JOIN member_group mg ON mg.id = mge.group_id WHERE mge.member_id = sm.id), '[]'::JSON)::TEXT AS groups,
                       coalesce((SELECT json_agg(json_build_object('id', ut.id, 'name', ut.name)) FROM user_tag_entry ute JOIN user_tag ut ON ut.id = ute.tag_id WHERE ute.member_id = sm.id), '[]'::JSON)::TEXT AS tags,
                       coalesce((SELECT json_object_agg(pfv.field_id, pfv.value) FROM profile_field_value pfv WHERE pfv.member_id = sm.id), '{}'::JSON)::TEXT AS profile_values
                FROM station_member sm
                LEFT JOIN account a ON a.id = sm.account_id
                WHERE sm.station_id = :station_id AND (sm.former = FALSE OR :include_former)
                ORDER BY a.last_name, a.first_name, sm.display_name;""")
                .single(call().bind("station_id", stationId).bind("include_former", includeFormer))
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
        return query(
                        "SELECT sm.id, sm.uid, coalesce(a.full_name, sm.display_name, 'Mitglied ' || sm.id) AS display_name FROM station_member sm LEFT JOIN account a ON sm.account_id = a.id WHERE sm.station_id = :station_id AND sm.former = FALSE ORDER BY display_name;")
                .single(call().bind("station_id", stationId))
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
     * Page-editor picker: case-insensitive substring search on the resolved display name with a
     * small cap. Empty {@code search} returns the most recently joined active members so the
     * picker has something to show on first focus. Returns active members only (former excluded).
     */
    public List<PickerMember> searchForPicker(int stationId, String search, int limit) {
        boolean hasSearch = search != null && !search.isBlank();
        String predicate = hasSearch ? "AND LOWER(COALESCE(a.full_name, sm.display_name, '')) LIKE :q" : "";
        String order = hasSearch ? "display_name" : "sm.join_date DESC";
        var c = call().bind("station_id", stationId).bind("limit", limit);
        if (hasSearch) {
            c = c.bind("q", "%" + search.trim().toLowerCase() + "%");
        }
        return query("""
                SELECT %s
                FROM station_member sm
                LEFT JOIN account a ON sm.account_id = a.id
                WHERE sm.station_id = :station_id AND sm.former = FALSE
                  %s
                ORDER BY %s
                LIMIT :limit;""", PICKER_MEMBER_COLUMNS, predicate, order)
                .single(c)
                .map(PickerMember.map())
                .all();
    }

    /**
     * Single-lookup variant of {@link #searchForPicker} used to resolve a stored {@code memberUid}
     * back to its picker shape. Scoped to the same station and active-only predicate so editors
     * cannot accidentally reveal former-member rows by guessing a UUID.
     */
    public Optional<PickerMember> findPickerByUid(int stationId, UUID memberUid) {
        return query("""
                SELECT %s
                FROM station_member sm
                LEFT JOIN account a ON sm.account_id = a.id
                WHERE sm.station_id = :station_id
                  AND sm.uid = :uid::UUID
                  AND sm.former = FALSE;""", PICKER_MEMBER_COLUMNS)
                .single(call().bind("station_id", stationId).bind("uid", memberUid, StandardValueConverter.UUID_STRING))
                .map(PickerMember.map())
                .first();
    }

    /**
     * Active members of the given group, ordered by the membership's stored sort order.
     */
    public List<PickerMember> findOfficersByGroup(int stationId, int groupId) {
        return query("""
                SELECT %s
                FROM station_member sm
                LEFT JOIN account a ON sm.account_id = a.id
                JOIN member_group_entry mge ON mge.member_id = sm.id
                WHERE sm.station_id = :station_id
                  AND sm.former = FALSE
                  AND mge.group_id = :group_id
                ORDER BY display_name;""", PICKER_MEMBER_COLUMNS)
                .single(call().bind("station_id", stationId).bind("group_id", groupId))
                .map(PickerMember.map())
                .all();
    }

    /**
     * Active members carrying the given tag, ordered alphabetically.
     */
    public List<PickerMember> findOfficersByTag(int stationId, int tagId) {
        return query("""
                SELECT %s
                FROM station_member sm
                LEFT JOIN account a ON sm.account_id = a.id
                JOIN user_tag_entry ute ON ute.member_id = sm.id
                WHERE sm.station_id = :station_id
                  AND sm.former = FALSE
                  AND ute.tag_id = :tag_id
                ORDER BY display_name;""", PICKER_MEMBER_COLUMNS)
                .single(call().bind("station_id", stationId).bind("tag_id", tagId))
                .map(PickerMember.map())
                .all();
    }

    /**
     * Active members for an explicit list of UUIDs (manual officers source).
     */
    public List<PickerMember> findOfficersByUids(int stationId, List<UUID> memberUids) {
        if (memberUids == null || memberUids.isEmpty()) return List.of();
        var uidStrings = memberUids.stream().map(UUID::toString).toList();
        return query("""
                SELECT %s
                FROM station_member sm
                LEFT JOIN account a ON sm.account_id = a.id
                WHERE sm.station_id = :station_id
                  AND sm.former = FALSE
                  AND sm.uid::TEXT = ANY(:uids);""", PICKER_MEMBER_COLUMNS)
                .single(call().bind("station_id", stationId).bind("uids", uidStrings, PostgreSqlTypes.VARCHAR))
                .map(PickerMember.map())
                .all();
    }

    /**
     * Find former members of a station.
     */
    public List<StationMember> findFormerByStation(int stationId) {
        return query("""
                SELECT %s FROM station_member WHERE station_id = :station_id AND former;""", STATION_MEMBER_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(StationMember.map())
                .all();
    }

    /**
     * Finds active members of a station that have a specific user type.
     */
    public List<StationMember> findByStationAndUserType(int stationId, StationUserType userType) {
        return query("""
                SELECT %s FROM station_member WHERE station_id = :station_id AND user_type = :user_type AND NOT former;""", STATION_MEMBER_COLUMNS)
                .single(call().bind("station_id", stationId).bind("user_type", userType))
                .map(StationMember.map())
                .all();
    }

    /**
     * Finds all station memberships for an account.
     */
    public List<StationMember> findByAccount(int accountId) {
        return query("""
                SELECT %s FROM station_member WHERE account_id = :account_id;""", STATION_MEMBER_COLUMNS)
                .single(call().bind("account_id", accountId))
                .map(StationMember.map())
                .all();
    }

    public StationMember create(int stationId, int accountId) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO station_member(station_id, account_id) VALUES(:station_id, :account_id)
                RETURNING %s;""".formatted(STATION_MEMBER_COLUMNS),
                call().bind("station_id", stationId).bind("account_id", accountId),
                StationMember.map());
    }

    /**
     * Replaces the {@code uid} column for the member. Used by the demo seeder to pin
     * deterministic UUIDs so demo media does not accumulate on disk across restarts.
     */
    public void setUid(int id, UUID uid) {
        query("UPDATE station_member SET uid = :uid::uuid WHERE id = :id;")
                .single(call().bind("uid", uid, StandardValueConverter.UUID_STRING)
                        .bind("id", id))
                .update();
        invalidateMemberCache(id);
    }

    public boolean delete(int id) {
        return SqlSupport.deleteById("station_member", id);
    }

    public void setFormer(int id, boolean former) {
        query(
                        "UPDATE station_member SET former = :former, former_at = CASE WHEN :former THEN now() ELSE NULL END WHERE id = :id;")
                .single(call().bind("id", id).bind("former", former))
                .update()
                .changed();
    }

    public void setDisplayNameAndClearAccount(int id, String displayName) {
        query("UPDATE station_member SET display_name = :display_name, account_id = NULL WHERE id = :id;")
                .single(call().bind("id", id).bind("display_name", displayName))
                .update();
    }

    public List<Permission> findAllPermissions() {
        return query("SELECT id, name FROM station_permission ORDER BY id;")
                .single()
                .map(Permission.map())
                .all();
    }

    public boolean hasLoginPermission(int accountId) {
        return SqlSupport.exists("""
                SELECT 1
                FROM station_member sm
                WHERE sm.account_id = :account_id
                  AND sm.former = FALSE
                  AND (
                    sm.user_type IN ('GUARDIAN', 'TEAM', 'MANAGER')
                    OR exists (
                        SELECT 1 FROM station_member_permission smp
                        JOIN station_permission sp ON sp.id = smp.permission_id
                        WHERE smp.member_id = sm.id AND sp.name = 'LOGIN'
                    )
                  )
                LIMIT 1;""", call().bind("account_id", accountId));
    }

    public List<Permission> findPermissions(int memberId) {
        return query("""
                SELECT sp.id, sp.name
                FROM station_permission sp JOIN station_member_permission smp ON sp.id = smp.permission_id
                WHERE smp.member_id = :member_id;""")
                .single(call().bind("member_id", memberId))
                .map(Permission.map())
                .all();
    }

    public Optional<Permission> findPermissionByName(StationPermission permission) {
        return query("SELECT id, name FROM station_permission WHERE name = :name;")
                .single(call().bind("name", permission))
                .map(Permission.map())
                .first();
    }

    public void grantPermission(int memberId, int permissionId) {
        query(
                        "INSERT INTO station_member_permission(member_id, permission_id) VALUES(:member_id, :permission_id) ON CONFLICT DO NOTHING;")
                .single(call().bind("member_id", memberId).bind("permission_id", permissionId))
                .insert();
    }

    public boolean revokePermission(int memberId, int permissionId) {
        return query(
                        "DELETE FROM station_member_permission WHERE member_id = :member_id AND permission_id = :permission_id;")
                .single(call().bind("member_id", memberId).bind("permission_id", permissionId))
                .delete()
                .changed();
    }

    public void revokeAllPermissions(int memberId) {
        query("DELETE FROM station_member_permission WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .delete();
    }

    /**
     * Find all active members of a station who have the given permission (directly or via group).
     */
    public List<StationMember> findMembersWithPermission(int stationId, StationPermission permission) {
        return query("""
                SELECT DISTINCT %s FROM station_member sm
                WHERE sm.station_id = :station_id AND sm.former = FALSE
                  AND (
                    exists (
                        SELECT 1 FROM station_member_permission smp
                        JOIN station_permission sp ON sp.id = smp.permission_id
                        WHERE smp.member_id = sm.id AND sp.name = :permission_name
                    )
                    OR exists (
                        SELECT 1 FROM member_group_entry mge
                        JOIN member_group_permission mgp ON mgp.group_id = mge.group_id
                        JOIN station_permission sp ON sp.id = mgp.permission_id
                        WHERE mge.member_id = sm.id AND sp.name = :permission_name
                    )
                  );""", SqlSupport.alias("sm", STATION_MEMBER_COLUMNS))
                .single(call().bind("station_id", stationId).bind("permission_name", permission))
                .map(StationMember.map())
                .all();
    }

    public boolean setUserType(int memberId, StationUserType userType) {
        return query("UPDATE station_member SET user_type = :user_type WHERE id = :id;")
                .single(call().bind("user_type", userType).bind("id", memberId))
                .update()
                .changed();
    }

    public void setJoinDate(int memberId, LocalDate joinDate) {
        query("UPDATE station_member SET join_date = :join_date WHERE id = :id;")
                .single(call().bind("join_date", joinDate).bind("id", memberId))
                .update()
                .changed();
    }

    public List<Permission> findUserTypePermissions(int stationId, StationUserType userType) {
        return query("""
                SELECT sp.id, sp.name
                FROM station_permission sp
                JOIN station_user_type_permission sutp ON sp.id = sutp.permission_id
                WHERE sutp.station_id = :station_id AND sutp.user_type = :user_type;""")
                .single(call().bind("station_id", stationId).bind("user_type", userType))
                .map(Permission.map())
                .all();
    }

    public void setUserTypePermissions(int stationId, StationUserType userType, List<Integer> permissionIds) {
        query("DELETE FROM station_user_type_permission WHERE station_id = :station_id AND user_type = :user_type;")
                .single(call().bind("station_id", stationId).bind("user_type", userType))
                .delete();
        for (int permissionId : permissionIds) {
            query(
                            "INSERT INTO station_user_type_permission(station_id, user_type, permission_id) VALUES(:station_id, :user_type, :permission_id) ON CONFLICT DO NOTHING;")
                    .single(call().bind("station_id", stationId)
                            .bind("user_type", userType)
                            .bind("permission_id", permissionId))
                    .insert();
        }
    }

    public List<StationMember> findManaged(int managerId) {
        return query("""
                SELECT %s FROM station_member sm
                JOIN member_manager mm ON sm.id = mm.managed_id
                WHERE mm.manager_id = :manager_id AND sm.former = FALSE;""", SqlSupport.alias("sm", STATION_MEMBER_COLUMNS))
                .single(call().bind("manager_id", managerId))
                .map(StationMember.map())
                .all();
    }

    public List<StationMember> findManagers(int managedId) {
        return query("""
                SELECT %s FROM station_member sm
                JOIN member_manager mm ON sm.id = mm.manager_id
                WHERE mm.managed_id = :managed_id;""", SqlSupport.alias("sm", STATION_MEMBER_COLUMNS))
                .single(call().bind("managed_id", managedId))
                .map(StationMember.map())
                .all();
    }

    /**
     * Adds a manager relation between two members. Adding an already existing relation is a
     * no-op.
     */
    public void addManager(int managerId, int managedId) {
        query(
                        "INSERT INTO member_manager(manager_id, managed_id) VALUES(:manager_id, :managed_id) ON CONFLICT DO NOTHING;")
                .single(call().bind("manager_id", managerId).bind("managed_id", managedId))
                .insert();
    }

    public boolean removeManager(int managerId, int managedId) {
        return query("DELETE FROM member_manager WHERE manager_id = :manager_id AND managed_id = :managed_id;")
                .single(call().bind("manager_id", managerId).bind("managed_id", managedId))
                .delete()
                .changed();
    }

    public void removeAllManagers(int managedId) {
        query("DELETE FROM member_manager WHERE managed_id = :managed_id;")
                .single(call().bind("managed_id", managedId))
                .delete();
    }

    public void removeAllManaged(int managerId) {
        query("DELETE FROM member_manager WHERE manager_id = :manager_id;")
                .single(call().bind("manager_id", managerId))
                .delete();
    }

    private record MemberKey(int stationId, UUID memberUid) {}

    /**
     * Lightweight result row for the editor's member-search picker. Exposes the member UUID —
     * never the internal id — so cell configs survive station transfer. {@code displayTag}
     * carries the member's highest-priority visible tag name (and color), or {@code null} when
     * the member has no visible tag.
     */
    public record PickerMember(
            UUID memberUid,
            UUID accountUid,
            String displayName,
            StationUserType userType,
            String displayTag,
            String displayTagColor,
            LocalDate joinDate) {
        public static RowMapping<PickerMember> map() {
            return row -> new PickerMember(
                    row.get("member_uid", StandardValueConverter.UUID_STRING),
                    row.get("account_uid", StandardValueConverter.UUID_STRING),
                    row.getString("display_name"),
                    row.getEnum("user_type", StationUserType.class),
                    row.getString("display_tag"),
                    row.getString("display_tag_color"),
                    row.getObject("join_date", LocalDate.class));
        }
    }
}
