/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.service;

import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.maps.entity.MapTileProvider;
import dev.chojo.ember.feature.maps.entity.MapsTilesConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Opportunistic on-disk cache for upstream map tiles. Sits between members and whichever
 * tile provider the operator chose so the upstream sees one request per unique tile per
 * cache lifetime (instead of one per viewer), and so a brief upstream outage is invisible
 * to users browsing tiles they recently fetched.
 *
 * <p>The cache is <em>opportunistic</em> — it only stores tiles a real user requested,
 * never pre-fetched grid regions. That matches the OSM Tile Usage Policy carve-out for
 * "long-lived browser caches". See {@code .concept/geolocation.md} §4.5.
 *
 * <p>LRU bookkeeping rides the filesystem's mtime: every read touches the file's mtime,
 * eviction picks the oldest mtimes first. No separate index file, survives restart.
 */
@Singleton
public class MapTileCacheService {
    private static final Logger log = LoggerFactory.getLogger(MapTileCacheService.class);
    private static final Path ROOT = Path.of("data", "maps", "tile-cache");
    private static final Duration UPSTREAM_TIMEOUT = Duration.ofSeconds(8);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final MapsConfigService configService;
    private final AtomicLong cachedBytes = new AtomicLong(0);
    private final AtomicLong cachedTiles = new AtomicLong(0);

    @Inject
    public MapTileCacheService(MapsConfigService configService) {
        this.configService = configService;
        try {
            Files.createDirectories(ROOT);
            recountFromDisk();
        } catch (IOException e) {
            log.warn("Failed to initialise tile cache root: {}", e.getMessage());
        }
    }

    private static String buildUrl(MapsTilesConfig tiles, int z, int x, int y) {
        String template = tiles.resolvedUrlTemplate();
        if (template == null || template.isBlank()) return null;
        // Sub-domain handling: OSM canonical {s} → rotate a/b/c by tile coordinates so the
        // request is sticky per tile (helps any upstream cache stay warm).
        String sub =
                switch (Math.floorMod(x + y, 3)) {
                    case 0 -> "a";
                    case 1 -> "b";
                    default -> "c";
                };
        return template.replace("{s}", sub)
                .replace("{z}", Integer.toString(z))
                .replace("{x}", Integer.toString(x))
                .replace("{y}", Integer.toString(y))
                .replace("{k}", tiles.apiKey() == null ? "" : tiles.apiKey())
                .replace("{apiKey}", tiles.apiKey() == null ? "" : tiles.apiKey());
    }

    private static Path tilePath(MapTileProvider provider, int z, int x, int y) {
        return ROOT.resolve(provider.name())
                .resolve(Integer.toString(z))
                .resolve(Integer.toString(x))
                .resolve(y + ".png");
    }

    private static String userAgent() {
        return "Ember/" + FederationService.FEDERATION_VERSION + " (https://github.com/RainbowDashLabs/ember)";
    }

    /**
     * Returns a cached tile if present, otherwise fetches from upstream and stores it.
     * Returns {@code null} on upstream failure with no stale tile available.
     */
    public TileResponse fetch(int z, int x, int y) {
        var tiles = configService.tilesConfig();
        if (tiles.provider() == null) return null;
        Path tilePath = tilePath(tiles.provider(), z, x, y);
        if (Files.isRegularFile(tilePath)) {
            try {
                Files.setLastModifiedTime(tilePath, FileTime.from(Instant.now()));
                return new TileResponse(Files.readAllBytes(tilePath), "image/png", CacheStatus.HIT);
            } catch (IOException e) {
                log.debug("Cached tile {}/{}/{} unreadable, refetching", z, x, y);
            }
        }
        return fetchUpstreamAndStore(tiles, tilePath, z, x, y);
    }

    /**
     * Returns the resolved tile URL for the given coordinates, for the admin "test tile"
     * button. Does NOT touch the cache.
     */
    public String resolveUpstreamUrl(int z, int x, int y) {
        var tiles = configService.tilesConfig();
        return buildUrl(tiles, z, x, y);
    }

