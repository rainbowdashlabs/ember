/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterBackendReach;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.entity.ClusterStationStorage;
import dev.chojo.ember.feature.storage.entity.ClusterStorageConfig;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.ClusterStorageConfigRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.service.StorageMigrationService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The storage an association keeps, what it decided about it, and which of its stations are actually on it.
 *
 * <p>Two facts, kept apart on purpose. What the association decided is written in a request and takes effect
 * at once; where a station's bytes are changes only when a copy finishes. A station whose placement does not
 * match the decision is <em>out of place</em>, which is a thing to be shown and acted on rather than a thing
 * to be hidden by reading one fact as if it were the other.
 */
@Singleton
public class ClusterStorageBackendService {
    private static final Logger log = LoggerFactory.getLogger(ClusterStorageBackendService.class);

    private final ClusterRepository clusterRepository;
    private final StationRepository stationRepository;
    private final ClusterStorageConfigRepository configRepository;
    private final ClusterStationStorageRepository placementRepository;
    private final StationStorageConfigRepository stationConfigRepository;
    private final StorageMigrationService migrationService;
    private final StorageBackendResolver resolver;

    @Inject
    public ClusterStorageBackendService(
            ClusterRepository clusterRepository,
            StationRepository stationRepository,
            ClusterStorageConfigRepository configRepository,
            ClusterStationStorageRepository placementRepository,
            StationStorageConfigRepository stationConfigRepository,
            StorageMigrationService migrationService,
            StorageBackendResolver resolver) {
        this.clusterRepository = clusterRepository;
        this.stationRepository = stationRepository;
        this.configRepository = configRepository;
        this.placementRepository = placementRepository;
        this.stationConfigRepository = stationConfigRepository;
        this.migrationService = migrationService;
        this.resolver = resolver;
    }

    /**
     * What the association decided and what it is standing on.
     *
     * @param clusterId the association
     * @return its policy and its current version, if it keeps one
     */
    public Policy findPolicy(int clusterId) {
        Cluster cluster = requireCluster(clusterId);
        return new Policy(
                cluster.storageBackendReach(),
                cluster.storageBackendLocked(),
                configRepository.findCurrent(clusterId).orElse(null));
    }

    /**
     * Sets how far the association's storage reaches and whether its stations may point themselves anywhere.
     *
     * <p>Reaching anywhere at all needs somewhere to reach: an association that has configured no storage
     * cannot decide that its files, or its stations', belong on it.
     *
     * @param clusterId the association
     * @param reach     how far its storage reaches
     * @param locked    whether only the association may move a station
     */
    public void setPolicy(int clusterId, ClusterBackendReach reach, boolean locked) {
        requireCluster(clusterId);
        if (reach != ClusterBackendReach.NONE
                && configRepository.findCurrent(clusterId).isEmpty()) {
            throw new BadRequestResponse("Configure the storage before deciding what it is for");
        }
        clusterRepository.setStorageBackendPolicy(clusterId, reach, locked);
        log.info("Cluster {} storage reaches {} and is {}", clusterId, reach, locked ? "locked" : "open");
    }

    /**
     * Saves the association's storage, as a new version or as new credentials for the one it has.
     *
     * <p>Two configurations naming the same destination are the same storage with a new secret, and rotating
     * a secret must not copy a terabyte. Anything else is somewhere else, so it becomes the current version
     * and everybody standing on the old one is out of place until they are carried across.
     *
     * @param clusterId the association
     * @param config    the backend, with its credentials already encrypted
     * @return the version that is current afterwards
     */
    public ClusterStorageConfig setBackend(int clusterId, StationStorageBackendConfig config) {
        requireCluster(clusterId);
        Optional<ClusterStorageConfig> current = configRepository.findCurrent(clusterId);
        if (current.isPresent() && current.get().config().destinationKey().equals(config.destinationKey())) {
            configRepository.updateInPlace(current.get().id(), config);
            // The destination is the same and nobody moves, but what is built for it changed
            resolver.invalidateStations(placedStationIds(clusterId));
            log.info("Cluster {} storage kept its destination and took new credentials", clusterId);
            return configRepository.findById(current.get().id()).orElseThrow();
        }
        ClusterStorageConfig version = configRepository.insertCurrent(clusterId, config);
        log.info("Cluster {} storage points somewhere new, version {}", clusterId, version.id());
        return version;
    }

