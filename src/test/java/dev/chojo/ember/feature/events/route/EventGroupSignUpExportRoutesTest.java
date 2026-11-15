/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.events.service.EventGroupSignUpTable;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.service.MemberTableRenderer;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.util.CsvWriter;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sign-ups of an event group as a file over HTTP: only whoever handles registrations takes it
 * away, as a spreadsheet in the separator asked for or as a sheet to print, and only an appointment
 * editor's file carries the questions kept for organisers.
 */
class EventGroupSignUpExportRoutesTest {
    private static final int STATION_ID = 3;
    private static final String EXPORT = PREFIX + "/events/groups/70/sign-ups/export";

    private EventGroupSignUpTable signUpTable;
    private MemberTableRenderer renderer;
    private RouteHarness harness;

    @BeforeEach
    void serve() throws Exception {
        var eventGroups = mock(EventGroupService.class);
        when(eventGroups.requireEventGroup(STATION_ID, 70))
                .thenReturn(new EventGroup(
                        70,
                        STATION_ID,
                        new EventGroupDetails("Jugendflamme 1", null, null, null, false, null, null, null),
                        RestrictionMode.AND,
                        RestrictionMode.AND,
                        false,
                        null,
                        null));
        when(eventGroups.requireEventGroup(STATION_ID, 71)).thenThrow(EventRefusal.EVENT_GROUP_NOT_HERE.raise());
        var stations = mock(StationService.class);
        var station = mock(Station.class);
        when(stations.findById(STATION_ID)).thenReturn(Optional.of(station));
        signUpTable = mock(EventGroupSignUpTable.class);
        when(signUpTable.table(eq(station), eq(70), any(), anyBoolean()))
                .thenReturn(new MemberTable(List.of(), List.of()));
        renderer = mock(MemberTableRenderer.class);
        when(renderer.toCsv(any(), any(), any())).thenReturn("Name;Status");
        when(renderer.toPdf(any(), any(), anyString(), anyString(), any())).thenReturn(new byte[] {1, 2});
        harness = RouteHarness.serving(new EventGroupSignUpExportRoutes(eventGroups, signUpTable, renderer, stations));
    }

    private Response get(String path, StationPermission... permissions) {
        return harness.request(client -> client.get(path, harness.as(TestSessions.member(STATION_ID, permissions))));
    }

    @Test
    void whoeverHandlesRegistrationsTakesTheSpreadsheet() throws Exception {
        var response = get(EXPORT + ".csv?separator=comma", StationPermission.EVENT_REGISTRATION);

        assertEquals(200, response.code());
        assertTrue(header(response, "Content-Type").startsWith("text/csv"));
        assertTrue(header(response, "Content-Disposition").contains("Jugendflamme"));
        assertEquals("Name;Status", response.body().string());
        verify(renderer).toCsv(any(), any(), eq(CsvWriter.Separator.COMMA));
        verify(signUpTable).table(any(), eq(70), any(), eq(false));
    }

    @Test
    void whoeverHandlesRegistrationsTakesTheSheetToPrint() {
        var response = get(EXPORT + ".pdf", StationPermission.EVENT_REGISTRATION, StationPermission.EVENT_EDIT);

        assertEquals(200, response.code());
        assertTrue(header(response, "Content-Type").startsWith("application/pdf"));
        verify(signUpTable).table(any(), eq(70), any(), eq(true));
    }

    @Test
    void aSheetThatCannotBeDrawnIsNamed() throws Exception {
        when(renderer.toPdf(any(), any(), anyString(), anyString(), any())).thenThrow(new IllegalStateException());

        assertEquals(
                EventRefusal.REGISTRATION_SHEET_NOT_DRAWN,
                refusalOf(get(EXPORT + ".pdf", StationPermission.EVENT_REGISTRATION)));
    }

    @Test
    void nobodyElseTakesTheFileAndAnotherStationsEventGroupStaysAbsent() {
        assertEquals(403, get(EXPORT + ".csv").code());
        assertEquals(403, get(EXPORT + ".pdf", StationPermission.EVENT_EDIT).code());
        assertEquals(
                EventRefusal.EVENT_GROUP_NOT_HERE,
                refusalOf(get(PREFIX + "/events/groups/71/sign-ups/export.csv", StationPermission.EVENT_REGISTRATION)));
        verify(signUpTable, never()).table(any(), anyInt(), any(), anyBoolean());
    }
}
