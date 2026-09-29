/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.maps.service.MapTileCacheService;
import dev.chojo.ember.feature.maps.service.MapTileCacheService.CacheStatus;
import dev.chojo.ember.feature.maps.service.MapTileCacheService.TileResponse;
import dev.chojo.ember.feature.maps.service.MapTileRateLimiter;
import dev.chojo.ember.feature.maps.service.MapsConfigService;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_SELF;
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

    private MapTileCacheService cache;
    private PublicMapsRoutes routes;
    private Method getTile;

    @BeforeEach
    void setup() throws Exception {
        cache = mock(MapTileCacheService.class);
        when(cache.fetch(anyInt(), anyInt(), anyInt()))
                .thenReturn(new TileResponse(TILE, "image/png", CacheStatus.HIT));
        routes = new PublicMapsRoutes(
                mock(MapsConfigService.class),
                cache,
                new MapTileRateLimiter(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)));
        getTile = PublicMapsRoutes.class.getDeclaredMethod("getTile", Context.class);
        getTile.setAccessible(true);
    }

    private Context tileRequest(String ip) {
        var ctx = mock(Context.class, RETURNS_SELF);
        when(ctx.ip()).thenReturn(ip);
        when(ctx.header(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        when(ctx.pathParam("z")).thenReturn("3");
        when(ctx.pathParam("x")).thenReturn("1");
        when(ctx.pathParam("y")).thenReturn("2");
        return ctx;
    }

    @Test
    void anAddressAskingTooFastIsRefusedBeforeTheCacheIsAsked() throws Exception {
        for (int i = 0; i < MapTileRateLimiter.BURST_CAPACITY; i++) {
            getTile.invoke(routes, tileRequest("203.0.113.7"));
        }
        verify(cache, times(MapTileRateLimiter.BURST_CAPACITY)).fetch(3, 1, 2);

        var refused = tileRequest("203.0.113.7");
        getTile.invoke(routes, refused);

        verify(refused).status(Refusal.MAP_TILES_TOO_OFTEN.status());
        verify(refused)
                .header(org.mockito.ArgumentMatchers.eq("Retry-After"), org.mockito.ArgumentMatchers.anyString());
        var body = ArgumentCaptor.forClass(Object.class);
        verify(refused).json(body.capture());
        assertEquals(ErrorResponseWrapper.class, body.getValue().getClass());
        verify(cache, times(MapTileRateLimiter.BURST_CAPACITY)).fetch(3, 1, 2);
    }

    @Test
    void anotherAddressIsServedMeanwhile() throws Exception {
        for (int i = 0; i <= MapTileRateLimiter.BURST_CAPACITY; i++) {
            getTile.invoke(routes, tileRequest("203.0.113.7"));
        }

        var other = tileRequest("198.51.100.1");
        getTile.invoke(routes, other);

        verify(other).result(TILE);
        verify(other).contentType("image/png");
    }
}
