/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterBackendReach;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.audit.StorageAuditOutcome;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.ClusterStorageConfigRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.BackendOverrideRequest;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.BackendOverrideSummary;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.MigrationResponse;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.ProbeResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * A station picking where its own files are kept: its own remote storage, the instance's, or its
 * association's, and the files carried over whenever that changes.
 *
 * <p>Credentials are encrypted before they are stored and never read back out.
 */
@Singleton
public class StationStorageBackendService {
    /**
     * What a failed probe tells the client. The probe opens a connection to an address from the request,
     * so a verbatim failure would tell apart a refused connection, a timeout and a protocol error, which
     * is a port scan of whatever the address validator does not cover. The real cause goes to the log,
     * where the operator can still read it.
     *
     * <p>What is said instead names the three things that are actually wrong when this happens and says
     * where the rest of it is, because an operator who has mistyped a key needs to know to go and look
     * rather than to conclude that Ember is broken.
     */
    static final String PROBE_FAILED =
            "The storage backend would not accept these settings. The address, the credentials or the target "
                    + "may be wrong, and the exact reason is in the instance log: it is kept there because this "
                    + "endpoint opens a connection to an address the request names";

    private static final Logger log = LoggerFactory.getLogger(StationStorageBackendService.class);

    private final StationStorageConfigRepository repository;
    private final StationRepository stationRepository;
    private final ClusterRepository clusterRepository;
    private final ClusterStorageConfigRepository clusterConfigRepository;
    private final ClusterStationStorageRepository placementRepository;
    private final StorageBackendResolver resolver;
    private final StorageBackendPayloads payloads;
    private final StorageProbeService probeService;
    private final StorageMigrationService migrationService;
    private final StorageBackendAuditService auditService;

    @Inject
    public StationStorageBackendService(
            StationStorageConfigRepository repository,
            StationRepository stationRepository,
            ClusterRepository clusterRepository,
            ClusterStorageConfigRepository clusterConfigRepository,
            ClusterStationStorageRepository placementRepository,
            StorageBackendResolver resolver,
            StorageBackendPayloads payloads,
            StorageProbeService probeService,
            StorageMigrationService migrationService,
            StorageBackendAuditService auditService) {
        this.repository = repository;
        this.stationRepository = stationRepository;
        this.clusterRepository = clusterRepository;
        this.clusterConfigRepository = clusterConfigRepository;
        this.placementRepository = placementRepository;
        this.resolver = resolver;
        this.payloads = payloads;
        this.probeService = probeService;
        this.migrationService = migrationService;
        this.auditService = auditService;
    }

    /**
     * The station a session stands for, refused when it names none or one that is gone.
     *
     * @param stationId the session's station, or null
     * @return the station
     */
    public int requireStation(Integer stationId) {
        if (stationId == null) throw Refusal.NO_STATION_CHOSEN_FOR_STORAGE.raise();
        if (stationRepository.findById(stationId).isEmpty()) throw Refusal.STORAGE_STATION_NOT_HERE.raise();
        return stationId;
    }

    /**
     * Where this station's files are, who decided that, and whether the station may change it.
     *
     * <p>A station under an association may be standing on the association's storage, and may have been
     * put there by somebody else. A station manager wondering why an upload failed should not have to ask
     * who to ask, so the answer says what is behind the station, on whose word, and what is still theirs
     * to do.
     *
     * @param stationId the station
     * @return where its files are
     */
    public BackendOverrideResponse describe(int stationId) {
        BackendOverrideSummary own = repository
                .findOne(stationId)
                .map(row -> StorageBackendPayloads.toSummary(row.config()))
                .orElse(null);
        Optional<Cluster> cluster = clusterRepository.findByStation(stationId);
        BackendOverrideSummary onCluster = placementRepository
                .findConfigForStation(stationId)
                .map(StorageBackendPayloads::toSummary)
                .orElse(null);
        boolean clusterOffersStorage = cluster.filter(c -> c.storageBackendReach() == ClusterBackendReach.EVERY_STATION)
                .flatMap(c -> clusterConfigRepository.findCurrent(c.id()))
                .isPresent();
        return new BackendOverrideResponse(
                resolver.instanceDefault().type(),
                own,
                onCluster,
                cluster.map(Cluster::name).orElse(null),
                clusterOffersStorage,
                cluster.map(Cluster::storageBackendLocked).orElse(false));
    }

