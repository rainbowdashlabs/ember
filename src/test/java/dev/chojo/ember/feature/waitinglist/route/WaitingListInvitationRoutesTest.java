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
import dev.chojo.ember.feature.waitinglist.entity.WaitingListEntryStatus;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListInvitation;
import dev.chojo.ember.feature.waitinglist.entity.WaitingListVisitor;
import dev.chojo.ember.feature.waitinglist.service.PublicWaitingListRateLimiter;
import dev.chojo.ember.feature.waitinglist.service.PublicWaitingListService;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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
        when(lists.reofferEntry(anyInt(), any())).thenReturn(entry);
        when(lists.moveToTesting(anyInt(), any())).thenReturn(entry);

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
    void otherDatesAreCheckedLikeAnInvitationAndReplaceIt() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.WAITLIST_EDIT));
            var answer = client.post(
                    ENTRY + "/other-dates",
                    body("{\"options\": [{\"eventId\": 7, \"date\": \"2026-05-15\"}], \"message\": \" Freitag? \"}"),
                    editor);
            assertEquals(200, answer.code());
        });

        var sent = ArgumentCaptor.forClass(WaitingListInvitation.class);
        verify(lists).reofferEntry(eq(9), sent.capture());
        assertEquals(
                LocalDate.of(2026, 5, 15), sent.getValue().options().getFirst().date());
        assertEquals("Freitag?", sent.getValue().message());
        verify(lists, never()).inviteEntry(anyInt(), any());
    }

    @Test
    void theTrialBeginsWithTheStationsMessageOrWithout() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.WAITLIST_EDIT));
            assertEquals(
                    200,
                    client.post(ENTRY + "/testing", body("{\"message\": \" Bis bald \"}"), editor)
                            .code());
            assertEquals(200, client.post(ENTRY + "/testing", body(""), editor).code());
        });

        verify(lists).moveToTesting(9, "Bis bald");
        verify(lists).moveToTesting(9, "");
    }

    /** The trial message stands on the family's page while the trial runs, and only then. */
    @Test
    void theFamilyReadsTheTrialMessageOnlyDuringTheTrial() {
        var family = mock(WaitingListEntry.class);
        when(family.listId()).thenReturn(5);
        when(family.confirmedAt()).thenReturn(Instant.EPOCH);
        when(family.createdAt()).thenReturn(Instant.EPOCH);
        when(family.trialMessage()).thenReturn("Bis Dienstag");
        when(lists.findEntryByToken("token")).thenReturn(Optional.of(family));

        when(family.status()).thenReturn(WaitingListEntryStatus.TESTING);
        harness.run((server, client) -> assertEquals(
                "Bis Dienstag",
                json(client.get(PREFIX + "/public/waiting-list/entry/token"))
                        .path("trialMessage")
                        .asString()));

        when(family.status()).thenReturn(WaitingListEntryStatus.JOINED);
        harness.run((server, client) -> assertEquals(
                "",
                json(client.get(PREFIX + "/public/waiting-list/entry/token"))
                        .path("trialMessage")
                        .asString()));
    }

    @Test
    void aMessageLongerThanAllowedWithdrawsNobody() {
        String message = "x".repeat(WaitingListInvitation.MESSAGE_MAX_LENGTH + 1);

        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.WAITLIST_EDIT));
            assertEquals(
                    WaitingListRefusal.STATION_MESSAGE_TOO_LONG,
                    refusalOf(client.post(ENTRY + "/withdraw", body("{\"message\": \"" + message + "\"}"), editor)));
        });

        verify(lists, never()).withdrawByStation(anyInt(), any());
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
                            body("{\"options\": [{\"eventId\": 7, \"date\": \"2026-05-12\"}], \"message\": \"" + note
                                    + "\"}"),
                            editor)));
        });

        verify(lists, never()).inviteEntry(anyInt(), any());
    }

    @Test
    void moreDatesThanAnInvitationOffersSendNothing() {
        String option = "{\"eventId\": 7, \"date\": \"2026-05-1%d\"}";
        var options = new ArrayList<String>();
        for (int day = 0; day <= WaitingListInvitation.MAX_OPTIONS; day++) options.add(option.formatted(day));

        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.WAITLIST_EDIT));
            assertEquals(
                    WaitingListRefusal.INVITATION_TOO_MANY_DATES,
                    refusalOf(client.post(
                            ENTRY + "/invite", body("{\"options\": [" + String.join(",", options) + "]}"), editor)));
        });

        verify(lists, never()).inviteEntry(anyInt(), any());
    }

    @Test
    void theSameDateOfferedTwiceSendsNothing() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.WAITLIST_EDIT));
            assertEquals(
                    WaitingListRefusal.INVITATION_DATE_TWICE,
                    refusalOf(client.post(
                            ENTRY + "/invite",
                            body("{\"options\": [{\"eventId\": 7, \"date\": \"2026-05-12\"},"
                                    + " {\"eventId\": 7, \"date\": \"2026-05-12\", \"arrivalTime\": \"17:30\"}]}"),
                            editor)));
        });

        verify(lists, never()).inviteEntry(anyInt(), any());
    }

    @Test
    void severalDatesKeepTheOrderTheyWereOfferedIn() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.WAITLIST_EDIT));
            var answer = client.post(
                    ENTRY + "/invite",
                    body("{\"options\": [{\"eventId\": 7, \"date\": \"2026-05-19\", \"arrivalTime\": \"17:30\"},"
                            + " {\"eventId\": 7, \"date\": \"2026-05-12\"}]}"),
                    editor);
            assertEquals(200, answer.code());
        });

        var sent = ArgumentCaptor.forClass(WaitingListInvitation.class);
        verify(lists).inviteEntry(eq(9), sent.capture());
        var options = sent.getValue().options();
        assertEquals(2, options.size());
        assertEquals(0, options.get(0).position());
        assertEquals(LocalDate.of(2026, 5, 19), options.get(0).date());
        assertEquals(LocalTime.of(17, 30), options.get(0).arrivalTime());
        assertEquals(1, options.get(1).position());
        assertEquals(LocalDate.of(2026, 5, 12), options.get(1).date());
    }

    @Test
    void theInvitationCarriesItsAppointmentAndNote() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.WAITLIST_EDIT));
            var answer = client.post(
                    ENTRY + "/invite",
                    body("{\"options\": [{\"eventId\": 7, \"date\": \"2026-05-12\"}], \"message\": \"  Bis bald  \"}"),
                    editor);
            assertEquals(200, answer.code());
        });

        var sent = ArgumentCaptor.forClass(WaitingListInvitation.class);
        verify(lists).inviteEntry(eq(9), sent.capture());
        var offered = sent.getValue().options().getFirst();
        assertEquals(7, offered.eventId());
        assertEquals(LocalDate.of(2026, 5, 12), offered.date());
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

    @Test
    void theVisitorsOfADateAreForWaitingListReadersOnly() {
        var visitor =
                new WaitingListVisitor(9, 5, "Jugend", "Mia", "Muster", LocalDate.of(2026, 6, 2), LocalTime.of(17, 45));
        when(lists.visitors(3, 12, LocalDate.of(2026, 6, 2))).thenReturn(List.of(visitor));

        harness.run((server, client) -> {
            var reader = harness.as(TestSessions.member(3, StationPermission.WAITLIST_READ));
            var answer = json(client.get(PREFIX + "/waiting-lists/visitors?event=12&date=2026-06-02", reader));
            assertEquals("Mia", answer.get(0).path("firstname").asString());
            assertEquals("17:45:00", answer.get(0).path("arrivalTime").asString());
            var member = harness.as(TestSessions.member(3, StationPermission.LOGIN));
            assertEquals(
                    403,
                    client.get(PREFIX + "/waiting-lists/visitors?event=12&date=2026-06-02", member)
                            .code());
            assertEquals(
                    400,
                    client.get(PREFIX + "/waiting-lists/visitors?event=12&date=soon", reader)
                            .code());
        });
    }
}
