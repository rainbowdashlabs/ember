/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.StorageRefusal;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterBackendReach;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.core.BackendRedaction;
import dev.chojo.ember.feature.storage.core.BackendRequest;
import dev.chojo.ember.feature.storage.core.BackendSummary;
import dev.chojo.ember.feature.storage.core.BackendValidation;
import dev.chojo.ember.feature.storage.core.MigrationResponse;
import dev.chojo.ember.feature.storage.core.ProbeResult;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.ClusterStorageConfigRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageMigrationService.Destination;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * A station picking where its own files are kept: its own remote storage, the instance's, or its
 * association's, and the files carried over whenever that changes.
 *
 * <p>Credentials are encrypted before they are stored and never read back out. Every change is written to
 * the station's history, a refused one included.
 */
@Singleton
public class StationStorageBackendService {
    private static final Logger log = LoggerFactory.getLogger(StationStorageBackendService.class);

    private final StationStorageConfigRepository repository;
    private final StationRepository stationRepository;
    private final ClusterRepository clusterRepository;
    private final ClusterStorageConfigRepository clusterConfigRepository;
    private final ClusterStationStorageRepository placementRepository;
    private final StorageBackendResolver resolver;
    private final BackendValidation validation;
    private final StorageProbeService probeService;
    private final StationMoves moves;
    private final StorageBackendAuditService auditService;

    @Inject
    public StationStorageBackendService(
            StationStorageConfigRepository repository,
            StationRepository stationRepository,
            ClusterRepository clusterRepository,
            ClusterStorageConfigRepository clusterConfigRepository,
            ClusterStationStorageRepository placementRepository,
            StorageBackendResolver resolver,
            BackendValidation validation,
            StorageProbeService probeService,
            StationMoves moves,
            StorageBackendAuditService auditService) {
        this.repository = repository;
        this.stationRepository = stationRepository;
        this.clusterRepository = clusterRepository;
        this.clusterConfigRepository = clusterConfigRepository;
        this.placementRepository = placementRepository;
        this.resolver = resolver;
        this.validation = validation;
        this.probeService = probeService;
        this.moves = moves;
        this.auditService = auditService;
    }

    /**
     * The station a session stands for, refused when it is gone.
     *
     * @param stationId the session's station
     * @return the station
     */
    public int requireStation(int stationId) {
        if (stationRepository.findById(stationId).isEmpty()) throw StorageRefusal.STORAGE_STATION_NOT_HERE.raise();
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
        @Nullable
        BackendSummary own = repository
                .findOne(stationId)
                .map(row -> BackendRedaction.summaryOf(row.config()))
                .orElse(null);
        Optional<Cluster> cluster = clusterRepository.findByStation(stationId);
        @Nullable
        BackendSummary onCluster = placementRepository
                .findConfigForStation(stationId)
                .map(BackendRedaction::summaryOf)
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
    public MigrationResponse apply(Actor actor, int stationId, BackendRequest request) {
        Owner owner = new Owner.Station(stationId);
        @Nullable
        StationStorageBackendConfig existing = repository
                .findOne(stationId)
                .map(StationStorageConfigRepository.Row::config)
                .orElse(null);
        Destination destination;
        try {
            requireStationMayChooseItsOwn(stationId);
            destination = destinationFor(stationId, request);
        } catch (RefusalResponse refused) {
            auditService.recordRejected(actor, owner, refused);
            throw refused;
        }

        StorageMigrationService.MigrationResult result;
        try {
            result = moves.move(actor, owner, stationId, destination);
        } catch (MigrationException e) {
            log.warn("Storage move for station {} failed", stationId, e);
            throw StorageRefusal.STATION_STORAGE_MOVE_NOT_DONE.raise();
        }
        recordConfigChange(actor, owner, existing, destination);
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
        var row = repository.findOne(stationId).orElseThrow(StorageRefusal.STATION_KEEPS_NO_STORAGE_OF_ITS_OWN::raise);
        return probeService.probeSaved(actor, new Owner.Station(stationId), row.config());
    }

    /**
     * Whether storage the station has not saved yet would answer, without storing or recording anything.
     *
     * @param stationId the station
     * @param request   the storage as the form describes it
     * @return the answer, with the reason of a failure kept in the log
     */
    public ProbeResult probe(int stationId, BackendRequest request) {
        return probeService.probe(new Owner.Station(stationId), validation.toConfig(request));
    }

    /**
     * What a finished move changed about the station's own storage: set, replaced, or given up for the
     * instance's or the association's.
     */
    private void recordConfigChange(
            Actor actor, Owner owner, @Nullable StationStorageBackendConfig existing, Destination destination) {
        @Nullable BackendSummary before = existing == null ? null : BackendRedaction.summaryOf(existing);
        if (destination instanceof Destination.Own own) {
            StorageAuditAction action = existing == null ? StorageAuditAction.CREATED : StorageAuditAction.UPDATED;
            auditService.recordConfigChange(actor, owner, action, before, BackendRedaction.summaryOf(own.config()));
        } else if (before != null) {
            auditService.recordConfigChange(actor, owner, StorageAuditAction.DELETED, before, null);
        }
    }

    /**
     * Where this station is asking to go.
     *
     * <p>Its association's storage is not something the station describes: it is looked up, so a station
     * cannot type its way onto somewhere the association never named.
     */
    private Destination destinationFor(int stationId, BackendRequest request) {
        return switch (request) {
            case BackendRequest.LocalRequest ignored -> new Destination.InstanceDefault();
            case BackendRequest.ClusterStorageRequest ignored -> clusterDestination(stationId);
            default -> new Destination.Own(validation.toConfig(request));
        };
    }

    private Destination clusterDestination(int stationId) {
        Cluster cluster = clusterRepository
                .findByStation(stationId)
                .orElseThrow(StorageRefusal.STATION_ANSWERS_TO_NO_ASSOCIATION::raise);
        if (cluster.storageBackendReach() != ClusterBackendReach.EVERY_STATION) {
            throw StorageRefusal.ASSOCIATION_KEEPS_NO_STORAGE_FOR_STATIONS.raise();
        }
        var current = clusterConfigRepository
                .findCurrent(cluster.id())
                .orElseThrow(StorageRefusal.ASSOCIATION_KEEPS_NO_STORAGE_OF_ITS_OWN::raise);
        return new Destination.Cluster(cluster.id(), current.id(), current.config());
    }

    /**
     * A locked association decides where its stations' files are, and a disabled button is not a permission.
     */
    private void requireStationMayChooseItsOwn(int stationId) {
        boolean locked = clusterRepository
                .findByStation(stationId)
                .map(Cluster::storageBackendLocked)
                .orElse(false);
        if (locked) throw StorageRefusal.ASSOCIATION_DECIDES_WHERE_FILES_ARE_KEPT.raise();
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
            @Nullable BackendSummary override,
            @Nullable BackendSummary clusterBackend,
            @Nullable String clusterName,
            boolean clusterOffersStorage,
            boolean locked) {}
}
