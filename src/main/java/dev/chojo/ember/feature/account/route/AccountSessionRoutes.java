/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.AccountSession;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for managing the active login sessions of the current account: listing them,
 * invalidating a single one, and invalidating all at once.
 */
@Singleton
public class AccountSessionRoutes implements Routes {
    private final AuthService authService;
    private final AccountRepository accountRepository;
    private final TokenHasher tokenHasher;

    @Inject
    public AccountSessionRoutes(AuthService authService, AccountRepository accountRepository, TokenHasher tokenHasher) {
        this.authService = authService;
        this.accountRepository = accountRepository;
        this.tokenHasher = tokenHasher;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/session/active", this::getActiveSessions, StationPermission.LOGIN);
        routes.delete(prefix + "/session/active/{id}", this::invalidateSession, StationPermission.LOGIN);
        routes.post(
                prefix + "/session/invalidate-all",
                this::invalidateAll,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
    }

    @OpenApi(
            path = "/api/v1/session/active",
            methods = HttpMethod.GET,
            summary = "List active sessions for the current account",
            description = "Returns all active sessions with user agent and last-used timestamp.",
            tags = {"Session"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ActiveSession[].class)))
    private void getActiveSessions(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String authHeader = ctx.header("Authorization");
        String currentToken = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : "";
        String currentHash = currentToken.isEmpty() ? "" : tokenHasher.hash(currentToken);
        List<AccountSession> sessions = authService.findSessionsByAccount(session.accountId());
        List<ActiveSession> result = sessions.stream()
                .map(s -> new ActiveSession(
                        s.id(),
                        s.userAgent(),
                        s.createdAt(),
                        s.lastUsedAt(),
                        s.expiresAt(),
                        s.tokenHash().equals(currentHash),
                        s.location()))
                .toList();
        ctx.json(result);
    }

    @OpenApi(
            path = "/api/v1/session/active/{id}",
            methods = HttpMethod.DELETE,
            summary = "Invalidate a specific session",
            tags = {"Session"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void invalidateSession(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        accountRepository.deleteSessionById(id, session.accountId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/session/invalidate-all",
            methods = HttpMethod.POST,
            summary = "Invalidate all sessions for the current account",
            description = "Deletes all sessions including the current one. The user will need to log in again.",
            tags = {"Session"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void invalidateAll(Context ctx) {
        UserSession session = UserSession.from(ctx);
        authService.invalidateAllSessions(session.accountId());
        ctx.json(new MessageResponse("All sessions invalidated"));
    }

    /**
     * Represents an active session as returned to the user.
     *
     * @param id         the session identifier
     * @param userAgent  the client's user agent string
     * @param createdAt  when the session was created
     * @param lastUsedAt when the session was last used
     * @param expiresAt  when the session expires
     * @param isCurrent  whether this is the session making the current request
     * @param location   the client's location
     */
    public record ActiveSession(
            int id,
            String userAgent,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt,
            boolean isCurrent,
            String location) {}
}
