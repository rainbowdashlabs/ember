/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.service;

import dev.chojo.ember.api.refusal.MapRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.maps.entity.MapTileProvider;
import dev.chojo.ember.feature.maps.entity.MapsTilesConfig;
import dev.chojo.ember.feature.maps.service.MapTileCacheService.CacheStatus;
import dev.chojo.ember.feature.maps.service.MapTileCacheService.UpstreamTile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapTileCacheServiceTest {

    private static final byte[] TILE = {1, 2, 3, 4};

    @TempDir
    Path root;

    private MapsConfigService config;
    private final AtomicInteger upstreamCalls = new AtomicInteger();

    @BeforeEach
    void setup() {
        config = Mockito.mock(MapsConfigService.class);
        Mockito.when(config.tilesConfig()).thenReturn(new MapsTilesConfig(MapTileProvider.OSM, "", "", "", 2, 18));
        Mockito.when(config.tileCacheMaxMb()).thenReturn(500);
    }

    private MapTileCacheService cacheAnswering(byte[] body, String contentType) {
        return new MapTileCacheService(config, root, url -> {
            upstreamCalls.incrementAndGet();
            return Optional.of(new UpstreamTile(body, contentType));
        });
    }

    private long filesOnDisk() throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void aZoomOutsideTheConfiguredRangeIsRefused() {
        var cache = cacheAnswering(TILE, "image/png");

        var tooFar = assertThrows(RefusalResponse.class, () -> cache.fetch(19, 0, 0));
        var tooClose = assertThrows(RefusalResponse.class, () -> cache.fetch(1, 0, 0));
        var negative = assertThrows(RefusalResponse.class, () -> cache.fetch(-1, 0, 0));

        assertEquals(MapRefusal.MAP_TILE_ZOOM_OUT_OF_RANGE, tooFar.refusal());
        assertEquals(MapRefusal.MAP_TILE_ZOOM_OUT_OF_RANGE, tooClose.refusal());
        assertEquals(MapRefusal.MAP_TILE_ZOOM_OUT_OF_RANGE, negative.refusal());
        assertEquals(0, upstreamCalls.get());
    }

    @Test
    void aTileOutsideTheGridIsRefused() {
        var cache = cacheAnswering(TILE, "image/png");

        for (int[] xy : new int[][] {{4, 0}, {0, 4}, {-1, 0}, {0, -1}}) {
            var refusal = assertThrows(RefusalResponse.class, () -> cache.fetch(2, xy[0], xy[1]));
            assertEquals(MapRefusal.MAP_TILE_OFF_THE_MAP, refusal.refusal());
        }
        assertEquals(0, upstreamCalls.get());
        assertEquals(CacheStatus.MISS, cache.fetch(2, 3, 3).status());
    }

    @Test
    void aTileIsFetchedOnceAndThenServedFromTheCache() {
        var cache = cacheAnswering(TILE, "image/png");

        var first = cache.fetch(5, 1, 2);
        var second = cache.fetch(5, 1, 2);

        assertEquals(CacheStatus.MISS, first.status());
        assertEquals(CacheStatus.HIT, second.status());
        assertArrayEquals(TILE, second.body());
        assertEquals(1, upstreamCalls.get());
    }

    @Test
    void theUpstreamContentTypeIsKeptAndServed() {
        var cache = cacheAnswering(TILE, "image/jpeg; charset=binary");

        var miss = cache.fetch(5, 1, 2);
        var hit = cache.fetch(5, 1, 2);

        assertEquals("image/jpeg", miss.contentType());
        assertEquals("image/jpeg", hit.contentType());
        assertTrue(Files.isRegularFile(root.resolve("OSM/5/1/2.jpg")));
    }

    @Test
    void theContentTypeSurvivesARestart() {
        cacheAnswering(TILE, "image/webp").fetch(5, 1, 2);

        var restarted = cacheAnswering(TILE, "image/png");
        var hit = restarted.fetch(5, 1, 2);

        assertEquals(CacheStatus.HIT, hit.status());
        assertEquals("image/webp", hit.contentType());
        assertEquals(1, restarted.stats().tiles());
    }

    @Test
    void anAnswerThatIsNotATileIsNeitherKeptNorServed() throws IOException {
        var cache = cacheAnswering("<html>".getBytes(), "text/html");

        assertNull(cache.fetch(5, 1, 2));
        assertEquals(0, filesOnDisk());
    }

    @Test
    void anUnreachableUpstreamGivesNothing() {
        var cache = new MapTileCacheService(config, root, url -> {
            throw new IOException("down");
        });

        assertNull(cache.fetch(5, 1, 2));
    }

    @Test
    void tilesAboveTheBudgetAreEvictedAndTheirFilesDeleted() throws IOException {
        Mockito.when(config.tileCacheMaxMb()).thenReturn(1);
        var cache = cacheAnswering(new byte[400 * 1024], "image/png");

        for (int x = 0; x < 6; x++) cache.fetch(5, x, 0);

        var stats = cache.stats();
        assertTrue(stats.bytes() <= stats.maxBytes(), "the index stays inside the budget");
        assertEquals(2, stats.tiles());
        assertEquals(2, filesOnDisk(), "an evicted tile leaves no file behind");
    }

    @Test
    void anOverfullCacheOnDiskIsTrimmedToTheBudget() throws IOException {
        var cache = cacheAnswering(new byte[400 * 1024], "image/png");
        for (int x = 0; x < 6; x++) cache.fetch(5, x, 0);
        assertEquals(6, filesOnDisk());

        Mockito.when(config.tileCacheMaxMb()).thenReturn(1);
        var restarted = cacheAnswering(new byte[400 * 1024], "image/png");

        assertEquals(2, restarted.stats().tiles());
        assertEquals(2, filesOnDisk());
    }

    @Test
    void purgeEmptiesTheCache() throws IOException {
        var cache = cacheAnswering(TILE, "image/png");
        cache.fetch(5, 1, 2);

        cache.purge();

        assertEquals(0, cache.stats().tiles());
        assertEquals(0, filesOnDisk());
        assertEquals(CacheStatus.MISS, cache.fetch(5, 1, 2).status());
    }

    @Test
    void concurrentRequestsForOneMissingTileShareOneUpstreamFetch() throws Exception {
        var release = new CountDownLatch(1);
        var cache = new MapTileCacheService(config, root, url -> {
            upstreamCalls.incrementAndGet();
            release.await(10, TimeUnit.SECONDS);
            return Optional.of(new UpstreamTile(TILE, "image/png"));
        });
        var answers = new ArrayList<byte[]>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            threads.add(Thread.ofPlatform().start(() -> {
                var tile = cache.fetch(7, 3, 3);
                synchronized (answers) {
                    answers.add(tile.body());
                }
            }));
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (threads.stream()
                .anyMatch(t -> t.getState() != Thread.State.WAITING && t.getState() != Thread.State.TIMED_WAITING)) {
            assertTrue(System.nanoTime() < deadline, "every request reached the fetch or the wait for it");
            Thread.onSpinWait();
        }
        release.countDown();
        for (var thread : threads) thread.join(TimeUnit.SECONDS.toMillis(10));

        assertEquals(1, upstreamCalls.get());
        assertEquals(8, answers.size());
        answers.forEach(body -> assertArrayEquals(TILE, body));
    }
}
