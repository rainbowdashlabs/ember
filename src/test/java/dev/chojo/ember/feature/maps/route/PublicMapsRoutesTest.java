/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.feature.maps.service.MapTileCacheService;
import dev.chojo.ember.feature.maps.service.MapTileCacheService.CacheStatus;
import dev.chojo.ember.feature.maps.service.MapTileCacheService.TileResponse;
import dev.chojo.ember.feature.maps.service.MapTileRateLimiter;
import dev.chojo.ember.feature.maps.service.MapsConfigService;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The public tile route holds each address to the tile rate before the cache, and so the
 * upstream, is asked for anything.
 */
class PublicMapsRoutesTest {
    private static final byte[] TILE = {1, 2, 3};
    private static final String TILE_PATH = RouteHarness.PREFIX + "/public/maps/tiles/3/1/2";

    private MapTileCacheService cache;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        cache = mock(MapTileCacheService.class);
        when(cache.fetch(anyInt(), anyInt(), anyInt()))
                .thenReturn(new TileResponse(TILE, "image/png", CacheStatus.HIT));
        var network = mock(Network.class);
        when(network.trustedProxies()).thenReturn(List.of("127.0.0.1/32", "::1/128"));
        harness = RouteHarness.serving(new PublicMapsRoutes(
                        mock(MapsConfigService.class),
                        cache,
                        new MapTileRateLimiter(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))))
                .withNetwork(network);
    }

    private static Response tileFrom(HttpClient client, String ip) {
        return client.get(TILE_PATH, request -> request.header("X-Forwarded-For", ip));
    }

    @Test
    void anAddressAskingTooFastIsRefusedBeforeTheCacheIsAsked() {
        harness.run((server, client) -> {
            for (int i = 0; i < MapTileRateLimiter.BURST_CAPACITY; i++) {
                assertEquals(200, tileFrom(client, "203.0.113.7").code());
            }
            verify(cache, times(MapTileRateLimiter.BURST_CAPACITY)).fetch(3, 1, 2);

            var refused = tileFrom(client, "203.0.113.7");

            assertEquals(Refusal.MAP_TILES_TOO_OFTEN, refusalOf(refused));
            assertNotNull(header(refused, "Retry-After"));
            verify(cache, times(MapTileRateLimiter.BURST_CAPACITY)).fetch(3, 1, 2);
        });
    }

    @Test
    void anotherAddressIsServedMeanwhile() {
        harness.run((server, client) -> {
            for (int i = 0; i <= MapTileRateLimiter.BURST_CAPACITY; i++) {
                tileFrom(client, "203.0.113.7");
            }

            var other = tileFrom(client, "198.51.100.1");

            assertEquals(200, other.code());
            assertTrue(header(other, "Content-Type").startsWith("image/png"));
            assertEquals(new String(TILE, StandardCharsets.UTF_8), other.body().string());
        });
    }
}
