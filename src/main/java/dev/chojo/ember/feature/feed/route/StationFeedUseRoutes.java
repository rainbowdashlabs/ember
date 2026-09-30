/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.feed.service.FeedUseService;
import dev.chojo.ember.feature.feed.service.FeedUseService.FeedUseResponse;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * What a station can see about the subscriptions its members keep, for the caller's own station
 * only.
 */
@Singleton
public class StationFeedUseRoutes implements Routes {

    private final FeedUseService feedUse;

    @Inject
    public StationFeedUseRoutes(FeedUseService feedUse) {
        this.feedUse = feedUse;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/station/monitoring/feeds", this::list, StationPermission.STATION_ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/station/monitoring/feeds",
            methods = HttpMethod.GET,
            summary = "List the calendar and notification subscriptions of the station's members",
            description =
                    "One row per member who has set a subscription up, with when they did and when each feed was last fetched. Never carries the token itself.",
            tags = {"Monitoring"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FeedUseResponse[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.stationId() == null) {
            throw Refusal.NO_STATION_CHOSEN_FOR_FEED_USE.raise();
        }
        ctx.json(feedUse.forStation(session.stationId()));
    }
}
