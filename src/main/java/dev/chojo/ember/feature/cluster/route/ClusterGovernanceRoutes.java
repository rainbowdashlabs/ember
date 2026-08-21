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
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.service.ClusterGovernanceService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What the cluster decides on behalf of its stations: which modules they may use, how they look, and how much
 * room they have.
 *
 * <p>Three permissions rather than one, because the three are different jobs. Somebody who chooses the
 * colours has no business handing out storage.
 */
@Singleton
public class ClusterGovernanceRoutes implements Routes {
    private final ClusterService clusterService;
    private final ClusterGovernanceService governanceService;

    @Inject
    public ClusterGovernanceRoutes(ClusterService clusterService, ClusterGovernanceService governanceService) {
        this.clusterService = clusterService;
        this.governanceService = governanceService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/modules", this::getModules, ClusterPermission.CLUSTER_MODULES);
        routes.put(prefix + "/cluster/modules", this::setModules, ClusterPermission.CLUSTER_MODULES);
        routes.get(prefix + "/cluster/look-and-feel", this::getLookAndFeel, ClusterPermission.CLUSTER_LOOK_AND_FEEL);
        routes.put(prefix + "/cluster/look-and-feel", this::setLookAndFeel, ClusterPermission.CLUSTER_LOOK_AND_FEEL);
        routes.get(prefix + "/cluster/storage", this::getStorage, ClusterPermission.CLUSTER_STORAGE);
        routes.put(
                prefix + "/cluster/storage/stations/{stationUid}", this::setQuota, ClusterPermission.CLUSTER_STORAGE);
        routes.put(prefix + "/clusters/{clusterUid}/storage-pool", this::setPool, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/cluster/modules",
            methods = HttpMethod.GET,
            summary = "The modules this cluster denies its stations",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DeniedModulesResponse.class)))
    private void getModules(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(new DeniedModulesResponse(governanceService.findDeniedModules(cluster.id()).stream()
                .map(Enum::name)
                .sorted()
                .toList()));
    }

    @OpenApi(
            path = "/api/v1/cluster/modules",
            methods = HttpMethod.PUT,
            summary = "Set which modules this cluster denies its stations",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DeniedModulesResponse.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setModules(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(DeniedModulesResponse.class);
        Set<StationModule> modules = EnumSet.noneOf(StationModule.class);
        for (String name : request.deniedModules()) {
            try {
                modules.add(StationModule.valueOf(name));
            } catch (IllegalArgumentException e) {
                throw new BadRequestResponse("No such module: " + name);
            }
        }
        governanceService.setDeniedModules(cluster.id(), modules);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/look-and-feel",
            methods = HttpMethod.GET,
            summary = "The look this cluster hands its stations",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LookAndFeelRequest.class)))
    private void getLookAndFeel(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(new LookAndFeelRequest(
                cluster.defaultTheme(),
                cluster.customThemeColors(),
                cluster.defaultFeel() != null ? cluster.defaultFeel().name() : null,
                cluster.themeLocked(),
                cluster.colorsLocked(),
                cluster.feelLocked(),
                cluster.logoLocked()));
    }

    @OpenApi(
            path = "/api/v1/cluster/look-and-feel",
            methods = HttpMethod.PUT,
            summary = "Set the look this cluster hands its stations",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LookAndFeelRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setLookAndFeel(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(LookAndFeelRequest.class);
        governanceService.setLookAndFeel(
                cluster.id(),
                request.defaultTheme(),
                request.customThemeColors(),
                parseFeel(request.defaultFeel()),
                request.themeLocked(),
                request.colorsLocked(),
                request.feelLocked(),
                request.logoLocked());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/storage",
            methods = HttpMethod.GET,
            summary = "What the cluster has handed out and what that leaves",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PoolResponse.class)))
    private void getStorage(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var usage = governanceService.findPoolUsage(cluster.id());
        ctx.json(new PoolResponse(
                usage.poolBytes(),
                usage.handedOut(),
                usage.stations().stream()
                        .map(station -> new StationQuotaResponse(
                                station.stationUid(), station.stationName(), station.quotaBytes()))
                        .toList()));
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/stations/{stationUid}",
            pathParams = @OpenApiParam(name = "stationUid", type = String.class, required = true),
            methods = HttpMethod.PUT,
            summary = "Hand a station a share of the cluster's pool",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuotaRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setQuota(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(QuotaRequest.class);
        int stationId = clusterService.findStations(cluster.id()).stream()
                .filter(station -> station.uid().equals(parseUid(ctx.pathParam("stationUid"))))
                .findFirst()
                .orElseThrow(() -> new NotFoundResponse("No such station in this cluster"))
                .id();
        governanceService.setStationQuota(cluster.id(), stationId, request.quotaBytes());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/clusters/{clusterUid}/storage-pool",
            pathParams = @OpenApiParam(name = "clusterUid", type = String.class, required = true),
            methods = HttpMethod.PUT,
            summary = "Grant a cluster the pool it may hand out",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuotaRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setPool(Context ctx) {
        Cluster cluster = clusterService
                .findByUid(parseUid(ctx.pathParam("clusterUid")))
                .orElseThrow(() -> new NotFoundResponse("No such cluster"));
        var request = ctx.bodyAsClass(QuotaRequest.class);
        governanceService.setStoragePool(cluster.id(), request.quotaBytes());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private Cluster requireActive(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer clusterId = session.clusterId();
        if (clusterId == null) throw new BadRequestResponse("No cluster selected");
        return clusterService.findById(clusterId).orElseThrow(NotFoundResponse::new);
    }

    private static ThemeFeel parseFeel(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ThemeFeel.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("No such feel: " + raw);
        }
    }

    private static UUID parseUid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Not an identity: " + raw);
        }
    }

    public record DeniedModulesResponse(List<String> deniedModules) {}

    /**
     * @param defaultFeel the feel by name, or {@code null} when the cluster has no opinion about it
     */
    public record LookAndFeelRequest(
            String defaultTheme,
            String customThemeColors,
            String defaultFeel,
            boolean themeLocked,
            boolean colorsLocked,
            boolean feelLocked,
            boolean logoLocked) {}

    /**
     * @param quotaBytes the new figure, or {@code null} to hand it back to the instance default
     */
    public record QuotaRequest(Long quotaBytes) {}

    public record StationQuotaResponse(UUID stationUid, String stationName, Long quotaBytes) {}

    /**
     * @param poolBytes the whole the cluster may hand out, or {@code null} when the instance set no cap
     */
    public record PoolResponse(Long poolBytes, long handedOut, List<StationQuotaResponse> stations) {}
}
