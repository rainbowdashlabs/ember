/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.transfer;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationExportService;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Token-authenticated source-side endpoints that hand the station's storage descriptor, the
 * raw bytes of station-scoped files, and the avatar bytes of accounts the destination created
 * over to the destination instance during a cross-instance transfer. Token validation reuses
 * {@link StationExportService#validateToken(String)} (same one-shot token used by the table
 * endpoints).
 */
@Singleton
public class StationTransferAssetRoutes implements Routes {

    private static final int DEFAULT_LIST_LIMIT = 500;
    private static final int MAX_LIST_LIMIT = 2000;

    private final StationExportService exportService;
    private final TransferBackendDescriptorService descriptorService;
    private final StationRepository stationRepository;
    private final StorageService storageService;

    @Inject
    public StationTransferAssetRoutes(
            StationExportService exportService,
            TransferBackendDescriptorService descriptorService,
            StationRepository stationRepository,
            StorageService storageService) {
        this.exportService = exportService;
        this.descriptorService = descriptorService;
        this.stationRepository = stationRepository;
        this.storageService = storageService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/transfer/{token}/backend", this::getBackendDescriptor);
        routes.get(prefix + "/public/transfer/{token}/files/{category}", this::listFiles);
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
            ctx.status(HttpStatus.TOO_MANY_REQUESTS);
            return new io.javalin.http.HttpResponseException(
                    HttpStatus.TOO_MANY_REQUESTS.getCode(),
                    "Backend descriptor has already been fetched for this transfer token",
                    java.util.Map.of());
        });
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

        List<String> sorted = new ArrayList<>(storageService.listKeys(scope, category, ""));
        Collections.sort(sorted);

        int startIndex = 0;
        if (after != null && !after.isBlank()) {
            int idx = Collections.binarySearch(sorted, after);
            startIndex = idx >= 0 ? idx + 1 : -idx - 1;
        }
        int endIndex = Math.min(startIndex + limit, sorted.size());
        List<String> page = sorted.subList(startIndex, endIndex);
        String next = endIndex < sorted.size() ? page.get(page.size() - 1) : null;
        ctx.json(new ListKeysResponse(List.copyOf(page), next));
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
        if (StorageCategory.LEGACY_CATEGORIES.contains(category)) {
            throw new BadRequestResponse("Category " + category + " is a legacy rollup and not transferable");
        }
        return category;
    }

    public record ListKeysResponse(List<String> keys, String next) {}
}
