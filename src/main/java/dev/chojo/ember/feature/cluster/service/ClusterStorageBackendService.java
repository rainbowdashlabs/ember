/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterBackendReach;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.core.BackendRedaction;
import dev.chojo.ember.feature.storage.core.BackendRequest;
import dev.chojo.ember.feature.storage.core.BackendSummary;
import dev.chojo.ember.feature.storage.core.BackendValidation;
import dev.chojo.ember.feature.storage.core.ProbeResult;
import dev.chojo.ember.feature.storage.core.RetiredVersions;
import dev.chojo.ember.feature.storage.entity.ClusterStationStorage;
import dev.chojo.ember.feature.storage.entity.ClusterStorageConfig;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.ClusterStorageConfigRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.service.StationMoves;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageMigrationService;
import dev.chojo.ember.feature.storage.service.StorageMigrationService.Destination;
import dev.chojo.ember.feature.storage.service.StorageProbeService;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The storage an association keeps, what it decided about it, and which of its stations are actually on it.
 *
 * <p>Two facts, kept apart on purpose. What the association decided is written in a request and takes effect
 * at once; where a station's bytes are changes only when a copy finishes. A station whose placement does not
 * match the decision is <em>out of place</em>, which is a thing to be shown and acted on rather than a thing
 * to be hidden by reading one fact as if it were the other.
 *
 * <p>Every change is written to the association's storage history, a refused one included, and a move it
 * makes for one of its stations to the station's history as well. Joining and leaving move files in the
 * name of the association's own routine rather than a person.
 */
@Singleton
public class ClusterStorageBackendService {
    /** The name a move made when a station joins is written down under. */
    static final String JOIN = "association-join";

    /** The name a move made when a station leaves is written down under. */
    static final String RELEASE = "association-release";

    private static final Logger log = LoggerFactory.getLogger(ClusterStorageBackendService.class);

    private final ClusterRepository clusterRepository;
    private final StationRepository stationRepository;
    private final ClusterStorageConfigRepository configRepository;
    private final ClusterStationStorageRepository placementRepository;
    private final StationStorageConfigRepository stationConfigRepository;
    private final StationMoves moves;
    private final StorageBackendResolver resolver;
    private final BackendValidation validation;
    private final StorageProbeService probeService;
    private final StorageBackendAuditService auditService;
    private final RetiredVersions retiredVersions;