    /**
     * Moves the station's files to where it asks and makes that where they are kept.
     *
     * <p>A local request drops the station's own storage and carries the files back to the instance's; a
     * cluster request puts the station on its association's. For a station without files the copy is
     * empty, which makes this the first-time setup as well.
     *
     * @param actor     who asked
     * @param stationId the station
     * @param request   where to
     * @return what was carried over
     */
    public MigrationResponse apply(Actor actor, int stationId, BackendOverrideRequest request) {
        requireStationMayChooseItsOwn(stationId);
        StationStorageBackendConfig existing = repository
                .findOne(stationId)
                .map(StationStorageConfigRepository.Row::config)
                .orElse(null);
        StorageMigrationService.Destination destination = destinationFor(stationId, request);
        StationStorageBackendConfig target =
                destination instanceof StorageMigrationService.Destination.Own own ? own.config() : null;

        auditService.recordMigration(actor, stationId, StorageAuditAction.MIGRATION_STARTED, existing, target, null);
        StorageMigrationService.MigrationResult result;
        try {
            result = migrationService.moveStation(stationId, destination);
        } catch (MigrationException e) {
            auditService.recordMigration(
                    actor, stationId, StorageAuditAction.MIGRATION_FAILED, existing, target, e.getMessage());
            log.warn("Storage move for station {} failed", stationId, e);
            throw Refusal.STATION_STORAGE_MOVE_NOT_DONE.raise();
        }
        auditService.recordMigration(actor, stationId, StorageAuditAction.MIGRATION_COMPLETED, existing, target, null);
        return new MigrationResponse(
                result.totalKeys(), result.copied(), result.skipped(), result.deleted(), result.copiedBytes());
    }

    /**
     * Whether the station's own storage answers, written to its history.
     *
     * @param actor     who asked
     * @param stationId the station
     * @return the answer, with the reason of a failure kept in the log
     */
    public ProbeResult probe(Actor actor, int stationId) {
        var row = repository.findOne(stationId).orElseThrow(Refusal.STATION_KEEPS_NO_STORAGE_OF_ITS_OWN::raise);
        ProbeResult result = masked(stationId, probeService.probe(row.config()));
        auditService.recordProbe(
                actor,
                stationId,
                result.healthy() ? StorageAuditOutcome.OK : StorageAuditOutcome.FAILED,
                result.error());
        return result;
    }

    /**
     * Whether storage the station has not saved yet would answer, without storing or recording anything.
     *
     * @param stationId the station
     * @param request   the storage as the form describes it
     * @return the answer, with the reason of a failure kept in the log
     */
    public ProbeResult probe(int stationId, BackendOverrideRequest request) {
        return masked(stationId, probeService.probe(payloads.toEntity(request)));
    }

    private static ProbeResult masked(int stationId, ProbeResult result) {
        if (result.error() == null) return result;
        log.warn("Storage backend probe for station {} failed: {}", stationId, result.error());
        return new ProbeResult(result.healthy(), PROBE_FAILED, result.checkedAt());
    }

    /**
     * Where this station is asking to go.
     *
     * <p>Its association's storage is not something the station describes: it is looked up, so a station
     * cannot type its way onto somewhere the association never named.
     */
    private StorageMigrationService.Destination destinationFor(int stationId, BackendOverrideRequest request) {
        return switch (request) {
            case StorageBackendPayloads.LocalRequest ignored ->
                new StorageMigrationService.Destination.InstanceDefault();
            case StorageBackendPayloads.ClusterRequest ignored -> clusterDestination(stationId);
            default -> new StorageMigrationService.Destination.Own(payloads.toEntity(request));
        };
    }

    private StorageMigrationService.Destination clusterDestination(int stationId) {
        Cluster cluster = clusterRepository
                .findByStation(stationId)
                .orElseThrow(Refusal.STATION_ANSWERS_TO_NO_ASSOCIATION::raise);
        if (cluster.storageBackendReach() != ClusterBackendReach.EVERY_STATION) {
            throw Refusal.ASSOCIATION_KEEPS_NO_STORAGE_FOR_STATIONS.raise();
        }
        var current = clusterConfigRepository
                .findCurrent(cluster.id())
                .orElseThrow(Refusal.ASSOCIATION_KEEPS_NO_STORAGE_OF_ITS_OWN::raise);
        return new StorageMigrationService.Destination.Cluster(cluster.id(), current.id(), current.config());
    }

    /**
     * A locked association decides where its stations' files are, and a disabled button is not a permission.
     */
    private void requireStationMayChooseItsOwn(int stationId) {
        boolean locked = clusterRepository
                .findByStation(stationId)
                .map(Cluster::storageBackendLocked)
                .orElse(false);
        if (locked) throw Refusal.ASSOCIATION_DECIDES_WHERE_FILES_ARE_KEPT.raise();
    }

    /**
     * What is behind this station's files, on whose word, and what is still the station's to change.
     *
     * @param instanceDefault      the kind of backend the instance provides
     * @param override             a backend the station brought itself, or {@code null}
     * @param clusterBackend       the association's storage its files were carried to, or {@code null}
     * @param clusterName          the association it answers to, or {@code null}
     * @param clusterOffersStorage whether that association keeps storage its stations may move onto
     * @param locked               whether the association decides, which makes this screen read-only
     */
    public record BackendOverrideResponse(
            StorageBackendType instanceDefault,
            BackendOverrideSummary override,
            BackendOverrideSummary clusterBackend,
            String clusterName,
            boolean clusterOffersStorage,
            boolean locked) {}
}
