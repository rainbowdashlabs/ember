/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.service.StationMailSettingsService;
import dev.chojo.ember.feature.members.service.UserSettingsService;
import dev.chojo.ember.feature.notifications.entity.NotificationSetting;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationPreferences;
import dev.chojo.ember.feature.station.entity.MailProviderType;
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
import java.util.List;
import java.util.Map;

/**
 * Routes for user-specific settings including notification preferences
 * and email notification configuration.
 */
@Singleton
public class UserSettingsRoutes implements Routes {
    private final UserSettingsService settingsService;
    private final NotificationPreferences preferences;
    private final StationMailSettingsService mailSettings;

    @Inject
    public UserSettingsRoutes(
            UserSettingsService settingsService,
            NotificationPreferences preferences,
            StationMailSettingsService mailSettings) {
        this.settingsService = settingsService;
        this.preferences = preferences;
        this.mailSettings = mailSettings;
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
        StationSession session = StationSession.from(ctx);
        int memberId = session.member().id();
        var userSettings = settingsService.getSettings(memberId);
        var notifSettings = preferences.settingsOf(memberId);
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
        StationSession session = StationSession.from(ctx);
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

        var notifMap = new EnumMap<NotificationType, NotificationSetting>(NotificationType.class);
        if (request.notifications() != null) {
            for (var entry : request.notifications().entrySet()) {
                var type = entry.getKey();
                var toggle = entry.getValue();
                notifMap.put(
                        type, new NotificationSetting(memberId, type, toggle.app(), toggle.email(), toggle.feed()));
            }
        }
        preferences.updateSettings(memberId, notifMap);

        var notifSettings = preferences.settingsOf(memberId);
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
        var mailProviders =
                mailSettings.senders(stationId).stream().map(MailProvider::of).toList();
        boolean mailConfigured = mailSettings.sendsMail(stationId);

        var responseMap = new LinkedHashMap<NotificationType, NotificationToggle>();
        for (var type : NotificationType.values()) {
            var setting = notifSettings.get(type);
            boolean app = setting == null || setting.appEnabled();
            boolean email = setting != null && setting.emailEnabled();
            boolean feed = setting == null || setting.feedEnabled();
            responseMap.put(type, new NotificationToggle(app, email, feed));
        }

        return new SettingsResponse(emailEnabled, theme, darkMode, feel, responseMap, mailConfigured, mailProviders);
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
            List<MailProvider> mailProviders) {}

    /**
     * One provider a member's mail may go out through, as the member is told about it.
     *
     * @param type the kind of provider, shown where no name was given
     * @param name the name its owner gave it, empty for none
     * @param url  its privacy notice, empty for none
     */
    public record MailProvider(MailProviderType type, String name, String url) {
        static MailProvider of(MailChainEntry entry) {
            return new MailProvider(entry.provider(), entry.providerName(), entry.providerUrl());
        }
    }
}
