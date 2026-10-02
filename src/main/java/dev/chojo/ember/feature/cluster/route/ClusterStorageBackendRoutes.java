/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterBackendReach;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.cluster.service.ClusterStationMoveService;
import dev.chojo.ember.feature.cluster.service.ClusterStorageBackendService;
import dev.chojo.ember.feature.cluster.service.ClusterStorageBackendService.PolicyResponse;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.storage.core.BackendRequest;
import dev.chojo.ember.feature.storage.core.MigrationResponse;
import dev.chojo.ember.feature.storage.core.ProbeResult;
import dev.chojo.ember.feature.storage.service.StorageAuditLogService;
import dev.chojo.ember.feature.storage.service.StorageAuditLogService.AuditEntryResponse;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The storage an association keeps, which of its stations stand on it, and the history of both.
 *
 * <p>{@code CLUSTER_STORAGE} governs all of it and there is no step-up: this reaches the association's own
 * stations and nothing beyond them. The instance's own backend swap keeps its step-up, because that one moves
 * every station there is.
 */
@Singleton
public class ClusterStorageBackendRoutes implements Routes {
    private final ClusterService clusterService;
    private final ClusterStorageBackendService backendService;
    private final ClusterStationMoveService moveService;
    private final StorageAuditLogService auditLog;

    @Inject
    public ClusterStorageBackendRoutes(
            ClusterService clusterService,
            ClusterStorageBackendService backendService,
            ClusterStationMoveService moveService,
            StorageAuditLogService auditLog) {
        this.clusterService = clusterService;
        this.backendService = backendService;
        this.moveService = moveService;
        this.auditLog = auditLog;
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
        routes.get(prefix + "/cluster/storage/audit", this::listAudit, ClusterPermission.CLUSTER_STORAGE);
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/backend",
            methods = HttpMethod.GET,
            summary = "What this cluster decided about storage of its own, and what it is standing on",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PolicyResponse.class)))
    private void get(Context ctx) {
        ctx.json(backendService.describe(requireActive(ctx).id()));
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
        backendService.setPolicy(actor(ctx), cluster.id(), request.reach(), request.locked());
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
        ctx.json(backendService.probe(actor(ctx), cluster.id()));
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/backend/probe-config",
            methods = HttpMethod.POST,
            summary = "Whether storage that has not been saved yet answers",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BackendRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProbeResult.class)))
    private void probeConfig(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(backendService.probe(cluster.id(), ctx.bodyAsClass(BackendRequest.class)));
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/backend/apply",
            methods = HttpMethod.POST,
            summary = "Saves the cluster's storage, as a new version or as new credentials for the one it has",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BackendRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PolicyResponse.class)))
    private void apply(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(backendService.apply(actor(ctx), cluster.id(), ctx.bodyAsClass(BackendRequest.class)));
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/backend",
            methods = HttpMethod.DELETE,
            summary = "Gives up storage of the cluster's own, leaving whoever stands on it out of place",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "204"))
    private void drop(Context ctx) {
        Cluster cluster = requireActive(ctx);
        backendService.dropBackend(actor(ctx), cluster.id());
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
        ctx.json(moveService.move(actor, cluster.id(), ctx.pathParam("stationUid")));
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/audit",
            methods = HttpMethod.GET,
            summary = "The history of the cluster's storage: its own, its decisions and the moves it made",
            tags = {"Cluster"},
            queryParams = {
                @OpenApiParam(name = "before", type = String.class),
                @OpenApiParam(name = "limit", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AuditEntryResponse[].class)))
    private void listAudit(Context ctx) {
        Cluster cluster = requireActive(ctx);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(StorageAuditLogService.DEFAULT_LIMIT);
        ctx.json(auditLog.listForCluster(cluster.id(), ctx.queryParam("before"), limit));
    }

    private Cluster requireActive(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer clusterId = session.clusterId();
        if (clusterId == null) throw ClusterRefusal.NO_CLUSTER_CHOSEN_FOR_STORAGE_BACKEND.raise();
        return clusterService
                .findById(clusterId)
                .orElseThrow(ClusterRefusal.CLUSTER_NOT_HERE_FOR_STORAGE_BACKEND::raise);
    }

    private Actor actor(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.account() == null) throw ClusterRefusal.NO_ACCOUNT_IN_SESSION_FOR_STORAGE_MOVE.raise();
        Integer memberId = session.memberOpt().map(StationMember::id).orElse(null);
        return Actor.human(session.account().id(), memberId);
    }

    /**
     * What the cluster is deciding.
     */
    public record PolicyRequest(@Nullable ClusterBackendReach reach, boolean locked) {}

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
