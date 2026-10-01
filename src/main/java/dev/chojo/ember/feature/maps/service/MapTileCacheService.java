/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.service.OutboundHttp;
import dev.chojo.ember.feature.maps.entity.MapTileProvider;
import dev.chojo.ember.feature.maps.entity.MapsTilesConfig;
import dev.chojo.ember.util.FilePaths;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Opportunistic on-disk cache for upstream map tiles. Sits between members and whichever
 * tile provider the operator chose so the upstream sees one request per unique tile per
 * cache lifetime (instead of one per viewer), and so a brief upstream outage is invisible
 * to users browsing tiles they recently fetched.
 *
 * <p>The cache is <em>opportunistic</em> - it only stores tiles a real user requested,
 * never pre-fetched grid regions. That matches the OSM Tile Usage Policy carve-out for
 * "long-lived browser caches".
 *
 * <p>Which tiles are kept is decided by an in-memory index weighed by file size and bounded
 * by the configured budget. The index is rebuilt from the files on disk once at startup;
 * after that a tile it evicts has its file deleted by the index itself, so no request ever
 * walks the cache directory. Concurrent requests for the same missing tile share a single
 * upstream fetch.
 *
 * <p>A tile is kept and served with the content type the upstream gave it, which is also
 * what its file extension records. Only raster image types are taken: anything else the
 * upstream answers with is treated as a failed fetch, so this instance never serves a page
 * of someone else's markup from its own origin.
 *
 * <p>Coordinates are checked against the tile grid and the configured zoom range before
 * anything is fetched, so the upstream is only ever asked for tiles that exist.
 */
@Singleton
public class MapTileCacheService {
    private static final Logger log = LoggerFactory.getLogger(MapTileCacheService.class);
    private static final Path DEFAULT_ROOT = Path.of("data", "maps", "tile-cache");
    private static final Duration UPSTREAM_TIMEOUT = Duration.ofSeconds(8);
    private static final long UNBOUNDED = Long.MAX_VALUE;

    private final HttpClient httpClient = OutboundHttp.trustedClient(Duration.ofSeconds(4), HttpClient.Redirect.NORMAL);

    private final MapsConfigService configService;
    private final Path root;
    private final TileFetcher fetcher;
    private final Cache<TileKey, CachedTile> index;
    private final ConcurrentHashMap<TileKey, CompletableFuture<TileResponse>> inFlight = new ConcurrentHashMap<>();

    @Inject
    public MapTileCacheService(MapsConfigService configService) {
        this(configService, DEFAULT_ROOT, null);
    }

    /**
     * A cache kept under a directory of the caller's choosing and fed by a fetcher of the caller's
     * choosing, which is how a test drives it without the network.
     *
     * @param configService where the provider, the zoom range and the budget are read from
     * @param root          the directory the tiles are kept in
     * @param fetcher       what asks the upstream for a tile, or {@code null} for the HTTP client
     */
    public MapTileCacheService(MapsConfigService configService, Path root, @Nullable TileFetcher fetcher) {
        this.configService = configService;
        this.root = root;
        this.fetcher = fetcher != null ? fetcher : this::fetchOverHttp;
        this.index = Caffeine.newBuilder()
                .maximumWeight(UNBOUNDED)
                .weigher((TileKey key, CachedTile tile) -> tile.weight())
                .executor(Runnable::run)
                .removalListener(this::onRemoval)
                .build();
        try {
            Files.createDirectories(root);
            rebuildIndexFromDisk();
        } catch (IOException e) {
            log.warn("Failed to initialise tile cache root: {}", e.getMessage());
        }
    }

