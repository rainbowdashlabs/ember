/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.service;

import dev.chojo.ember.feature.maps.entity.MapTileProvider;
import dev.chojo.ember.feature.maps.entity.MapsGeocodingConfig;
import dev.chojo.ember.feature.maps.entity.MapsTilesConfig;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Reads/writes the instance-wide maps configuration. Defaults to OSM raw tiles and no
 * geocoding when nothing is persisted. Validation lives here; the route layer is a thin
 * delegate.
 */
@Singleton
public class MapsConfigService {

    public static final int DEFAULT_TILE_CACHE_MB = 500;
    public static final int MAX_TILE_CACHE_MB = 10_000;

    private static final String KEY_TILES = "maps_tiles";
    private static final String KEY_GEOCODING = "maps_geocoding";
    private static final String KEY_CACHE_MB = "maps_tile_cache_max_mb";

    private final ApplicationSettingRepository settings;

    @Inject
    public MapsConfigService(ApplicationSettingRepository settings) {
        this.settings = settings;
    }

    public MapsTilesConfig tilesConfig() {
        return settings.get(KEY_TILES).map(MapsTilesConfig::parse).orElse(MapsTilesConfig.DEFAULT);
    }

    public MapsGeocodingConfig geocodingConfig() {
        return settings.get(KEY_GEOCODING).map(MapsGeocodingConfig::parse).orElse(MapsGeocodingConfig.DEFAULT);
    }

    public int tileCacheMaxMb() {
        return settings.get(KEY_CACHE_MB).map(Integer::parseInt).orElse(DEFAULT_TILE_CACHE_MB);
    }

    /**
     * Persists a new tile config. Throws {@link BadRequestResponse} when the provider
     * requires an API key but none was supplied, when the zoom range is inverted, or when
     * {@link MapTileProvider#CUSTOM} is selected without a non-empty URL template.
     */
    public void updateTilesConfig(MapsTilesConfig config) {
        validate(config);
        settings.set(KEY_TILES, config.toJson());
    }

    public void updateGeocodingConfig(MapsGeocodingConfig config) {
        if (config == null || config.provider() == null) {
            throw new BadRequestResponse("provider required");
        }
        settings.set(KEY_GEOCODING, config.toJson());
    }

    public void updateTileCacheMaxMb(int maxMb) {
        if (maxMb < 0 || maxMb > MAX_TILE_CACHE_MB) {
            throw new BadRequestResponse("max cache size must be between 0 and " + MAX_TILE_CACHE_MB + " MB");
        }
        settings.set(KEY_CACHE_MB, Integer.toString(maxMb));
    }

    private static void validate(MapsTilesConfig config) {
        if (config == null || config.provider() == null) {
            throw new BadRequestResponse("provider required");
        }
        if (config.minZoom() < 0 || config.maxZoom() > 22 || config.minZoom() > config.maxZoom()) {
            throw new BadRequestResponse("zoom range must satisfy 0 <= minZoom <= maxZoom <= 22");
        }
        if (config.provider().requiresApiKey()
                && (config.apiKey() == null || config.apiKey().isBlank())) {
            throw new BadRequestResponse(config.provider() + " requires an API key");
        }
        if (config.provider() == MapTileProvider.CUSTOM
                && (config.urlTemplate() == null || config.urlTemplate().isBlank())) {
            throw new BadRequestResponse("CUSTOM provider requires a urlTemplate");
        }
    }
}