    /**
     * Gives up the association's storage.
     *
     * <p>The versions people are standing on stay, because the alternative is a station pointed at nothing.
     * They are out of place from this moment and the lock does not hold them there: a freeze cannot freeze a
     * station onto storage its association no longer keeps.
     *
     * @param clusterId the association
     */
    public void dropBackend(int clusterId) {
        requireCluster(clusterId);
        configRepository.retireCurrent(clusterId);
        clusterRepository.setStorageBackendPolicy(clusterId, ClusterBackendReach.NONE, false);
        log.info("Cluster {} gave up storage of its own", clusterId);
    }

    /**
     * Every station of the association, where its bytes are and where they belong.
     *
     * @param clusterId the association
     * @return one row per station, the association's own store first
     */
    public List<Placement> listPlacements(int clusterId) {
        Cluster cluster = requireCluster(clusterId);
        Policy policy = findPolicy(clusterId);

        List<Placement> placements = new ArrayList<>();
        stationRepository
                .findById(cluster.homeStationId())
                .ifPresent(home -> placements.add(placementOf(home, policy, true)));
        for (int stationId : clusterRepository.findStationIds(clusterId)) {
            stationRepository
                    .findById(stationId)
                    .ifPresent(station -> placements.add(placementOf(station, policy, false)));
        }
        return placements;
    }

    /**
     * Carries one station's bytes to where its association's policy says they belong.
     *
     * @param clusterId the association
     * @param stationId the station of it being moved
     * @return what was carried
     */
    public StorageMigrationService.MigrationResult moveStation(int clusterId, int stationId) {
        Cluster cluster = requireCluster(clusterId);
        Station station = requireStationOf(cluster, stationId);
        Policy policy = findPolicy(clusterId);
        Placement placement = placementOf(station, policy, station.id() == cluster.homeStationId());
        if (placement.inPlace()) {
            throw new BadRequestResponse("That station's files are already where they belong");
        }
        return migrationService.moveStation(stationId, destinationFor(placement.expected(), policy));
    }

    /**
     * Carries a joining station's bytes onto the association's storage before it is taken in.
     *
     * <p>One of the two moments a move is not on demand, and for the reason that makes the difference: the
     * alternative is a station whose files stay on a disk it no longer answers to. The copy runs first and
     * the membership is written after it, so a copy that cannot run leaves the station unjoined rather than
     * joined and stranded.
     *
     * @param clusterId the association taking the station in
     * @param stationId the station joining it
     * @throws dev.chojo.ember.feature.storage.migration.MigrationException when the copy cannot be made
     */
    public void takeOverOnJoin(int clusterId, int stationId) {
        Policy policy = findPolicy(clusterId);
        if (policy.reach() != ClusterBackendReach.EVERY_STATION || policy.current() == null) return;
        boolean bringsOwn = stationConfigRepository.findOne(stationId).isPresent();
        // A station bringing its own is opting out, which it may do unless the association has said otherwise
        if (bringsOwn && !policy.locked()) return;

        ClusterStorageConfig current = policy.current();
        migrationService.moveStation(
                stationId, new StorageMigrationService.Destination.Cluster(clusterId, current.id(), current.config()));
        log.info("Station {} arrived on cluster {} storage version {}", stationId, clusterId, current.id());
    }

    /**
     * Carries a leaving station's bytes back to the instance default before it is let go.
     *
     * <p>The mirror of {@link #takeOverOnJoin}: what the association was keeping for it is not the
     * association's to keep afterwards, and a station answering to nobody resolves to the instance default,
     * where its files have to already be.
     *
     * @param clusterId the association letting go
     * @param stationId the station being released
     * @throws dev.chojo.ember.feature.storage.migration.MigrationException when the copy cannot be made
     */
    public void handBackOnRelease(int clusterId, int stationId) {
        boolean onThisCluster = placementRepository
                .findByStation(stationId)
                .filter(placement -> placement.clusterId() == clusterId)
                .isPresent();
        if (!onThisCluster) return;

        migrationService.moveStation(stationId, new StorageMigrationService.Destination.InstanceDefault());
        log.info("Station {} took its files off cluster {} storage on the way out", stationId, clusterId);
    }

    /**
     * Where a station belongs, read off the two settings and what the station brought.
     *
     * <p>The one rule that overrides every other: a station standing on a version that is no longer current
     * is out of place whatever the policy says, which is what makes a new destination and a dropped backend
     * mean anything at all.
     */
    private Expected expectedFor(Station station, Policy policy, boolean isHome) {
        boolean bringsOwn = stationConfigRepository.findOne(station.id()).isPresent();
        boolean clusterReaches = policy.reach() == ClusterBackendReach.EVERY_STATION
                || (isHome && policy.reach() == ClusterBackendReach.OWN_FILES);

        if (!clusterReaches) {
            // Frozen: the association is not reaching for this station and has said nobody moves anything
            if (policy.locked()) return Expected.WHEREVER_IT_IS;
            return bringsOwn ? Expected.ITS_OWN : Expected.INSTANCE_DEFAULT;
        }
        // A station that brought its own is opting out, which it may do while the association allows it
        if (bringsOwn && !policy.locked()) return Expected.ITS_OWN;
        return Expected.THE_CLUSTERS;
    }