    // ---------------------------------------------------------------------

    /**
     * Performs the upstream fetch but does not write to disk. Returns the HTTP status code,
     * or {@code -1} on transport error.
     */
    public int probeUpstream(int z, int x, int y) {
        var tiles = configService.tilesConfig();
        try {
            String url = buildUrl(tiles, z, x, y);
            if (url == null) return -1;
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(UPSTREAM_TIMEOUT)
                    .header("User-Agent", userAgent())
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (Exception e) {
            log.debug("Tile probe failed: {}", e.getMessage());
            return -1;
        }
    }

    public CacheStats stats() {
        return new CacheStats(cachedBytes.get(), cachedTiles.get(), configService.tileCacheMaxMb() * 1024L * 1024L);
    }

    /**
     * Clears every tile under the cache root. Used after a provider switch.
     */
    public void purge() {
        try (Stream<Path> stream = Files.walk(ROOT)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                if (path.equals(ROOT)) return;
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
            Files.createDirectories(ROOT);
        } catch (IOException e) {
            log.warn("Tile cache purge failed: {}", e.getMessage());
        }
        cachedBytes.set(0);
        cachedTiles.set(0);
    }

    private TileResponse fetchUpstreamAndStore(MapsTilesConfig tiles, Path tilePath, int z, int x, int y) {
        String url = buildUrl(tiles, z, x, y);
        if (url == null) return null;
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(UPSTREAM_TIMEOUT)
                    .header("User-Agent", userAgent())
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.debug("Upstream tile fetch returned HTTP {} for {}/{}/{}", response.statusCode(), z, x, y);
                return null;
            }
            byte[] bytes = response.body();
            String contentType = response.headers().firstValue("content-type").orElse("image/png");
            try {
                Files.createDirectories(tilePath.getParent());
                Files.write(tilePath, bytes);
                cachedBytes.addAndGet(bytes.length);
                cachedTiles.incrementAndGet();
                evictIfOverBudget();
            } catch (IOException e) {
                log.debug("Failed to persist tile {}/{}/{}: {}", z, x, y, e.getMessage());
            }
            return new TileResponse(bytes, contentType, CacheStatus.MISS);
        } catch (Exception e) {
            log.debug("Upstream tile fetch failed for {}/{}/{}: {}", z, x, y, e.getMessage());
            return null;
        }
    }

    /**
     * Evicts least-recently-used tiles until cache size is back under the configured budget.
     */
    private void evictIfOverBudget() {
        long budget = configService.tileCacheMaxMb() * 1024L * 1024L;
        if (budget <= 0) return;
        if (cachedBytes.get() <= budget) return;

        List<Path> tiles;
        try (Stream<Path> stream = Files.walk(ROOT)) {
            tiles = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparingLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0;
                        }
                    }))
                    .toList();
        } catch (IOException e) {
            log.debug("Eviction walk failed: {}", e.getMessage());
            return;
        }
        for (Path p : tiles) {
            if (cachedBytes.get() <= budget) break;
            try {
                long size = Files.size(p);
                Files.deleteIfExists(p);
                cachedBytes.addAndGet(-size);
                cachedTiles.decrementAndGet();
            } catch (IOException ignored) {
            }
        }
    }

    private void recountFromDisk() {
        long bytes = 0;
        long count = 0;
        if (!Files.isDirectory(ROOT)) {
            cachedBytes.set(0);
            cachedTiles.set(0);
            return;
        }
        try (Stream<Path> stream = Files.walk(ROOT)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(p)) continue;
                try {
                    bytes += Files.size(p);
                    count++;
                } catch (IOException ignored) {
                }
            }
        } catch (IOException e) {
            log.warn("Failed to walk tile cache root: {}", e.getMessage());
        }
        cachedBytes.set(bytes);
        cachedTiles.set(count);
    }

    public enum CacheStatus {
        HIT,
        MISS,
        STALE_OUTAGE
    }

    public record TileResponse(byte[] body, String contentType, CacheStatus status) {}

    public record CacheStats(long bytes, long tiles, long maxBytes) {}
}
