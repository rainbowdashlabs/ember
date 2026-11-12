/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.feature.station.entity.ReminderAnswer;
import dev.chojo.ember.feature.station.entity.ReminderResult;
import dev.chojo.ember.feature.station.entity.ReminderTrigger;
import dev.chojo.ember.feature.station.entity.StationOverview;
import dev.chojo.ember.feature.station.entity.StationState;
import dev.chojo.ember.feature.station.service.ManagerReminderService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The station list of the instance administration and the reminder sent from it, over HTTP.
 */
class AdminStationRoutesTest {
    private static final UUID UID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final Instant WAITING = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant REMINDED = Instant.parse("2026-11-08T10:00:00Z");

    @Test
    void theListNamesEachStationsStateManagerAndLastReminder() {
        var service = mock(ManagerReminderService.class);
        when(service.list()).thenReturn(List.of(station(StationState.SETTING_UP, REMINDED)));
        var harness = RouteHarness.serving(new AdminStationRoutes(service));

        var listed = json(harness.request(
                        client -> client.get(PREFIX + "/admin/stations", harness.as(TestSessions.administrator()))))
                .get(0);

        assertEquals(UID.toString(), listed.path("id").asString());
        assertEquals("SETTING_UP", listed.path("state").asString());
        assertEquals(12, listed.path("memberCount").asInt());
        assertEquals("Mara Nager", listed.path("managerName").asString());
        assertEquals("Verbund Nord", listed.path("associationName").asString());
        assertEquals("MANUAL", listed.path("lastReminder").path("trigger").asString());
    }

    @Test
    void onlyAnInstanceAdministratorSeesTheList() {
        var harness = RouteHarness.serving(new AdminStationRoutes(mock(ManagerReminderService.class)));

        var refused =
                harness.request(client -> client.get(PREFIX + "/admin/stations", harness.as(TestSessions.member(3))));

        assertEquals(403, refused.code());
    }

    @Test
    void oneStationIsAnsweredOrRefusedByName() {
        var service = mock(ManagerReminderService.class);
        when(service.find(UID)).thenReturn(Optional.of(station(StationState.WAITING_FOR_MANAGER, null)));
        var harness = RouteHarness.serving(new AdminStationRoutes(service));

        harness.run((server, client) -> {
            var admin = harness.as(TestSessions.administrator());
            var found = json(client.get(PREFIX + "/admin/stations/" + UID, admin));
            assertEquals("WAITING_FOR_MANAGER", found.path("state").asString());
            assertTrue(found.path("lastReminder").isNull());
            assertEquals(
                    StationRefusal.STATION_NOT_HERE_BY_NAME,
                    refusalOf(client.get(PREFIX + "/admin/stations/" + UUID.randomUUID(), admin)));
            assertEquals(
                    GeneralRefusal.ADDRESS_NOT_AN_IDENTIFIER,
                    refusalOf(client.get(PREFIX + "/admin/stations/not-a-uid", admin)));
        });
    }

    @Test
    void theReminderGoesOutForTheAdministratorAndAnswersTheStationAfterIt() {
        var service = mock(ManagerReminderService.class);
        when(service.find(UID)).thenReturn(Optional.of(station(StationState.WAITING_FOR_MANAGER, null)));
        when(service.remindByHand(5, TestSessions.administrator().accountId()))
                .thenReturn(
                        new ReminderAnswer(station(StationState.WAITING_FOR_MANAGER, REMINDED), ReminderResult.SENT));
        var harness = RouteHarness.serving(new AdminStationRoutes(service));

        var answered = json(harness.request(client -> client.post(
                PREFIX + "/admin/stations/" + UID + "/remind-manager",
                null,
                harness.as(TestSessions.administrator()))));

        assertEquals("SENT", answered.path("result").asString());
        assertEquals(
                REMINDED,
                Instant.parse(answered.path("station")
                        .path("lastReminder")
                        .path("sentAt")
                        .asString()));
        verify(service).remindByHand(5, TestSessions.administrator().accountId());
    }

    @Test
    void theRefusalsOfTheReminderReachTheAdministrator() {
        var service = mock(ManagerReminderService.class);
        when(service.find(UID)).thenReturn(Optional.of(station(StationState.SET_UP, null)));
        int admin = TestSessions.administrator().accountId();
        when(service.remindByHand(5, admin))
                .thenThrow(StationRefusal.STATION_ALREADY_SET_UP.raise())
                .thenThrow(StationRefusal.STATION_HAS_NO_MANAGER.raise())
                .thenThrow(StationRefusal.MANAGER_REMINDED_JUST_NOW.raise())
                .thenThrow(MemberRefusal.ACCOUNT_NOBODY_TO_WRITE_TO.raise())
                .thenThrow(StationRefusal.STATION_MOVED_AWAY.raise());
        var harness = RouteHarness.serving(new AdminStationRoutes(service));

        harness.run((server, client) -> {
            var session = harness.as(TestSessions.administrator());
            String path = PREFIX + "/admin/stations/" + UID + "/remind-manager";
            assertEquals(StationRefusal.STATION_ALREADY_SET_UP, refusalOf(client.post(path, null, session)));
            assertEquals(StationRefusal.STATION_HAS_NO_MANAGER, refusalOf(client.post(path, null, session)));
            assertEquals(StationRefusal.MANAGER_REMINDED_JUST_NOW, refusalOf(client.post(path, null, session)));
            assertEquals(MemberRefusal.ACCOUNT_NOBODY_TO_WRITE_TO, refusalOf(client.post(path, null, session)));
            assertEquals(StationRefusal.STATION_MOVED_AWAY, refusalOf(client.post(path, null, session)));
            assertEquals(
                    StationRefusal.STATION_NOT_HERE_BY_NAME,
                    refusalOf(client.post(
                            PREFIX + "/admin/stations/" + UUID.randomUUID() + "/remind-manager", null, session)));
        });
    }

    private static StationOverview station(StationState state, Instant remindedAt) {
        return new StationOverview(
                5,
                UID,
                "Nord",
                "de-DE",
                state,
                12,
                state == StationState.NO_MANAGER ? null : 9,
                "Mara",
                "Nager",
                "mara@test.example",
                WAITING,
                WAITING,
                "Verbund Nord",
                remindedAt,
                remindedAt == null ? null : ReminderTrigger.MANUAL,
                false);
    }
}
