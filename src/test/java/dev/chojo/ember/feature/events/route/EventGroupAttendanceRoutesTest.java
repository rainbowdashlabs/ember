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
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.service.EventGroupAttendanceService;
import dev.chojo.ember.feature.events.service.EventGroupAttendanceService.AttendanceRow;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetResult;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventGroupAttendanceRoutesTest {
    private static final int STATION_ID = 3;
    private static final String EVENT_GROUP = PREFIX + "/events/groups/70";

    private RouteHarness harness;

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
        when(eventGroups.requireEventGroup(STATION_ID, 70))
                .thenReturn(new EventGroup(
                        70,
                        STATION_ID,
                        "Jugendflamme 1",
                        null,
                        null,
                        null,
                        false,
                        RestrictionMode.AND,
                        RestrictionMode.AND,
                        false,
                        null,
                        null));
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
        harness = RouteHarness.serving(
                new EventGroupAttendanceRoutes(attendance, eventGroups, identities, names, stations));
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
}
