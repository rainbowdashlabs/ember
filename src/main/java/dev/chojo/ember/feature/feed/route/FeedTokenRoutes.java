/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.feed.service.FeedTokenService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class FeedTokenRoutes implements Routes {
    private final FeedTokenService tokenService;

    @Inject
    public FeedTokenRoutes(FeedTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/feed/token", this::getToken, Roles.LOGIN);
        routes.post(prefix + "/feed/token", this::createToken, Roles.LOGIN);
        routes.post(prefix + "/feed/token/regenerate", this::regenerateToken, Roles.LOGIN);
        routes.delete(prefix + "/feed/token", this::revokeToken, Roles.LOGIN);
    }

    private void getToken(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var token = tokenService.findByMember(session.member().id());
        token.ifPresentOrElse(
                t -> ctx.json(new TokenResponse(t.token(), t.createdAt().toString())),
                () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    private void createToken(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var token = tokenService.getOrCreate(session.member().id());
        ctx.status(HttpStatus.CREATED)
                .json(new TokenResponse(token.token(), token.createdAt().toString()));
    }

    private void regenerateToken(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var token = tokenService.regenerate(session.member().id());
        ctx.json(new TokenResponse(token.token(), token.createdAt().toString()));
    }

    private void revokeToken(Context ctx) {
        UserSession session = UserSession.from(ctx);
        tokenService.revoke(session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record TokenResponse(String token, String createdAt) {}
}
