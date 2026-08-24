/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ClusterApplicationResolved;
import dev.chojo.ember.event.events.ClusterStationReleased;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.inventory.service.ClusterItemHandoverService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.repository.ClusterStorageQuotaRepository;
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
    private final ClusterItemHandoverService itemHandoverService;
    private final FederationService federationService;
    private final ClusterGovernanceService governanceService;
    private final ClusterProfileFieldService fieldService;
    private final ClusterStorageQuotaRepository quotaRepository;
    private final ClusterStorageBackendService storageBackendService;
    private final ClusterStationGroupService stationGroupService;
    private final DomainEventBus eventBus;

    @Inject
    public ClusterService(
            ClusterRepository clusterRepository,
            StationRepository stationRepository,
            ClusterItemHandoverService itemHandoverService,
            FederationService federationService,
            ClusterGovernanceService governanceService,
            ClusterProfileFieldService fieldService,
            ClusterStorageQuotaRepository quotaRepository,
            ClusterStorageBackendService storageBackendService,
            ClusterStationGroupService stationGroupService,
            DomainEventBus eventBus) {
        this.clusterRepository = clusterRepository;
        this.stationRepository = stationRepository;
        this.itemHandoverService = itemHandoverService;
        this.federationService = federationService;
        this.governanceService = governanceService;
        this.fieldService = fieldService;
        this.quotaRepository = quotaRepository;
        this.storageBackendService = storageBackendService;
        this.stationGroupService = stationGroupService;
        this.eventBus = eventBus;
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

    /**
     * Deletes a cluster and the shell it owns.
     *
     * <p>Refused while any station still answers to it: releasing them is a decision with consequences at
     * each one, and doing it wholesale as a side effect of a delete would hide those.
     *
     * @param clusterId the cluster
     * @return {@code true} when it was deleted
     * @throws BadRequestResponse when stations still belong to it
     */
    public boolean delete(int clusterId) {
        Cluster cluster =
                clusterRepository.findById(clusterId).orElseThrow(() -> new BadRequestResponse("No such cluster"));
        List<Integer> stations = clusterRepository.findStationIds(clusterId);
        if (!stations.isEmpty()) {
            throw new BadRequestResponse(
                    "This cluster still has %d station(s). Release them first.".formatted(stations.size()));
        }
        boolean deleted = clusterRepository.delete(clusterId);
        if (deleted) {
            stationRepository.delete(cluster.homeStationId());
            log.info("Deleted cluster {} and its home station {}", clusterId, cluster.homeStationId());
        }
        return deleted;
    }

    /**
     * Creates a station that belongs to the cluster from its first moment.
     *
     * <p>The other way in is an application from a station that already exists, which its owner has to open.
     * A cluster never reaches out and takes one.
     *
     * @param clusterId the cluster
     * @param name      what the station is called
     * @return the station
     */
    public Station createStation(int clusterId, String name) {
        if (name == null || name.isBlank()) throw new BadRequestResponse("A station needs a name");
        requireCluster(clusterId);

        Station station = stationRepository.create(name.trim());
        joinStation(clusterId, station.id());
        log.info("Cluster {} created station {}", clusterId, station.id());
        return station;
    }

    /**
     * Puts a station under a cluster.
     *
     * <p>Everything a cluster hands its members follows from this one row: the federation pairs that carry
     * its content, the modules it denies, the look it sets and the gear it lends. Each of those is wired in
     * as it arrives, and each of them is undone again by {@link #releaseStation(int, int)}.
     *
     * @param clusterId the cluster
     * @param stationId the station joining it
     */
    public void joinStation(int clusterId, int stationId) {
        Cluster cluster = requireCluster(clusterId);
        Station station = requireStation(stationId);
        if (station.stationKind() == StationKind.CLUSTER_HOME) {
            throw new BadRequestResponse("A cluster's own station cannot join another cluster");
        }
        if (station.clusterId() != null && station.clusterId() != clusterId) {
            throw new BadRequestResponse("This station already belongs to another cluster");
        }

        // The files first, and the membership after them: a station taken in whose bytes stayed behind
        // resolves to storage they are not on, and a copy that cannot run leaves it unjoined instead
        storageBackendService.takeOverOnJoin(clusterId, stationId);

        stationRepository.setCluster(stationId, clusterId);
        itemHandoverService.adoptAtStation(clusterId, stationId);
        governanceService.applyLookTo(cluster, station);
        federationService.createClusterFederation(
                cluster.homeStationId(),
                stationId,
                clusterRepository.findStationIds(clusterId),
                cluster.autoFederate());
        log.info("Station {} joined cluster {}", stationId, clusterId);
        eventBus.publish(new ClusterApplicationResolved(stationId, cluster.name(), true, null));
    }

    /**
     * Lets a station go.
     *
     * <p>What the station brought stays with it and what the cluster lent it goes back, which is why gear the
     * cluster owns is put back in its own store rather than deleted: the station losing its cluster is not
     * the same as the gear ceasing to exist.
     *
     * @param clusterId the cluster letting go
     * @param stationId the station being released
     * @throws BadRequestResponse when that station does not answer to this cluster
     */
    public void releaseStation(int clusterId, int stationId) {
        Cluster cluster = requireCluster(clusterId);
        Station station = requireStation(stationId);
        if (station.clusterId() == null || station.clusterId() != clusterId) {
            throw new BadRequestResponse("That station does not belong to this cluster");
        }

        // And on the way out the files come first as well, for the same reason in the other direction
        storageBackendService.handBackOnRelease(clusterId, stationId);

        itemHandoverService.recallFromStation(clusterId, stationId);
        federationService.removeClusterFederation(stationId);
        // The answers go, the history of who changed what stays: an audit trail is not the cluster's to
        // take away when it lets a station go
        fieldService.clearValuesOfStation(stationId);
        stationGroupService.forgetStation(stationId);
        // The room went with the membership. What the instance says about the station stands again, untouched
        // all along
        quotaRepository.deleteGrant(stationId);
        stationRepository.setCluster(stationId, null);
        log.info("Cluster {} released station {}", clusterId, stationId);
        eventBus.publish(new ClusterStationReleased(stationId, cluster.name()));
    }

    /**
     * Says whether the cluster wants its stations connected to each other.
     *
     * <p>Turning it on fills in the pairs that were never made. Turning it off leaves the ones that exist
     * alone: the stations have been seeing each other's calendars and lending each other gear, and quietly
     * cutting all of that because a setting moved would be a bigger act than the setting looks like.
     *
     * @param clusterId    the cluster
     * @param autoFederate whether member stations should be paired with each other
     */
    public void setAutoFederate(int clusterId, boolean autoFederate) {
        requireCluster(clusterId);
        clusterRepository.setAutoFederate(clusterId, autoFederate);
        if (autoFederate) {
            federationService.backfillClusterMesh(clusterRepository.findStationIds(clusterId));
        }
        log.info("Cluster {} auto-federation set to {}", clusterId, autoFederate);
    }

    /**
     * The member stations of a cluster, without the shell it owns.
     *
     * @param clusterId the cluster
     * @return its stations
     */
    public List<Station> findStations(int clusterId) {
        return stationRepository.findByCluster(clusterId);
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
     * The members of a cluster who hold a given permission, expanded.
     *
     * <p>What somebody at the cluster is told about follows from what they are allowed to act on, so this is
     * how a notification finds its recipients. Resolved per member rather than in one query, because the
     * expansion of a user type's defaults lives in the enum and not in the database.
     *
     * @param clusterId  the cluster
     * @param permission what they must hold
     * @return the cluster member ids
     */
    public List<Integer> findMemberIdsWith(int clusterId, ClusterPermission permission) {
        return findMembers(clusterId).stream()
                .filter(member -> resolvePermissions(member).contains(permission))
                .map(ClusterMember::id)
                .toList();
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

    private Cluster requireCluster(int clusterId) {
        return clusterRepository.findById(clusterId).orElseThrow(() -> new BadRequestResponse("No such cluster"));
    }

    private Station requireStation(int stationId) {
        return stationRepository.findById(stationId).orElseThrow(() -> new BadRequestResponse("No such station"));
    }

    private Set<StationModule> modulesToDisable() {
        Set<StationModule> disabled = EnumSet.allOf(StationModule.class);
        disabled.removeAll(CLUSTER_MODULES);
        return disabled;
    }
}
