/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.feed.FeedRateLimiter;
import dev.chojo.ember.feature.feed.entity.StationCalendarFeed;
import dev.chojo.ember.feature.feed.entity.StationFeedLevel;
import dev.chojo.ember.feature.feed.service.FeedMetricsService;
import dev.chojo.ember.feature.feed.service.StationCalendarFeedService;
import dev.chojo.ember.feature.feed.service.StationFeedCalendar;
import dev.chojo.ember.feature.station.entity.StationModule;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The calendar feeds of a station: managing them for whoever holds
 * {@link StationPermission#EVENT_CALENDAR_FEEDS}, and the address a shared calendar fetches.
 */
@Singleton
public class StationCalendarFeedRoutes implements Routes {
    private static final String TOKEN_DEFINES_THE_STATION =
            "the feed token belongs to one feed of one station, and the feed serves only that station's appointments";

    private final StationCalendarFeedService feeds;
    private final StationFeedCalendar calendars;
    private final FeedRateLimiter rateLimiter;
    private final FeedMetricsService metricsService;

    @Inject
    public StationCalendarFeedRoutes(
            StationCalendarFeedService feeds,
            StationFeedCalendar calendars,
            FeedRateLimiter rateLimiter,
            FeedMetricsService metricsService) {
        this.feeds = feeds;
        this.calendars = calendars;
        this.rateLimiter = rateLimiter;
        this.metricsService = metricsService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/station-feed/{token}/events.ics", this::icalFeed);
        routes.get(
                prefix + "/calendar-feeds", this::list, StationPermission.EVENT_CALENDAR_FEEDS, StationModule.EVENTS);
        routes.post(
                prefix + "/calendar-feeds", this::create, StationPermission.EVENT_CALENDAR_FEEDS, StationModule.EVENTS);
        routes.put(
                prefix + "/calendar-feeds/{id}",
                this::update,
                StationPermission.EVENT_CALENDAR_FEEDS,
                StationModule.EVENTS);
        routes.post(
                prefix + "/calendar-feeds/{id}/regenerate",
                this::regenerate,
                StationPermission.EVENT_CALENDAR_FEEDS,
                StationModule.EVENTS);
        routes.delete(
                prefix + "/calendar-feeds/{id}",
                this::delete,
                StationPermission.EVENT_CALENDAR_FEEDS,
                StationModule.EVENTS);
    }

    @OpenApi(
            path = "/api/v1/public/station-feed/{token}/events.ics",
            methods = HttpMethod.GET,
            summary = "Get a station calendar feed",
            description = "Every appointment of the station up to the feed's level, without sign-up state.",
            tags = {"Station Calendar Feeds"},
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true),
            responses = {
                @OpenApiResponse(
                        status = "200",
                        description = "iCal calendar. Cache-Control: public, max-age=3600",
                        content = @OpenApiContent(type = "text/calendar")),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree(TOKEN_DEFINES_THE_STATION)
    private void icalFeed(Context ctx) {
        FeedResponses.applyPrivacyHeaders(ctx);
        long start = System.nanoTime();
        int entryCount = 0;
        try {
            var feed = calendars.feed(ctx.pathParam("token"));
            if (FeedResponses.rateLimited(ctx, rateLimiter)) return;
            entryCount = FeedResponses.serve(
                    ctx, calendars.calendar(feed, FeedResponses.flag(ctx, "verbose")), "text/calendar; charset=utf-8");
        } finally {
            FeedResponses.recordMetric(ctx, metricsService, "ics", start, entryCount);
        }
    }

    @OpenApi(
            path = "/api/v1/calendar-feeds",
            methods = HttpMethod.GET,
            summary = "List the station's calendar feeds",
            tags = {"Station Calendar Feeds"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = CalendarFeedResponse[].class)))
    private void list(Context ctx) {
        ctx.json(feeds.list(StationSession.from(ctx).stationId()).stream()
                .map(CalendarFeedResponse::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/calendar-feeds",
            methods = HttpMethod.POST,
            summary = "Create a station calendar feed",
            tags = {"Station Calendar Feeds"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CalendarFeedRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = CalendarFeedResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        var session = StationSession.from(ctx);
        var req = ctx.bodyAsClass(CalendarFeedRequest.class);
        var feed = feeds.create(
                session.stationId(),
                req.name(),
                req.level(),
                req.hiddenCategories(),
                session.member().id());
        ctx.status(HttpStatus.CREATED).json(CalendarFeedResponse.of(feed));
    }

    @OpenApi(
            path = "/api/v1/calendar-feeds/{id}",
            methods = HttpMethod.PUT,
            summary = "Change a station calendar feed",
            description = "Changes its name, level and the categories it leaves out. The address stays.",
            tags = {"Station Calendar Feeds"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CalendarFeedRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CalendarFeedResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        var req = ctx.bodyAsClass(CalendarFeedRequest.class);
        var feed = feeds.update(
                pathInt(ctx, "id"),
                StationSession.from(ctx).stationId(),
                req.name(),
                req.level(),
                req.hiddenCategories());
        ctx.json(CalendarFeedResponse.of(feed));
    }

    @OpenApi(
            path = "/api/v1/calendar-feeds/{id}/regenerate",
            methods = HttpMethod.POST,
            summary = "Give a station calendar feed a new address",
            description = "Calendars holding the old address stop receiving the feed.",
            tags = {"Station Calendar Feeds"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CalendarFeedResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void regenerate(Context ctx) {
        var feed = feeds.regenerate(pathInt(ctx, "id"), StationSession.from(ctx).stationId());
        ctx.json(CalendarFeedResponse.of(feed));
    }

    @OpenApi(
            path = "/api/v1/calendar-feeds/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a station calendar feed",
            tags = {"Station Calendar Feeds"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        feeds.delete(pathInt(ctx, "id"), StationSession.from(ctx).stationId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * A station calendar feed as it is created or changed.
     *
     * @param name             the feed's name
     * @param level            up to which visibility it shows appointments
     * @param hiddenCategories the categories it leaves out, none where absent
     */
    public record CalendarFeedRequest(
            @Nullable String name,
            @Nullable StationFeedLevel level,
            @Nullable List<Integer> hiddenCategories) {
        @Override
        public List<Integer> hiddenCategories() {
            return hiddenCategories == null ? List.of() : hiddenCategories;
        }
    }

    /**
     * A station calendar feed as its managers see it, address token included.
     *
     * @param id               the feed
     * @param name             the feed's name
     * @param level            up to which visibility it shows appointments
     * @param token            the token of its address
     * @param hiddenCategories the categories it leaves out
     * @param createdAt        when it was created
     * @param polledAt         when a calendar last fetched it, null while nothing has
     */
    public record CalendarFeedResponse(
            int id,
            String name,
            StationFeedLevel level,
            String token,
            List<Integer> hiddenCategories,
            String createdAt,
            @Nullable String polledAt) {
        static CalendarFeedResponse of(StationCalendarFeed feed) {
            return new CalendarFeedResponse(
                    feed.id(),
                    feed.name(),
                    feed.level(),
                    feed.token(),
                    feed.hiddenCategoryIds(),
                    feed.createdAt().toString(),
                    feed.polledAt() == null ? null : feed.polledAt().toString());
        }
    }
}
