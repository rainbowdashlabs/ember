/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.transfer;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationExportService;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.feature.storage.service.TransferBackendDescriptorService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * Token-authenticated source-side endpoints that hand the station's storage descriptor, the
 * raw bytes of station-scoped files, and the avatar bytes of accounts the destination created
 * over to the destination instance during a cross-instance transfer. Token validation reuses
 * {@link StationExportService#validateToken(String)} (same one-shot token used by the table
 * endpoints).
 */
@Singleton
public class StationTransferAssetRoutes implements Routes {

    private static final Logger log = LoggerFactory.getLogger(StationTransferAssetRoutes.class);
    private static final int DEFAULT_LIST_LIMIT = 500;
    private static final int MAX_LIST_LIMIT = 2000;

    private final StationExportService exportService;
    private final TransferBackendDescriptorService descriptorService;
    private final StationRepository stationRepository;
    private final StorageService storageService;
    private final AvatarService avatarService;

    @Inject
    public StationTransferAssetRoutes(
            StationExportService exportService,
            TransferBackendDescriptorService descriptorService,
            StationRepository stationRepository,
            StorageService storageService,
            AvatarService avatarService) {
        this.exportService = exportService;
        this.descriptorService = descriptorService;
        this.stationRepository = stationRepository;
        this.storageService = storageService;
        this.avatarService = avatarService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/transfer/{token}/backend", this::getBackendDescriptor);
        routes.get(prefix + "/public/transfer/{token}/files/{category}", this::listFiles);
        routes.get(prefix + "/public/transfer/{token}/files/{category}/<key>", this::streamFile);
        routes.get(prefix + "/public/transfer/{token}/avatars/{accountUid}", this::streamAvatar);
    }

