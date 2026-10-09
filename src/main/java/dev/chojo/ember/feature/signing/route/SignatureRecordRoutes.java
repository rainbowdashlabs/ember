/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.FileResponse;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.signing.service.SignatureRecords;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The readable record of a signed document's sealed version, for whoever may read the document: on its
 * own, or joined to the document as one copy to hand out. Both are built from the evidence the version
 * carries and sealed by the station when they are asked for ({@link SignatureRecords}).
 */
@Singleton
public class SignatureRecordRoutes implements Routes {
    private final SignatureRecords records;
    private final DocumentCatalogService catalog;
    private final DocumentAccessService access;

    @Inject
    public SignatureRecordRoutes(
            SignatureRecords records, DocumentCatalogService catalog, DocumentAccessService access) {
        this.records = records;
        this.catalog = catalog;
        this.access = access;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/documents/{id}/versions/{version}/record", this::record, StationPermission.LOGIN);
        routes.get(
                prefix + "/documents/{id}/versions/{version}/with-record", this::withRecord, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/documents/{id}/versions/{version}/record",
            methods = HttpMethod.GET,
            summary = "The signature record of one sealed version of a document, sealed when asked for",
            tags = {"Signing"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "version", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void record(Context ctx) {
        var record = records.record(requireReadable(ctx), pathInt(ctx, "version"));
        FileResponse.send(ctx, DocumentService.SEALED_TYPE, record.fileName(), record.pdf());
    }

    @OpenApi(
            path = "/api/v1/documents/{id}/versions/{version}/with-record",
            methods = HttpMethod.GET,
            summary = "One sealed version of a document with its signature record joined, sealed when asked for",
            tags = {"Signing"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "version", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void withRecord(Context ctx) {
        var copy = records.withRecord(requireReadable(ctx), pathInt(ctx, "version"));
        FileResponse.send(ctx, DocumentService.SEALED_TYPE, copy.fileName(), copy.pdf());
    }

    /** The document behind the path, at the reader's station and readable by them. */
    private Document requireReadable(Context ctx) {
        var document = RouteSupport.requireOwnedOrNotFound(ctx, pathInt(ctx, "id"), catalog::find, Document::stationId);
        access.requireReadable(StationSession.from(ctx), document);
        return document;
    }
}
