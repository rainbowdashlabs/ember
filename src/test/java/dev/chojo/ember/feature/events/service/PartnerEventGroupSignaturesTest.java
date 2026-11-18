/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventGroupPartState;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupDetail;
import dev.chojo.ember.feature.events.service.EventGroupPartnerService.PartnerStanding;
import dev.chojo.ember.feature.events.service.PartnerEventGroupSignatures.Holding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A partner's event group with documents to sign, from the member's home station: each part newly held takes
 * on its documents, each part given up lets the open ones go, as for a shared appointment, and a partner that
 * cannot be asked leaves everything as it was.
 */
class PartnerEventGroupSignaturesTest {
    private static final int STATION_ID = 3;
    private static final UUID HOLDER = UUID.randomUUID();

    private PartnerEventGroupService partnerEventGroups;
    private PartnerAppointmentSignatures signatures;
    private PartnerEventGroupSignatures service;
    private StationSession session;

    @BeforeEach
    void wire() {
        partnerEventGroups = mock(PartnerEventGroupService.class);
        signatures = mock(PartnerAppointmentSignatures.class);
        var calendar = mock(OccurrenceCalendar.class);
        when(calendar.zoneOf(STATION_ID)).thenReturn(ZoneId.of("Europe/Berlin"));
        service = new PartnerEventGroupSignatures(partnerEventGroups, signatures, calendar);
        session = StationSession.of(TestSessions.member(STATION_ID));
    }

    private static EventGroupPartState part(int eventId, String start) {
        return new EventGroupPartState(
                eventId, 1, "Tag " + eventId, Instant.parse(start), Instant.parse(start), false, false, false, null, 0);
    }

    @Test
    void readsTheHeldPartsAndTheirDaysOnThisStationsCalendar() {
        var member = session.member();
        when(partnerEventGroups.eventGroup(eq(STATION_ID), eq(HOLDER), eq(70), any()))
                .thenReturn(new RemoteEventGroupDetail(
                        null,
                        List.of(),
                        List.of(part(101, "2027-03-06T23:30:00Z"), part(102, "2027-03-08T08:00:00Z")),
                        List.of(new PartnerStanding(
                                member.uid(),
                                RegistrationStatus.ACCEPTED,
                                Instant.now(),
                                List.of(101),
                                List.of(),
                                null)),
                        null));

        var before = service.before(STATION_ID, HOLDER, 70, member);

        assertEquals(Set.of(101), before.held());
        assertEquals(LocalDate.parse("2027-03-07"), before.dates().get(101));
        assertEquals(LocalDate.parse("2027-03-08"), before.dates().get(102));
    }

    @Test
    void aPartnerThatCannotBeAskedKnowsNothing() {
        when(partnerEventGroups.eventGroup(anyInt(), any(), anyInt(), any()))
                .thenThrow(EventRefusal.EVENT_GROUP_NOT_HERE.raise());

        assertEquals(Holding.unknown(), service.before(STATION_ID, HOLDER, 70, session.member()));
    }

    @Test
    void newPartsTakeOnTheirDocumentsAndPartsGivenUpLetThemGo() {
        var uid = UUID.randomUUID();
        var before = new Holding(
                Set.of(101, 102),
                Map.of(
                        101, LocalDate.parse("2027-03-07"),
                        102, LocalDate.parse("2027-03-08"),
                        103, LocalDate.parse("2027-03-09")));

        service.after(session, HOLDER, before, Set.of(102, 103), uid);

        verify(signatures).registered(session, HOLDER, 103, LocalDate.parse("2027-03-09"), uid);
        verify(signatures).withdrawn(session, HOLDER, 101, LocalDate.parse("2027-03-07"), uid);
        verify(signatures, never()).registered(any(), any(), eq(102), any(), any());
        verify(signatures, never()).withdrawn(any(), any(), eq(102), any(), any());
    }

    @Test
    void aPartOfUnknownDateIsLeftAlone() {
        service.after(session, HOLDER, Holding.unknown(), Set.of(101), UUID.randomUUID());

        verify(signatures, never()).registered(any(), any(), anyInt(), any(), any());
    }
}
