/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed;

import dev.chojo.ember.util.Sha256;
import io.javalin.http.Context;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Computes ETags / Last-Modified timestamps for the personal feeds and turns matching
 * conditional request headers into {@code 304 Not Modified} responses.
 *
 * <p>The fingerprint is derived from cheap freshness signals exposed by the underlying
 * repositories (max(updated_at), max(created_at), id sequences) plus the request-shaping
 * query parameters (verbose, images, locale). Any change to those inputs invalidates the
 * cache and forces a full render.
 */
public final class FeedFingerprint {
    private static final DateTimeFormatter HTTP_DATE = DateTimeFormatter.ofPattern(
                    "EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private FeedFingerprint() {}

    /**
     * Computes a stable, quoted ETag and the corresponding {@code Last-Modified} instant for
     * the given input tuple. Strings are joined with {@code \0} so distinct fields cannot
     * accidentally collide. The first 16 hex characters of the hash are kept: collisions stay out of
     * reach and the header stays short for readers that poll often.
     */
    public static Result compute(Instant lastModified, Object... parts) {
        var input = new StringBuilder();
        for (Object part : parts) {
            input.append(part).append('\0');
        }
        input.append(lastModified.toEpochMilli());
        return new Result("\"" + Sha256.hexPrefix(input.toString(), 16) + "\"", lastModified);
    }

    /**
     * Honours {@code If-None-Match} or {@code If-Modified-Since} on the request: when either
     * matches the freshly-computed fingerprint, emits {@code 304 Not Modified} (with the
     * standard freshness headers) and returns {@code true}. Callers should skip the render in
     * that case.
     *
     * <p>On miss the response is left untouched aside from setting {@code ETag},
     * {@code Last-Modified}, and {@code Vary} so the caller can stream the body.
     */
    public static boolean handleConditional(Context ctx, Result fp) {
        String inm = ctx.header("If-None-Match");
        String ims = ctx.header("If-Modified-Since");

        boolean matchByEtag = inm != null && inm.contains(fp.etag());
        boolean matchByDate =
                ims != null && parseHttpDate(ims) >= fp.lastModified().getEpochSecond();

        if (matchByEtag || matchByDate) {
            ctx.status(304);
            ctx.header("ETag", fp.etag());
            ctx.header("Last-Modified", HTTP_DATE.format(fp.lastModified()));
            ctx.header("Vary", "If-None-Match, If-Modified-Since");
            return true;
        }
        ctx.header("ETag", fp.etag());
        ctx.header("Last-Modified", HTTP_DATE.format(fp.lastModified()));
        ctx.header("Vary", "If-None-Match, If-Modified-Since");
        return false;
    }

    private static long parseHttpDate(String value) {
        try {
            return Instant.from(HTTP_DATE.parse(value.trim())).getEpochSecond();
        } catch (Exception ignored) {
            return -1;
        }
    }

    /**
     * Computed fingerprint pair: an {@code ETag} value plus its {@code Last-Modified} instant.
     */
    public record Result(String etag, Instant lastModified) {}
}
