/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Clusters, their home stations and their members.
 *
 * <p>A cluster and its home station arrive together and go together. The home station is a real station row
 * with a real identity and a real storage scope, which is exactly what makes the rest of the design cheap:
 * the cluster's knowledge base, news, events and inventory are ordinary rows on an ordinary station, and they
 * travel to member stations through the federation machinery that already exists.
 */
@Singleton
public class ClusterService {
    private static final Logger log = LoggerFactory.getLogger(ClusterService.class);

    /**
     * The four things a cluster owns. Everything else is turned off on the home station at creation and the
     * cluster's module screen cannot turn it back on: a shell nobody joins has no use for attendance sheets
     * or a waiting list.
     */
    private static final Set<StationModule> CLUSTER_MODULES =
            EnumSet.of(StationModule.KNOWLEDGE_BASE, StationModule.NEWS, StationModule.EVENTS, StationModule.INVENTORY);

    private final ClusterRepository clusterRepository;
    private final StationRepository stationRepository;

    @Inject
    public ClusterService(ClusterRepository clusterRepository, StationRepository stationRepository) {
        this.clusterRepository = clusterRepository;
        this.stationRepository = stationRepository;
    }

    /**
     * Creates a cluster together with the station shell it owns.
     *
     * @param name        what the cluster is called
     * @param description a sentence about it, or {@code null}
     * @return the cluster
     */
    public Cluster create(String name, String description) {
        if (name == null || name.isBlank()) throw new BadRequestResponse("A cluster needs a name");

        Station home = stationRepository.create(name.trim());
        stationRepository.markAsClusterHome(home.id());
        stationRepository.setDisabledModules(home.id(), modulesToDisable());

        Cluster cluster = clusterRepository.create(name.trim(), description, home.id());
        log.info("Created cluster {} ('{}') on home station {}", cluster.id(), cluster.name(), home.id());
        return cluster;
    }

    /**
     * Renames a cluster, keeping the home station in step. Federation resolves a partner's label from the
     * local station row, so the two drifting apart would show member stations the old name on shared content.
     */
    public boolean rename(int clusterId, String name, String description) {
        if (name == null || name.isBlank()) throw new BadRequestResponse("A cluster needs a name");
        Cluster cluster =
                clusterRepository.findById(clusterId).orElseThrow(() -> new BadRequestResponse("No such cluster"));
        stationRepository.update(cluster.homeStationId(), name.trim());
        return clusterRepository.rename(clusterId, name.trim(), description);
    }

    public Optional<Cluster> findById(int id) {
        return clusterRepository.findById(id);
    }

    public Optional<Cluster> findByUid(UUID uid) {
        return clusterRepository.findByUid(uid);
    }

    public List<Cluster> findAll() {
        return clusterRepository.findAll();
    }

    /**
     * The cluster a station answers to, which is what decides whether its gear has an owner that can speak
     * for itself.
     */
    public Optional<Cluster> findByStation(int stationId) {
        return clusterRepository.findByStation(stationId);
    }

    public List<Integer> findStationIds(int clusterId) {
        return clusterRepository.findStationIds(clusterId);
    }

    public List<Cluster> findClustersForAccount(int accountId) {
        return clusterRepository.findClustersForAccount(accountId);
    }

    public Optional<ClusterMember> findMember(int clusterId, int accountId) {
        return clusterRepository.findMember(clusterId, accountId);
    }

    public List<ClusterMember> findMembers(int clusterId) {
        return clusterRepository.findMembers(clusterId);
    }

    /**
     * Takes an account on as a cluster member.
     *
     * @throws BadRequestResponse when they already belong to this cluster
     */
    public ClusterMember addMember(int clusterId, int accountId, ClusterUserType userType) {
        if (clusterRepository.findMember(clusterId, accountId).isPresent()) {
            throw new BadRequestResponse("That account is already a member of this cluster");
        }
        ClusterMember member = clusterRepository.addMember(
                clusterId, accountId, userType != null ? userType : ClusterUserType.CLUSTER_USER);
        log.info("Added account {} to cluster {} as {}", accountId, clusterId, member.userType());
        return member;
    }

    public boolean removeMember(int memberId) {
        return clusterRepository.removeMember(memberId);
    }

    /**
     * Everything a member may do, expanded.
     *
     * <p>Three sources feed it and none of them is special: the user type's defaults, the grants made to the
     * member, and the grants carried by the groups they are in. The defaults come from the enum rather than
     * from the database, so a cluster created before a user type learned a new permission still gets it.
     *
     * @param member the cluster member
     * @return every permission they hold
     */
    public Set<ClusterPermission> resolvePermissions(ClusterMember member) {
        Set<ClusterPermission> held = EnumSet.noneOf(ClusterPermission.class);
        held.addAll(clusterRepository.findMemberPermissions(member.id()));
        held.addAll(List.of(member.userType().defaultPermissions()));
        return ClusterPermission.expand(held);
    }

    /**
     * Grants a permission to a member directly.
     */
    public void grant(int memberId, ClusterPermission permission) {
        int permissionId = clusterRepository
                .findPermissionId(permission)
                .orElseThrow(() -> new BadRequestResponse("No such permission: " + permission));
        clusterRepository.grantPermission(memberId, permissionId);
    }

    public boolean revoke(int memberId, ClusterPermission permission) {
        return clusterRepository
                .findPermissionId(permission)
                .map(id -> clusterRepository.revokePermission(memberId, id))
                .orElse(false);
    }

    private Set<StationModule> modulesToDisable() {
        Set<StationModule> disabled = EnumSet.allOf(StationModule.class);
        disabled.removeAll(CLUSTER_MODULES);
        return disabled;
    }
}
