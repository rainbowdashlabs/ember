/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.BodyRefusal;
import dev.chojo.ember.feature.account.entity.SidebarMode;
import dev.chojo.ember.feature.account.service.AccountPreferenceService;
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

/**
 * How the signed-in account likes the app laid out. Everybody signed in sets their own; the choice follows
 * the account to every device and reaches the app with the session.
 */
@Singleton
public class AccountPreferenceRoutes implements Routes {
    private final AccountPreferenceService preferences;

    @Inject
    public AccountPreferenceRoutes(AccountPreferenceService preferences) {
        this.preferences = preferences;
    }

    /**
     * How the sidebar lists its sections.
     *
     * @param sidebar one section at a time, or every section at once
     */
    public record SidebarPreference(@Nullable SidebarMode sidebar) {}

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.put(prefix + "/session/preferences/sidebar", this::setSidebar, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/session/preferences/sidebar",
            methods = HttpMethod.PUT,
            summary = "Choose how the sidebar of the own account lists its sections",
            tags = {"Session"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SidebarPreference.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setSidebar(Context ctx) {
        var mode = ctx.bodyAsClass(SidebarPreference.class).sidebar();
        if (mode == null) throw BodyRefusal.BODY_DOES_NOT_MATCH.raise();
        preferences.setSidebar(UserSession.from(ctx).accountId(), mode);
        ctx.status(HttpStatus.NO_CONTENT);
    }
}
