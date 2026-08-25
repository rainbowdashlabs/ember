/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterBackendReach;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.audit.StorageAuditEntry;
import dev.chojo.ember.feature.storage.audit.StorageAuditOutcome;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.ClusterStorageConfigRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.repository.StorageBackendAuditRepository;
import dev.chojo.ember.feature.storage.route.StorageBackendPayloads.BackendOverrideRequest;
import dev.chojo.ember.feature.storage.route.StorageBackendPayloads.BackendOverrideSummary;
import dev.chojo.ember.feature.storage.route.StorageBackendPayloads.MigrationResponse;
import dev.chojo.ember.feature.storage.route.StorageBackendPayloads.ProbeResult;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageMigrationService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Station-scoped self-service routes for picking a remote storage backend. A station manager
 * can override the inherited instance default for the entire station without involving an
 * instance admin. The override covers every station-scoped movable category at once.
 * Credentials are encrypted with {@link CredentialCipher} before they reach the repository
 * and are never returned to clients in plaintext.
 */
@Singleton
public class StationStorageBackendRoutes implements Routes {
    /**
     * What a failed probe tells the client. The endpoint builds a connection to an address from the
     * request, so a verbatim failure would tell apart a refused connection, a timeout and a protocol
     * error, which is a port scan of whatever the address validator does not cover. The real cause
     * goes to the log, where the operator can still read it.
     */
    private static final String PROBE_FAILED = "The backend could not be reached with this configuration";

    private static final Logger log = LoggerFactory.getLogger(StationStorageBackendRoutes.class);

    private final StationStorageConfigRepository repository;
    private final StorageBackendFactory factory;
    private final StorageBackendResolver resolver;
    private final CredentialCipher credentialCipher;
    private final StationRepository stationRepository;
    private final StorageBackendAuditService auditService;
    private final StorageBackendAuditRepository auditRepository;
    private final StorageMigrationService migrationService;
    private final StorageBackendPayloads payloads;
    private final ClusterRepository clusterRepository;
    private final ClusterStorageConfigRepository clusterConfigRepository;
    private final ClusterStationStorageRepository placementRepository;

    @Inject
    public StationStorageBackendRoutes(
            StationStorageConfigRepository repository,
            StorageBackendFactory factory,
            StorageBackendResolver resolver,
            CredentialCipher credentialCipher,
            StationRepository stationRepository,
            StorageBackendAuditService auditService,
            StorageBackendAuditRepository auditRepository,
            StorageMigrationService migrationService,
            StorageBackendPayloads payloads,
            ClusterRepository clusterRepository,
            ClusterStorageConfigRepository clusterConfigRepository,
            ClusterStationStorageRepository placementRepository) {
        this.repository = repository;
        this.factory = factory;
        this.resolver = resolver;
        this.credentialCipher = credentialCipher;
        this.stationRepository = stationRepository;
        this.auditService = auditService;
        this.auditRepository = auditRepository;
        this.migrationService = migrationService;
        this.payloads = payloads;
        this.clusterRepository = clusterRepository;
        this.clusterConfigRepository = clusterConfigRepository;
        this.placementRepository = placementRepository;
    }

    static AuditEntryResponse toResponse(StorageAuditEntry entry) {
        return new AuditEntryResponse(
                entry.id(),
                entry.ts().toString(),
                entry.actorAccountId().orElse(null),
                entry.actorMemberId().orElse(null),
                entry.systemActor().orElse(null),
                entry.stationId().orElse(null),
                entry.action(),
                entry.oldConfig().orElse(null),
                entry.newConfig().orElse(null),
                entry.outcome(),
                entry.error().orElse(null));
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/station/storage/backend", this::get, StationPermission.STATION_ADMINISTRATOR);
        routes.post(prefix + "/station/storage/backend/probe", this::probe, StationPermission.STATION_ADMINISTRATOR);
        routes.post(
                prefix + "/station/storage/backend/probe-config",
                this::probeConfig,
                StationPermission.STATION_ADMINISTRATOR);
        routes.post(prefix + "/station/storage/backend/apply", this::apply, StationPermission.STATION_ADMINISTRATOR);
        routes.get(prefix + "/station/storage/audit", this::listAudit, StationPermission.STATION_ADMINISTRATOR);
    }

