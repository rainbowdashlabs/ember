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
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
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
            feel_locked, logo_locked, storage_pool_bytes, created_at""";
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

    public boolean delete(int id) {
        return SqlSupport.deleteById("cluster", id);
    }

    // -- Stations --

    /**
     * Puts a station under a cluster, or lets it go when the cluster is {@code null}.
     */
    public boolean setStationCluster(int stationId, Integer clusterId) {
        return query("UPDATE station SET cluster_id = :cluster_id WHERE id = :id;")
                .single(call().bind("cluster_id", clusterId).bind("id", stationId))
                .update()
                .changed();
    }

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
