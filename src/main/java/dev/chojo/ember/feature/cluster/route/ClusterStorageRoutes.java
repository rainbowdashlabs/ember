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
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.cluster.service.ClusterStorageQuotaService;
import dev.chojo.ember.feature.cluster.service.ClusterStorageQuotaService.Dimensions;
import dev.chojo.ember.feature.storage.entity.ClusterQuotaDefaults;
import dev.chojo.ember.feature.storage.entity.ClusterStorageQuotaPreset;
import dev.chojo.ember.feature.storage.entity.QuotaOrigin;
import dev.chojo.ember.feature.storage.entity.StationQuotas;
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

import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The room a cluster hands its stations: what it gives them by default, the tiers it keeps, and what each one
 * was granted.
 *
 * <p>The same system the instance has, one level down, and behind the same permission throughout: handing out
 * room is one job however it is spelled. The pool itself is the exception, because it is the instance that
 * decides how much a cluster has to give away in the first place.
 */
@Singleton
public class ClusterStorageRoutes implements Routes {
    private final ClusterService clusterService;
    private final ClusterStorageQuotaService quotaService;

    @Inject
    public ClusterStorageRoutes(ClusterService clusterService, ClusterStorageQuotaService quotaService) {
        this.clusterService = clusterService;
        this.quotaService = quotaService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/storage", this::getOverview, ClusterPermission.CLUSTER_STORAGE);
        routes.put(prefix + "/cluster/storage/defaults", this::setDefaults, ClusterPermission.CLUSTER_STORAGE);
        routes.get(prefix + "/cluster/storage/presets", this::listPresets, ClusterPermission.CLUSTER_STORAGE);
        routes.post(prefix + "/cluster/storage/presets", this::createPreset, ClusterPermission.CLUSTER_STORAGE);
        routes.put(
                prefix + "/cluster/storage/presets/{presetId}", this::updatePreset, ClusterPermission.CLUSTER_STORAGE);
        routes.delete(
                prefix + "/cluster/storage/presets/{presetId}", this::deletePreset, ClusterPermission.CLUSTER_STORAGE);
        routes.post(
                prefix + "/cluster/storage/presets/{presetId}/apply",
                this::applyPreset,
                ClusterPermission.CLUSTER_STORAGE);
        routes.put(
                prefix + "/cluster/storage/stations/{stationUid}", this::setGrant, ClusterPermission.CLUSTER_STORAGE);
        routes.delete(
                prefix + "/cluster/storage/stations/{stationUid}", this::handBack, ClusterPermission.CLUSTER_STORAGE);
        routes.put(prefix + "/clusters/{clusterUid}/storage-pool", this::setPool, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/cluster/storage",
            methods = HttpMethod.GET,
            summary = "The pool, what is promised out of it, and what every station has and uses",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = OverviewResponse.class)))
    private void getOverview(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var overview = quotaService.findOverview(cluster.id());
        ctx.json(new OverviewResponse(
                overview.poolBytes(),
                overview.handedOut(),
                dimensionsOf(overview.defaults()),
                overview.presets().stream().map(TierResponse::of).toList(),
                overview.stations().stream()
                        .map(station -> new StationRoomResponse(
                                station.stationUid(),
                                station.stationName(),
                                station.granted().totalBytes(),
                                station.ownStore(),
                                station.granted(),
                                ResolvedResponse.of(station.resolved()),
                                station.usedBytes(),
                                station.usage().stream()
                                        .map(usage -> new CategoryUsageResponse(
                                                usage.category().name(), usage.totalBytes(), usage.fileCount()))
                                        .toList(),
                                station.presetId(),
                                station.presetName()))
                        .toList()));
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/defaults",
            methods = HttpMethod.PUT,
            summary = "What the cluster gives a station it has granted nothing of its own",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DimensionsRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setDefaults(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(DimensionsRequest.class);
        quotaService.setDefaults(new ClusterQuotaDefaults(
                cluster.id(),
                request.totalBytes(),
                request.kbBytes(),
                request.boardBytes(),
                request.imagesBytes(),
                request.pagesBytes(),
                request.perFileBytes(),
                request.perImageBytes()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/presets",
            methods = HttpMethod.GET,
            summary = "The tiers this cluster keeps",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TierResponse[].class)))
    private void listPresets(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(quotaService.findPresets(cluster.id()).stream()
                .map(TierResponse::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/presets",
            methods = HttpMethod.POST,
            summary = "Add a tier",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TierRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TierResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createPreset(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(TierRequest.class);
        var preset = quotaService.createPreset(
                cluster.id(),
                request.name(),
                request.total(),
                request.kb(),
                request.board(),
                request.images(),
                request.pages(),
                request.perFile(),
                request.perImage());
        ctx.json(TierResponse.of(preset));
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/presets/{presetId}",
            pathParams = @OpenApiParam(name = "presetId", type = Integer.class, required = true),
            methods = HttpMethod.PUT,
            summary = "Change a tier, leaving the stations already on it where they are",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TierRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updatePreset(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(TierRequest.class);
        quotaService.updatePreset(
                cluster.id(),
                pathInt(ctx, "presetId"),
                request.name(),
                request.total(),
                request.kb(),
                request.board(),
                request.images(),
                request.pages(),
                request.perFile(),
                request.perImage());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/presets/{presetId}",
            pathParams = @OpenApiParam(name = "presetId", type = Integer.class, required = true),
            methods = HttpMethod.DELETE,
            summary = "Remove a tier",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "204"))
    private void deletePreset(Context ctx) {
        Cluster cluster = requireActive(ctx);
        quotaService.deletePreset(cluster.id(), pathInt(ctx, "presetId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/presets/{presetId}/apply",
            pathParams = @OpenApiParam(name = "presetId", type = Integer.class, required = true),
            methods = HttpMethod.POST,
            summary = "Put several stations on one tier",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ApplyTierRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void applyPreset(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(ApplyTierRequest.class);
        List<UUID> stationUids = request.stationUids() == null
                ? List.of()
                : request.stationUids().stream()
                        .map(ClusterStorageRoutes::parseUid)
                        .toList();
        quotaService.applyPreset(cluster.id(), pathInt(ctx, "presetId"), stationUids);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/stations/{stationUid}",
            pathParams = @OpenApiParam(name = "stationUid", type = String.class, required = true),
            methods = HttpMethod.PUT,
            summary = "Hand a station a share of the cluster's pool",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DimensionsRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setGrant(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(DimensionsRequest.class);
        UUID stationUid = parseUid(ctx.pathParam("stationUid"));
        // The screen that only knows about a total sends that one field and nothing else, and its meaning has
        // not changed: null hands the room back
        if (request.onlyTotal() && request.totalBytes() == null) {
            quotaService.handBack(cluster.id(), stationUid);
        } else {
            quotaService.setGrant(
                    cluster.id(),
                    stationUid,
                    new Dimensions(
                            request.totalBytes(),
                            request.kbBytes(),
                            request.boardBytes(),
                            request.imagesBytes(),
                            request.pagesBytes(),
                            request.perFileBytes(),
                            request.perImageBytes()));
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/storage/stations/{stationUid}",
            pathParams = @OpenApiParam(name = "stationUid", type = String.class, required = true),
            methods = HttpMethod.DELETE,
            summary = "Take the room back, so the station lives on the cluster's defaults again",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "204"))
    private void handBack(Context ctx) {
        Cluster cluster = requireActive(ctx);
        quotaService.handBack(cluster.id(), parseUid(ctx.pathParam("stationUid")));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/clusters/{clusterUid}/storage-pool",
            pathParams = @OpenApiParam(name = "clusterUid", type = String.class, required = true),
            methods = HttpMethod.PUT,
            summary = "Grant a cluster the pool it may hand out",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PoolRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setPool(Context ctx) {
        Cluster cluster = clusterService
                .findByUid(parseUid(ctx.pathParam("clusterUid")))
                .orElseThrow(() -> new NotFoundResponse("No such cluster"));
        var request = ctx.bodyAsClass(PoolRequest.class);
        quotaService.setStoragePool(cluster.id(), request.quotaBytes());
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
            throw new BadRequestResponse("Not an identity: " + raw);
        }
    }

    private static Dimensions dimensionsOf(ClusterQuotaDefaults defaults) {
        return new Dimensions(
                defaults.quotaBytes(),
                defaults.quotaKbBytes(),
                defaults.quotaBoardBytes(),
                defaults.quotaImagesBytes(),
                defaults.quotaPagesBytes(),
                defaults.perFileBytes(),
                defaults.perImageBytes());
    }

    /**
     * The seven dimensions as they arrive.
     *
     * <p>A {@code null} means the sender is not deciding that one. The storage screen sends only a total and
     * means exactly that, which is why an all-null body is a request to hand the room back rather than a
     * request to grant nothing.
     */
    public record DimensionsRequest(
            Long totalBytes,
            Long kbBytes,
            Long boardBytes,
            Long imagesBytes,
            Long pagesBytes,
            Long perFileBytes,
            Long perImageBytes) {
        boolean onlyTotal() {
            return kbBytes == null
                    && boardBytes == null
                    && imagesBytes == null
                    && pagesBytes == null
                    && perFileBytes == null
                    && perImageBytes == null;
        }
    }

    /**
     * @param quotaBytes the pool, or {@code null} for no cap
     */
    public record PoolRequest(Long quotaBytes) {}

    public record TierRequest(
            String name, long total, long kb, long board, long images, long pages, long perFile, long perImage) {}

    public record ApplyTierRequest(List<String> stationUids) {}

    public record TierResponse(
            int id,
            String name,
            long total,
            long kb,
            long board,
            long images,
            long pages,
            long perFile,
            long perImage) {
        static TierResponse of(ClusterStorageQuotaPreset preset) {
            return new TierResponse(
                    preset.id(),
                    preset.name(),
                    preset.total(),
                    preset.kb(),
                    preset.board(),
                    preset.images(),
                    preset.pages(),
                    preset.perFile(),
                    preset.perImage());
        }
    }

    /**
     * One dimension as it resolved, with whose word it is on.
     */
    public record ResolvedValueResponse(long bytes, QuotaOrigin origin) {}

    public record ResolvedResponse(
            ResolvedValueResponse total,
            ResolvedValueResponse kb,
            ResolvedValueResponse board,
            ResolvedValueResponse images,
            ResolvedValueResponse pages,
            ResolvedValueResponse perFile,
            ResolvedValueResponse perImage) {
        static ResolvedResponse of(StationQuotas quotas) {
            return new ResolvedResponse(
                    value(quotas.total()),
                    value(quotas.kb()),
                    value(quotas.board()),
                    value(quotas.images()),
                    value(quotas.pages()),
                    value(quotas.perFile()),
                    value(quotas.perImage()));
        }

        private static ResolvedValueResponse value(StationQuotas.ResolvedQuota quota) {
            return new ResolvedValueResponse(quota.bytes(), quota.origin());
        }
    }

    public record CategoryUsageResponse(String category, long totalBytes, int fileCount) {}

    /**
     * @param quotaBytes the total granted, kept beside the rest because the screen that only reads a total is
     *                   still the one in front of people
     * @param ownStore   whether this is the cluster's own store rather than one of its member stations
     */
    public record StationRoomResponse(
            UUID stationUid,
            String stationName,
            Long quotaBytes,
            boolean ownStore,
            Dimensions granted,
            ResolvedResponse resolved,
            long usedBytes,
            List<CategoryUsageResponse> usage,
            Integer presetId,
            String presetName) {}

    /**
     * @param poolBytes the whole the cluster may hand out, or {@code null} when the instance set no cap
     * @param handedOut the sum of the totals promised, the cluster's own store included
     */
    public record OverviewResponse(
            Long poolBytes,
            long handedOut,
            Dimensions defaults,
            List<TierResponse> presets,
            List<StationRoomResponse> stations) {}
}