    @Inject
    public ClusterStorageBackendService(
            ClusterRepository clusterRepository,
            StationRepository stationRepository,
            ClusterStorageConfigRepository configRepository,
            ClusterStationStorageRepository placementRepository,
            StationStorageConfigRepository stationConfigRepository,
            StationMoves moves,
            StorageBackendResolver resolver,
            BackendValidation validation,
            StorageProbeService probeService,
            StorageBackendAuditService auditService,
            RetiredVersions retiredVersions) {
        this.clusterRepository = clusterRepository;
        this.stationRepository = stationRepository;
        this.configRepository = configRepository;
        this.placementRepository = placementRepository;
        this.stationConfigRepository = stationConfigRepository;
        this.moves = moves;
        this.resolver = resolver;
        this.validation = validation;
        this.probeService = probeService;
        this.auditService = auditService;
        this.retiredVersions = retiredVersions;
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
     * What the association decided, and the storage it stands on with nothing secret in it.
     *
     * @param clusterId the association
     * @return the answer for its storage screen
     */
    public PolicyResponse describe(int clusterId) {
        Policy policy = findPolicy(clusterId);
        ClusterStorageConfig current = policy.current();
        return new PolicyResponse(
                policy.reach(), policy.locked(), current == null ? null : BackendRedaction.summaryOf(current.config()));
    }

    /**
     * Sets how far the association's storage reaches and whether its stations may point themselves anywhere.
     *
     * <p>Reaching anywhere at all needs somewhere to reach: an association that has configured no storage
     * cannot decide that its files, or its stations', belong on it.
     *
     * @param actor     who decided
     * @param clusterId the association
     * @param reach     how far its storage reaches
     * @param locked    whether only the association may move a station
     */
    public void setPolicy(Actor actor, int clusterId, @Nullable ClusterBackendReach reach, boolean locked) {
        Cluster cluster = requireCluster(clusterId);
        ClusterBackendReach checked = recordingRefusals(actor, clusterId, () -> {
            if (reach == null) throw ClusterRefusal.CLUSTER_STORAGE_POLICY_NEEDS_A_REACH.raise();
            if (reach != ClusterBackendReach.NONE
                    && configRepository.findCurrent(clusterId).isEmpty()) {
                throw ClusterRefusal.CLUSTER_STORAGE_REACH_WITHOUT_STORAGE.raise();
            }
            return reach;
        });
        writePolicy(actor, cluster, new StorageBackendAuditService.Policy(checked, locked));
    }

    /**
     * Whether the storage the association saved answers, written to its history.
     *
     * @param actor     who asked
     * @param clusterId the association
     * @return the answer, with the reason of a failure kept in the log
     */
    public ProbeResult probe(Actor actor, int clusterId) {
        ClusterStorageConfig current =
                configRepository.findCurrent(requireCluster(clusterId).id()).orElse(null);
        if (current == null) throw ClusterRefusal.CLUSTER_KEEPS_NO_STORAGE.raise();
        return probeService.probeSaved(actor, new Owner.Association(clusterId), current.config());
    }

    /**
     * Whether storage the association has not saved yet would answer, without storing or recording anything.
     *
     * @param clusterId the association
     * @param request   the storage as the form describes it
     * @return the answer, with the reason of a failure kept in the log
     */
    public ProbeResult probe(int clusterId, BackendRequest request) {
        requireCluster(clusterId);
        return probeService.probe(new Owner.Association(clusterId), validation.toConfig(request));
    }

    /**
     * Saves the storage the association typed in, checked as every owner's storage is.
     *
     * @param actor     who saved it
     * @param clusterId the association
     * @param request   the storage; S3, SMB or SFTP
     * @return what the association decided and stands on afterwards
     */
    public PolicyResponse apply(Actor actor, int clusterId, BackendRequest request) {
        requireCluster(clusterId);
        StationStorageBackendConfig config = recordingRefusals(actor, clusterId, () -> validation.toConfig(request));
        setBackend(actor, clusterId, config);
        return describe(clusterId);
    }

    /**
     * Saves the association's storage, as a new version or as new credentials for the one it has.
     *
     * <p>Two configurations naming the same destination are the same storage with a new secret, and rotating
     * a secret must not copy a terabyte: nobody moves, and only the backend built for it is rebuilt, whoever
     * still remembers it. Anything else is somewhere else, so it becomes the current version, everybody
     * standing on the old one is out of place until they are carried across, and an old version nobody
     * stands on goes with its credentials.
     *
     * @param actor     who saved it
     * @param clusterId the association
     * @param config    the backend, with its credentials already encrypted
     * @return the version that is current afterwards
     */
    public ClusterStorageConfig setBackend(Actor actor, int clusterId, StationStorageBackendConfig config) {
        requireCluster(clusterId);
        Owner owner = new Owner.Association(clusterId);
        Optional<ClusterStorageConfig> current = configRepository.findCurrent(clusterId);
        @Nullable
        BackendSummary before = current.map(version -> BackendRedaction.summaryOf(version.config()))
                .orElse(null);
        BackendSummary after = BackendRedaction.summaryOf(config);
        if (current.isPresent() && current.get().config().destinationKey().equals(config.destinationKey())) {
            configRepository.updateInPlace(current.get().id(), config);
            resolver.invalidateClusterVersion(current.get().id());
            auditService.recordConfigChange(actor, owner, StorageAuditAction.UPDATED, before, after);
            log.info("Cluster {} storage kept its destination and took new credentials", clusterId);
            return configRepository.findById(current.get().id()).orElseThrow();
        }
        ClusterStorageConfig version = configRepository.insertCurrent(clusterId, config);
        StorageAuditAction action = before == null ? StorageAuditAction.CREATED : StorageAuditAction.UPDATED;
        auditService.recordConfigChange(actor, owner, action, before, after);
        retiredVersions.sweep(clusterId);
        log.info("Cluster {} storage points somewhere new, version {}", clusterId, version.id());
        return version;
    }

    /**
     * Gives up the association's storage.
     *
     * <p>The versions people are standing on stay, because the alternative is a station pointed at nothing.
     * They are out of place from this moment and the lock does not hold them there: a freeze cannot freeze a
     * station onto storage its association no longer keeps. A version nobody stands on goes at once.
     *
     * @param actor     who gave it up
     * @param clusterId the association
     */
    public void dropBackend(Actor actor, int clusterId) {
        Cluster cluster = requireCluster(clusterId);
        configRepository
                .findCurrent(clusterId)
                .ifPresent(current -> auditService.recordConfigChange(
                        actor,
                        new Owner.Association(clusterId),
                        StorageAuditAction.DELETED,
                        BackendRedaction.summaryOf(current.config()),
                        null));
        configRepository.retireCurrent(clusterId);
        writePolicy(actor, cluster, new StorageBackendAuditService.Policy(ClusterBackendReach.NONE, false));
        retiredVersions.sweep(clusterId);
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
     * Carries one station's bytes to where its association's policy says they belong. Every check is made
     * before the move is written down, so a refused move never reads as one that started.
     *
     * @param actor     who asked
     * @param clusterId the association
     * @param stationId the station of it being moved
     * @return what was carried
     * @throws dev.chojo.ember.feature.storage.migration.MigrationException when the copy cannot be made
     */
    public StorageMigrationService.MigrationResult moveStation(Actor actor, int clusterId, int stationId) {
        Cluster cluster = requireCluster(clusterId);
        Station station = requireStationOf(cluster, stationId);
        Policy policy = findPolicy(clusterId);
        Placement placement = placementOf(station, policy, station.id() == cluster.homeStationId());
        if (placement.inPlace()) {
            throw ClusterRefusal.CLUSTER_STORAGE_STATION_ALREADY_IN_PLACE.raise();
        }
        Destination destination = destinationFor(placement.expected(), policy);
        return moves.move(actor, new Owner.Association(clusterId), stationId, destination);
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
        ClusterStorageConfig current = policy.current();
        if (policy.reach() != ClusterBackendReach.EVERY_STATION || current == null) return;
        boolean bringsOwn = stationConfigRepository.findOne(stationId).isPresent();
        boolean optsOut = bringsOwn && !policy.locked();
        if (optsOut) return;

        moves.move(
                Actor.system(JOIN),
                new Owner.Association(clusterId),
                stationId,
                new Destination.Cluster(clusterId, current.id(), current.config()));
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

        moves.move(
                Actor.system(RELEASE), new Owner.Association(clusterId), stationId, new Destination.InstanceDefault());
        log.info("Station {} took its files off cluster {} storage on the way out", stationId, clusterId);
    }

    /** Runs a check, writing a refusal to the association's history before it is answered. */
    private <T> T recordingRefusals(Actor actor, int clusterId, Supplier<T> check) {
        try {
            return check.get();
        } catch (RefusalResponse refused) {
            auditService.recordRejected(actor, new Owner.Association(clusterId), refused);
            throw refused;
        }
    }

    private void writePolicy(Actor actor, Cluster cluster, StorageBackendAuditService.Policy after) {
        var before =
                new StorageBackendAuditService.Policy(cluster.storageBackendReach(), cluster.storageBackendLocked());
        clusterRepository.setStorageBackendPolicy(cluster.id(), after.reach(), after.locked());
        if (!before.equals(after)) auditService.recordPolicyChange(actor, cluster.id(), before, after);
        log.info(
                "Cluster {} storage reaches {} and is {}",
                cluster.id(),
                after.reach(),
                after.locked() ? "locked" : "open");
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
            if (policy.locked()) return Expected.WHEREVER_IT_IS;
            return bringsOwn ? Expected.ITS_OWN : Expected.INSTANCE_DEFAULT;
        }
        boolean optsOut = bringsOwn && !policy.locked();
        if (optsOut) return Expected.ITS_OWN;
        return Expected.THE_CLUSTERS;
    }

    private Placement placementOf(Station station, Policy policy, boolean isHome) {
        Optional<ClusterStationStorage> placed = placementRepository.findByStation(station.id());
        boolean bringsOwn = stationConfigRepository.findOne(station.id()).isPresent();
        Actual actual = bringsOwn ? Actual.ITS_OWN : placed.isPresent() ? Actual.THE_CLUSTERS : Actual.INSTANCE_DEFAULT;
        Expected expected = expectedFor(station, policy, isHome);

        ClusterStorageConfig current = policy.current();
        boolean onCurrent = current != null
                && placed.map(row -> row.configId() == current.id()).orElse(false);
        boolean inPlace =
                switch (expected) {
                    case WHEREVER_IT_IS -> true;
                    case ITS_OWN -> actual == Actual.ITS_OWN;
                    case INSTANCE_DEFAULT -> actual == Actual.INSTANCE_DEFAULT;
                    case THE_CLUSTERS -> actual == Actual.THE_CLUSTERS && onCurrent;
                };
        return new Placement(station.id(), station.uid(), station.name(), isHome, actual, expected, inPlace);
    }

    private Destination destinationFor(Expected expected, Policy policy) {
        return switch (expected) {
            case THE_CLUSTERS -> {
                ClusterStorageConfig current = policy.current();
                if (current == null) throw ClusterRefusal.CLUSTER_STORAGE_NONE_TO_MOVE_ONTO.raise();
                yield new Destination.Cluster(current.clusterId(), current.id(), current.config());
            }
            case INSTANCE_DEFAULT, WHEREVER_IT_IS -> new Destination.InstanceDefault();
            case ITS_OWN -> throw ClusterRefusal.CLUSTER_STORAGE_STATION_OWN_STORAGE.raise();
        };
    }

    private Cluster requireCluster(int clusterId) {
        return clusterRepository.findById(clusterId).orElseThrow(ClusterRefusal.CLUSTER_STORAGE_CLUSTER_GONE::raise);
    }

    private Station requireStationOf(Cluster cluster, int stationId) {
        Station station =
                stationRepository.findById(stationId).orElseThrow(ClusterRefusal.CLUSTER_STORAGE_STATION_GONE::raise);
        boolean belongs = station.id() == cluster.homeStationId()
                || (station.clusterId() != null && station.clusterId() == cluster.id());
        if (!belongs) throw ClusterRefusal.CLUSTER_STORAGE_STATION_NOT_IN_CLUSTER.raise();
        return station;
    }

    /**
     * What an association decided about storage, and what it is standing on.
     *
     * @param reach   how far its own storage reaches
     * @param locked  whether only it may move a station
     * @param current the version new placements are carried to, or {@code null} when it keeps none
     */
    public record Policy(
            ClusterBackendReach reach,
            boolean locked,
            @Nullable ClusterStorageConfig current) {}

    /**
     * What the association decided, and the storage it is standing on with nothing secret in it.
     *
     * @param reach   how far its own storage reaches
     * @param locked  whether only it may move a station
     * @param backend the storage it keeps, or {@code null} when it keeps none
     */
    public record PolicyResponse(
            ClusterBackendReach reach,
            boolean locked,
            @Nullable BackendSummary backend) {}

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
