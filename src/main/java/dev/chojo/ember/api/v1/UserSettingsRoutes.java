/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.v1;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.repository.StationMailConfigRepository;
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
    private final StationMailConfigRepository mailConfigRepository;

    @Inject
    public UserSettingsRoutes(UserSettingsService settingsService, StationMailConfigRepository mailConfigRepository) {
        this.settingsService = settingsService;
        this.mailConfigRepository = mailConfigRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/settings", this::getSettings, Roles.LOGIN);
        routes.put(prefix + "/settings", this::updateSettings, Roles.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/settings",
            methods = HttpMethod.GET,
            summary = "Get user notification settings with mail provider info",
            tags = {"User Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SettingsResponse.class)))
    private void getSettings(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var settings = settingsService.getSettings(session.member().id());
        var mailConfig = mailConfigRepository.findByStation(session.stationId());

        String mailProviderName = "";
        String mailProviderUrl = "";
        boolean mailConfigured = false;
        if (mailConfig.isPresent() && mailConfig.get().isConfigured()) {
            var mc = mailConfig.get();
            mailProviderName = mc.providerName();
            mailProviderUrl = mc.providerUrl();
            mailConfigured = true;
        }

        ctx.json(new SettingsResponse(
                settings.memberId(),
                settings.emailEnabled(),
                settings.notifyNews(),
                settings.notifyNewEvents(),
                settings.notifyEventStatus(),
                mailConfigured,
                mailProviderName,
                mailProviderUrl));
    }

    @OpenApi(
            path = "/api/v1/settings",
            methods = HttpMethod.PUT,
            summary = "Update user notification settings",
            tags = {"User Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SettingsRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SettingsResponse.class)))
    private void updateSettings(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SettingsRequest.class);
        var settings = settingsService.updateSettings(
                session.member().id(),
                request.emailEnabled(),
                request.notifyNews(),
                request.notifyNewEvents(),
                request.notifyEventStatus());

        var mailConfig = mailConfigRepository.findByStation(session.stationId());
        String mailProviderName = "";
        String mailProviderUrl = "";
        boolean mailConfigured = false;
        if (mailConfig.isPresent() && mailConfig.get().isConfigured()) {
            var mc = mailConfig.get();
            mailProviderName = mc.providerName();
            mailProviderUrl = mc.providerUrl();
            mailConfigured = true;
        }

        ctx.json(new SettingsResponse(
                settings.memberId(),
                settings.emailEnabled(),
                settings.notifyNews(),
                settings.notifyNewEvents(),
                settings.notifyEventStatus(),
                mailConfigured,
                mailProviderName,
                mailProviderUrl));
    }

    public record SettingsRequest(
            boolean emailEnabled, boolean notifyNews, boolean notifyNewEvents, boolean notifyEventStatus) {}

    public record SettingsResponse(
            int memberId,
            boolean emailEnabled,
            boolean notifyNews,
            boolean notifyNewEvents,
            boolean notifyEventStatus,
            boolean mailConfigured,
            String mailProviderName,
            String mailProviderUrl) {}
}
