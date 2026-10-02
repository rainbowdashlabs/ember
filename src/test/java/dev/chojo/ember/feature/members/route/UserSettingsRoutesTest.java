/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.mail.service.StationMailSettingsService;
import dev.chojo.ember.feature.members.entity.UserSettings;
import dev.chojo.ember.feature.members.service.UserSettingsService;
import dev.chojo.ember.feature.notifications.service.NotificationPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A member's own settings page, over HTTP. */
class UserSettingsRoutesTest {
    private static final int STATION_ID = 3;

    private RouteHarness harness;

    @BeforeEach
    void setup() {
        var settings = mock(UserSettingsService.class);
        var own = new UserSettings(TestSessions.MEMBER_ID, false, "ember", "system", "DEFAULT");
        when(settings.getSettings(anyInt())).thenReturn(own);
        when(settings.findOrCreate(anyInt())).thenReturn(own);
        var preferences = mock(NotificationPreferences.class);
        when(preferences.settingsOf(anyInt())).thenReturn(Map.of());
        var mail = mock(StationMailSettingsService.class);
        when(mail.firstEntry(STATION_ID)).thenReturn(Optional.empty());
        harness = RouteHarness.serving(new UserSettingsRoutes(settings, preferences, mail));
    }

    /**
     * The notification settings page sends back every type it shows. A type the server no longer
     * knows, such as the exchange notices that became movement notices, cannot be read, so a page
     * still offering one could not save anything once that switch had been touched.
     */
    @Test
    void aTypeTheServerNoLongerKnowsCannotBeSaved() {
        var answer = harness.request(client -> client.put(
                PREFIX + "/settings", body("""
                        {"emailEnabled": false, "notifications": {
                            "NEW_NEWS": {"app": true, "email": false, "feed": true},
                            "EXCHANGE_STATUS_CHANGE": {"app": false, "email": false, "feed": true}}}"""), harness.as(TestSessions.member(STATION_ID, StationPermission.LOGIN))));

        assertEquals(400, answer.code());
    }

    @Test
    void theTypesTheServerKnowsAreSaved() {
        var answer = harness.request(client -> client.put(
                PREFIX + "/settings", body("""
                        {"emailEnabled": false, "notifications": {
                            "NEW_NEWS": {"app": true, "email": false, "feed": true}}}"""), harness.as(TestSessions.member(STATION_ID, StationPermission.LOGIN))));

        assertEquals(200, answer.code());
    }
}
