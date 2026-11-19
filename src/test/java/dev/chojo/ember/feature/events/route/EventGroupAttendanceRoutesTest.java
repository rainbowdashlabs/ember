/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.service.EventGroupAttendanceService;
import dev.chojo.ember.feature.events.service.EventGroupAttendanceService.AttendanceRow;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetResult;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.MemberTableRenderer;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.repository.EventGroupFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventGroupAttendanceRoutesTest {
    private static final int STATION_ID = 3;
    private static final String EVENT_GROUP = PREFIX + "/events/groups/70";

    private static final byte[] PDF = {'%', 'P', 'D', 'F'};

    private RouteHarness harness;
    private MemberTableRenderer renderer;

    @BeforeEach
    void serve() {
        var attendance = mock(EventGroupAttendanceService.class);
        when(attendance.summary(STATION_ID, 70))
                .thenReturn(List.of(new AttendanceRow(
                        12,
                        List.of(101),
                        List.of(new SetResult(1, "Übung", 2, 1), new SetResult(2, "Prüfung", 1, 0)),
                        false)));
        var eventGroups = mock(EventGroupService.class);
        when(eventGroups.requireEventGroup(STATION_ID, 71)).thenThrow(EventRefusal.EVENT_GROUP_NOT_HERE.raise());
        when(eventGroups.requireEventGroup(STATION_ID, 70))
                .thenReturn(EventGroupFixtures.eventGroup(70, STATION_ID, "Jugendflamme 1"));
        when(eventGroups.findSets(70))
                .thenReturn(
                        List.of(new EventGroupSet(1, 70, "Übung", 2, 0), new EventGroupSet(2, 70, "Prüfung", 1, 1)));
        var identities = mock(MemberIdentityFactory.class);
        when(identities.local(anyInt(), anyInt())).thenReturn(new MemberIdentity(UUID.randomUUID(), UUID.randomUUID()));
        var names = mock(MemberNameResolver.class);
        when(names.called(12)).thenReturn("Anna Muster");
        var stations = mock(StationService.class);
        var station = mock(Station.class);
        when(station.locale()).thenReturn("de-DE");
        when(stations.findById(STATION_ID)).thenReturn(Optional.of(station));
        renderer = spy(new MemberTableRenderer(mock(StationLogoService.class), mock(Api.class)));
        harness = RouteHarness.serving(
                new EventGroupAttendanceRoutes(attendance, eventGroups, identities, names, stations, renderer));
    }

    @Test
    void onlyWhoeverHandlesRegistrationsReadsTheAttendance() {
        var member = harness.as(TestSessions.member(STATION_ID, StationPermission.USER));
        assertEquals(
                403,
                harness.request(client -> client.get(EVENT_GROUP + "/attendance", member))
                        .code());

        var handler = harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_REGISTRATION));
        assertEquals(
                200,
                harness.request(client -> client.get(EVENT_GROUP + "/attendance", handler))
                        .code());
    }

    @Test
    void theSpreadsheetHasOneColumnPerSet() {
        var handler = harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_REGISTRATION));
        String csv = harness.request(client -> client.get(EVENT_GROUP + "/attendance/export.csv", handler))
                .body()
                .string();

        var lines = csv.strip().split("\n");
        assertEquals("Mitglied;Übung (2);Prüfung (1);Erfüllt", lines[0]);
        assertTrue(lines[1].startsWith("Anna Muster;1/2;0/1;Nein"));
    }

    @Test
    void theSheetToPrintHoldsTheSameTableAsTheSpreadsheet() throws Exception {
        doReturn(PDF).when(renderer).toPdf(any(), any(), anyString(), anyString(), any());
        var handler = harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_REGISTRATION));

        var response = harness.request(client -> client.get(EVENT_GROUP + "/attendance/export.pdf", handler));

        assertEquals(200, response.code());
        assertTrue(header(response, "Content-Type").startsWith("application/pdf"));
        assertEquals("%PDF", response.body().string());
        var table = ArgumentCaptor.forClass(MemberTable.class);
        verify(renderer)
                .toPdf(table.capture(), any(), eq("Jugendflamme 1"), eq("Anwesenheit in der Terminreihe"), any());
        assertEquals(
                List.of("Mitglied", "Übung (2)", "Prüfung (1)", "Erfüllt"), renderer.headerLabels(table.getValue()));
        assertEquals(
                List.of("Anna Muster", "1/2", "0/1", "Nein"),
                table.getValue().rows().get(0).values());
    }

    @Test
    void onlyWhoeverHandlesRegistrationsPrintsTheAttendance() {
        var member = harness.as(TestSessions.member(STATION_ID, StationPermission.USER));
        assertEquals(
                403,
                harness.request(client -> client.get(EVENT_GROUP + "/attendance/export.pdf", member))
                        .code());
    }

    @Test
    void anEventGroupOfAnotherStationIsNotPrinted() {
        var handler = harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_REGISTRATION));

        assertEquals(
                EventRefusal.EVENT_GROUP_NOT_HERE,
                refusalOf(harness.request(
                        client -> client.get(PREFIX + "/events/groups/71/attendance/export.pdf", handler))));
    }

    @Test
    void aSheetTheRendererRefusesIsNamed() throws Exception {
        doThrow(new IllegalStateException()).when(renderer).toPdf(any(), any(), anyString(), anyString(), any());
        var handler = harness.as(TestSessions.member(STATION_ID, StationPermission.EVENT_REGISTRATION));

        assertEquals(
                EventRefusal.REGISTRATION_SHEET_NOT_DRAWN,
                refusalOf(harness.request(client -> client.get(EVENT_GROUP + "/attendance/export.pdf", handler))));
    }
}
