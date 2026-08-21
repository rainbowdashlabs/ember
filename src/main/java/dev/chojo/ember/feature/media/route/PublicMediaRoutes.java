/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.SafeContentDisposition;
import dev.chojo.ember.util.SafeInlineMime;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * Delivery of a station's media by content hash, without authentication, for the public site.
 *
 * <p>The route is station-scoped rather than owner-scoped on purpose: an inline image in a
 * ticket description has to render for everyone who may read that ticket, so ownership answers
 * "what may I pick from" and never "what may I see on a page". A file is only as private as its
 * hash, which is why restricted content is served through the authenticated twin instead.
 *
 * <p>The former {@code /public/pages/{stationUid}/files/{hash}} address stays registered for one
 * release so a deployed frontend bundle keeps working across the backend restart that renames it.
 */
@Singleton
public class PublicMediaRoutes implements Routes {
    private final MediaLibraryService media;
    private final StationRepository stationRepository;

    @Inject
    public PublicMediaRoutes(MediaLibraryService media, StationRepository stationRepository) {
        this.media = media;
        this.stationRepository = stationRepository;
    }

    private static Integer parseOptionalWidth(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // The instance's own library, served to everyone: a system notice is read in every
        // station, so the picture in it cannot be addressed through one of them. The literal path
        // is registered first, or "instance" would be read as a station's identifier.
        routes.get(prefix + "/public/media/" + MediaLibraryService.INSTANCE_SCOPE + "/{hash}", this::serveInstanceFile);
        routes.get(prefix + "/public/media/{stationUid}/{hash}", this::serveFile);
        routes.get(prefix + "/public/pages/{stationUid}/files/{hash}", this::serveFile);
    }

    private void serveFile(Context ctx) {
        serve(ctx, resolveStation(ctx));
    }

    /** A file the instance holds, which belongs to no station and is served to every one of them. */
    private void serveInstanceFile(Context ctx) {
        serve(ctx, null);
    }

    private void serve(Context ctx, Integer stationId) {
        String hash = ctx.pathParam("hash");
        Integer width = parseOptionalWidth(ctx.queryParam("w"));
        var fileData =
                media.readVariant(stationId, hash, width, ctx.header("Accept")).orElseThrow(NotFoundResponse::new);
        String stored = fileData.contentType();
        ctx.contentType(SafeInlineMime.safeContentType(stored));
        var disposition = SafeInlineMime.isInlineSafe(stored)
                ? SafeContentDisposition.Disposition.INLINE
                : SafeContentDisposition.Disposition.ATTACHMENT;
        ctx.header("Content-Disposition", SafeContentDisposition.build(disposition, hash));
        ctx.header("Cache-Control", "public, max-age=31536000, immutable");
        ctx.header("Vary", "Accept");
        ctx.result(fileData.data());
    }

    private int resolveStation(Context ctx) {
        String param = ctx.pathParam("stationUid");
        try {
            UUID uid = UUID.fromString(param);
            return stationRepository.resolveId(uid).orElseThrow(NotFoundResponse::new);
        } catch (IllegalArgumentException e) {
            // Not a UUID - try as public slug
            return stationRepository.findBySlug(param).map(Station::id).orElseThrow(NotFoundResponse::new);
        }
    }
}
