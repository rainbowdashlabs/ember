/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.repository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterBackendReach;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.cluster.entity.ClusterMemberGroup;
import dev.chojo.ember.feature.cluster.entity.LossReportRequirement;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for clusters, their members and the three ways a member comes to hold a permission: by user
 * type, by a grant of their own, and through a group.
 */
@Singleton
public class ClusterRepository {
    private static final String CLUSTER_COLUMNS = """
            id, uid, name, description, home_station_id, auto_federate, theme_locked, colors_locked, \
            feel_locked, logo_locked, storage_pool_bytes, default_theme, custom_theme_colors, default_feel, \
            uses_inventory, loss_report_requires, storage_backend_reach, storage_backend_locked, created_at""";
    private static final String MEMBER_COLUMNS = "id, cluster_id, account_id, user_type";

    /**
     * Identities change only when a cluster is created or deleted, so holding them briefly saves a query on
     * every serialised id without any risk of going stale in a way anybody notices.
     */
    private final Cache<Integer, UUID> uidCache = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(1_000)
            .build();

    // -- Clusters --

    public Optional<Cluster> findById(int id) {
        return SqlSupport.findById("cluster", CLUSTER_COLUMNS, id, Cluster.map());
    }

    public Optional<Cluster> findByUid(UUID uid) {
        return query("SELECT %s FROM cluster WHERE uid = :uid::UUID;", CLUSTER_COLUMNS)
                .single(call().bind("uid", uid, StandardValueConverter.UUID_STRING))
                .map(Cluster.map())
                .first();
    }

    /**
     * The cluster a station answers to, if any.
     */
    public Optional<Cluster> findByStation(int stationId) {
        return query("""
                SELECT %s FROM cluster c
                JOIN station s ON s.cluster_id = c.id
                WHERE s.id = :station_id;""", SqlSupport.alias("c", CLUSTER_COLUMNS))
                .single(call().bind("station_id", stationId))
                .map(Cluster.map())
                .first();
    }

