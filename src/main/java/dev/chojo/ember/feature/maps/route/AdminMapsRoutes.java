/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.maps.entity.GeocodingProvider;
import dev.chojo.ember.feature.maps.entity.MapTileProvider;
import dev.chojo.ember.feature.maps.entity.MapsGeocodingConfig;
import dev.chojo.ember.feature.maps.entity.MapsTilesConfig;
import dev.chojo.ember.feature.maps.service.MapTileCacheService;
import dev.chojo.ember.feature.maps.service.MapsConfigService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Admin-only routes for the maps feature: read/write the full config (API key included),
 * fire a test tile request against the upstream provider, and inspect/purge the on-disk
 * cache.
 */
@Singleton
public class AdminMapsRoutes implements Routes {

    private final MapsConfigService configService;
    private final MapTileCacheService cacheService;

    @Inject
    public AdminMapsRoutes(MapsConfigService configService, MapTileCacheService cacheService) {
        this.configService = configService;
        this.cacheService = cacheService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/settings/maps", this::getConfig, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/settings/maps",
                this::updateConfig,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(prefix + "/admin/maps/test-tile", this::testTile, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/maps/cache/stats", this::cacheStats, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/maps/cache/purge", this::purgeCache, InstancePermission.ADMINISTRATOR);
    }

    private void getConfig(Context ctx) {
        ctx.json(new AdminMapsConfig(
                configService.tilesConfig(), configService.geocodingConfig(), configService.tileCacheMaxMb()));
    }

    private void updateConfig(Context ctx) {
        var body = ctx.bodyAsClass(AdminMapsConfig.class);
        if (body.tiles() != null) configService.updateTilesConfig(body.tiles());
        if (body.geocoding() != null) configService.updateGeocodingConfig(body.geocoding());
        configService.updateTileCacheMaxMb(body.tileCacheMaxMb());
        getConfig(ctx);
    }

    private void testTile(Context ctx) {
        int z = parseIntParam(ctx, "z");
        int x = parseIntParam(ctx, "x");
        int y = parseIntParam(ctx, "y");
        String url = cacheService.resolveUpstreamUrl(z, x, y);
        int status = cacheService.probeUpstream(z, x, y);
        ctx.json(new TestTileResult(url, status));
    }

    private void cacheStats(Context ctx) {
        ctx.json(cacheService.stats());
    }

    private void purgeCache(Context ctx) {
        cacheService.purge();
        ctx.json(cacheService.stats());
    }

    private static int parseIntParam(Context ctx, String name) {
        String raw = ctx.queryParam(name);
        if (raw == null) throw new BadRequestResponse(name + " required");
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new BadRequestResponse(name + " must be an integer");
        }
    }

    /**
     * Combined admin-facing config payload. Carries the API key so the admin can edit it;
     * never use this DTO for public-facing reads — see {@link PublicMapsRoutes.PublicMapsConfig}.
     */
    public record AdminMapsConfig(MapsTilesConfig tiles, MapsGeocodingConfig geocoding, int tileCacheMaxMb) {
        public AdminMapsConfig {
            if (tiles == null) tiles = MapsTilesConfig.DEFAULT;
            if (geocoding == null) geocoding = new MapsGeocodingConfig(GeocodingProvider.NONE, "", "");
        }

        /**
         * Convenience for the typed default; used so the route handler can synthesise a
         * record even when the operator only typed one section. {@link MapTileProvider}
         * import is kept around so the IDE refactor tooling will pick this DTO up on a
         * future provider rename.
         */
        @SuppressWarnings("unused")
        private static final MapTileProvider DEFAULT_PROVIDER = MapTileProvider.OSM;
    }

    /**
     * Response for the "test tile" button: the URL the backend would hit upstream and the
     * status code it got back (or {@code -1} on transport error).
     */
    public record TestTileResult(String url, int status) {}
}
