/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.v1;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.entity.UserSettings;
import dev.chojo.ember.service.UserSettingsService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class UserSettingsRoutes implements Routes {
    private final UserSettingsService settingsService;

    @Inject
    public UserSettingsRoutes(UserSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/settings", this::getSettings, Roles.LOGIN);
        routes.put(prefix + "/settings", this::updateSettings, Roles.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/settings",
            methods = HttpMethod.GET,
            summary = "Get user notification settings",
            tags = {"User Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = UserSettings.class)))
    private void getSettings(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(settingsService.getSettings(session.member().id()));
    }

    @OpenApi(
            path = "/api/v1/settings",
            methods = HttpMethod.PUT,
            summary = "Update user notification settings",
            tags = {"User Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SettingsRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = UserSettings.class)))
    private void updateSettings(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SettingsRequest.class);
        ctx.json(settingsService.updateSettings(
                session.member().id(),
                request.notifyNews(),
                request.notifyNewEvents(),
                request.notifyEventStatus()));
    }

    public record SettingsRequest(boolean notifyNews, boolean notifyNewEvents, boolean notifyEventStatus) {}
}