    /**
     * Where this station's files are, who decided that, and whether the station may change it.
     *
     * <p>A station under an association may be standing on the association's storage, and may have been put
     * there by somebody else. A station manager wondering why an upload failed should not have to ask who to
     * ask, so the answer says what is behind the station, on whose word, and what is still theirs to do.
     */
    private void get(Context ctx) {
        int stationId = sessionStationId(ctx);
        StorageBackendType instanceDefault = resolver.instanceDefault().type();
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
        boolean locked = cluster.map(Cluster::storageBackendLocked).orElse(false);

        ctx.json(new BackendOverrideResponse(
                instanceDefault,
                own,
                onCluster,
                cluster.map(Cluster::name).orElse(null),
                clusterOffersStorage,
                locked));
    }

    /**
     * Unified entry point for "save and apply": probes the target backend, migrates every
     * station-scoped movable category from the currently-resolved source backend onto it, and
     * atomically swaps the {@code station_storage_config} row when the migration succeeds. A
     * {@link StorageBackendPayloads.LocalRequest} target means "drop the override and move bytes back to the
     * instance default", and a {@link StorageBackendPayloads.ClusterRequest} means "put me on my
     * association's storage". For an empty source the copy phase is a no-op, so this path is also the
     * green-field setup flow - no separate save endpoint is needed.
     */
    private void apply(Context ctx) {
        Actor actor = actor(ctx);
        int stationId = sessionStationId(ctx);
        BackendOverrideRequest request = ctx.bodyAsClass(BackendOverrideRequest.class);
        requireStationMayChooseItsOwn(stationId);
        Optional<StationStorageBackendConfig> existing =
                repository.findOne(stationId).map(StationStorageConfigRepository.Row::config);
        StorageMigrationService.Destination destination = destinationFor(stationId, request);
        StationStorageBackendConfig target =
                destination instanceof StorageMigrationService.Destination.Own own ? own.config() : null;

        auditService.recordMigration(
                actor, stationId, StorageAuditAction.MIGRATION_STARTED, existing.orElse(null), target, null);
        StorageMigrationService.MigrationResult result;
        try {
            result = migrationService.moveStation(stationId, destination);
        } catch (MigrationException e) {
            auditService.recordMigration(
                    actor,
                    stationId,
                    StorageAuditAction.MIGRATION_FAILED,
                    existing.orElse(null),
                    target,
                    e.getMessage());
            throw new BadRequestResponse("Apply failed: " + e.getMessage());
        }
        auditService.recordMigration(
                actor, stationId, StorageAuditAction.MIGRATION_COMPLETED, existing.orElse(null), target, null);
        ctx.status(HttpStatus.OK)
                .json(new MigrationResponse(
                        result.totalKeys(), result.copied(), result.skipped(), result.deleted(), result.copiedBytes()));
    }

    private void probe(Context ctx) {
        Actor actor = actor(ctx);
        int stationId = sessionStationId(ctx);
        var row = repository
                .findOne(stationId)
                .orElseThrow(() -> new BadRequestResponse("No backend override configured for this station"));
        boolean healthy;
        String errorOrNull;
        Instant checkedAt;
        try (StorageBackend backend = factory.buildForStation(row.config())) {
            HealthStatus status = backend.probe();
            healthy = status.healthy();
            errorOrNull =
                    status.error().map(error -> probeFailure(stationId, error)).orElse(null);
            checkedAt = status.checkedAt();
        } catch (Exception e) {
            healthy = false;
            errorOrNull = probeFailure(stationId, e.getMessage());
            checkedAt = Instant.now();
        }
        auditService.recordProbe(
                actor, stationId, healthy ? StorageAuditOutcome.OK : StorageAuditOutcome.FAILED, errorOrNull);
        ctx.json(new ProbeResult(healthy, errorOrNull, checkedAt.toString()));
    }

