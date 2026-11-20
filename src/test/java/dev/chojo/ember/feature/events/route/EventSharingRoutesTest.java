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
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventPartnerPlaces;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventPartnerRegistrationService;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.EventShareService;
import dev.chojo.ember.feature.events.service.EventSharingGuards;
import dev.chojo.ember.feature.events.service.FederatedRegistrantService;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The registrations partners sent for an event are listed with the member the registrant service names. */
class EventSharingRoutesTest {
    private static final int STATION = 3;

    @Test
    void eachPartnerRegistrationCarriesItsMember() {
        var crud = mock(EventCrudService.class);
        var shares = mock(EventShareService.class);
        var events = mock(EventPartnerRegistrationService.class);
        var registrants = mock(FederatedRegistrantService.class);
        var event = mock(StationEvent.class);
        when(event.stationId()).thenReturn(STATION);
        when(crud.findById(4)).thenReturn(Optional.of(event));
        var member = UUID.fromString("00000000-0000-0000-0000-000000000077");
        var registration = new EventFederationRegistration(
                1, 4, 7, member, LocalDate.of(2026, 5, 1), RegistrationStatus.PENDING, Instant.EPOCH, null);
        when(events.findRegistrations(4, LocalDate.of(2026, 5, 1))).thenReturn(List.of(registration));
        when(registrants.identify(registration))
                .thenReturn(new MemberIdentity(UUID.fromString("00000000-0000-0000-0000-000000000099"), member)
                        .withDisplay("Kim", "Wache Nord", null, null));
        var harness = RouteHarness.serving(routes(crud, shares, events, registrants));

        var answer = harness.request(client -> client.get(
                PREFIX + "/events/4/federation-registrations?date=2026-05-01",
                harness.as(TestSessions.member(STATION, StationPermission.EVENT_REGISTRATION))));

        assertEquals(
                "Kim", json(answer).path(0).path("memberIdentity").path("name").asString());
    }

    @Test
    void partnerPlacesHaveOneShapeAndCountTheTakenOnlyForADay() {
        var crud = mock(EventCrudService.class);
        var shares = mock(EventShareService.class);
        var events = mock(EventPartnerRegistrationService.class);
        var event = mock(StationEvent.class);
        when(event.stationId()).thenReturn(STATION);
        when(crud.findById(4)).thenReturn(Optional.of(event));
        when(shares.partnerPlaces(4)).thenReturn(List.of(new EventPartnerPlaces(4, 7, 5, true)));
        when(events.countPartnerPlaces(4, 7, LocalDate.of(2026, 5, 1)))
                .thenReturn(new EventPartnerRegistrationService.PartnerPlaceCount(2, 5, true));
        var harness = RouteHarness.serving(routes(crud, shares, events, mock(FederatedRegistrantService.class)));

        harness.run((server, client) -> {
            var registrar = harness.as(TestSessions.member(STATION, StationPermission.EVENT_REGISTRATION));
            var always = json(client.get(PREFIX + "/events/4/partner-places", registrar))
                    .path(0);
            assertEquals(7, always.path("partnerId").asInt());
            assertEquals(5, always.path("slotBudget").asInt());
            assertTrue(always.path("taken").isNull());
            var onTheDay = json(client.get(PREFIX + "/events/4/partner-places?eventDate=2026-05-01", registrar))
                    .path(0);
            assertEquals(2, onTheDay.path("taken").asInt());
        });
    }

    /** Sharing an appointment hands the request to the sharing service, which decides whether it may be shared. */
    @Test
    void sharingAnAppointmentIsLeftToTheSharingService() {
        var crud = mock(EventCrudService.class);
        var shares = mock(EventShareService.class);
        var event = mock(StationEvent.class);
        when(event.stationId()).thenReturn(STATION);
        when(crud.findById(4)).thenReturn(Optional.of(event));
        var harness = RouteHarness.serving(routes(
                crud, shares, mock(EventPartnerRegistrationService.class), mock(FederatedRegistrantService.class)));

        harness.run((server, client) -> {
            var sharer = harness.as(TestSessions.member(STATION, StationPermission.EVENTS_FEDERATE));
            assertEquals(
                    200,
                    client.put(PREFIX + "/events/4/federation", body("{\"scope\": \"ALL_PARTNERS\"}"), sharer)
                            .code());
            verify(shares).share(event, ShareScope.ALL_PARTNERS, List.of());
        });
    }

