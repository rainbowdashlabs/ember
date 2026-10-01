/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.transfer;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.station.service.StationExportService;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StationTransferFileService;
import dev.chojo.ember.feature.storage.service.StationTransferFileService.ListKeysResponse;
import dev.chojo.ember.feature.storage.service.TransferBackendDescriptorService;
import io.javalin.http.Context;
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

import java.util.Locale;
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

    private final StationExportService exportService;
    private final TransferBackendDescriptorService descriptorService;
    private final StationTransferFileService fileService;
    private final AvatarService avatarService;

    @Inject
    public StationTransferAssetRoutes(
            StationExportService exportService,
            TransferBackendDescriptorService descriptorService,
            StationTransferFileService fileService,
            AvatarService avatarService) {
        this.exportService = exportService;
        this.descriptorService = descriptorService;
        this.fileService = fileService;
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
                    "When the source station owns a remote storage backend, the response carries the plaintext credentials so the destination can re-encrypt them with its own key and reuse the same target. When the source uses the instance default, the response is {\"type\":\"LOCAL\"} and the destination must byte-copy each file via the /files endpoints. The endpoint is one-shot per transfer token: the second call returns 410 Gone.",
            tags = {"Transfer"},
            pathParams = @OpenApiParam(name = "token", required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TransferBackendDescriptor.class)),
                @OpenApiResponse(status = "403"),
                @OpenApiResponse(status = "410")
            })
    private void getBackendDescriptor(Context ctx) {
        String token = ctx.pathParam("token");
        exportService.validateToken(token).orElseThrow(Refusal.TRANSFER_TOKEN_NOT_GOOD_ON_BACKEND::raise);
        int stationId = exportService.claimBackendDescriptor(token).orElseThrow(() -> {
            log.info("[export] backend descriptor already claimed - responding 410");
            return Refusal.TRANSFER_BACKEND_ALREADY_HANDED_OVER.raise();
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
                exportService.validateToken(token).orElseThrow(Refusal.TRANSFER_TOKEN_NOT_GOOD_ON_FILE_LIST::raise);
        StorageCategory category = parseStationFileCategory(ctx.pathParam("category"));
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(0);
        ctx.json(fileService.page(stationId, category, ctx.queryParam("after"), limit));
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
        int stationId = exportService.validateToken(token).orElseThrow(Refusal.TRANSFER_TOKEN_NOT_GOOD_ON_FILE::raise);
        StorageCategory category = parseStationFileCategory(ctx.pathParam("category"));
        String key = ctx.pathParam("key");
        if (key == null || key.isBlank()) {
            throw Refusal.TRANSFER_FILE_KEY_MISSING.raise();
        }

        var stream = fileService.open(stationId, category, key);
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
    @StationFree("the transfer token is the authorisation, and an account's avatar belongs to the account")
    private void streamAvatar(Context ctx) {
        String token = ctx.pathParam("token");
        exportService.validateToken(token).orElseThrow(Refusal.TRANSFER_TOKEN_NOT_GOOD_ON_AVATAR::raise);
        UUID accountUid = pathUuid(ctx, "accountUid");
        var avatar = avatarService.read(accountUid, 0).orElseThrow(Refusal.TRANSFER_AVATAR_NOT_HERE::raise);
        log.info("[export] streaming avatar for account {} ({} bytes)", accountUid, avatar.data().length);
        ctx.contentType(avatar.contentType());
        ctx.result(avatar.data());
    }

    private StorageCategory parseStationFileCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            throw Refusal.TRANSFER_FILE_KIND_MISSING.raise();
        }
        StorageCategory category;
        try {
            category = StorageCategory.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw Refusal.TRANSFER_FILE_KIND_UNKNOWN.raise(raw);
        }
        if (category.scopeKind() != StorageScope.Kind.STATION) {
            throw Refusal.TRANSFER_FILE_KIND_NOT_A_STATIONS.raise(raw);
        }
        if (!category.isMovable()) {
            throw Refusal.TRANSFER_FILE_KIND_NOT_MOVABLE.raise(raw);
        }
        return category;
    }
}
