/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.feed.entity.FeedToken;
import dev.chojo.ember.feature.feed.service.FeedTokenService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;

@Singleton
public class FeedTokenRoutes implements Routes {
    private final FeedTokenService tokenService;

    @Inject
    public FeedTokenRoutes(FeedTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/feed/token", this::getToken, StationPermission.USER);
        routes.get(prefix + "/feed/token/status", this::getStatus, StationPermission.USER);
        routes.post(prefix + "/feed/token", this::createToken, StationPermission.USER);
        routes.post(prefix + "/feed/token/regenerate", this::regenerateToken, StationPermission.USER);
        routes.delete(prefix + "/feed/token", this::revokeToken, StationPermission.USER);
    }

    @OpenApi(
            path = "/api/v1/feed/token",
            methods = HttpMethod.GET,
            summary = "Get the current user's feed token",
            tags = {"Feed Tokens"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TokenResponse.class)),
                @OpenApiResponse(status = "404")
            })
    private void getToken(Context ctx) {
        UserSession session = UserSession.from(ctx);
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
        UserSession session = UserSession.from(ctx);
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
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = TokenResponse.class)))
    private void createToken(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var token = tokenService.getOrCreate(session.member().id());
        ctx.status(HttpStatus.CREATED).json(toResponse(token));
    }

    @OpenApi(
            path = "/api/v1/feed/token/regenerate",
            methods = HttpMethod.POST,
            summary = "Regenerate the feed token for the current user",
            tags = {"Feed Tokens"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TokenResponse.class)))
    private void regenerateToken(Context ctx) {
        UserSession session = UserSession.from(ctx);
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
        UserSession session = UserSession.from(ctx);
        tokenService.revoke(session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private TokenResponse toResponse(FeedToken t) {
        return new TokenResponse(
                t.token(),
                t.createdAt().toString(),
                t.icalPolledAt() != null ? t.icalPolledAt().toString() : null,
                t.notificationPolledAt() != null ? t.notificationPolledAt().toString() : null);
    }

    @OpenApiName("FeedTokenResponse")
    public record TokenResponse(String token, String createdAt, String icalPolledAt, String notificationPolledAt) {}

    public record FeedStatusResponse(boolean hasToken, boolean icalActive, boolean notificationActive) {}
}