    /** A part of an event group takes its places and its share from the event group, so neither is changed on the part. */
    @Test
    void aPartOfAnEventGroupKeepsNoPlacesOrShareOfItsOwn() {
        var crud = mock(EventCrudService.class);
        var shares = mock(EventShareService.class);
        var events = mock(EventPartnerRegistrationService.class);
        var event = mock(StationEvent.class);
        when(event.stationId()).thenReturn(STATION);
        when(crud.findById(4)).thenReturn(Optional.of(event));
        when(crud.isEventGroupPart(4)).thenReturn(true);
        var harness = RouteHarness.serving(routes(crud, shares, events, mock(FederatedRegistrantService.class)));

        harness.run((server, client) -> {
            var sharer = harness.as(TestSessions.member(
                    STATION,
                    StationPermission.EVENTS_FEDERATE,
                    StationPermission.EVENT_MANAGER,
                    StationPermission.EVENT_EDIT));
            assertEquals(
                    EventRefusal.EVENT_GROUP_PART_SHARED_WITH_EVENT_GROUP,
                    refusalOf(client.put(
                            PREFIX + "/events/4/partner-places/7", body("{\"partnerConfirms\": true}"), sharer)));
            assertEquals(
                    EventRefusal.EVENT_GROUP_PART_SHARED_WITH_EVENT_GROUP,
                    refusalOf(client.delete(PREFIX + "/events/4/federation", null, sharer)));
        });
    }

    @Test
    void aDecisionOnAPartnersMemberIsAcceptOrTurnDownAndCarriesTheLimitChoice() {
        var crud = mock(EventCrudService.class);
        var shares = mock(EventShareService.class);
        var events = mock(EventPartnerRegistrationService.class);
        var event = mock(StationEvent.class);
        when(event.stationId()).thenReturn(STATION);
        when(crud.findById(4)).thenReturn(Optional.of(event));
        var day = LocalDate.of(2026, 5, 1);
        var registration = new EventFederationRegistration(
                1, 4, 7, UUID.randomUUID(), day, RegistrationStatus.QUEUED, Instant.EPOCH, null);
        when(events.findRegistrationById(1)).thenReturn(Optional.of(registration));
        when(shares.partnerPlaces(4, 7)).thenReturn(new EventPartnerPlaces(4, 7, null, false));
        when(events.acceptWithinBudget(1, 4, 7, day, true)).thenReturn(true);
        var harness = RouteHarness.serving(routes(crud, shares, events, mock(FederatedRegistrantService.class)));

        harness.run((server, client) -> {
            var registrar = harness.as(TestSessions.member(STATION, StationPermission.EVENT_REGISTRATION));
            String path = PREFIX + "/events/federation-registrations/1/status";
            assertEquals(
                    EventRefusal.REGISTRATION_DECISION_UNKNOWN,
                    refusalOf(client.put(path, body("{\"status\": \"QUEUED\"}"), registrar)));
            assertEquals(
                    200,
                    client.put(path, body("{\"status\": \"ACCEPTED\", \"overLimit\": true}"), registrar)
                            .code());
            verify(events).acceptWithinBudget(1, 4, 7, day, true);
        });
    }

    @Test
    void whoeverDecidesPutsPartnersMembersOnTheListAndReordersIt() {
        var crud = mock(EventCrudService.class);
        var shares = mock(EventShareService.class);
        var events = mock(EventPartnerRegistrationService.class);
        var event = mock(StationEvent.class);
        when(event.stationId()).thenReturn(STATION);
        when(crud.findById(4)).thenReturn(Optional.of(event));
        var registration = new EventFederationRegistration(
                1, 4, 7, UUID.randomUUID(), LocalDate.of(2026, 5, 1), RegistrationStatus.PENDING, Instant.EPOCH, null);
        when(events.findRegistrationById(1)).thenReturn(Optional.of(registration));
        var harness = RouteHarness.serving(routes(crud, shares, events, mock(FederatedRegistrantService.class)));

        harness.run((server, client) -> {
            var registrar = harness.as(TestSessions.member(STATION, StationPermission.EVENT_REGISTRATION));
            var member = harness.as(TestSessions.member(STATION, StationPermission.USER));
            String queue = PREFIX + "/events/federation-registrations/queue";
            assertEquals(
                    403,
                    client.post(queue, body("{\"registrationIds\": [1]}"), member)
                            .code());
            assertEquals(
                    204,
                    client.post(queue, body("{\"registrationIds\": [1]}"), registrar)
                            .code());
            verify(events).moveToQueue(List.of(1));

            String position = PREFIX + "/events/federation-registrations/1/queue-position";
            assertEquals(EventRefusal.QUEUE_POSITION_MISSING, refusalOf(client.put(position, body("{}"), registrar)));
            assertEquals(
                    204,
                    client.put(position, body("{\"position\": 2}"), registrar).code());
            verify(events).moveInQueue(1, 2);
        });
    }

    private static EventSharingRoutes routes(
            EventCrudService crud,
            EventShareService shares,
            EventPartnerRegistrationService events,
            FederatedRegistrantService registrants) {
        return new EventSharingRoutes(
                crud, shares, events, registrants, mock(EventRegistrationService.class), new EventSharingGuards(crud));
    }
}
