/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.WaitingListRefusal;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.waitinglist.entity.WaitingList;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntry;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvitation;
import dev.chojo.ember.feature.waitinglist.service.PublicWaitingListRateLimiter;
import dev.chojo.ember.feature.waitinglist.service.PublicWaitingListService;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Inviting from the station's side and the trial from the family's side, over HTTP: an invitation
 * names its appointment and the station sees where it goes first, and a family reads and ends only the
 * trials of its own household.
 */
class WaitingListInvitationRoutesTest {
    private static final String ENTRY = PREFIX + "/waiting-lists/5/entries/9";

    private WaitingListService lists;
    private GuardianPolicy guardians;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        lists = mock(WaitingListService.class);
        guardians = mock(GuardianPolicy.class);
        var events = mock(EventCrudService.class);
        var restrictions = mock(EventRestrictionService.class);

        var list = mock(WaitingList.class);
        when(list.stationId()).thenReturn(3);
        when(lists.findById(5)).thenReturn(Optional.of(list));
        var entry = mock(WaitingListEntry.class);
        when(entry.listId()).thenReturn(5);
        when(lists.findEntryById(9)).thenReturn(Optional.of(entry));
        when(lists.inviteEntry(anyInt(), any())).thenReturn(entry);

        var event = mock(StationEvent.class);
        when(event.id()).thenReturn(7);
        when(event.stationId()).thenReturn(3);
        when(events.findById(7)).thenReturn(Optional.of(event));
        when(restrictions.canView(anyInt(), anyInt(), any())).thenReturn(true);
        when(guardians.household(any())).thenReturn(List.of(TestSessions.MEMBER_ID, 12));

        harness = RouteHarness.serving(new WaitingListRoutes(
                lists,
                mock(PublicWaitingListService.class),
                mock(ConsentService.class),
                mock(PublicWaitingListRateLimiter.class),
                events,
                restrictions,
                guardians));
    }

    @Test
    void theStationSeesWhereTheInvitationGoesBeforeSendingIt() {
        when(lists.invitationRecipients(9))
                .thenReturn(new WaitingListService.InvitationRecipients(true, List.of("mutter@test.com")));

        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.WAITLIST_EDIT));
            var answer = client.get(ENTRY + "/invite/recipients", editor);
            assertEquals(200, answer.code());
            assertEquals(
                    "mutter@test.com", json(answer).path("addresses").path(0).asString());
        });
    }

    @Test
    void anInvitationWithoutAnAppointmentSendsNothing() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.WAITLIST_EDIT));
            assertEquals(
                    WaitingListRefusal.INVITATION_NEEDS_AN_APPOINTMENT,
                    refusalOf(client.post(ENTRY + "/invite", body("{\"message\": \"Hallo\"}"), editor)));
        });

        verify(lists, never()).inviteEntry(anyInt(), any());
    }

    @Test
    void aNoteLongerThanAnInvitationCarriesSendsNothing() {
        String note = "x".repeat(WaitingListInvitation.MESSAGE_MAX_LENGTH + 1);

        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.WAITLIST_EDIT));
            assertEquals(
                    WaitingListRefusal.INVITATION_MESSAGE_TOO_LONG,
                    refusalOf(client.post(
                            ENTRY + "/invite",
                            body("{\"eventId\": 7, \"date\": \"2026-05-12\", \"message\": \"" + note + "\"}"),
                            editor)));
        });

        verify(lists, never()).inviteEntry(anyInt(), any());
    }

    @Test
    void theInvitationCarriesItsAppointmentAndNote() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.WAITLIST_EDIT));
            var answer = client.post(
                    ENTRY + "/invite",
                    body("{\"eventId\": 7, \"date\": \"2026-05-12\", \"message\": \"  Bis bald  \"}"),
                    editor);
            assertEquals(200, answer.code());
        });

        var sent = ArgumentCaptor.forClass(WaitingListInvitation.class);
        verify(lists).inviteEntry(eq(9), sent.capture());
        assertEquals(7, sent.getValue().eventId());
        assertEquals(LocalDate.of(2026, 5, 12), sent.getValue().date());
        assertEquals("Bis bald", sent.getValue().message());
    }

    @Test
    void aFamilyReadsTheTrialsOfItsHousehold() {
        when(lists.trialsOf(List.of(TestSessions.MEMBER_ID, 12)))
                .thenReturn(List.of(
                        new WaitingListService.TrialOverview(9, "Kind Probe", "Schnupperstunde", Instant.EPOCH, 2, 5)));

        harness.run((server, client) -> {
            var guardian = harness.as(TestSessions.member(3, StationPermission.LOGIN));
            var answer = client.get(PREFIX + "/trials", guardian);
            assertEquals(200, answer.code());
            assertEquals(2, json(answer).path(0).path("visits").asInt());
        });
    }

    @Test
    void aFamilyEndsATrialInTheStationItIsIn() {
        harness.run((server, client) -> {
            var guardian = harness.as(TestSessions.member(3, StationPermission.LOGIN));
            assertEquals(
                    204,
                    client.post(PREFIX + "/trials/9/end", body("{}"), guardian).code());
        });

        verify(lists).endTrial(eq(3), eq(9), anyList());
    }
}