    /**
     * Dry-run probe against an unsaved form payload - accepts a {@link BackendOverrideRequest},
     * builds a transient backend, runs {@link StorageBackend#probe()} and returns the result
     * without touching the repository or the audit log. The UI calls this from the
     * "Verbindung testen" button so admins can validate credentials before clicking Save.
     */
    private void probeConfig(Context ctx) {
        int stationId = sessionStationId(ctx);
        BackendOverrideRequest request = ctx.bodyAsClass(BackendOverrideRequest.class);
        StationStorageBackendConfig config = payloads.toEntity(request);
        boolean healthy;
        String errorOrNull;
        Instant checkedAt;
        try (StorageBackend backend = factory.buildForStation(config)) {
            HealthStatus status = backend.probe();
            healthy = status.healthy();
            errorOrNull =
                    status.error().map(error -> probeFailure(stationId, error)).orElse(null);
            checkedAt = status.checkedAt();
        } catch (Exception e) {
            healthy = false;
            errorOrNull = probeFailure(stationId, e.getMessage());
            checkedAt = Instant.now();
        }
        ctx.status(HttpStatus.OK).json(new ProbeResult(healthy, errorOrNull, checkedAt.toString()));
    }

    private static String probeFailure(int stationId, String cause) {
        log.warn("Storage backend probe for station {} failed: {}", stationId, cause);
        return PROBE_FAILED;
    }

    private void listAudit(Context ctx) {
        int stationId = sessionStationId(ctx);
        Optional<Instant> before = Optional.ofNullable(ctx.queryParam("before")).map(Instant::parse);
        int limit = Math.clamp(ctx.queryParamAsClass("limit", Integer.class).getOrDefault(50), 1, 200);
        List<AuditEntryResponse> entries = auditRepository.findByStation(stationId, before, limit).stream()
                .map(StationStorageBackendRoutes::toResponse)
                .toList();
        ctx.json(entries);
    }

    private int sessionStationId(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer stationId = session.stationId();
        if (stationId == null) {
            throw new ForbiddenResponse("No station selected");
        }
        if (stationRepository.findById(stationId).isEmpty()) {
            throw new BadRequestResponse("Unknown station");
        }
        return stationId;
    }

    private Actor actor(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.account() == null) throw new ForbiddenResponse("No account in session");
        Integer memberId = session.member() != null ? session.member().id() : null;
        return Actor.human(session.account().id(), memberId);
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
            case StorageBackendPayloads.ClusterRequest ignored -> {
                Cluster cluster = clusterRepository
                        .findByStation(stationId)
                        .orElseThrow(() -> new BadRequestResponse("This station answers to no association"));
                if (cluster.storageBackendReach() != ClusterBackendReach.EVERY_STATION) {
                    throw new BadRequestResponse("This association does not keep storage for its stations");
                }
                var current = clusterConfigRepository
                        .findCurrent(cluster.id())
                        .orElseThrow(() -> new BadRequestResponse("This association keeps no storage of its own"));
                yield new StorageMigrationService.Destination.Cluster(cluster.id(), current.id(), current.config());
            }
            default -> new StorageMigrationService.Destination.Own(payloads.toEntity(request));
        };
    }

    /**
     * A locked association decides where its stations' files are, and a disabled button is not a permission.
     */
    private void requireStationMayChooseItsOwn(int stationId) {
        boolean locked = clusterRepository
                .findByStation(stationId)
                .map(Cluster::storageBackendLocked)
                .orElse(false);
        if (locked) {
            throw new ForbiddenResponse("This station's association decides where its files are kept");
        }
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

    public record AuditEntryResponse(
            long id,
            String ts,
            Integer actorAccountId,
            Integer actorMemberId,
            String systemActor,
            Integer stationId,
            StorageAuditAction action,
            String oldConfig,
            String newConfig,
            StorageAuditOutcome outcome,
            String error) {}
}