    private Placement placementOf(Station station, Policy policy, boolean isHome) {
        Optional<ClusterStationStorage> placed = placementRepository.findByStation(station.id());
        boolean bringsOwn = stationConfigRepository.findOne(station.id()).isPresent();
        Actual actual = bringsOwn ? Actual.ITS_OWN : placed.isPresent() ? Actual.THE_CLUSTERS : Actual.INSTANCE_DEFAULT;
        Expected expected = expectedFor(station, policy, isHome);

        boolean onCurrent = placed.map(row -> policy.current() != null
                        && row.configId() == policy.current().id())
                .orElse(false);
        boolean inPlace =
                switch (expected) {
                    case WHEREVER_IT_IS -> true;
                    case ITS_OWN -> actual == Actual.ITS_OWN;
                    case INSTANCE_DEFAULT -> actual == Actual.INSTANCE_DEFAULT;
                    case THE_CLUSTERS -> actual == Actual.THE_CLUSTERS && onCurrent;
                };
        return new Placement(station.id(), station.uid(), station.name(), isHome, actual, expected, inPlace);
    }

    private StorageMigrationService.Destination destinationFor(Expected expected, Policy policy) {
        return switch (expected) {
            case THE_CLUSTERS -> {
                ClusterStorageConfig current = policy.current();
                if (current == null) throw new BadRequestResponse("This association keeps no storage to move onto");
                yield new StorageMigrationService.Destination.Cluster(
                        current.clusterId(), current.id(), current.config());
            }
            case INSTANCE_DEFAULT, WHEREVER_IT_IS -> new StorageMigrationService.Destination.InstanceDefault();
            case ITS_OWN ->
                throw new BadRequestResponse("Storage a station brought itself is the station's to point at");
        };
    }

    private List<Integer> placedStationIds(int clusterId) {
        return placementRepository.findByCluster(clusterId).stream()
                .map(ClusterStationStorage::stationId)
                .toList();
    }

    private Cluster requireCluster(int clusterId) {
        return clusterRepository.findById(clusterId).orElseThrow(() -> new NotFoundResponse("No such cluster"));
    }

    private Station requireStationOf(Cluster cluster, int stationId) {
        Station station =
                stationRepository.findById(stationId).orElseThrow(() -> new NotFoundResponse("No such station"));
        boolean belongs = station.id() == cluster.homeStationId()
                || (station.clusterId() != null && station.clusterId() == cluster.id());
        if (!belongs) throw new BadRequestResponse("That station does not belong to this cluster");
        return station;
    }

    /**
     * What an association decided about storage, and what it is standing on.
     *
     * @param reach   how far its own storage reaches
     * @param locked  whether only it may move a station
     * @param current the version new placements are carried to, or {@code null} when it keeps none
     */
    public record Policy(ClusterBackendReach reach, boolean locked, ClusterStorageConfig current) {}

    /**
     * One station of the association, where its bytes are and where they belong.
     *
     * @param stationId   the station
     * @param stationUid  its identity, which is how the move is addressed
     * @param name        what it is called
     * @param homeStation whether this is the association's own store
     * @param actual      where its bytes are
     * @param expected    where the policy says they belong
     * @param inPlace     whether those two are the same thing
     */
    public record Placement(
            int stationId,
            UUID stationUid,
            String name,
            boolean homeStation,
            Actual actual,
            Expected expected,
            boolean inPlace) {}

    /**
     * Where a station's bytes are.
     */
    public enum Actual {
        /** On a backend the station brought itself. */
        ITS_OWN,
        /** On a version of its association's storage. */
        THE_CLUSTERS,
        /** On whatever the instance provides. */
        INSTANCE_DEFAULT
    }

    /**
     * Where a station's bytes belong, given what its association decided.
     */
    public enum Expected {
        /** Its own backend, which under an open policy is a legal opt-out. */
        ITS_OWN,
        /** The association's current version. */
        THE_CLUSTERS,
        /** Whatever the instance provides. */
        INSTANCE_DEFAULT,
        /** Nowhere in particular: the association froze the arrangement in force. */
        WHEREVER_IT_IS
    }
}