    private static @Nullable String buildUrl(MapsTilesConfig tiles, int z, int x, int y) {
        String template = tiles.resolvedUrlTemplate();
        if (template == null || template.isBlank()) return null;
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

    private static String userAgent() {
        return "Ember/" + FederationContractVersions.current().core() + " (https://github.com/RainbowDashLabs/ember)";
    }

    /**
     * Refuses coordinates that name no tile: a zoom outside the configured range, or a column or
     * row outside the {@code 2^z} by {@code 2^z} grid of that zoom.
     */
    private static void requireOnTheMap(MapsTilesConfig tiles, int z, int x, int y) {
        if (z < Math.max(0, tiles.minZoom()) || z > tiles.maxZoom()) {
            throw Refusal.MAP_TILE_ZOOM_OUT_OF_RANGE.raise();
        }
        long size = 1L << z;
        if (x < 0 || y < 0 || x >= size || y >= size) throw Refusal.MAP_TILE_OFF_THE_MAP.raise();
    }

    /**
     * Returns a cached tile if present, otherwise fetches it from upstream and stores it.
     * Concurrent requests for the same missing tile wait for the one fetch already under way.
     *
     * @return the tile, or {@code null} when no provider is configured or the upstream failed
     */
    public @Nullable TileResponse fetch(int z, int x, int y) {
        var tiles = configService.tilesConfig();
        if (tiles.provider() == null) return null;
        requireOnTheMap(tiles, z, x, y);
        applyBudget();
        var key = new TileKey(tiles.provider(), z, x, y);
        var hit = readCached(key);
        if (hit.isPresent()) return hit.get();
        return fetchOnce(tiles, key);
    }

    private Optional<TileResponse> readCached(TileKey key) {
        var cached = index.getIfPresent(key);
        if (cached == null) return Optional.empty();
        try {
            return Optional.of(
                    new TileResponse(Files.readAllBytes(cached.path()), cached.contentType(), CacheStatus.HIT));
        } catch (IOException e) {
            log.debug("Cached tile {} unreadable, refetching", key);
            index.invalidate(key);
            return Optional.empty();
        }
    }

    private @Nullable TileResponse fetchOnce(MapsTilesConfig tiles, TileKey key) {
        var mine = new CompletableFuture<TileResponse>();
        var running = inFlight.putIfAbsent(key, mine);
        if (running != null) return running.join();
        try {
            var response = fetchUpstreamAndStore(tiles, key);
            mine.complete(response);
            return response;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    /**
     * Returns the resolved tile URL for the given coordinates, for the admin "test tile"
     * button. Does NOT touch the cache.
     */
    public @Nullable String resolveUpstreamUrl(int z, int x, int y) {
        var tiles = configService.tilesConfig();
        return buildUrl(tiles, z, x, y);
    }

    /**
     * Performs the upstream fetch but does not write to disk. Returns the HTTP status code,
     * or {@code -1} on transport error.
     */
    public int probeUpstream(int z, int x, int y) {
        var tiles = configService.tilesConfig();
        try {
            String url = buildUrl(tiles, z, x, y);
            if (url == null) return -1;
            var response = httpClient.send(request(url), HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (Exception e) {
            log.debug("Tile probe failed: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * How much the cache holds against how much it may.
     *
     * @return the bytes and tiles kept and the configured budget
     */
    public CacheStats stats() {
        applyBudget();
        index.cleanUp();
        long bytes =
                index.policy().eviction().map(e -> e.weightedSize().orElse(0L)).orElse(0L);
        return new CacheStats(bytes, index.estimatedSize(), budgetBytes());
    }

    /**
     * Clears every tile under the cache root. Used after a provider switch.
     */
    public void purge() {
        index.invalidateAll();
        index.cleanUp();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                if (path.equals(root)) return;
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.debug("Tile {} survived the purge", path, e);
                }
            });
            Files.createDirectories(root);
        } catch (IOException e) {
            log.warn("Tile cache purge failed: {}", e.getMessage());
        }
    }

    private long budgetBytes() {
        return configService.tileCacheMaxMb() * 1024L * 1024L;
    }

    /** Carries a changed budget over to the index; a budget of zero keeps every tile. */
    private void applyBudget() {
        long budget = budgetBytes();
        long maximum = budget <= 0 ? UNBOUNDED : budget;
        index.policy().eviction().ifPresent(eviction -> {
            if (eviction.getMaximum() != maximum) eviction.setMaximum(maximum);
        });
    }

    private @Nullable TileResponse fetchUpstreamAndStore(MapsTilesConfig tiles, TileKey key) {
        String url = buildUrl(tiles, key.z(), key.x(), key.y());
        if (url == null) return null;
        Optional<UpstreamTile> upstream;
        try {
            upstream = fetcher.fetch(url);
        } catch (IOException e) {
            log.debug("Upstream tile fetch failed for {}: {}", key, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        if (upstream.isEmpty()) return null;
        var format = TileFormat.ofContentType(upstream.get().contentType());
        if (format.isEmpty()) {
            log.debug(
                    "Upstream answered {} with {}, which is not a tile",
                    key,
                    upstream.get().contentType());
            return null;
        }
        byte[] bytes = upstream.get().body();
        store(key, bytes, format.get());
        return new TileResponse(bytes, format.get().contentType, CacheStatus.MISS);
    }

    private void store(TileKey key, byte[] bytes, TileFormat format) {
        Path path = key.path(root, format);
        try {
            FilePaths.createParentDirectories(path);
            Files.write(path, bytes);
            index.put(key, new CachedTile(path, format.contentType, bytes.length));
        } catch (IOException e) {
            log.debug("Failed to persist tile {}: {}", key, e.getMessage());
        }
    }

    private void onRemoval(TileKey key, CachedTile tile, RemovalCause cause) {
        if (tile == null || cause == RemovalCause.REPLACED) return;
        try {
            Files.deleteIfExists(tile.path());
        } catch (IOException e) {
            log.debug("Tile {} could not be deleted after leaving the cache", tile.path(), e);
        }
    }

    private void rebuildIndexFromDisk() {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(this::indexFile);
        } catch (IOException e) {
            log.warn("Failed to walk tile cache root: {}", e.getMessage());
        }
    }

    private void indexFile(Path path) {
        var key = TileKey.fromPath(root, path);
        var format = TileFormat.ofFile(path);
        if (key.isEmpty() || format.isEmpty()) {
            log.debug("Ignoring {} in the tile cache, it names no tile", path);
            return;
        }
        try {
            index.put(key.get(), new CachedTile(path, format.get().contentType, Files.size(path)));
        } catch (IOException e) {
            log.debug("Tile {} could not be measured and is missing from the index", path, e);
        }
    }

    private HttpRequest request(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(UPSTREAM_TIMEOUT)
                .header("User-Agent", userAgent())
                .GET()
                .build();
    }

    private Optional<UpstreamTile> fetchOverHttp(String url) throws IOException, InterruptedException {
        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request(url), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IllegalArgumentException e) {
            throw new IOException("Tile URL is not usable", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.debug("Upstream tile fetch returned HTTP {}", response.statusCode());
            return Optional.empty();
        }
        String contentType = response.headers().firstValue("content-type").orElse("");
        return Optional.of(new UpstreamTile(response.body(), contentType));
    }

    /** Whether a tile came out of the cache or had to be fetched from the upstream. */
    public enum CacheStatus {
        HIT,
        MISS
    }

    /**
     * Asks the upstream for one tile.
     */
    @FunctionalInterface
    public interface TileFetcher {
        /**
         * @param url the resolved upstream address of the tile
         * @return the tile, or empty when the upstream answered with anything but success
         * @throws IOException          when the upstream could not be reached
         * @throws InterruptedException when the waiting thread was interrupted
         */
        Optional<UpstreamTile> fetch(String url) throws IOException, InterruptedException;
    }

    /**
     * A tile as the upstream answered it.
     *
     * @param body        the bytes
     * @param contentType the content type the upstream declared
     */
    public record UpstreamTile(byte[] body, String contentType) {}

    public record TileResponse(byte[] body, String contentType, CacheStatus status) {}

    public record CacheStats(long bytes, long tiles, long maxBytes) {}

    /**
     * One tile of one provider.
     *
     * @param provider the provider it was fetched from
     * @param z        the zoom
     * @param x        the column
     * @param y        the row
     */
    private record TileKey(MapTileProvider provider, int z, int x, int y) {
        /** Reads the tile a cached file stands for from its place under the root. */
        static Optional<TileKey> fromPath(Path root, Path file) {
            var relative = root.relativize(file);
            if (relative.getNameCount() != 4) return Optional.empty();
            try {
                var provider = MapTileProvider.valueOf(relative.getName(0).toString());
                int z = Integer.parseInt(relative.getName(1).toString());
                int x = Integer.parseInt(relative.getName(2).toString());
                String name = relative.getName(3).toString();
                int dot = name.lastIndexOf('.');
                if (dot < 0) return Optional.empty();
                int y = Integer.parseInt(name.substring(0, dot));
                return Optional.of(new TileKey(provider, z, x, y));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        Path path(Path root, TileFormat format) {
            return root.resolve(provider.name())
                    .resolve(Integer.toString(z))
                    .resolve(Integer.toString(x))
                    .resolve(y + "." + format.extension);
        }
    }

    /**
     * A tile kept on disk.
     *
     * @param path        where its bytes are
     * @param contentType what it is served as
     * @param size        how many bytes it takes
     */
    private record CachedTile(Path path, String contentType, long size) {
        int weight() {
            return (int) Math.min(Integer.MAX_VALUE, Math.max(1, size));
        }
    }

    /**
     * The raster formats a tile may come in, and the extension each is kept under. The extension is
     * what records the content type on disk, so a tile is served as what the upstream said it was.
     */
    private enum TileFormat {
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg"),
        WEBP("webp", "image/webp");

        private final String extension;
        private final String contentType;

        TileFormat(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        /** The format an upstream content type names, ignoring any parameters after it. */
        static Optional<TileFormat> ofContentType(String declared) {
            if (declared == null) return Optional.empty();
            String bare = declared.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            return Stream.of(values())
                    .filter(format -> format.contentType.equals(bare))
                    .findFirst();
        }

        /** The format a cached file was kept as, read from its extension. */
        static Optional<TileFormat> ofFile(Path file) {
            String name = FilePaths.nameOf(file);
            String extension = name.substring(name.lastIndexOf('.') + 1);
            return Stream.of(values())
                    .filter(format -> format.extension.equals(extension))
                    .findFirst();
        }
    }
}
