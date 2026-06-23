/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.transfer;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.station.service.StationExportService;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Token-authenticated source-side endpoints that hand the station's storage descriptor, the
 * raw bytes of station-scoped files, and the avatar bytes of accounts the destination created
 * over to the destination instance during a cross-instance transfer. Token validation reuses
 * {@link StationExportService#validateToken(String)} (same one-shot token used by the table
 * endpoints).
 */
@Singleton
public class StationTransferAssetRoutes implements Routes {

    private final StationExportService exportService;
    private final TransferBackendDescriptorService descriptorService;

    @Inject
    public StationTransferAssetRoutes(
            StationExportService exportService, TransferBackendDescriptorService descriptorService) {
        this.exportService = exportService;
        this.descriptorService = descriptorService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/transfer/{token}/backend", this::getBackendDescriptor);
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
}
