/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.route;

import dev.chojo.ember.feature.feed.FeedFingerprint;
import dev.chojo.ember.feature.feed.FeedRateLimiter;
import dev.chojo.ember.feature.feed.service.FeedMetricsService;
import dev.chojo.ember.feature.feed.service.PersonalFeedService.Feed;
import io.javalin.http.Context;

/**
 * What every feed address answers alike, whoever the feed belongs to: privacy headers, the rate limit
 * per address, conditional requests and the render metrics.
 */
final class FeedResponses {
    private FeedResponses() {}

    /**
     * Applies the cross-cutting privacy headers used by every public feed endpoint:
     * {@code Referrer-Policy: no-referrer} so the feed token never leaks via {@code Referer}
     * to embedded image hosts or reader proxies, and {@code X-Robots-Tag: noindex} so leaked
     * URLs cannot be picked up by search engines. Safe to call before any conditional 304 or
     * 429 short-circuit so the headers stick on every response status.
     */
    static void applyPrivacyHeaders(Context ctx) {
        ctx.header("Referrer-Policy", "no-referrer");
        ctx.header("X-Robots-Tag", "noindex");
    }

    /**
     * Enforces the per-token rate limit. On excess emits a {@code 429} with {@code Retry-After}
     * and returns {@code true} so the caller can short-circuit. The image endpoint is
     * intentionally exempt, see {@link FeedRateLimiter}.
     */
    static boolean rateLimited(Context ctx, FeedRateLimiter rateLimiter) {
        return rateLimiter
                .tryAcquire(ctx.pathParam("token"))
                .map(retryAfter -> {
                    ctx.status(429);
                    ctx.header("Retry-After", String.valueOf(retryAfter));
                    return true;
                })
                .orElse(false);
    }

    /**
     * Answers a conditional request from the fingerprint, or renders the feed and writes it.
     *
     * @return how many entries were written
     */
    static int serve(Context ctx, Feed feed, String contentType) {
        if (FeedFingerprint.handleConditional(ctx, feed.fingerprint())) return 0;
        var rendered = feed.render().get();
        ctx.contentType(contentType);
        ctx.header("Cache-Control", "public, max-age=3600");
        ctx.result(rendered.body());
        return rendered.entries();
    }

    /** Whether a query flag is on, which it is unless set to {@code 0}. */
    static boolean flag(Context ctx, String name) {
        return !"0".equals(ctx.queryParam(name));
    }

    /**
     * Records a finished feed render to the metrics service. Called from a {@code finally} so
     * 429, 304 and 500 paths all get accounted for.
     */
    static void recordMetric(
            Context ctx, FeedMetricsService metricsService, String type, long startNanos, int entries) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        metricsService.recordRender(type, ctx.status().getCode(), durationMs, entries, ctx.header("User-Agent"));
    }
}
