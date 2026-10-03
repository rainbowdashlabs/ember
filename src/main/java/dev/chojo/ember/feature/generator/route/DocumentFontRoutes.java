/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService.DocumentFontsResponse;
import dev.chojo.ember.feature.generator.service.font.FontSampleService;
import dev.chojo.ember.owner.Owner;
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
 * The fonts documents print with, at the three owners that keep them: a station for its own templates,
 * an association for its own and its stations', and the instance for every station.
 *
 * <p>Each owner lists, uploads and deletes only its own fonts; the list also names every family its
 * templates reach, for the pickers, and draws a picture of sample text in any of them. The owner comes
 * from the session, never from the request. There is no route that hands a font file out: the files are
 * drawn with on the server and never reach a browser.
 *
 * <p>An upload is a form with the file ({@code file}), the family name ({@code family}), the style
 * ({@code style}, regular where left out) and the uploader's confirmation that the owner may use the
 * font ({@code confirmed}).
 */
@Singleton
public class DocumentFontRoutes implements Routes {
    private static final String KEPT_BY_THE_ASSOCIATION =
            "a font of the association is looked up among the association's own, which the session names";

    private final DocumentFontService fonts;
    private final FontSampleService samples;

    @Inject
    public DocumentFontRoutes(DocumentFontService fonts, FontSampleService samples) {
        this.fonts = fonts;
        this.samples = samples;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/document-fonts", this::stationList, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.post(prefix + "/document-fonts", this::stationUpload, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.delete(prefix + "/document-fonts/{id}", this::stationDelete, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.get(
                prefix + "/cluster/document-fonts",
                this::associationList,
                ClusterPermission.CLUSTER_DOCUMENT_TEMPLATE_EDIT);
        routes.post(
                prefix + "/cluster/document-fonts",
                this::associationUpload,
                ClusterPermission.CLUSTER_DOCUMENT_TEMPLATE_EDIT);
        routes.delete(
                prefix + "/cluster/document-fonts/{id}",
                this::associationDelete,
                ClusterPermission.CLUSTER_DOCUMENT_TEMPLATE_EDIT);
        routes.get(prefix + "/admin/document-fonts", this::instanceList, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/document-fonts", this::instanceUpload, InstancePermission.ADMINISTRATOR);
        routes.delete(prefix + "/admin/document-fonts/{id}", this::instanceDelete, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/document-fonts/sample", this::stationSample, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.get(
                prefix + "/cluster/document-fonts/sample",
                this::associationSample,
                ClusterPermission.CLUSTER_DOCUMENT_TEMPLATE_EDIT);
        routes.get(prefix + "/admin/document-fonts/sample", this::instanceSample, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/document-fonts/sample",
            methods = HttpMethod.GET,
            summary = "A picture of sample text in a font family the station's templates reach, or the default font",
            tags = {"Documents"},
            queryParams = {
                @OpenApiParam(name = "family", description = "The family, the default font where left out"),
                @OpenApiParam(name = "style", description = "The style, regular where left out"),
                @OpenApiParam(name = "v", description = "The version of the sample the list named, for caching")
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(type = "image/png")),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void stationSample(Context ctx) {
        sample(ctx, StationSession.from(ctx).owner());
    }

    @OpenApi(
            path = "/api/v1/cluster/document-fonts/sample",
            methods = HttpMethod.GET,
            summary =
                    "A picture of sample text in a font family the association's templates reach, or the default font",
            tags = {"Cluster"},
            queryParams = {
                @OpenApiParam(name = "family", description = "The family, the default font where left out"),
                @OpenApiParam(name = "style", description = "The style, regular where left out"),
                @OpenApiParam(name = "v", description = "The version of the sample the list named, for caching")
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(type = "image/png")),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void associationSample(Context ctx) {
        sample(ctx, association(ctx));
    }

    @OpenApi(
            path = "/api/v1/admin/document-fonts/sample",
            methods = HttpMethod.GET,
            summary = "A picture of sample text in a font family the instance offers, or the default font",
            tags = {"Admin"},
            queryParams = {
                @OpenApiParam(name = "family", description = "The family, the default font where left out"),
                @OpenApiParam(name = "style", description = "The style, regular where left out"),
                @OpenApiParam(name = "v", description = "The version of the sample the list named, for caching")
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(type = "image/png")),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void instanceSample(Context ctx) {
        sample(ctx, UserSession.from(ctx).instance());
    }

    private void sample(Context ctx, Owner owner) {
        var style = FontStyle.parse(ctx.queryParam("style"))
                .orElseThrow(DocumentRefusal.DOCUMENT_FONT_STYLE_UNKNOWN::raise);
        ctx.contentType("image/png").result(samples.sample(owner, ctx.queryParam("family"), style));
    }

    @OpenApi(
            path = "/api/v1/document-fonts",
            methods = HttpMethod.GET,
            summary = "The station's fonts, and every font family its templates can print in",
            tags = {"Documents"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentFontsResponse.class)))
    private void stationList(Context ctx) {
        ctx.json(fonts.list(StationSession.from(ctx).owner()));
    }

    @OpenApi(
            path = "/api/v1/document-fonts",
            methods = HttpMethod.POST,
            summary = "Upload one style of a font family for the station's documents",
            tags = {"Documents"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentFontsResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void stationUpload(Context ctx) {
        var session = StationSession.from(ctx);
        upload(ctx, session.owner(), session.accountId());
    }

    @OpenApi(
            path = "/api/v1/document-fonts/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a font of the station that no template in use prints with",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentFontsResponse.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void stationDelete(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(fonts.delete(session.owner(), pathInt(ctx, "id"), session.accountId()));
    }

    @OpenApi(
            path = "/api/v1/cluster/document-fonts",
            methods = HttpMethod.GET,
            summary = "The association's fonts, and every font family its templates can print in",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentFontsResponse.class)))
    private void associationList(Context ctx) {
        ctx.json(fonts.list(association(ctx)));
    }

    @OpenApi(
            path = "/api/v1/cluster/document-fonts",
            methods = HttpMethod.POST,
            summary = "Upload one style of a font family for the documents of the association and its stations",
            tags = {"Cluster"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentFontsResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void associationUpload(Context ctx) {
        upload(ctx, association(ctx), UserSession.from(ctx).accountId());
    }

    @OpenApi(
            path = "/api/v1/cluster/document-fonts/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a font of the association that no template in use prints with",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentFontsResponse.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(KEPT_BY_THE_ASSOCIATION)
    private void associationDelete(Context ctx) {
        ctx.json(fonts.delete(
                association(ctx), pathInt(ctx, "id"), UserSession.from(ctx).accountId()));
    }

    @OpenApi(
            path = "/api/v1/admin/document-fonts",
            methods = HttpMethod.GET,
            summary = "The fonts the instance offers every station",
            tags = {"Admin"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentFontsResponse.class)))
    private void instanceList(Context ctx) {
        ctx.json(fonts.list(UserSession.from(ctx).instance()));
    }

    @OpenApi(
            path = "/api/v1/admin/document-fonts",
            methods = HttpMethod.POST,
            summary = "Upload one style of a font family for the documents of every station",
            tags = {"Admin"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentFontsResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void instanceUpload(Context ctx) {
        var session = UserSession.from(ctx);
        upload(ctx, session.instance(), session.accountId());
    }

    @OpenApi(
            path = "/api/v1/admin/document-fonts/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a font of the instance that no template in use prints with",
            tags = {"Admin"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentFontsResponse.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void instanceDelete(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(fonts.delete(session.instance(), pathInt(ctx, "id"), session.accountId()));
    }

    private void upload(Context ctx, Owner owner, int accountId) {
        var style =
                FontStyle.parse(ctx.formParam("style")).orElseThrow(DocumentRefusal.DOCUMENT_FONT_STYLE_UNKNOWN::raise);
        ctx.json(fonts.upload(
                owner,
                ctx.uploadedFile("file"),
                ctx.formParam("family"),
                style,
                Boolean.parseBoolean(ctx.formParam("confirmed")),
                accountId));
    }

    private static Owner.Association association(Context ctx) {
        return UserSession.from(ctx).association(ClusterRefusal.NO_CLUSTER_CHOSEN_FOR_FONTS);
    }
}
