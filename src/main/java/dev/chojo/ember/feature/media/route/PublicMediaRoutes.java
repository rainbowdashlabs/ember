/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.media.service.PublicMediaService;
import dev.chojo.ember.util.SafeContentDisposition;
import dev.chojo.ember.util.SafeInlineMime;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Delivery of a station's media by content hash, without authentication, for the public site.
 *
 * <p>The route is station-scoped rather than owner-scoped on purpose: an inline image in a
 * ticket description has to render for everyone who may read that ticket, so ownership answers
 * "what may I pick from" and never "what may I see on a page". A file is only as private as its
 * hash, which is why restricted content is served through the authenticated twin instead.
 *
 * <p>The instance's own library is served to everyone too: a system notice is read in every
 * station, so the picture in it cannot be addressed through one of them. Its literal path is
 * registered first, or "instance" would be read as a station's identifier.
 *
 * <p>The former {@code /public/pages/{stationUid}/files/{hash}} address stays registered for one
 * release so a deployed frontend bundle keeps working across the backend restart that renames it.
 */
@Singleton
public class PublicMediaRoutes implements Routes {
    private final PublicMediaService media;

    @Inject
    public PublicMediaRoutes(PublicMediaService media) {
        this.media = media;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/media/" + MediaLibraryService.INSTANCE_SCOPE + "/{hash}", this::serveInstanceFile);
        routes.get(prefix + "/public/media/{stationUid}/{hash}", this::serveFile);
        routes.get(prefix + "/public/pages/{stationUid}/files/{hash}", this::serveFile);
    }

    @OpenApi(
            path = "/api/v1/public/media/{stationUid}/{hash}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    @OpenApi(
            path = "/api/v1/public/pages/{stationUid}/files/{hash}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void serveFile(Context ctx) {
        int stationId = media.resolveStation(ctx.pathParam("stationUid"));
        String hash = ctx.pathParam("hash");
        serve(ctx, hash, media.read(stationId, hash, ctx.queryParam("w"), ctx.header("Accept")));
    }

    /** A file the instance holds, which belongs to no station and is served to every one of them. */
    @StationFree("a file of the instance's own library belongs to no station and is served to every one of them")
    @OpenApi(
            path = "/api/v1/public/media/instance/{hash}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void serveInstanceFile(Context ctx) {
        String hash = ctx.pathParam("hash");
        serve(ctx, hash, media.readInstance(hash, ctx.queryParam("w"), ctx.header("Accept")));
    }

    private static void serve(Context ctx, String hash, MediaContent file) {
        String stored = file.contentType();
        ctx.contentType(SafeInlineMime.safeContentType(stored));
        var disposition = SafeInlineMime.isInlineSafe(stored)
                ? SafeContentDisposition.Disposition.INLINE
                : SafeContentDisposition.Disposition.ATTACHMENT;
        ctx.header("Content-Disposition", SafeContentDisposition.build(disposition, hash));
        ctx.header("Cache-Control", "public, max-age=31536000, immutable");
        ctx.header("Vary", "Accept");
        ctx.result(file.data());
    }
}
