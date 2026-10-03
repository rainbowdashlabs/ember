/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.Sha256;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The headers every response carries beside its body: browser hardening, caching and the identity
 * of the station a federation response comes from.
 *
 * <p>They are written by after-handlers, in a fixed order: the security headers first, then the
 * cache headers, then the federation headers. A cache rule may tighten the referrer policy the
 * security headers set, and the security headers leave a policy a route already chose alone.
 */
@Singleton
public class ResponseHeaderPolicy {
    private static final String API_PREFIX = ApiServer.API_PREFIX;

    /**
     * How long a browser keeps a picture of sample text in a font: a week. The pickers ask for it with
     * the version of the font's files in the address, so a changed font is asked for anew.
     */
    private static final int FONT_SAMPLE_MAX_AGE_SECONDS = 7 * 24 * 60 * 60;

    private final Demo demoConfig;
    private final StationRepository stationRepository;

    @Inject
    public ResponseHeaderPolicy(Demo demoConfig, StationRepository stationRepository) {
        this.demoConfig = demoConfig;
        this.stationRepository = stationRepository;
    }

    /**
     * Registers the after-handlers on the router, in the order they have to run.
     *
     * @param routes the router to register on
     */
    public void install(RoutesConfig routes) {
        routes.after(this::applyBrowserSecurityHeaders);
        routes.after(ResponseHeaderPolicy::applyCacheHeaders);
        routes.after(this::applyFederationHeaders);
    }

    /**
     * Hardens every response in the browser.
     *
     * <p>The API hands out what members uploaded, and {@code SafeInlineMime} answers with
     * {@code application/octet-stream} for everything it will not show inline. Without
     * {@code nosniff} a browser may look at the body anyway and render it as HTML, which is the
     * hole that allow-list exists to close, so this header is what makes the refusal hold.
     *
     * <p>Framing is limited to this origin rather than refused outright: the application shows a
     * PDF, a presentation and a knowledge-base file by pointing an {@code iframe} at the endpoint
     * that serves it, so a flat refusal blocks the application from displaying its own files while
     * doing nothing about another site, which the same-origin rule already stops.
     *
     * <p>A route that has already asked for something stricter keeps its own referrer policy: the
     * user feed says {@code no-referrer} so its token cannot travel in a {@code Referer}. Strict
     * transport security is sent only over HTTPS, and never by a development instance, which is
     * served over plain HTTP and would pin a browser to a scheme it cannot answer.
     *
     * @param ctx the response to harden
     */
    void applyBrowserSecurityHeaders(Context ctx) {
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.header("X-Frame-Options", "SAMEORIGIN");
        ctx.header("Content-Security-Policy", "frame-ancestors 'self'");
        if (ctx.res().getHeader("Referrer-Policy") == null) {
            ctx.header("Referrer-Policy", "strict-origin-when-cross-origin");
        }
        if (!demoConfig.dev() && "https".equalsIgnoreCase(ctx.scheme())) {
            ctx.header("Strict-Transport-Security", "max-age=31536000");
        }
    }

