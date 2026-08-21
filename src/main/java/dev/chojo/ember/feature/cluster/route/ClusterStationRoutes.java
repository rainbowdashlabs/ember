/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterApplication;
import dev.chojo.ember.feature.cluster.entity.ClusterApplicationStatus;
import dev.chojo.ember.feature.cluster.service.ClusterApplicationService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.UUID;

/**
 * The stations a cluster has, and the ones asking to join it.
 *
 * <p>Both halves of the same question, so they sit together: a station is either one the cluster made, one
 * that asked and was let in, or one that is still waiting to hear.
 */
@Singleton
public class ClusterStationRoutes implements Routes {
    private final ClusterService clusterService;
    private final ClusterApplicationService applicationService;
    private final StationRepository stationRepository;

    @Inject
    public ClusterStationRoutes(
            ClusterService clusterService,
            ClusterApplicationService applicationService,
            StationRepository stationRepository) {
        this.clusterService = clusterService;
        this.applicationService = applicationService;
        this.stationRepository = stationRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/stations", this::listStations, ClusterPermission.USER);
        routes.post(prefix + "/cluster/stations", this::createStation, ClusterPermission.CLUSTER_STATIONS);
        routes.delete(
                prefix + "/cluster/stations/{stationUid}", this::releaseStation, ClusterPermission.CLUSTER_STATIONS);
        routes.get(prefix + "/cluster/applications", this::listApplications, ClusterPermission.CLUSTER_STATIONS);
        routes.put(prefix + "/cluster/applications/{id}", this::decide, ClusterPermission.CLUSTER_STATIONS);
    }

    @OpenApi(
            path = "/api/v1/cluster/stations",
            methods = HttpMethod.GET,
            summary = "List the stations that belong to this cluster",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterStationResponse[].class)))
    private void listStations(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(clusterService.findStations(cluster.id()).stream()
                .map(ClusterStationRoutes::toStationResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/stations",
            methods = HttpMethod.POST,
            summary = "Create a station that belongs to this cluster",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NewClusterStationRequest.class)),
            responses =
                    @OpenApiResponse(status = "201", content = @OpenApiContent(from = ClusterStationResponse.class)))
    private void createStation(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(NewClusterStationRequest.class);
        Station station = clusterService.createStation(cluster.id(), request.name());
        ctx.status(HttpStatus.CREATED).json(toStationResponse(station));
    }

    @OpenApi(
            path = "/api/v1/cluster/stations/{stationUid}",
            methods = HttpMethod.DELETE,
            summary = "Let a station go",
            tags = {"Cluster"},
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void releaseStation(Context ctx) {
        Cluster cluster = requireActive(ctx);
        Station station = stationRepository
                .findByUid(parseUid(ctx.pathParam("stationUid")))
                .orElseThrow(() -> new NotFoundResponse("No such station"));
        clusterService.releaseStation(cluster.id(), station.id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/applications",
            methods = HttpMethod.GET,
            summary = "List the stations asking to join this cluster",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = ClusterApplicationResponse[].class)))
    private void listApplications(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(applicationService.findByCluster(cluster.id()).stream()
                .map(this::toApplicationResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/applications/{id}",
            methods = HttpMethod.PUT,
            summary = "Approve or deny a station's request to join",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ApplicationDecisionRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void decide(Context ctx) {
        Cluster cluster = requireActive(ctx);
        UserSession session = UserSession.from(ctx);
        Integer decidedBy =
                session.clusterMember() != null ? session.clusterMember().id() : null;
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(ApplicationDecisionRequest.class);

        if (request.approve()) {
            applicationService.approve(id, cluster.id(), decidedBy);
        } else {
            applicationService.deny(id, cluster.id(), request.reason(), decidedBy);
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private Cluster requireActive(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer clusterId = session.clusterId();
        if (clusterId == null) throw new BadRequestResponse("No cluster selected");
        return clusterService.findById(clusterId).orElseThrow(NotFoundResponse::new);
    }

    private static UUID parseUid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Not a station identity: " + raw);
        }
    }

    private static ClusterStationResponse toStationResponse(Station station) {
        return new ClusterStationResponse(station.uid(), station.name(), station.publicSlug());
    }

    private ClusterApplicationResponse toApplicationResponse(ClusterApplication application) {
        String stationName = stationRepository
                .findById(application.stationId())
                .map(Station::name)
                .orElse(null);
        return new ClusterApplicationResponse(
                application.id(),
                stationName,
                application.requestedAt(),
                application.status(),
                application.denyReason(),
                application.resolvedAt());
    }

    public record NewClusterStationRequest(String name) {}

    /**
     * @param approve whether to let the station in; when false the reason is shown to its owner
     */
    public record ApplicationDecisionRequest(boolean approve, String reason) {}

    public record ClusterStationResponse(UUID uid, String name, String publicSlug) {}

    public record ClusterApplicationResponse(
            int id,
            String stationName,
            Instant requestedAt,
            ClusterApplicationStatus status,
            String denyReason,
            Instant resolvedAt) {}
}
