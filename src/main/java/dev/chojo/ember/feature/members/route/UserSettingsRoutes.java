/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.notifications.entity.NotificationSetting;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.station.repository.StationMailConfigRepository;
import dev.chojo.ember.feature.members.service.UserSettingsService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.EnumMap;
import java.util.Map;

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
        int memberId = session.member().id();
        var userSettings = settingsService.getSettings(memberId);
        var notifSettings = settingsService.getNotificationSettings(memberId);
        ctx.json(toResponse(userSettings.emailEnabled(), notifSettings, session.stationId()));
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
        int memberId = session.member().id();
        var request = ctx.bodyAsClass(SettingsRequest.class);

        var updated = settingsService.updateEmailEnabled(memberId, request.emailEnabled());

        // Build notification settings map from request
        var notifMap = new EnumMap<NotificationType, NotificationSetting>(NotificationType.class);
        if (request.notifications() != null) {
            for (var entry : request.notifications().entrySet()) {
                var type = NotificationType.valueOf(entry.getKey());
                var toggle = entry.getValue();
                notifMap.put(type, new NotificationSetting(memberId, type, toggle.app(), toggle.email()));
            }
        }
        settingsService.updateNotificationSettings(memberId, notifMap);

        var notifSettings = settingsService.getNotificationSettings(memberId);
        ctx.json(toResponse(updated.emailEnabled(), notifSettings, session.stationId()));
    }

    private SettingsResponse toResponse(
            boolean emailEnabled, Map<NotificationType, NotificationSetting> notifSettings, int stationId) {
        var mailConfig = mailConfigRepository.findByStation(stationId);
        String mailProviderName = "";
        String mailProviderUrl = "";
        boolean mailConfigured = false;
        if (mailConfig.isPresent() && mailConfig.get().isConfigured()) {
            var mc = mailConfig.get();
            mailProviderName = mc.providerName();
            mailProviderUrl = mc.providerUrl();
            mailConfigured = true;
        }

        // Build response map with defaults for missing types
        var responseMap = new java.util.LinkedHashMap<String, NotificationToggle>();
        for (var type : NotificationType.values()) {
            var setting = notifSettings.get(type);
            boolean app = setting != null ? setting.appEnabled() : true;
            boolean email = setting != null ? setting.emailEnabled() : false;
            responseMap.put(type.name(), new NotificationToggle(app, email));
        }

        return new SettingsResponse(emailEnabled, responseMap, mailConfigured, mailProviderName, mailProviderUrl);
    }

    public record NotificationToggle(boolean app, boolean email) {}

    public record SettingsRequest(boolean emailEnabled, Map<String, NotificationToggle> notifications) {}

    public record SettingsResponse(
            boolean emailEnabled,
            Map<String, NotificationToggle> notifications,
            boolean mailConfigured,
            String mailProviderName,
            String mailProviderUrl) {}
}