    /**
     * Sets Cache-Control and ETag headers based on the request path.
     *
     * <p>Ordering matters: content-hashed page files get an immutable year-long cache; the public
     * configuration is revalidated every time because it names the running version, which is the one
     * thing a deployment changes, and held for an hour it made every deployment look as though it had
     * not happened, while its tag still makes asking again a {@code 304} on all the days nothing
     * changed; a waiting-list entry behind its own link is nobody's to keep, so it is stored nowhere;
     * everything else under {@code /public/} is publicly cacheable; a picture of sample text in a font is
     * kept privately for a week, per station and association it was asked for; only then are non-public
     * binary resources given a short private cache. Error responses receive no caching headers, and the
     * binary-resource match is segment-precise so an authenticated path that merely contains
     * {@code image}/{@code logo} as a substring (e.g. the logout endpoint) is not mis-tagged as
     * cacheable.
     *
     * @param ctx the response to tag
     */
    static void applyCacheHeaders(Context ctx) {
        if (ctx.method() != HandlerType.GET) return;
        if (ctx.statusCode() >= 400) return;

        String path = ctx.path();

        if (path.startsWith(API_PREFIX + "/public/pages/") && path.contains("/files/")) {
            ctx.header("Cache-Control", "public, max-age=31536000, immutable");
            ctx.header("Vary", "Accept");
            return;
        }

        if (path.equals(API_PREFIX + "/public/config")) {
            ctx.header("Cache-Control", "public, no-cache");
            addETag(ctx);
            return;
        }

        if (path.startsWith(API_PREFIX + "/public/waiting-list/entry/")) {
            ctx.header("Cache-Control", "private, no-store");
            return;
        }

        if (path.startsWith(API_PREFIX + "/public/shared/") || path.startsWith(API_PREFIX + "/public/shared-form/")) {
            ctx.header("Cache-Control", "private, no-store");
            ctx.header("Referrer-Policy", "no-referrer");
            ctx.header("X-Robots-Tag", "noindex");
            return;
        }

        if (path.startsWith(API_PREFIX + "/public/")) {
            ctx.header("Cache-Control", "public, max-age=3600");
            addETag(ctx);
            return;
        }

        if (path.endsWith("/document-fonts/sample")) {
            ctx.header("Cache-Control", "private, max-age=" + FONT_SAMPLE_MAX_AGE_SECONDS);
            ctx.header("Vary", "X-Station-Id, X-Cluster-Id");
            return;
        }

        if (isBinaryResourcePath(path)) {
            ctx.header("Cache-Control", "private, max-age=300");
            return;
        }

        if (path.startsWith(API_PREFIX + "/demo/")) {
            ctx.header("Cache-Control", "public, max-age=60");
            return;
        }

        if (path.startsWith(API_PREFIX + "/")) {
            ctx.header("Cache-Control", "private, no-cache");
            addETag(ctx);
        }
    }

    /**
     * Matches the binary-resource endpoints (avatars, logos, images) by whole path
     * segment or suffix rather than substring, so unrelated paths that merely contain
     * {@code avatar}/{@code logo}/{@code image} - such as {@code /auth/logout} - are
     * excluded.
     */
    private static boolean isBinaryResourcePath(String path) {
        return path.endsWith("/avatar")
                || path.endsWith("/logo")
                || path.endsWith("/image")
                || path.endsWith("/images")
                || path.contains("/images/")
                || path.contains("/logo-fragment/");
    }

    /**
     * Sets the station identity headers on responses from {@code /remote/} endpoints, naming this
     * station, the one serving the data, to the partner that asked. Responses from
     * {@code /federated/} endpoints are left alone: their handlers set these headers themselves per
     * entity.
     *
     * @param ctx the response to name the station on
     */
    void applyFederationHeaders(Context ctx) {
        String path = ctx.path();
        if (!path.startsWith(API_PREFIX + "/remote/")) return;

        FederationSession fedSession = ctx.attribute(FederationSession.ATTR_FEDERATION_SESSION);
        if (fedSession != null) {
            stationRepository
                    .findById(fedSession.stationId())
                    .ifPresent(station -> FederationHeaders.setStationHeaders(ctx, station));
        }
    }

    /**
     * Tags the response with the SHA-256 of its body, truncated to 16 hex characters. Javalin
     * compares a tag set here with {@code If-None-Match} when it writes the response and answers
     * {@code 304 Not Modified} on a match.
     *
     * <p>The tag is computed here rather than by Javalin's own generator for two reasons: only the
     * paths {@link #applyCacheHeaders} names are tagged, and Javalin's generator uses an Adler-32
     * checksum, for which a different body with the same tag is easy to build, while a truncated
     * SHA-256 is not.
     */
    private static void addETag(Context ctx) {
        String body = ctx.result();
        if (body == null || body.isEmpty()) return;
        ctx.header("ETag", "\"" + Sha256.hexPrefix(body, 16) + "\"");
    }
}
