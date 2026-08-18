/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.repository.StationMailProviderRepository;
import dev.chojo.ember.feature.members.service.UserSettingsService;
import dev.chojo.ember.feature.notifications.entity.NotificationSetting;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Routes for user-specific settings including notification preferences
 * and email notification configuration.
 */
@Singleton
public class UserSettingsRoutes implements Routes {
    private final UserSettingsService settingsService;
    private final StationMailProviderRepository mailProviderRepository;

    @Inject
    public UserSettingsRoutes(
            UserSettingsService settingsService, StationMailProviderRepository mailProviderRepository) {
        this.settingsService = settingsService;
        this.mailProviderRepository = mailProviderRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/settings", this::getSettings, StationPermission.LOGIN);
        routes.put(prefix + "/settings", this::updateSettings, StationPermission.LOGIN);
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
        ctx.json(toResponse(
                userSettings.emailEnabled(),
                userSettings.theme(),
                userSettings.darkMode(),
                userSettings.feel(),
                notifSettings,
                session.stationId()));
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

        var current = settingsService.findOrCreate(memberId);
        if (request.emailEnabled() != null) {
            settingsService.updateEmailEnabled(memberId, request.emailEnabled());
        }
        if (request.theme() != null || request.darkMode() != null || request.feel() != null) {
            settingsService.updateTheme(
                    memberId,
                    request.theme() != null ? request.theme() : current.theme(),
                    request.darkMode() != null ? request.darkMode() : current.darkMode(),
                    request.feel() != null ? request.feel() : current.feel());
        }

        // Build notification settings map from request
        var notifMap = new EnumMap<NotificationType, NotificationSetting>(NotificationType.class);
        if (request.notifications() != null) {
            for (var entry : request.notifications().entrySet()) {
                var type = entry.getKey();
                var toggle = entry.getValue();
                notifMap.put(
                        type, new NotificationSetting(memberId, type, toggle.app(), toggle.email(), toggle.feed()));
            }
        }
        settingsService.updateNotificationSettings(memberId, notifMap);

        var notifSettings = settingsService.getNotificationSettings(memberId);
        var finalSettings = settingsService.findOrCreate(memberId);
        ctx.json(toResponse(
                finalSettings.emailEnabled(),
                finalSettings.theme(),
                finalSettings.darkMode(),
                finalSettings.feel(),
                notifSettings,
                session.stationId()));
    }

    private SettingsResponse toResponse(
            boolean emailEnabled,
            String theme,
            String darkMode,
            String feel,
            Map<NotificationType, NotificationSetting> notifSettings,
            int stationId) {
        var first = mailProviderRepository.findByStation(stationId).stream().findFirst();
        String mailProviderName = first.map(MailChainEntry::providerName).orElse("");
        String mailProviderUrl = first.map(MailChainEntry::providerUrl).orElse("");
        boolean mailConfigured = first.isPresent();

        // Build response map with defaults for missing types
        var responseMap = new LinkedHashMap<NotificationType, NotificationToggle>();
        for (var type : NotificationType.values()) {
            var setting = notifSettings.get(type);
            boolean app = setting == null || setting.appEnabled();
            boolean email = setting != null && setting.emailEnabled();
            boolean feed = setting == null || setting.feedEnabled();
            responseMap.put(type, new NotificationToggle(app, email, feed));
        }

        return new SettingsResponse(
                emailEnabled, theme, darkMode, feel, responseMap, mailConfigured, mailProviderName, mailProviderUrl);
    }

    public record NotificationToggle(boolean app, boolean email, boolean feed) {}

    public record SettingsRequest(
            Boolean emailEnabled,
            String theme,
            String darkMode,
            String feel,
            Map<NotificationType, NotificationToggle> notifications) {}

    public record SettingsResponse(
            boolean emailEnabled,
            String theme,
            String darkMode,
            String feel,
            Map<NotificationType, NotificationToggle> notifications,
            boolean mailConfigured,
            String mailProviderName,
            String mailProviderUrl) {}
}