    @OpenApi(
            path = "/api/v1/public/transfer/{token}/backend",
            methods = HttpMethod.GET,
            summary = "Returns the source station's storage backend descriptor (one-shot per token)",
            description =
                    "When the source station owns a remote storage backend, the response carries the plaintext credentials so the destination can re-encrypt them with its own key and reuse the same target. When the source uses the instance default, the response is {\"type\":\"LOCAL\"} and the destination must byte-copy each file via the /files endpoints. The endpoint is one-shot per transfer token: the second call returns 429 Too Many Requests.",
            tags = {"Transfer"},
            pathParams = @OpenApiParam(name = "token", required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TransferBackendDescriptor.class)),
                @OpenApiResponse(status = "403"),
                @OpenApiResponse(status = "429")
            })
    private void getBackendDescriptor(Context ctx) {
        String token = ctx.pathParam("token");
        exportService.validateToken(token).orElseThrow(() -> new ForbiddenResponse("Invalid or expired token"));
        int stationId = exportService.claimBackendDescriptor(token).orElseThrow(() -> {
            log.info("[export] backend descriptor already claimed - responding 429");
            ctx.status(HttpStatus.TOO_MANY_REQUESTS);
            return new HttpResponseException(
                    HttpStatus.TOO_MANY_REQUESTS.getCode(),
                    "Backend descriptor has already been fetched for this transfer token",
                    Map.of());
        });
        log.info("[export] serving backend descriptor for station {}", stationId);
        ctx.json(descriptorService.describe(stationId));
    }

    @OpenApi(
            path = "/api/v1/public/transfer/{token}/files/{category}",
            methods = HttpMethod.GET,
            summary = "Lists every file key in the given category, paginated by cursor",
            description =
                    "Only station-scoped movable categories are accepted; passing IMAGE_AVATAR or an instance-scoped category answers 400. Keys come back lexicographically sorted. The response's `next` value is the cursor for the following page (pass it as `after`); it is null when the listing is exhausted.",
            tags = {"Transfer"},
            pathParams = {
                @OpenApiParam(name = "token", required = true),
                @OpenApiParam(name = "category", required = true)
            },
            queryParams = {@OpenApiParam(name = "after"), @OpenApiParam(name = "limit", type = Integer.class)},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ListKeysResponse.class)),
                @OpenApiResponse(status = "400"),
                @OpenApiResponse(status = "403"),
                @OpenApiResponse(status = "404")
            })
    private void listFiles(Context ctx) {
        String token = ctx.pathParam("token");
        int stationId =
                exportService.validateToken(token).orElseThrow(() -> new ForbiddenResponse("Invalid or expired token"));
        StorageCategory category = parseStationFileCategory(ctx.pathParam("category"));

        Station station = stationRepository
                .findById(stationId)
                .orElseThrow(() -> new NotFoundResponse("Station " + stationId + " not found"));
        StorageScope.Station scope = new StorageScope.Station(stationId, station.uid());

        String after = ctx.queryParam("after");
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(DEFAULT_LIST_LIMIT);
        if (limit <= 0) limit = DEFAULT_LIST_LIMIT;
        if (limit > MAX_LIST_LIMIT) limit = MAX_LIST_LIMIT;

        List<String> sorted = new ArrayList<>(originalsOnly(category, storageService.listKeys(scope, category, "")));
        Collections.sort(sorted);

        int startIndex = 0;
        if (after != null && !after.isBlank()) {
            int idx = Collections.binarySearch(sorted, after);
            startIndex = idx >= 0 ? idx + 1 : -idx - 1;
        }
        int endIndex = Math.min(startIndex + limit, sorted.size());
        List<String> page = sorted.subList(startIndex, endIndex);
        String next = endIndex < sorted.size() ? page.getLast() : null;
        log.info(
                "[export] listing files: station {} category {} after='{}' limit={} → {} key(s), next={}",
                stationId,
                category,
                after == null ? "" : after,
                limit,
                page.size(),
                next == null ? "none" : "present");
        ctx.json(new ListKeysResponse(List.copyOf(page), next, sorted.size()));
    }

    @OpenApi(
            path = "/api/v1/public/transfer/{token}/files/{category}/{key}",
            methods = HttpMethod.GET,
            summary = "Streams the raw bytes of one file under the given category",
            description =
                    "The key is the category-relative path returned by the /files/{category} listing, including any embedded variant segment for image categories. Responds with the original Content-Type stamped at upload. 404 when the key is no longer present (the source may have concurrently deleted the row during the transfer window).",
            tags = {"Transfer"},
            pathParams = {
                @OpenApiParam(name = "token", required = true),
                @OpenApiParam(name = "category", required = true),
                @OpenApiParam(name = "key", required = true)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400"),
                @OpenApiResponse(status = "403"),
                @OpenApiResponse(status = "404")
            })
    private void streamFile(Context ctx) {
        String token = ctx.pathParam("token");
        int stationId =
                exportService.validateToken(token).orElseThrow(() -> new ForbiddenResponse("Invalid or expired token"));
        StorageCategory category = parseStationFileCategory(ctx.pathParam("category"));
        String key = ctx.pathParam("key");
        if (key == null || key.isBlank()) {
            throw new BadRequestResponse("key is required");
        }

        Station station = stationRepository
                .findById(stationId)
                .orElseThrow(() -> new NotFoundResponse("Station " + stationId + " not found"));
        StorageScope.Station scope = new StorageScope.Station(stationId, station.uid());

        var stream = storageService
                .readRelative(scope, category, key)
                .orElseThrow(() -> new NotFoundResponse("Key not found: " + key));
        log.info(
                "[export] streaming file: station {} category {} key '{}' ({} bytes)",
                stationId,
                category,
                key,
                stream.contentLength());
        ctx.contentType(stream.metadata().contentType());
        ctx.header("Content-Length", String.valueOf(stream.contentLength()));
        try {
            ctx.result(stream.body());
        } catch (RuntimeException e) {
            try {
                stream.close();
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    @OpenApi(
            path = "/api/v1/public/transfer/{token}/avatars/{accountUid}",
            methods = HttpMethod.GET,
            summary = "Streams the original avatar bytes for one account",
            description =
                    "Used by the destination after table import to carry avatars over for accounts it just created. 404 when the account has no avatar on the source.",
            tags = {"Transfer"},
            pathParams = {
                @OpenApiParam(name = "token", required = true),
                @OpenApiParam(name = "accountUid", required = true)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400"),
                @OpenApiResponse(status = "403"),
                @OpenApiResponse(status = "404")
            })
    private void streamAvatar(Context ctx) {
        String token = ctx.pathParam("token");
        exportService.validateToken(token).orElseThrow(() -> new ForbiddenResponse("Invalid or expired token"));
        UUID accountUid = pathUuid(ctx, "accountUid");
        var avatar = avatarService.read(accountUid, 0).orElseThrow(() -> new NotFoundResponse("Avatar not found"));
        log.info("[export] streaming avatar for account {} ({} bytes)", accountUid, avatar.data().length);
        ctx.contentType(avatar.contentType());
        ctx.result(avatar.data());
    }

    /**
     * Strips derived image variants (smaller-width resizes and re-encoded WebP copies) from a
     * raw category listing so the destination only pulls the bytes it cannot regenerate locally.
     * For categories that never have variants the input is returned unchanged. Package-private
     * for direct unit tests.
     */
    static List<String> originalsOnly(StorageCategory category, List<String> keys) {
        String origBase = originalBaseName(category);
        if (origBase == null) return keys;

        Map<String, List<String>> byDir = new LinkedHashMap<>();
        for (String key : keys) {
            int slash = key.lastIndexOf('/');
            String dir = slash < 0 ? "" : key.substring(0, slash);
            byDir.computeIfAbsent(dir, _ -> new ArrayList<>()).add(key);
        }

        var out = new ArrayList<String>(byDir.size());
        for (var entry : byDir.entrySet()) {
            String chosen = pickOriginal(entry.getValue(), origBase);
            if (chosen != null) out.add(chosen);
        }
        return out;
    }

    /**
     * Returns the variant base name that identifies the uploaded original for the given
     * category, or {@code null} when the category never produces derived variants.
     */
    private static String originalBaseName(StorageCategory category) {
        return switch (category) {
            case PAGE_FILES -> "orig";
            case PAGE_IMAGES, IMAGE_LOST_AND_FOUND, IMAGE_QUIZ_QUESTION, IMAGE_KB_ICON, IMAGE_KB_IMAGE -> "original";
            default -> null;
        };
    }

    /**
     * Picks the canonical original from the files in a single hash / image-id directory.
     * Prefers a non-WebP {@code <base>.<ext>} when present (the uploaded source-of-truth) so
     * stations carrying a legacy {@code orig.webp} alongside an uploaded {@code orig.png} ship
     * the PNG. Falls back to a WebP copy only when no other format exists (the uploaded-WebP
     * case).
     */
    private static String pickOriginal(List<String> dirKeys, String origBase) {
        String webpFallback = null;
        for (String key : dirKeys) {
            int slash = key.lastIndexOf('/');
            String filename = slash < 0 ? key : key.substring(slash + 1);
            int dot = filename.lastIndexOf('.');
            String base = dot < 0 ? filename : filename.substring(0, dot);
            if (!base.equals(origBase)) continue;
            String ext = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (!ext.equals("webp")) return key;
            webpFallback = key;
        }
        return webpFallback;
    }

    private StorageCategory parseStationFileCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestResponse("category is required");
        }
        StorageCategory category;
        try {
            category = StorageCategory.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Unknown storage category: " + raw);
        }
        if (category.scopeKind() != StorageScope.Kind.STATION) {
            throw new BadRequestResponse("Category " + category + " is not station-scoped");
        }
        if (!category.isMovable()) {
            throw new BadRequestResponse("Category " + category + " is not movable");
        }
        return category;
    }

    /**
     * Wire shape of {@code GET /files/{category}}. {@code total} is the count of original keys
     * for the whole category, repeated identically on every page so the destination can pin its
     * progress denominator on the first response and never see it grow as later pages arrive.
     */
    public record ListKeysResponse(List<String> keys, String next, int total) {}
}
