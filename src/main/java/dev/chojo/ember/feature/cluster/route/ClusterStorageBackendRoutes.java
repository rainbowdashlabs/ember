/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterBackendReach;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.cluster.service.ClusterStorageBackendService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.route.StorageBackendPayloads;
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
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.UUID;

/**
 * The storage an association keeps, and which of its stations stand on it.
 *
 * <p>{@code CLUSTER_STORAGE} governs all of it and there is no step-up: this reaches the association's own
 * stations and nothing beyond them. The instance's own backend swap keeps its step-up, because that one moves
 * every station there is.
 */
@Singleton
public class ClusterStorageBackendRoutes implements Routes {
    private final ClusterService clusterService;
    private final ClusterStorageBackendService backendService;
    private final StorageBackendPayloads payloads;
    private final StorageBackendFactory factory;
    private final StationRepository stationRepository;
    private final StorageBackendAuditService auditService;

    @Inject
    public ClusterStorageBackendRoutes(
            ClusterService clusterService,
            ClusterStorageBackendService backendService,
            StorageBackendPayloads payloads,
            StorageBackendFactory factory,
            StationRepository stationRepository,
            StorageBackendAuditService auditService) {
        this.clusterService = clusterService;
        this.backendService = backendService;
        this.payloads = payloads;
        this.factory = factory;
        this.stationRepository = stationRepository;
        this.auditService = auditService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/storage/backend", this::get, ClusterPermission.CLUSTER_STORAGE);
        routes.put(prefix + "/cluster/storage/backend/policy", this::setPolicy, ClusterPermission.CLUSTER_STORAGE);
        routes.post(prefix + "/cluster/storage/backend/probe", this::probe, ClusterPermission.CLUSTER_STORAGE);
        routes.post(
                prefix + "/cluster/storage/backend/probe-config", this::probeConfig, ClusterPermission.CLUSTER_STORAGE);
        routes.post(prefix + "/cluster/storage/backend/apply", this::apply, ClusterPermission.CLUSTER_STORAGE);
        routes.delete(prefix + "/cluster/storage/backend", this::drop, ClusterPermission.CLUSTER_STORAGE);
        routes.get(
                prefix + "/cluster/storage/backend/placements",
                this::listPlacements,
                ClusterPermission.CLUSTER_STORAGE);
        routes.post(
                prefix + "/cluster/storage/backend/placements/{stationUid}/move",
                this::move,
                ClusterPermission.CLUSTER_STORAGE);
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/backend",
            methods = HttpMethod.GET,
            summary = "What this cluster decided about storage of its own, and what it is standing on",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PolicyResponse.class)))
    private void get(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var policy = backendService.findPolicy(cluster.id());
        ctx.json(new PolicyResponse(
                policy.reach(),
                policy.locked(),
                policy.current() == null
                        ? null
                        : StorageBackendPayloads.toSummary(policy.current().config())));
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/backend/policy",
            methods = HttpMethod.PUT,
            summary = "How far the cluster's storage reaches, and whether its stations may point themselves",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PolicyRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setPolicy(Context ctx) {
        Cluster cluster = requireActive(ctx);
        PolicyRequest request = ctx.bodyAsClass(PolicyRequest.class);
        if (request.reach() == null) throw new BadRequestResponse("reach is required");
        backendService.setPolicy(cluster.id(), request.reach(), request.locked());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/backend/probe",
            methods = HttpMethod.POST,
            summary = "Whether the storage the cluster saved answers",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProbeResult.class)))
    private void probe(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var policy = backendService.findPolicy(cluster.id());
        if (policy.current() == null) throw new BadRequestResponse("This cluster keeps no storage of its own");
        ctx.json(probeOf(policy.current().config()));
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/backend/probe-config",
            methods = HttpMethod.POST,
            summary = "Whether storage that has not been saved yet answers",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BackendOverrideRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProbeResult.class)))
    private void probeConfig(Context ctx) {
        requireActive(ctx);
        ctx.json(probeOf(payloads.toEntity(ctx.bodyAsClass(BackendOverrideRequest.class))));
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/backend/apply",
            methods = HttpMethod.POST,
            summary = "Saves the cluster's storage, as a new version or as new credentials for the one it has",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BackendOverrideRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PolicyResponse.class)))
    private void apply(Context ctx) {
        Cluster cluster = requireActive(ctx);
        StationStorageBackendConfig config = payloads.toEntity(ctx.bodyAsClass(BackendOverrideRequest.class));
        var version = backendService.setBackend(cluster.id(), config);
        var policy = backendService.findPolicy(cluster.id());
        ctx.json(new PolicyResponse(
                policy.reach(), policy.locked(), StorageBackendPayloads.toSummary(version.config())));
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/backend",
            methods = HttpMethod.DELETE,
            summary = "Gives up storage of the cluster's own, leaving whoever stands on it out of place",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "204"))
    private void drop(Context ctx) {
        Cluster cluster = requireActive(ctx);
        backendService.dropBackend(cluster.id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/backend/placements",
            methods = HttpMethod.GET,
            summary = "Every station of the cluster, where its files are and where they belong",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PlacementResponse[].class)))
    private void listPlacements(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(backendService.listPlacements(cluster.id()).stream()
                .map(placement -> new PlacementResponse(
                        placement.stationUid(),
                        placement.name(),
                        placement.homeStation(),
                        placement.actual(),
                        placement.expected(),
                        placement.inPlace()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/backend/placements/{stationUid}/move",
            methods = HttpMethod.POST,
            summary = "Carries one station's files to where the cluster's decision says they belong",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "stationUid", type = UUID.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MigrationResponse.class)))
    private void move(Context ctx) {
        Cluster cluster = requireActive(ctx);
        Actor actor = actor(ctx);
        int stationId = stationRepository
                .findByUid(parseUid(ctx.pathParam("stationUid")))
                .orElseThrow(() -> new NotFoundResponse("No such station"))
                .id();

        auditService.recordMigration(actor, stationId, StorageAuditAction.MIGRATION_STARTED, null, null, null);
        StorageMigrationService.MigrationResult result;
        try {
            result = backendService.moveStation(cluster.id(), stationId);
        } catch (MigrationException e) {
            auditService.recordMigration(
                    actor, stationId, StorageAuditAction.MIGRATION_FAILED, null, null, e.getMessage());
            throw new BadRequestResponse("Move failed: " + e.getMessage());
        }
        auditService.recordMigration(actor, stationId, StorageAuditAction.MIGRATION_COMPLETED, null, null, null);
        ctx.json(new MigrationResponse(
                result.totalKeys(), result.copied(), result.skipped(), result.deleted(), result.copiedBytes()));
    }

    private ProbeResult probeOf(StationStorageBackendConfig config) {
        try (StorageBackend backend = factory.buildForStation(config)) {
            HealthStatus status = backend.probe();
            return new ProbeResult(
                    status.healthy(),
                    status.error().orElse(null),
                    status.checkedAt().toString());
        } catch (Exception e) {
            return new ProbeResult(false, e.getMessage(), Instant.now().toString());
        }
    }

    private static UUID parseUid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Not a station identifier");
        }
    }

    private Cluster requireActive(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer clusterId = session.clusterId();
        if (clusterId == null) throw new BadRequestResponse("No cluster selected");
        return clusterService.findById(clusterId).orElseThrow(NotFoundResponse::new);
    }

    private Actor actor(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.account() == null) throw new ForbiddenResponse("No account in session");
        Integer memberId = session.member() != null ? session.member().id() : null;
        return Actor.human(session.account().id(), memberId);
    }

    /**
     * What the cluster decided, and the storage it is standing on with nothing secret in it.
     */
    public record PolicyResponse(ClusterBackendReach reach, boolean locked, BackendOverrideSummary backend) {}

    /**
     * What the cluster is deciding.
     */
    public record PolicyRequest(ClusterBackendReach reach, boolean locked) {}

    /**
     * One station of the cluster, where its files are and where they belong.
     */
    public record PlacementResponse(
            UUID stationUid,
            String name,
            boolean homeStation,
            ClusterStorageBackendService.Actual actual,
            ClusterStorageBackendService.Expected expected,
            boolean inPlace) {}
}