    /**
     * The cluster that owns a station, if the station is one a cluster keeps its own things on.
     *
     * <p>Distinct from {@link #findByStation(int)}, which answers for a member station. This one answers for
     * the station nobody joins: the shell a cluster's knowledge base, news and calendar live on.
     *
     * @param stationId the station
     * @return the cluster whose own station it is
     */
    public Optional<Cluster> findByHomeStation(int stationId) {
        return query("SELECT %s FROM cluster WHERE home_station_id = :station_id;", CLUSTER_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(Cluster.map())
                .first();
    }

    /**
     * The public identity of a cluster, which is what leaves the backend on the wire.
     *
     * @param clusterId the internal id
     * @return the identity, or {@code null} when no such cluster exists
     */
    public UUID resolveUid(int clusterId) {
        return uidCache.get(clusterId, id -> query("SELECT uid FROM cluster WHERE id = :id;")
                .single(call().bind("id", id))
                .map(row -> row.get("uid", StandardValueConverter.UUID_STRING))
                .first()
                .orElse(null));
    }

    /**
     * Forgets every identity held in memory.
     *
     * <p>Only one thing takes clusters away without deleting them one at a time, and that is the demo being
     * wiped. Afterwards the same internal id belongs to a different cluster with a different identity, and a
     * cached one is an identity nothing can be found by: it is written out on every item the cluster owns
     * and refused the moment anybody sends it back.
     */
    public void invalidateIdentityCache() {
        uidCache.invalidateAll();
    }

    public List<Cluster> findAll() {
        return query("""
                SELECT %s FROM cluster ORDER BY name;""", CLUSTER_COLUMNS).single(call()).map(Cluster.map()).all();
    }

    public Cluster create(String name, String description, int homeStationId) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO cluster(name, description, home_station_id)
                VALUES (:name, :description, :home_station_id)
                RETURNING %s;""",
                call().bind("name", name).bind("description", description).bind("home_station_id", homeStationId),
                Cluster.map(),
                CLUSTER_COLUMNS);
    }

    public boolean rename(int id, String name, String description) {
        return query("UPDATE cluster SET name = :name, description = :description WHERE id = :id;")
                .single(call().bind("name", name)
                        .bind("description", description)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Whether the cluster wants its member stations connected to each other.
     *
     * @param id           the cluster
     * @param autoFederate the new setting
     * @return {@code true} if a row was updated
     */
    public boolean setAutoFederate(int id, boolean autoFederate) {
        return query("UPDATE cluster SET auto_federate = :auto_federate WHERE id = :id;")
                .single(call().bind("auto_federate", autoFederate).bind("id", id))
                .update()
                .changed();
    }

    // -- Member groups --

    private static final String GROUP_COLUMNS = "id, cluster_id, name";

    public List<ClusterMemberGroup> findGroups(int clusterId) {
        return query("SELECT %s FROM cluster_member_group WHERE cluster_id = :cluster_id ORDER BY name;", GROUP_COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(ClusterMemberGroup.map())
                .all();
    }

    public Optional<ClusterMemberGroup> findGroupById(int groupId) {
        return SqlSupport.findById("cluster_member_group", GROUP_COLUMNS, groupId, ClusterMemberGroup.map());
    }

    public ClusterMemberGroup createGroup(int clusterId, String name) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO cluster_member_group(cluster_id, name)
                VALUES (:cluster_id, :name)
                RETURNING %s;""", call().bind("cluster_id", clusterId).bind("name", name), ClusterMemberGroup.map(), GROUP_COLUMNS);
    }

    public boolean renameGroup(int groupId, String name) {
        return query("UPDATE cluster_member_group SET name = :name WHERE id = :id;")
                .single(call().bind("name", name).bind("id", groupId))
                .update()
                .changed();
    }

    public boolean deleteGroup(int groupId) {
        return SqlSupport.deleteById("cluster_member_group", groupId);
    }

    /** The members of a group, as ids, which is what a screen listing them needs. */
    public List<Integer> findGroupMemberIds(int groupId) {
        return query("SELECT member_id FROM cluster_member_group_membership WHERE group_id = :group_id;")
                .single(call().bind("group_id", groupId))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    /** The groups one member is in. */
    public List<ClusterMemberGroup> findGroupsOfMember(int memberId) {
        return query("""
                SELECT %s FROM cluster_member_group g
                JOIN cluster_member_group_membership gm ON gm.group_id = g.id
                WHERE gm.member_id = :member_id
                ORDER BY g.name;""", SqlSupport.alias("g", GROUP_COLUMNS))
                .single(call().bind("member_id", memberId))
                .map(ClusterMemberGroup.map())
                .all();
    }

    public void addToGroup(int groupId, int memberId) {
        query("""
                INSERT INTO cluster_member_group_membership(group_id, member_id)
                VALUES (:group_id, :member_id)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("group_id", groupId).bind("member_id", memberId))
                .insert();
    }

    public boolean removeFromGroup(int groupId, int memberId) {
        return query("""
                DELETE FROM cluster_member_group_membership
                WHERE group_id = :group_id AND member_id = :member_id;""")
                .single(call().bind("group_id", groupId).bind("member_id", memberId))
                .delete()
                .changed();
    }

    /** What a group carries, expanded nowhere: the raw grants, as the screen shows them. */
    public Set<ClusterPermission> findGroupPermissions(int groupId) {
        Set<ClusterPermission> held = EnumSet.noneOf(ClusterPermission.class);
        for (String name : query("""
                SELECT p.name FROM cluster_member_group_permission gp
                JOIN cluster_permission p ON p.id = gp.permission_id
                WHERE gp.group_id = :group_id;""")
                .single(call().bind("group_id", groupId))
                .map(row -> row.getString("name"))
                .all()) {
            try {
                held.add(ClusterPermission.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // A permission the code no longer knows is not worth failing over
            }
        }
        return held;
    }

    public void grantToGroup(int groupId, int permissionId) {
        query("""
                INSERT INTO cluster_member_group_permission(group_id, permission_id)
                VALUES (:group_id, :permission_id)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("group_id", groupId).bind("permission_id", permissionId))
                .insert();
    }

    public boolean revokeFromGroup(int groupId, int permissionId) {
        return query("""
                DELETE FROM cluster_member_group_permission
                WHERE group_id = :group_id AND permission_id = :permission_id;""")
                .single(call().bind("group_id", groupId).bind("permission_id", permissionId))
                .delete()
                .changed();
    }

    /** The grants made to one member by name, as opposed to what their type or groups carry. */
    public Set<ClusterPermission> findDirectPermissions(int memberId) {
        Set<ClusterPermission> held = EnumSet.noneOf(ClusterPermission.class);
        for (String name : query("""
                SELECT p.name FROM cluster_member_permission mp
                JOIN cluster_permission p ON p.id = mp.permission_id
                WHERE mp.member_id = :member_id;""")
                .single(call().bind("member_id", memberId))
                .map(row -> row.getString("name"))
                .all()) {
            try {
                held.add(ClusterPermission.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // as above
            }
        }
        return held;
    }

    /** Changes what a member's user type is, which changes what they hold by default. */
    public boolean setMemberUserType(int memberId, ClusterUserType userType) {
        return query("UPDATE cluster_member SET user_type = :user_type WHERE id = :id;")
                .single(call().bind("user_type", userType).bind("id", memberId))
                .update()
                .changed();
    }

    public Optional<ClusterMember> findMemberById(int memberId) {
        return SqlSupport.findById("cluster_member", MEMBER_COLUMNS, memberId, ClusterMember.map());
    }

    /**
     * The modules this cluster switches off for every station under it.
     *
     * @param clusterId the cluster
     * @return the denied modules, skipping any name the code no longer knows
     */
    public Set<StationModule> findDeniedModules(int clusterId) {
        Set<StationModule> denied = EnumSet.noneOf(StationModule.class);
        for (String name : query("SELECT module FROM cluster_denied_module WHERE cluster_id = :cluster_id;")
                .single(call().bind("cluster_id", clusterId))
                .map(row -> row.getString("module"))
                .all()) {
            // A module dropped from the code leaves its rows behind, and a denial of something that no
            // longer exists is not worth failing over
            try {
                denied.add(StationModule.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // deliberately skipped
            }
        }
        return denied;
    }

    /**
     * Replaces the denial list outright.
     *
     * @param clusterId the cluster
     * @param modules   what it now denies
     */
    public void setDeniedModules(int clusterId, Set<StationModule> modules) {
        query("DELETE FROM cluster_denied_module WHERE cluster_id = :cluster_id;")
                .single(call().bind("cluster_id", clusterId))
                .delete();
        for (StationModule module : modules) {
            query("""
                    INSERT INTO cluster_denied_module(cluster_id, module)
                    VALUES (:cluster_id, :module)
                    ON CONFLICT DO NOTHING;""")
                    .single(call().bind("cluster_id", clusterId).bind("module", module))
                    .insert();
        }
    }

    /**
     * The clusters that deny a given module, which is how a station's own check finds out about it.
     *
     * @param stationId the station asking
     * @param module    the module in question
     * @return {@code true} when the station's cluster denies it
     */
    public boolean isModuleDeniedForStation(int stationId, StationModule module) {
        return SqlSupport.exists("""
                SELECT 1 FROM cluster_denied_module cdm
                JOIN station s ON s.cluster_id = cdm.cluster_id
                WHERE s.id = :station_id AND cdm.module = :module;""", call().bind("station_id", stationId).bind("module", module));
    }

    /**
     * The look the cluster hands its stations, and which parts of it they may not change.
     *
     * @param clusterId         the cluster
     * @param defaultTheme      the colour theme, or {@code null} for no opinion
     * @param customThemeColors the colour set, or {@code null}
     * @param defaultFeel       the interface feel, or {@code null} for no opinion
     * @param themeLocked       whether the station may change the theme
     * @param colorsLocked      whether the station may change the colours
     * @param feelLocked        whether the station may change the feel
     * @param logoLocked        whether the station may change its logo
     * @return {@code true} if a row was updated
     */
    public boolean setLookAndFeel(
            int clusterId,
            String defaultTheme,
            String customThemeColors,
            ThemeFeel defaultFeel,
            boolean themeLocked,
            boolean colorsLocked,
            boolean feelLocked,
            boolean logoLocked) {
        return query("""
                UPDATE cluster
                SET default_theme       = :default_theme,
                    custom_theme_colors = :custom_theme_colors,
                    default_feel        = :default_feel,
                    theme_locked        = :theme_locked,
                    colors_locked       = :colors_locked,
                    feel_locked         = :feel_locked,
                    logo_locked         = :logo_locked
                WHERE id = :id;""")
                .single(call().bind("default_theme", defaultTheme)
                        .bind("custom_theme_colors", customThemeColors)
                        .bind("default_feel", defaultFeel)
                        .bind("theme_locked", themeLocked)
                        .bind("colors_locked", colorsLocked)
                        .bind("feel_locked", feelLocked)
                        .bind("logo_locked", logoLocked)
                        .bind("id", clusterId))
                .update()
                .changed();
    }

    /**
     * The pool the instance grants the cluster, or {@code null} for no cap of its own.
     *
     * @param clusterId the cluster
     * @param poolBytes how much it may hand out in total
     * @return {@code true} if a row was updated
     */
    public boolean setStoragePool(int clusterId, Long poolBytes) {
        return query("UPDATE cluster SET storage_pool_bytes = :pool WHERE id = :id;")
                .single(call().bind("pool", poolBytes).bind("id", clusterId))
                .update()
                .changed();
    }

    /**
     * Says whether the cluster keeps its gear here.
     *
     * <p>Switching it off does not touch anything already recorded. What it changes is what happens next: a
     * movement created afterwards falls through to the station's own flow, because there is nobody at the
     * cluster to acknowledge a step.
     *
     * @param id            the cluster
     * @param usesInventory whether it keeps its gear here
     * @return {@code true} if a row was updated
     */
    public boolean setUsesInventory(int id, boolean usesInventory) {
        return query("UPDATE cluster SET uses_inventory = :uses_inventory WHERE id = :id;")
                .single(call().bind("uses_inventory", usesInventory).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Sets what a station has to bring when it reports a piece of the cluster's gear missing.
     *
     * @param id       the cluster
     * @param requires nothing, a note, or a document as well
     * @return {@code true} if a row was updated
     */
    public boolean setLossReportRequires(int id, LossReportRequirement requires) {
        return query("UPDATE cluster SET loss_report_requires = :requires WHERE id = :id;")
                .single(call().bind("requires", requires).bind("id", id))
                .update()
                .changed();
    }

    /**
     * What the cluster decided about storage of its own, which is not where anybody's bytes are.
     *
     * @param id     the cluster
     * @param reach  how far its storage reaches
     * @param locked whether only the cluster may move one of its stations
     * @return {@code true} if a row was updated
     */
    public boolean setStorageBackendPolicy(int id, ClusterBackendReach reach, boolean locked) {
        return query("""
                UPDATE cluster SET storage_backend_reach = :reach, storage_backend_locked = :locked
                WHERE id = :id;""")
                .single(call().bind("reach", reach).bind("locked", locked).bind("id", id))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return SqlSupport.deleteById("cluster", id);
    }

    // -- Stations --

    /**
     * Puts a station under a cluster, or lets it go when the cluster is {@code null}.
     */
    public List<Integer> findStationIds(int clusterId) {
        return query("""
                SELECT id FROM station
                WHERE cluster_id = :cluster_id AND station_kind = 'REGULAR'
                ORDER BY name;""")
                .single(call().bind("cluster_id", clusterId))
                .map(row -> row.getInt("id"))
                .all();
    }

    // -- Members --

    public Optional<ClusterMember> findMember(int clusterId, int accountId) {
        return query("""
                SELECT %s FROM cluster_member
                WHERE cluster_id = :cluster_id AND account_id = :account_id;""", MEMBER_COLUMNS)
                .single(call().bind("cluster_id", clusterId).bind("account_id", accountId))
                .map(ClusterMember.map())
                .first();
    }

    public List<ClusterMember> findMembers(int clusterId) {
        return query("""
                SELECT %s FROM cluster_member WHERE cluster_id = :cluster_id ORDER BY id;""", MEMBER_COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(ClusterMember.map())
                .all();
    }

    /**
     * The clusters an account may act for, which is how a switcher knows what to offer.
     */
    public List<Cluster> findClustersForAccount(int accountId) {
        return query("""
                SELECT %s FROM cluster c
                JOIN cluster_member m ON m.cluster_id = c.id
                WHERE m.account_id = :account_id
                ORDER BY c.name;""", SqlSupport.alias("c", CLUSTER_COLUMNS))
                .single(call().bind("account_id", accountId))
                .map(Cluster.map())
                .all();
    }

    public ClusterMember addMember(int clusterId, int accountId, ClusterUserType userType) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO cluster_member(cluster_id, account_id, user_type)
                VALUES (:cluster_id, :account_id, :user_type)
                RETURNING %s;""",
                call().bind("cluster_id", clusterId)
                        .bind("account_id", accountId)
                        .bind("user_type", userType),
                ClusterMember.map(),
                MEMBER_COLUMNS);
    }

    public boolean removeMember(int memberId) {
        return SqlSupport.deleteById("cluster_member", memberId);
    }

    // -- Permissions --

    /**
     * Everything a member holds before expansion: what their user type grants at this cluster, what they have
     * been granted directly, and what the groups they are in carry.
     *
     * <p>Reading all three in one statement rather than three keeps the answer consistent with itself even
     * while somebody is editing the grants.
     *
     * @param memberId the cluster member
     * @return the permissions held, unexpanded
     */
    public Set<ClusterPermission> findMemberPermissions(int memberId) {
        List<String> names = query("""
                SELECT p.name FROM cluster_permission p
                WHERE p.id IN (
                    SELECT mp.permission_id FROM cluster_member_permission mp WHERE mp.member_id = :member_id
                    UNION
                    SELECT gp.permission_id FROM cluster_member_group_permission gp
                    JOIN cluster_member_group_membership gm ON gm.group_id = gp.group_id
                    WHERE gm.member_id = :member_id
                    UNION
                    SELECT tp.permission_id FROM cluster_user_type_permission tp
                    JOIN cluster_member m ON m.cluster_id = tp.cluster_id AND m.user_type = tp.user_type
                    WHERE m.id = :member_id
                );""")
                .single(call().bind("member_id", memberId))
                .map(row -> row.getString("name"))
                .all();

        Set<ClusterPermission> permissions = EnumSet.noneOf(ClusterPermission.class);
        for (String name : names) {
            // A permission the database knows and the code does not is a row from a newer version: skip it
            // rather than refusing the whole request
            try {
                permissions.add(ClusterPermission.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // deliberately ignored
            }
        }
        return permissions;
    }

    public Optional<Integer> findPermissionId(ClusterPermission permission) {
        return query("SELECT id FROM cluster_permission WHERE name = :name;")
                .single(call().bind("name", permission.name()))
                .map(row -> row.getInt("id"))
                .first();
    }

    public void grantPermission(int memberId, int permissionId) {
        query("""
                INSERT INTO cluster_member_permission(member_id, permission_id)
                VALUES (:member_id, :permission_id)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("member_id", memberId).bind("permission_id", permissionId))
                .insert();
    }

    public boolean revokePermission(int memberId, int permissionId) {
        return query("""
                DELETE FROM cluster_member_permission
                WHERE member_id = :member_id AND permission_id = :permission_id;""")
                .single(call().bind("member_id", memberId).bind("permission_id", permissionId))
                .delete()
                .changed();
    }
}
