/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterApplication;
import dev.chojo.ember.feature.cluster.entity.ClusterApplicationStatus;
import dev.chojo.ember.feature.cluster.service.ClusterApplicationService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
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
import java.util.List;
import java.util.UUID;

/**
 * The cluster seen from a station: which one it answers to, and how its owner asks to join one.
 *
 * <p>Deliberately outside the cluster's own routes. The people who need this hold no cluster membership at
 * all, so a permission from the cluster's side could never let them in. What guards it is the station's own
 * permission, and beyond that the service checks that the caller really is the station's owner: applying is
 * a decision about the station as a whole, not one a manager makes on the side.
 */
@Singleton
public class StationClusterRoutes implements Routes {
    private final ClusterService clusterService;
    private final ClusterApplicationService applicationService;

    @Inject
    public StationClusterRoutes(ClusterService clusterService, ClusterApplicationService applicationService) {
        this.clusterService = clusterService;
        this.applicationService = applicationService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/station/cluster", this::get, StationPermission.LOGIN);
        routes.get(prefix + "/station/cluster/available", this::listAvailable, StationPermission.STATION_GENERAL);
        routes.post(prefix + "/station/cluster/applications", this::apply, StationPermission.STATION_GENERAL);
        routes.delete(prefix + "/station/cluster/applications/{id}", this::withdraw, StationPermission.STATION_GENERAL);
    }

    @OpenApi(
            path = "/api/v1/station/cluster",
            methods = HttpMethod.GET,
            summary = "The cluster this station belongs to, and what it has asked",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationClusterResponse.class)))
    private void get(Context ctx) {
        int stationId = requireStation(ctx);
        Cluster cluster = clusterService.findByStation(stationId).orElse(null);
        List<ClusterApplicationView> applications = applicationService.findByStation(stationId).stream()
                .map(this::toView)
                .toList();
        ctx.json(new StationClusterResponse(
                cluster != null ? cluster.uid() : null,
                cluster != null ? cluster.name() : null,
                cluster != null ? cluster.description() : null,
                applications));
    }

    @OpenApi(
            path = "/api/v1/station/cluster/available",
            methods = HttpMethod.GET,
            summary = "The clusters this station could ask to join",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = AvailableClusterResponse[].class)))
    private void listAvailable(Context ctx) {
        requireStation(ctx);
        ctx.json(clusterService.findAll().stream()
                .map(cluster -> new AvailableClusterResponse(cluster.uid(), cluster.name(), cluster.description()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/station/cluster/applications",
            methods = HttpMethod.POST,
            summary = "Ask a cluster to take this station on",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ApplyRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ClusterApplicationView.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void apply(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int stationId = requireStation(ctx);
        var request = ctx.bodyAsClass(ApplyRequest.class);
        Cluster cluster = clusterService
                .findByUid(parseUid(request.clusterUid()))
                .orElseThrow(() -> new NotFoundResponse("No such cluster"));

        ClusterApplication application = applicationService.apply(cluster.id(), stationId, requireMember(session));
        ctx.status(HttpStatus.CREATED).json(toView(application));
    }

    @OpenApi(
            path = "/api/v1/station/cluster/applications/{id}",
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            methods = HttpMethod.DELETE,
            summary = "Take back a request to join",
            tags = {"Cluster"},
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void withdraw(Context ctx) {
        UserSession session = UserSession.from(ctx);
        requireStation(ctx);
        applicationService.withdraw(ctx.pathParamAsClass("id", Integer.class).get(), requireMember(session));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private ClusterApplicationView toView(ClusterApplication application) {
        String clusterName = clusterService
                .findById(application.clusterId())
                .map(Cluster::name)
                .orElse(null);
        return new ClusterApplicationView(
                application.id(),
                clusterName,
                application.requestedAt(),
                application.status(),
                application.denyReason(),
                application.resolvedAt());
    }

    private static int requireStation(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer stationId = session.stationId();
        if (stationId == null) throw new BadRequestResponse("No station selected");
        return stationId;
    }

    private static int requireMember(UserSession session) {
        if (session.member() == null) throw new BadRequestResponse("No station selected");
        return session.member().id();
    }

    private static UUID parseUid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestResponse("Not a cluster identity: " + raw);
        }
    }

    public record ApplyRequest(String clusterUid) {}

    /**
     * @param clusterUid the cluster this station answers to, or {@code null} when it answers to nobody
     */
    public record StationClusterResponse(
            UUID clusterUid,
            String clusterName,
            String clusterDescription,
            List<ClusterApplicationView> applications) {}

    public record AvailableClusterResponse(UUID uid, String name, String description) {}

    public record ClusterApplicationView(
            int id,
            String clusterName,
            Instant requestedAt,
            ClusterApplicationStatus status,
            String denyReason,
            Instant resolvedAt) {}
}
