/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.feature.maps.entity.GeocodingProvider;
import dev.chojo.ember.feature.maps.entity.MapsGeocodingConfig;
import dev.chojo.ember.feature.maps.entity.MapsTilesConfig;
import dev.chojo.ember.feature.maps.service.MapTileCacheService;
import dev.chojo.ember.feature.maps.service.MapsConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The administrators' map settings name the address search the instance asks while no server is entered, so the
 * page need not keep its own copy, and saving takes nothing from that read-only value.
 */
class AdminMapsRoutesTest {
    private static final String SETTINGS = PREFIX + "/admin/settings/maps";

    private MapsConfigService config;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        config = mock(MapsConfigService.class);
        when(config.tilesConfig()).thenReturn(MapsTilesConfig.DEFAULT);
        when(config.geocodingConfig()).thenReturn(MapsGeocodingConfig.DEFAULT);
        when(config.tileCacheMaxMb()).thenReturn(500);
        harness = RouteHarness.serving(new AdminMapsRoutes(config, mock(MapTileCacheService.class)));
    }

    @Test
    void theDefaultAddressSearchServerIsNamedBesideTheStoredOne() {
        when(config.geocodingConfig())
                .thenReturn(new MapsGeocodingConfig(GeocodingProvider.PHOTON, "https://photon.station.test"));

        var read = json(harness.request(client -> client.get(SETTINGS, harness.as(TestSessions.administrator()))));

        assertEquals(
                MapsGeocodingConfig.DEFAULT_BASE_URL,
                read.get("defaultGeocodingServer").asString());
        assertEquals(
                "https://photon.station.test",
                read.get("geocoding").get("baseUrl").asString());
    }

    @Test
    void savingIgnoresTheNamedDefault() {
        String saved = "{\"geocoding\": {\"provider\": \"" + GeocodingProvider.PHOTON
                + "\", \"baseUrl\": \"\"}, \"tileCacheMaxMb\": 500, \"defaultGeocodingServer\": \"https://elsewhere.test\"}";

        var response =
                harness.request(client -> client.put(SETTINGS, body(saved), harness.as(TestSessions.administrator())));

        assertEquals(200, response.code());
        assertEquals(
                MapsGeocodingConfig.DEFAULT_BASE_URL,
                json(response).get("defaultGeocodingServer").asString());
        verify(config).updateGeocodingConfig(new MapsGeocodingConfig(GeocodingProvider.PHOTON, ""));
    }
}
