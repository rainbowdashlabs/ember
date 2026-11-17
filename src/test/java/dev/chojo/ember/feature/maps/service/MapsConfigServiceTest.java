/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.service;

import dev.chojo.ember.api.refusal.MapRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.maps.entity.GeocodingProvider;
import dev.chojo.ember.feature.maps.entity.MapTileProvider;
import dev.chojo.ember.feature.maps.entity.MapsGeocodingConfig;
import dev.chojo.ember.feature.maps.entity.MapsTilesConfig;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

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
                RefusalResponse.class,
                () -> service.updateTilesConfig(new MapsTilesConfig(MapTileProvider.MAPBOX, "", "", "", 0, 19)));
        assertThrows(
                RefusalResponse.class,
                () -> service.updateTilesConfig(new MapsTilesConfig(MapTileProvider.MAPTILER, null, "", "", 0, 19)));
    }

    @Test
    void updateRejectsCustomProviderWithoutTemplate() {
        assertThrows(
                RefusalResponse.class,
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
                RefusalResponse.class,
                () -> service.updateTilesConfig(new MapsTilesConfig(MapTileProvider.OSM, "", "", "", 18, 5)));
    }

    @Test
    void updateRejectsNullConfig() {
        assertThrows(RefusalResponse.class, () -> service.updateTilesConfig(null));
        assertThrows(RefusalResponse.class, () -> service.updateGeocodingConfig(null));
    }

    @Test
    void updateRejectsConfigWithNullProvider() {
        assertThrows(
                RefusalResponse.class, () -> service.updateTilesConfig(new MapsTilesConfig(null, "", "", "", 0, 19)));
        assertThrows(RefusalResponse.class, () -> service.updateGeocodingConfig(new MapsGeocodingConfig(null, "")));
    }

    @Test
    void updateAndReadGeocoding() {
        service.updateGeocodingConfig(new MapsGeocodingConfig(GeocodingProvider.PHOTON, "https://photon.example/"));
        var loaded = service.geocodingConfig();
        assertEquals(GeocodingProvider.PHOTON, loaded.provider());
        assertEquals("https://photon.example", loaded.resolvedBaseUrl());
    }

    @Test
    void aBlankServerIsThePublicOne() {
        service.updateGeocodingConfig(new MapsGeocodingConfig(GeocodingProvider.PHOTON, " "));
        assertEquals(
                MapsGeocodingConfig.DEFAULT_BASE_URL, service.geocodingConfig().resolvedBaseUrl());
        assertEquals(MapsGeocodingConfig.DEFAULT_BASE_URL, MapsGeocodingConfig.DEFAULT.resolvedBaseUrl());
        assertEquals(
                MapsGeocodingConfig.DEFAULT_BASE_URL,
                new MapsGeocodingConfig(GeocodingProvider.PHOTON, null).resolvedBaseUrl());
    }

    @Test
    void theSearchPathIsLeftOffAPastedServerAddress() {
        assertEquals(
                "https://photon.example",
                new MapsGeocodingConfig(GeocodingProvider.PHOTON, "https://photon.example/api/").resolvedBaseUrl());
        assertEquals(
                "https://maps.example/photon",
                new MapsGeocodingConfig(GeocodingProvider.PHOTON, "https://maps.example/photon/api").resolvedBaseUrl());
    }

    @Test
    void updateRejectsAServerThatIsNotAWebAddress() {
        for (String address :
                List.of("photon.example", "ftp://photon.example", "https://", "https://a b", "https://x/?q=1")) {
            var refusal = assertThrows(
                    RefusalResponse.class,
                    () -> service.updateGeocodingConfig(new MapsGeocodingConfig(GeocodingProvider.PHOTON, address)),
                    address);
            assertEquals(MapRefusal.MAP_GEOCODING_ADDRESS_NOT_USABLE, refusal.refusal(), address);
        }
    }

    @Test
    void aSettingNamingARetiredProviderReadsAsSwitchedOff() {
        applicationSettingRepo.set(
                "maps_geocoding", "{\"provider\":\"NOMINATIM\",\"apiKey\":\"k\",\"contactEmail\":\"a@b.c\"}");
        var loaded = service.geocodingConfig();
        assertEquals(GeocodingProvider.NONE, loaded.provider());
        assertEquals(MapsGeocodingConfig.DEFAULT_BASE_URL, loaded.resolvedBaseUrl());
        assertFalse(loaded.toJson().contains("apiKey"));
    }

    @Test
    void aSettingWithoutProviderReadsAsSwitchedOff() {
        assertEquals(
                GeocodingProvider.NONE,
                MapsGeocodingConfig.parse("{\"provider\":null}").provider());
        assertEquals(GeocodingProvider.NONE, MapsGeocodingConfig.parse("{}").provider());
        assertThrows(RuntimeException.class, () -> MapsGeocodingConfig.parse("not json"));
    }

    @Test
    void updateTileCacheMaxMb() {
        service.updateTileCacheMaxMb(250);
        assertEquals(250, service.tileCacheMaxMb());
        assertThrows(RefusalResponse.class, () -> service.updateTileCacheMaxMb(-1));
        assertThrows(RefusalResponse.class, () -> service.updateTileCacheMaxMb(99_999));
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
