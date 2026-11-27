/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.feed.entity.FeedToken;
import dev.chojo.ember.feature.feed.service.FeedTokenService;
import dev.chojo.ember.feature.feed.service.PersonalFeedFilterService;
import dev.chojo.ember.feature.station.entity.StationModule;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Singleton
public class FeedTokenRoutes implements Routes {
    private final FeedTokenService tokenService;
    private final PersonalFeedFilterService filters;

    @Inject
    public FeedTokenRoutes(FeedTokenService tokenService, PersonalFeedFilterService filters) {
        this.tokenService = tokenService;
        this.filters = filters;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/feed/token", this::getToken, StationPermission.USER);
        routes.get(prefix + "/feed/token/status", this::getStatus, StationPermission.USER);
        routes.post(prefix + "/feed/token", this::createToken, StationPermission.USER);
        routes.post(prefix + "/feed/token/regenerate", this::regenerateToken, StationPermission.USER);
        routes.delete(prefix + "/feed/token", this::revokeToken, StationPermission.USER);
        routes.get(
                prefix + "/feed/categories", this::getHiddenCategories, StationPermission.USER, StationModule.EVENTS);
        routes.put(
                prefix + "/feed/categories", this::setHiddenCategories, StationPermission.USER, StationModule.EVENTS);
    }

    @OpenApi(
            path = "/api/v1/feed/categories",
            methods = HttpMethod.GET,
            summary = "Get the categories the current user's calendar feed leaves out",
            tags = {"Feed Tokens"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = HiddenCategories.class)))
    private void getHiddenCategories(Context ctx) {
        ctx.json(new HiddenCategories(
                filters.hiddenCategories(StationSession.from(ctx).member().id())));
    }

    @OpenApi(
            path = "/api/v1/feed/categories",
            methods = HttpMethod.PUT,
            summary = "Set the categories the current user's calendar feed leaves out",
            description =
                    "Replaces the whole choice. Appointments the member or a member in their care signed up for stay in the feed whatever their category. The address stays.",
            tags = {"Feed Tokens"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = HiddenCategories.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = HiddenCategories.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setHiddenCategories(Context ctx) {
        var session = StationSession.from(ctx);
        var req = ctx.bodyAsClass(HiddenCategories.class);
        ctx.json(new HiddenCategories(
                filters.setHiddenCategories(session.member().id(), session.stationId(), req.hiddenCategories())));
    }

    @OpenApi(
            path = "/api/v1/feed/token",
            methods = HttpMethod.GET,
            summary = "Get the current user's feed token",
            tags = {"Feed Tokens"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FeedTokenResponse.class)),
                @OpenApiResponse(status = "404")
            })
    private void getToken(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var token = tokenService.findByMember(session.member().id());
        token.ifPresentOrElse(t -> ctx.json(toResponse(t)), () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    @OpenApi(
            path = "/api/v1/feed/token/status",
            methods = HttpMethod.GET,
            summary = "Get feed polling status for the current user",
            tags = {"Feed Tokens"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FeedStatusResponse.class)))
    private void getStatus(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var token = tokenService.findByMember(session.member().id());
        if (token.isEmpty()) {
            ctx.json(new FeedStatusResponse(false, false, false));
            return;
        }
        var t = token.get();
        var cutoff = Instant.now().minus(Duration.ofHours(24));
        boolean icalActive = t.icalPolledAt() != null && t.icalPolledAt().isAfter(cutoff);
        boolean notificationActive =
                t.notificationPolledAt() != null && t.notificationPolledAt().isAfter(cutoff);
        ctx.json(new FeedStatusResponse(true, icalActive, notificationActive));
    }

    @OpenApi(
            path = "/api/v1/feed/token",
            methods = HttpMethod.POST,
            summary = "Create a feed token for the current user",
            tags = {"Feed Tokens"},
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = FeedTokenResponse.class)))
    private void createToken(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var token = tokenService.getOrCreate(session.member().id());
        ctx.status(HttpStatus.CREATED).json(toResponse(token));
    }

    @OpenApi(
            path = "/api/v1/feed/token/regenerate",
            methods = HttpMethod.POST,
            summary = "Regenerate the feed token for the current user",
            tags = {"Feed Tokens"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FeedTokenResponse.class)))
    private void regenerateToken(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var token = tokenService.regenerate(session.member().id());
        ctx.json(toResponse(token));
    }

    @OpenApi(
            path = "/api/v1/feed/token",
            methods = HttpMethod.DELETE,
            summary = "Revoke the feed token for the current user",
            tags = {"Feed Tokens"},
            responses = @OpenApiResponse(status = "204"))
    private void revokeToken(Context ctx) {
        StationSession session = StationSession.from(ctx);
        tokenService.revoke(session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private FeedTokenResponse toResponse(FeedToken t) {
        return new FeedTokenResponse(
                t.token(),
                t.createdAt().toString(),
                t.icalPolledAt() != null ? t.icalPolledAt().toString() : null,
                t.notificationPolledAt() != null ? t.notificationPolledAt().toString() : null);
    }

    public record FeedTokenResponse(
            String token,
            String createdAt,
            @Nullable String icalPolledAt,
            @Nullable String notificationPolledAt) {}

    /**
     * The categories a member's personal calendar feed leaves out.
     *
     * @param hiddenCategories the category ids, none where absent
     */
    public record HiddenCategories(@Nullable List<Integer> hiddenCategories) {
        @Override
        public List<Integer> hiddenCategories() {
            return hiddenCategories == null ? List.of() : hiddenCategories;
        }
    }

    public record FeedStatusResponse(boolean hasToken, boolean icalActive, boolean notificationActive) {}
}
