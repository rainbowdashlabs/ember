/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.feed.repository.FeedTokenRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;

/**
 * What a station can see about the subscriptions its members keep.
 *
 * <p>A calendar that a phone fetches every hour and a subscription nobody has ever opened look the
 * same from the inside; the difference is in when it was last fetched, which is recorded already and
 * had nowhere to be read. Answers for the caller's own station only, and never with the token: it is
 * the whole key to one person's calendar.
 */
@Singleton
public class StationFeedUseRoutes implements Routes {

    private final FeedTokenRepository feedTokenRepository;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public StationFeedUseRoutes(FeedTokenRepository feedTokenRepository, MemberIdentityFactory memberIdentityFactory) {
        this.feedTokenRepository = feedTokenRepository;
        this.memberIdentityFactory = memberIdentityFactory;
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
            throw new BadRequestResponse("No station selected");
        }
        int stationId = session.stationId();
        ctx.json(feedTokenRepository.findUseByStation(stationId).stream()
                .map(use -> new FeedUseResponse(
                        use.memberId(),
                        memberIdentityFactory.local(stationId, use.memberId()),
                        use.createdAt(),
                        use.icalPolledAt(),
                        use.notificationPolledAt()))
                .toList());
    }

    /**
     * One member's subscription as the monitoring page reads it.
     *
     * @param memberId             the member
     * @param identity             their name and picture, resolved the way every list resolves them
     * @param createdAt            when the subscription was set up
     * @param icalPolledAt         when a calendar last fetched it, null where none ever has
     * @param notificationPolledAt when a reader last fetched the notifications, null where none has
     */
    public record FeedUseResponse(
            int memberId,
            MemberIdentity identity,
            Instant createdAt,
            Instant icalPolledAt,
            Instant notificationPolledAt) {}
}
