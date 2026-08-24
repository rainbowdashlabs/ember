/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.feature.maps.entity.MapTileProvider;
import dev.chojo.ember.feature.maps.service.MapTileCacheService;
import dev.chojo.ember.feature.maps.service.MapsConfigService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Anonymous-internet routes for the maps feature:
 *
 * <ul>
 *   <li>{@code GET /public/settings/maps} - read the operator-configured tile provider so
 *       the Vue frontend can render the right Leaflet tile layer with the right
 *       attribution.</li>
 *   <li>{@code GET /public/maps/tiles/{z}/{x}/{y}} - passthrough through the on-disk cache
 *       so member browsers never speak directly to the upstream provider (privacy +
 *       outage resilience).</li>
 * </ul>
 */
@Singleton
public class PublicMapsRoutes implements Routes {

    private final MapsConfigService configService;
    private final MapTileCacheService cacheService;

    @Inject
    public PublicMapsRoutes(MapsConfigService configService, MapTileCacheService cacheService) {
        this.configService = configService;
        this.cacheService = cacheService;
    }

    private static int parseInt(String value, String fieldName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BadRequestResponse(fieldName + " must be an integer");
        }
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/settings/maps", this::getConfig);
        routes.get(prefix + "/public/maps/tiles/{z}/{x}/{y}", this::getTile);
    }

    private void getConfig(Context ctx) {
        var tiles = configService.tilesConfig();
        // We deliberately rewrite the URL template to point at this instance's own cache
        // endpoint when the operator hasn't overridden it via the CUSTOM provider; that way
        // every member tile request goes through our cache by default.
        String urlTemplate;
        if (tiles.provider() == MapTileProvider.CUSTOM) {
            urlTemplate = tiles.resolvedUrlTemplate();
        } else {
            urlTemplate = "/api/v1/public/maps/tiles/{z}/{x}/{y}";
        }
        ctx.header("Cache-Control", "public, max-age=60");
        ctx.json(new PublicMapsConfig(
                tiles.provider(), urlTemplate, tiles.resolvedAttribution(), tiles.minZoom(), tiles.maxZoom()));
    }

    @StationFree("the parameters are map coordinates, not a row; the tile is the same for everyone")
    private void getTile(Context ctx) {
        int z = parseInt(ctx.pathParam("z"), "z");
        int x = parseInt(ctx.pathParam("x"), "x");
        int y = parseInt(ctx.pathParam("y"), "y");
        var response = cacheService.fetch(z, x, y);
        if (response == null) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            return;
        }
        ctx.contentType(response.contentType());
        ctx.header("Cache-Control", "public, max-age=604800");
        ctx.header("X-Tile-Cache", response.status().name());
        ctx.header("X-Robots-Tag", "noindex");
        ctx.result(response.body());
    }

    /**
     * Public-facing tile config - never includes the API key. Frontend reads only this.
     */
    public record PublicMapsConfig(
            MapTileProvider provider, String urlTemplate, String attribution, int minZoom, int maxZoom) {}
}
