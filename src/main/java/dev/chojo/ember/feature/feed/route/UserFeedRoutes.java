/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.feature.feed.FeedFingerprint;
import dev.chojo.ember.feature.feed.FeedRateLimiter;
import dev.chojo.ember.feature.feed.service.FeedMetricsService;
import dev.chojo.ember.feature.feed.service.PersonalFeedService;
import dev.chojo.ember.feature.feed.service.PersonalFeedService.Feed;
import dev.chojo.ember.feature.feed.service.PersonalFeedService.FeedFormat;
import dev.chojo.ember.feature.members.entity.StationMember;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class UserFeedRoutes implements Routes {
    private static final String TOKEN_NAMES_THE_MEMBER =
            "the feed token names one member, and everything the feed serves is that member's household"
                    + " in the member's own station";

    private final PersonalFeedService feeds;
    private final FeedRateLimiter rateLimiter;
    private final FeedMetricsService metricsService;

    @Inject
    public UserFeedRoutes(PersonalFeedService feeds, FeedRateLimiter rateLimiter, FeedMetricsService metricsService) {
        this.feeds = feeds;
        this.rateLimiter = rateLimiter;
        this.metricsService = metricsService;
    }

    /**
     * Applies the cross-cutting privacy headers used by every public feed endpoint:
     * {@code Referrer-Policy: no-referrer} so the feed token never leaks via {@code Referer}
     * to embedded image hosts or reader proxies, and {@code X-Robots-Tag: noindex} so leaked
     * URLs cannot be picked up by search engines. Safe to call before any conditional 304 or
     * 429 short-circuit so the headers stick on every response status.
     */
    private static void applyPrivacyHeaders(Context ctx) {
        ctx.header("Referrer-Policy", "no-referrer");
        ctx.header("X-Robots-Tag", "noindex");
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/feed/{token}/events.ics", this::icalFeed);
        routes.get(prefix + "/public/feed/{token}/notifications.rss", this::rssFeed);
        routes.get(prefix + "/public/feed/{token}/notifications.atom", this::atomFeed);
        routes.get(prefix + "/public/feed/{token}/lost-and-found/{id}/image", this::lostAndFoundImage);
    }

    /**
     * Records a finished feed render to the metrics service. Called from a {@code finally} so
     * 429, 304 and 500 paths all get accounted for.
     */
    private void recordMetric(Context ctx, String type, long startNanos, int entries) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        metricsService.recordRender(type, ctx.status().getCode(), durationMs, entries, ctx.header("User-Agent"));
    }

    /**
     * Enforces the per-token rate limit. On excess emits a {@code 429} with {@code Retry-After}
     * and returns {@code true} so the caller can short-circuit. The image endpoint is
     * intentionally exempt, see {@link FeedRateLimiter}.
     */
    private boolean rateLimit(Context ctx) {
        return rateLimiter
                .tryAcquire(ctx.pathParam("token"))
                .map(retryAfter -> {
                    ctx.status(429);
                    ctx.header("Retry-After", String.valueOf(retryAfter));
                    return true;
                })
                .orElse(false);
    }

    private StationMember resolveToken(Context ctx) {
        return feeds.member(ctx.pathParam("token"));
    }

    /**
     * Answers a conditional request from the fingerprint, or renders the feed and writes it.
     *
     * @return how many entries were written
     */
    private static int serve(Context ctx, Feed feed, String contentType) {
        if (FeedFingerprint.handleConditional(ctx, feed.fingerprint())) return 0;
        var rendered = feed.render().get();
        ctx.contentType(contentType);
        ctx.header("Cache-Control", "public, max-age=3600");
        ctx.result(rendered.body());
        return rendered.entries();
    }

    private static boolean flag(Context ctx, String name) {
        return !"0".equals(ctx.queryParam(name));
    }

    @OpenApi(
            path = "/api/v1/public/feed/{token}/events.ics",
            methods = HttpMethod.GET,
            summary = "Get personal iCal event feed",
            tags = {"User Feed"},
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true),
            responses = {
                @OpenApiResponse(
                        status = "200",
                        description = "iCal calendar. Cache-Control: public, max-age=3600",
                        content = @OpenApiContent(type = "text/calendar")),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(TOKEN_NAMES_THE_MEMBER)
    private void icalFeed(Context ctx) {
        applyPrivacyHeaders(ctx);
        long start = System.nanoTime();
        int entryCount = 0;
        try {
            var member = resolveToken(ctx);
            if (rateLimit(ctx)) return;
            entryCount = serve(ctx, feeds.calendar(member, flag(ctx, "verbose")), "text/calendar; charset=utf-8");
        } finally {
            recordMetric(ctx, "ics", start, entryCount);
        }
    }

    @OpenApi(
            path = "/api/v1/public/feed/{token}/lost-and-found/{id}/image",
            methods = HttpMethod.GET,
            summary = "Get a lost-and-found image scoped to a feed token",
            tags = {"User Feed"},
            pathParams = {
                @OpenApiParam(name = "token", type = String.class, required = true),
                @OpenApiParam(name = "id", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(
                        status = "200",
                        description = "Image. Cache-Control: public, max-age=86400. Referrer-Policy: no-referrer."),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void lostAndFoundImage(Context ctx) {
        applyPrivacyHeaders(ctx);
        var member = resolveToken(ctx);
        int itemId = ctx.pathParamAsClass("id", Integer.class).get();
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
        var image = feeds.lostAndFoundImage(member.stationId(), itemId, size);
        ctx.contentType(image.contentType());
        ctx.header("Cache-Control", "public, max-age=86400");
        ctx.result(image.data());
    }

    @OpenApi(
            path = "/api/v1/public/feed/{token}/notifications.rss",
            methods = HttpMethod.GET,
            summary = "Get personal RSS notification feed",
            tags = {"User Feed"},
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true),
            responses = {
                @OpenApiResponse(
                        status = "200",
                        description = "RSS feed. Cache-Control: public, max-age=3600",
                        content = @OpenApiContent(type = "application/rss+xml")),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(TOKEN_NAMES_THE_MEMBER)
    private void rssFeed(Context ctx) {
        notificationFeed(ctx, FeedFormat.RSS, "rss", "application/rss+xml; charset=utf-8");
    }

    @OpenApi(
            path = "/api/v1/public/feed/{token}/notifications.atom",
            methods = HttpMethod.GET,
            summary = "Get personal Atom notification feed",
            tags = {"User Feed"},
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true),
            responses = {
                @OpenApiResponse(
                        status = "200",
                        description = "Atom feed. Cache-Control: public, max-age=3600",
                        content = @OpenApiContent(type = "application/atom+xml")),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(TOKEN_NAMES_THE_MEMBER)
    private void atomFeed(Context ctx) {
        notificationFeed(ctx, FeedFormat.ATOM, "atom", "application/atom+xml; charset=utf-8");
    }

    private void notificationFeed(Context ctx, FeedFormat format, String metricType, String contentType) {
        applyPrivacyHeaders(ctx);
        long start = System.nanoTime();
        int entryCount = 0;
        try {
            var member = resolveToken(ctx);
            if (rateLimit(ctx)) return;
            var feed = feeds.notifications(
                    member, ctx.pathParam("token"), format, flag(ctx, "verbose"), flag(ctx, "images"));
            entryCount = serve(ctx, feed, contentType);
        } finally {
            recordMetric(ctx, metricType, start, entryCount);
        }
    }
}
