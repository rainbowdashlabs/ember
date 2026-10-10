/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.mail.entity.InstanceMailStation;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailDashboardService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.mail.service.StationMailSettingsService;
import dev.chojo.ember.feature.mail.service.StationMailSettingsService.MailReplyTo;
import dev.chojo.ember.feature.mail.service.StationMailSettingsService.WebhookUrl;
import dev.chojo.ember.feature.members.entity.UserSettings;
import dev.chojo.ember.feature.members.route.UserSettingsRoutes;
import dev.chojo.ember.feature.members.service.UserSettingsService;
import dev.chojo.ember.feature.notifications.service.NotificationPreferences;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationImportService;
import dev.chojo.ember.feature.station.service.StationLocationService;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.feature.station.service.StationNotificationTimesService;
import dev.chojo.ember.feature.station.service.StationNotificationTimesService.NotificationSchedulePayload;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.feature.station.service.StationSettingsService;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The station's own settings page, over HTTP: every handler hands the station asking to its service.
 */
class StationManageRoutesTest {
    private static final int STATION_ID = 3;
    private static final WebhookUrl WEBHOOK = new WebhookUrl("https://ember.test/hook", true);

    private StationService stations;
    private StationSettingsService settings;
    private StationMailSettingsService mail;
    private StationNotificationTimesService times;
    private MailDashboardService dashboard;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        stations = mock(StationService.class);
        settings = mock(StationSettingsService.class);
        mail = mock(StationMailSettingsService.class);
        times = mock(StationNotificationTimesService.class);
        dashboard = mock(MailDashboardService.class);
        var station = mock(Station.class);
        when(station.uid()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        when(station.name()).thenReturn("Nord");
        when(settings.update(eq(STATION_ID), any())).thenReturn(station);
        when(stations.lookAndFeelLocks(anyInt())).thenReturn(new StationService.Locks(false, false, false, false));
        when(mail.webhook(STATION_ID)).thenReturn(WEBHOOK);
        when(mail.updateSigningSecret(STATION_ID, "s")).thenReturn(WEBHOOK);
        when(mail.regenerateWebhook(STATION_ID)).thenReturn(WEBHOOK);
        when(mail.providers(STATION_ID)).thenReturn(List.of());
        when(mail.updateProviders(eq(STATION_ID), any())).thenReturn(List.of());
        when(times.times(STATION_ID)).thenReturn(new NotificationSchedulePayload(List.of("07:00"), 30));
        harness = RouteHarness.serving(new StationManageRoutes(
                stations,
                settings,
                mock(MailLocaleService.class),
                mail,
                mock(EmailService.class),
                dashboard,
                mock(AuthService.class),
                mock(StationImportService.class),
                mock(StationLocationService.class),
                mock(StationLogoService.class),
                mock(ClusterService.class),
                times));
    }

    private Response as(HttpClient client, String method, String path, Object json) {
        var manager = harness.as(TestSessions.member(
                STATION_ID,
                StationPermission.STATION_GENERAL,
                StationPermission.STATION_MAIL,
                StationPermission.STATION_ADMINISTRATOR));
        return switch (method) {
            case "GET" -> client.get(PREFIX + path, manager);
            case "PUT" -> client.put(PREFIX + path, json, manager);
            case "POST" -> client.post(PREFIX + path, json, manager);
            default -> client.delete(PREFIX + path, json, manager);
        };
    }

    @Test
    void theGeneralSettingsAreWrittenThroughTheSettingsService() {
        var answer = harness.request(client -> as(client, "PUT", "/station/manage", body("{\"name\": \"Nord\"}")));

        assertEquals("Nord", json(answer).path("name").asString());
        verify(settings).update(eq(STATION_ID), any());
    }

    @Test
    void theWebhookAndItsSigningSecretBelongToTheStationAsking() {
        harness.run((server, client) -> {
            assertTrue(json(as(client, "GET", "/station/manage/mail/webhook", null))
                    .path("signingSecretSet")
                    .asBoolean());
            assertEquals(
                    200,
                    as(client, "PUT", "/station/manage/mail/signing-secret", body("{\"secret\": \"s\"}"))
                            .code());
            assertEquals(
                    200,
                    as(client, "POST", "/station/manage/mail/webhook", null).code());
        });

        verify(mail).updateSigningSecret(STATION_ID, "s");
        verify(mail).regenerateWebhook(STATION_ID);
    }

