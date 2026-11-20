/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.SharedEvent;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.feature.federation.FederationTestContracts.pathIs;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** How this station's members read, join and leave the appointments partners share with it. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PartnerEventServiceTest extends EventFederationTestBase {
    @Test
    @Order(30)
    void browseFederatedEventsWithShare() {
        shareService.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var items = clientService.browseFederatedEvents(stationB.id());
        assertFalse(items.isEmpty(), "Should find shared events");
        assertTrue(items.stream().anyMatch(item -> {
            if (item.event() instanceof SharedEvent re) {
                return eventId == re.id();
            }
            return false;
        }));
    }

    @Test
    @Order(31)
    void browseFederatedEventsNoShares() {
        shareService.removeShare(eventId);
        var items = clientService.browseFederatedEvents(stationB.id());
        assertTrue(items.stream().noneMatch(item -> {
            if (item.event() instanceof SharedEvent re) {
                return eventId == re.id();
            }
            return false;
        }));
    }

    /**
     * A partner on this instance is told what it was given, exactly as one across the network is.
     *
     * <p>Both stations keep a record of the other, and everything the host writes hangs off the
     * host's. Read through the visitor's, which is the one the visitor's own screens carry, the host
     * appears to have set nothing aside: the places went missing and the choosing silently stayed
     * with the host, on the one arrangement where it was meant to move.
     */
    @Test
    @Order(31)
    void aPartnerOnThisInstanceIsToldWhatItWasGiven() {
        shareService.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var hostPartner = federationService.findPartners(stationA.id()).stream()
                .filter(p -> p.partnerStationId().equals(stationB.uid()))
                .findFirst()
                .orElseThrow();
        givePlaces(eventId, hostPartner.id(), 2, true);
        try {
            var detail = clientService.getFederatedEvent(stationB.id(), stationA.uid(), eventId);
            assertNotNull(detail.places(), "the arrangement reaches the station it was made with");
            assertTrue(detail.places().decidesItself(), "which is what says the choosing is theirs");
            assertEquals(2, detail.places().slotBudget(), "and how many places they were given");
        } finally {
            givePlaces(eventId, hostPartner.id(), null, false);
        }
    }

    @Test
    @Order(32)
    void getFederatedEventLocal() {
        shareService.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var result = clientService.getFederatedEvent(stationB.id(), stationA.uid(), eventId);
        assertNotNull(result);
        assertEquals(eventId, result.event().id());
        assertEquals("Federated Event", result.event().name());
        assertNull(result.places(), "nothing was set aside, so the holder decides as it always did");
        transport.assertParity(
                partnerOf(stationB, stationA),
                RemoteEventRoutes.GET_EVENT.at(eventId),
                null,
                RemoteEventRoutes.RemoteEventDetail.class);
        transport.assertParity(
                partnerOf(stationB, stationA), RemoteEventRoutes.LIST_EVENTS.at(), null, SharedEvent.class);
    }

    /**
     * An appointment shared with named partners names the holder's own partner rows. A partner on
     * this instance used to be looked up by its own row instead and never saw such an appointment.
     */
    @Test
    @Order(32)
    void anAppointmentSharedWithNamedPartnersReachesAPartnerHere() {
        shareService.setShare(
                eventId,
                ShareScope.SPECIFIC,
                List.of(partnerOf(stationA, stationB).id()));
        assertEquals(
                eventId,
                clientService
                        .getFederatedEvent(stationB.id(), stationA.uid(), eventId)
                        .event()
                        .id());
        assertTrue(clientService.browseFederatedEvents(stationB.id()).stream()
                .anyMatch(item -> ((SharedEvent) item.event()).id() == eventId));
        shareService.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
    }

    /** Whether the partner still exists depends on other tests; either way an unshared event is refused. */
    @Test
    @Order(33)
    void getFederatedEventNotShared() {
        shareService.removeShare(eventId);
        assertThrows(Exception.class, () -> clientService.getFederatedEvent(stationB.id(), stationA.uid(), eventId));
    }

    @Test
    @Order(34)
    void federatedEventItemRecord() {
        var event =
                new SharedEvent(1, "Training", "", StationEvent.EventType.ONE_TIME, 0, "", "", false, true, null, null);
        var item = new PartnerEventService.FederatedEventItem(
                42, "TestStation", "00000000-0000-0000-0000-000000000042", event);
        assertEquals(42, item.partnerId());
        assertEquals("TestStation", item.partnerStationName());
        assertEquals(event, item.event());
    }

    @Test
    @Order(40)
    void browseFederatedEventsViaHttp() {
        shareService.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());

        var remoteEvent = new SharedEvent(
                9999,
                "Remote Event",
                "Remote desc",
                StationEvent.EventType.ONE_TIME,
                0,
                Instant.now().toString(),
                Instant.now().plus(2, ChronoUnit.HOURS).toString(),
                true,
                false,
                null,
                null);
        when(httpClient.getList(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events"),
                        any(),
                        eq(stationA.id()),
                        eq(SharedEvent.class)))
                .thenReturn(List.of(remoteEvent));

        var items = clientService.browseFederatedEvents(stationA.id());
        assertFalse(items.isEmpty(), "Should include events from local and/or remote partners");

        verify(httpClient)
                .getList(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events"),
                        any(),
                        eq(stationA.id()),
                        eq(SharedEvent.class));

        assertTrue(
                items.stream().anyMatch(i -> {
                    if (i.event() instanceof SharedEvent re) {
                        return re.id() == 9999 && re.name().equals("Remote Event");
                    }
                    return false;
                }),
                "Should contain the mocked remote event");
    }

    /** A partner on another instance answers for itself, and what it answers is passed straight on. */
    @Test
    @Order(41)
    void getFederatedEventRemote() {
        var remoteEvent = new SharedEvent(
                eventId,
                "Remote Event",
                "desc",
                StationEvent.EventType.RECURRING,
                1,
                "10:00",
                "12:00",
                false,
                false,
                LocalDate.parse("2026-12-31"),
                null);
        when(httpClient.get(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/" + eventId),
                        any(),
                        eq(stationA.id()),
                        any()))
                .thenReturn(new RemoteEventRoutes.RemoteEventDetail(remoteEvent, List.of(), null, List.of(), null));

        var result = clientService.getFederatedEvent(stationA.id(), stationC.uid(), eventId);
        assertNotNull(result);
        assertEquals(eventId, result.event().id());
        assertEquals("Remote Event", result.event().name());

        verify(httpClient)
                .get(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/" + eventId),
                        any(),
                        eq(stationA.id()),
                        any());
    }

    @Test
    @Order(42)
    void getFederatedEventRemoteReturnsNull() {
        when(httpClient.get(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/" + eventId),
                        any(),
                        eq(stationA.id()),
                        any()))
                .thenReturn(null);

        var refused = assertThrows(
                RefusalResponse.class, () -> clientService.getFederatedEvent(stationA.id(), stationC.uid(), eventId));
        assertEquals(FederationRefusal.FEDERATION_PARTNER_DID_NOT_ANSWER, refused.refusal());
    }

    /** Station B has no remote partner, so it browses only station A's locally shared events. */
    @Test
    @Order(43)
    void browseFederatedEventsHttpReturnsEmpty() {
        shareService.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var items = clientService.browseFederatedEvents(stationB.id());
        assertNotNull(items);
        assertTrue(
                items.stream().anyMatch(i -> {
                    if (i.event() instanceof SharedEvent re) {
                        return eventId == re.id();
                    }
                    return false;
                }),
                "Should contain locally shared events from stationA");
    }

    /** Station B shares nothing and station C answers with nothing, so station A browses nothing. */
    @Test
    @Order(44)
    void browseFederatedEventsRemoteReturnsEmptyLocalHasNone() {
        when(httpClient.getList(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events"),
                        any(),
                        eq(stationA.id()),
                        eq(SharedEvent.class)))
                .thenReturn(List.of());

        var items = clientService.browseFederatedEvents(stationA.id());
        assertNotNull(items);
    }

    @Test
    @Order(84)
    void findMyRegistrationsLocal() {
        hostService.registerFederated(eventId, partnerId, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1));
        var regs = clientService.findMyRegistrations(stationA.id(), List.of(REMOTE_MEMBER_1));
        assertNotNull(regs);
        assertFalse(regs.isEmpty());
    }

    @Test
    @Order(84)
    void findMyRegistrationsEmpty() {
        var regs = clientService.findMyRegistrations(stationA.id(), List.of(UUID.randomUUID()));
        assertNotNull(regs);
    }

    @Test
    @Order(85)
    void getFederatedEventUnknownPartner() {
        assertThrows(
                RefusalResponse.class,
                () -> clientService.getFederatedEvent(stationA.id(), UUID.randomUUID(), eventId));
    }

    @Test
    @Order(90)
    void browseFederatedEventsAsksAPartnerElsewhere() {
        var remoteEvent = new SharedEvent(
                1, "Test Event", "desc", StationEvent.EventType.ONE_TIME, 0, "10:00", "12:00", true, false, null, null);
        when(httpClient.getList(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events"),
                        eq(stationC.uid()),
                        eq(stationA.id()),
                        eq(SharedEvent.class)))
                .thenReturn(List.of(remoteEvent));

        var result = clientService.browseFederatedEvents(stationA.id());
        assertTrue(result.stream().anyMatch(item -> item.event().equals(remoteEvent)));
    }

    /**
     * What the other station recorded is carried back rather than assumed. An appointment that asks
     * for no confirmation accepts at once, and telling our own member they are waiting would be
     * telling them something nobody said.
     */
    @Test
    @Order(91)
    void registerForFederatedEvent() {
        var accepted = new EventFederationRegistration(
                1, 1, 1, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1), RegistrationStatus.ACCEPTED, Instant.now(), null);
        when(httpClient.post(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/1/register"),
                        any(),
                        eq(stationC.uid()),
                        eq(stationA.id()),
                        eq(EventFederationRegistration.class)))
                .thenReturn(accepted);

        var status = clientService.registerForFederatedEvent(
                stationA.id(), stationC.uid(), 1, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1), List.of());
        assertEquals(RegistrationStatus.ACCEPTED, status);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> clientService.registerForFederatedEvent(
                        stationA.id(), stationC.uid(), 2, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1), List.of()));
        assertEquals(EventRefusal.FEDERATED_REGISTRATION_NOT_TAKEN, refused.refusal());
    }

    @Test
    @Order(92)
    void withdrawFederatedRegistration() {
        when(httpClient.delete(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/1/register"),
                        any(),
                        eq(stationC.uid()),
                        eq(stationA.id())))
                .thenReturn(true);

        assertDoesNotThrow(() -> clientService.withdrawFederatedRegistration(
                stationA.id(), stationC.uid(), 1, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1)));
        assertDoesNotThrow(() -> clientService.withdrawFederatedRegistration(
                stationA.id(), stationC.uid(), 2, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1)));
    }

    /** A partner elsewhere that does not answer a confirmation or an undo is told in this station's words. */
    @Test
    @Order(93)
    void aPartnerElsewhereThatDoesNotAnswerIsNamedInTheFeaturesWords() {
        var confirm = assertThrows(
                RefusalResponse.class,
                () -> clientService.confirmOwnFederatedMember(
                        stationA.id(), stationC.uid(), 1, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1)));
        assertEquals(EventRefusal.NO_PLACES_LEFT_AT_HOLDER, confirm.refusal());
        var undo = assertThrows(
                RefusalResponse.class,
                () -> clientService.undoFederatedWithdrawal(
                        stationA.id(), stationC.uid(), 1, REMOTE_MEMBER_1, LocalDate.of(2026, 7, 1)));
        assertEquals(EventRefusal.FEDERATED_WITHDRAWAL_NO_LONGER_UNDONE, undo.refusal());
    }
}
