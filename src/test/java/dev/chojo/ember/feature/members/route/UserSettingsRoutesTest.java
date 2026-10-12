/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.entity.SmtpEncryption;
import dev.chojo.ember.feature.mail.service.StationMailSettingsService;
import dev.chojo.ember.feature.members.entity.UserSettings;
import dev.chojo.ember.feature.members.service.UserSettingsService;
import dev.chojo.ember.feature.notifications.service.NotificationPreferences;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
        when(mail.senders(STATION_ID)).thenReturn(List.of(own(), instanceProvider()));
        harness = RouteHarness.serving(new UserSettingsRoutes(settings, preferences, mail));
    }

    /**
     * A member is told every provider their mail may go through, in the order they are tried, the
     * instance's included, each with the name and privacy notice its owner gave it.
     */
    @Test
    void everyProviderAMailMayGoThroughIsListed() {
        var answer = harness.request(client ->
                client.get(PREFIX + "/settings", harness.as(TestSessions.member(STATION_ID, StationPermission.LOGIN))));

        var providers = RouteHarness.json(answer).get("mailProviders");
        assertEquals(2, providers.size());
        assertEquals("Wache Mail", providers.get(0).get("name").asText());
        assertEquals(
                "https://wache.example/privacy", providers.get(0).get("url").asText());
        assertEquals("BREVO", providers.get(1).get("type").asText());
        assertEquals("", providers.get(1).get("name").asText(), "no name given, so the page shows the type");
    }

    private static MailChainEntry own() {
        return new MailChainEntry(
                0,
                MailProviderType.SMTP,
                "relay.wache.example",
                587,
                SmtpEncryption.STARTTLS,
                "",
                "",
                "",
                "post@wache.example",
                "Wache",
                2,
                0,
                "Wache Mail",
                "https://wache.example/privacy");
    }

    private static MailChainEntry instanceProvider() {
        return new MailChainEntry(
                        0,
                        MailProviderType.BREVO,
                        "",
                        587,
                        SmtpEncryption.STARTTLS,
                        "",
                        "",
                        "key",
                        "post@instance.example",
                        "Ember",
                        2,
                        0,
                        "",
                        "")
                .asInstanceProvider(0);
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