    @Test
    void theProvidersAndTheNotificationTimesAreTheStationsOwn() {
        harness.run((server, client) -> {
            assertEquals(
                    200,
                    as(client, "GET", "/station/manage/mail/providers", null).code());
            assertEquals(
                    200,
                    as(client, "PUT", "/station/manage/mail/providers", body("[]"))
                            .code());
            assertEquals(204, as(client, "DELETE", "/station/manage/mail", null).code());
            assertEquals(
                    30,
                    json(as(client, "GET", "/station/manage/notifications", null))
                            .path("floorMinutes")
                            .asInt());
            assertEquals(
                    204,
                    as(
                                    client,
                                    "PUT",
                                    "/station/manage/notifications",
                                    body("{\"sendTimes\": [\"08:00\"], \"floorMinutes\": 0}"))
                            .code());
            assertEquals(
                    204,
                    as(client, "DELETE", "/station/manage/mail/blocks?provider=BREVO&domain=example.org", null)
                            .code());
        });

        verify(mail).updateProviders(STATION_ID, List.of());
        verify(mail).clear(STATION_ID);
        verify(times).update(STATION_ID, List.of("08:00"));
        verify(dashboard).liftBlock(STATION_ID, MailProviderType.BREVO, "example.org");
    }

    @Test
    void theReplyAddressBelongsToTheStationAsking() {
        when(mail.replyTo(STATION_ID)).thenReturn(new MailReplyTo("kontakt@nord.test"));
        when(mail.updateReplyTo(STATION_ID, "neu@nord.test")).thenReturn(new MailReplyTo("neu@nord.test"));

        harness.run((server, client) -> {
            assertEquals(
                    "kontakt@nord.test",
                    json(as(client, "GET", "/station/manage/mail/reply-to", null))
                            .path("replyTo")
                            .asString());
            assertEquals(
                    "neu@nord.test",
                    json(as(client, "PUT", "/station/manage/mail/reply-to", body("{\"replyTo\": \"neu@nord.test\"}")))
                            .path("replyTo")
                            .asString());
        });

        verify(mail).updateReplyTo(STATION_ID, "neu@nord.test");
    }

    @Test
    void theStationSeesWhetherTheInstanceCarriesItsMail() {
        when(mail.instanceMail(STATION_ID))
                .thenReturn(new InstanceMailStation(
                        UUID.fromString("00000000-0000-0000-0000-000000000003"), "Nord", true, null, 20, 6));

        var answer = harness.request(client -> as(client, "GET", "/station/manage/mail/instance", null));

        assertEquals(20, json(answer).path("dailyLimit").asInt());
        assertEquals(6, json(answer).path("sentToday").asInt());
    }

    @Test
    void aTestMailNeedsAProviderAndOnlyAMovedStationIsDeletedAtOnce() {
        doThrow(StationRefusal.NO_MAIL_PROVIDER_SET.raise()).when(mail).requireProvider(STATION_ID);
        doThrow(StationRefusal.STATION_NOT_MOVED.raise()).when(stations).deleteMoved(STATION_ID);

        harness.run((server, client) -> {
            assertEquals(
                    StationRefusal.NO_MAIL_PROVIDER_SET,
                    refusalOf(as(client, "POST", "/station/manage/mail/test-mail", null)));
            assertEquals(
                    StationRefusal.STATION_NOT_MOVED,
                    refusalOf(as(client, "POST", "/station/manage/delete-moved", null)));
        });
    }

    @Test
    void theMemberSettingsNameTheStationsProviders() {
        var userSettings = mock(UserSettingsService.class);
        var own = mock(UserSettings.class);
        when(userSettings.getSettings(TestSessions.MEMBER_ID)).thenReturn(own);
        var preferences = mock(NotificationPreferences.class);
        when(preferences.settingsOf(TestSessions.MEMBER_ID)).thenReturn(Map.of());
        when(mail.senders(STATION_ID)).thenReturn(List.of());
        var settingsHarness = RouteHarness.serving(new UserSettingsRoutes(userSettings, preferences, mail));

        var answer = settingsHarness.request(client -> client.get(
                PREFIX + "/settings", settingsHarness.as(TestSessions.member(STATION_ID, StationPermission.LOGIN))));

        assertEquals(false, json(answer).path("mailConfigured").asBoolean());
        assertEquals(0, json(answer).path("mailProviders").size());
        verify(mail).senders(STATION_ID);
        verify(mail).sendsMail(STATION_ID);
    }
}
