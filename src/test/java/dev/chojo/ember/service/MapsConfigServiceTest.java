/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.maps.entity.GeocodingProvider;
import dev.chojo.ember.feature.maps.entity.MapTileProvider;
import dev.chojo.ember.feature.maps.entity.MapsGeocodingConfig;
import dev.chojo.ember.feature.maps.entity.MapsTilesConfig;
import dev.chojo.ember.feature.maps.service.MapsConfigService;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapsConfigServiceTest extends RepositoryTestBase {

    private static MapsConfigService service;

    @BeforeAll
    static void init() {
        service = new MapsConfigService(applicationSettingRepo);
    }

    @Test
    void defaultRecordsAreOsmAndNone() {
        assertEquals(MapTileProvider.OSM, MapsTilesConfig.DEFAULT.provider());
        assertEquals(0, MapsTilesConfig.DEFAULT.minZoom());
        assertEquals(19, MapsTilesConfig.DEFAULT.maxZoom());
        assertNotNull(MapsTilesConfig.DEFAULT.resolvedUrlTemplate());
        assertNotNull(MapsTilesConfig.DEFAULT.resolvedAttribution());
        assertEquals(GeocodingProvider.NONE, MapsGeocodingConfig.DEFAULT.provider());
    }

    @Test
    void updateAndReadTiles() {
        var config = new MapsTilesConfig(MapTileProvider.STADIA, "my-key", "", "Custom attribution", 2, 18);
        service.updateTilesConfig(config);
        var loaded = service.tilesConfig();
        assertEquals(MapTileProvider.STADIA, loaded.provider());
        assertEquals("my-key", loaded.apiKey());
        assertEquals("Custom attribution", loaded.resolvedAttribution());
        assertEquals(2, loaded.minZoom());
        assertEquals(18, loaded.maxZoom());
    }

    @Test
    void resolvedUrlTemplateFallsBackToProviderDefault() {
        service.updateTilesConfig(new MapsTilesConfig(MapTileProvider.OSM, "", "", "", 0, 19));
        var loaded = service.tilesConfig();
        assertTrue(loaded.resolvedUrlTemplate().contains("openstreetmap"));
    }

    @Test
    void updateRejectsMissingApiKeyForRequiredProvider() {
        assertThrows(
                BadRequestResponse.class,
                () -> service.updateTilesConfig(new MapsTilesConfig(MapTileProvider.MAPBOX, "", "", "", 0, 19)));
        assertThrows(
                BadRequestResponse.class,
                () -> service.updateTilesConfig(new MapsTilesConfig(MapTileProvider.MAPTILER, null, "", "", 0, 19)));
    }

    @Test
    void updateRejectsCustomProviderWithoutTemplate() {
        assertThrows(
                BadRequestResponse.class,
                () -> service.updateTilesConfig(new MapsTilesConfig(MapTileProvider.CUSTOM, "", "", "", 0, 19)));
    }

    @Test
    void updateAcceptsCustomWithTemplate() {
        service.updateTilesConfig(new MapsTilesConfig(
                MapTileProvider.CUSTOM, "", "https://tiles.example/{z}/{x}/{y}.png", "Custom", 0, 19));
        assertEquals(MapTileProvider.CUSTOM, service.tilesConfig().provider());
    }

    @Test
    void updateRejectsInvertedZoomRange() {
        assertThrows(
                BadRequestResponse.class,
                () -> service.updateTilesConfig(new MapsTilesConfig(MapTileProvider.OSM, "", "", "", 18, 5)));
    }

    @Test
    void updateRejectsNullConfig() {
        assertThrows(BadRequestResponse.class, () -> service.updateTilesConfig(null));
        assertThrows(BadRequestResponse.class, () -> service.updateGeocodingConfig(null));
    }

    @Test
    void updateRejectsConfigWithNullProvider() {
        assertThrows(
                BadRequestResponse.class,
                () -> service.updateTilesConfig(new MapsTilesConfig(null, "", "", "", 0, 19)));
        assertThrows(
                BadRequestResponse.class, () -> service.updateGeocodingConfig(new MapsGeocodingConfig(null, "", "")));
    }

    @Test
    void updateAndReadGeocoding() {
        service.updateGeocodingConfig(new MapsGeocodingConfig(GeocodingProvider.NOMINATIM, "", "admin@example.com"));
        var loaded = service.geocodingConfig();
        assertEquals(GeocodingProvider.NOMINATIM, loaded.provider());
        assertEquals("admin@example.com", loaded.contactEmail());
    }

    @Test
    void updateTileCacheMaxMb() {
        service.updateTileCacheMaxMb(250);
        assertEquals(250, service.tileCacheMaxMb());
        assertThrows(BadRequestResponse.class, () -> service.updateTileCacheMaxMb(-1));
        assertThrows(BadRequestResponse.class, () -> service.updateTileCacheMaxMb(99_999));
    }

    @Test
    void mapTileProviderRequiresApiKeyFlag() {
        assertFalse(MapTileProvider.OSM.requiresApiKey());
        assertTrue(MapTileProvider.MAPBOX.requiresApiKey());
        assertTrue(MapTileProvider.STADIA.requiresApiKey());
        assertTrue(MapTileProvider.MAPTILER.requiresApiKey());
        assertTrue(MapTileProvider.THUNDERFOREST.requiresApiKey());
        assertFalse(MapTileProvider.CUSTOM.requiresApiKey());
    }
}
