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
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventPartnerPlaces;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.FederatedRegistrantService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The registrations partners sent for an event are listed with the member the registrant service names. */
class EventSharingRoutesTest {
    private static final int STATION = 3;

    @Test
    void eachPartnerRegistrationCarriesItsMember() {
        var crud = mock(EventCrudService.class);
        var events = mock(EventFederationService.class);
        var registrants = mock(FederatedRegistrantService.class);
        var event = mock(StationEvent.class);
        when(event.stationId()).thenReturn(STATION);
        when(crud.findById(4)).thenReturn(Optional.of(event));
        var member = UUID.fromString("00000000-0000-0000-0000-000000000077");
        var registration = new EventFederationRegistration(
                1, 4, 7, member, LocalDate.of(2026, 5, 1), RegistrationStatus.PENDING, Instant.EPOCH);
        when(events.findRegistrations(4, LocalDate.of(2026, 5, 1))).thenReturn(List.of(registration));
        when(registrants.identify(registration))
                .thenReturn(new MemberIdentity(UUID.fromString("00000000-0000-0000-0000-000000000099"), member)
                        .withDisplay("Kim", "Wache Nord", null, null));
        var harness = RouteHarness.serving(new EventSharingRoutes(crud, events, registrants));

        var answer = harness.request(client -> client.get(
                PREFIX + "/events/4/federation-registrations?date=2026-05-01",
                harness.as(TestSessions.member(STATION, StationPermission.EVENT_REGISTRATION))));

        assertEquals(
                "Kim", json(answer).path(0).path("memberIdentity").path("name").asString());
    }

    @Test
    void partnerPlacesHaveOneShapeAndCountTheTakenOnlyForADay() {
        var crud = mock(EventCrudService.class);
        var events = mock(EventFederationService.class);
        var event = mock(StationEvent.class);
        when(event.stationId()).thenReturn(STATION);
        when(crud.findById(4)).thenReturn(Optional.of(event));
        when(events.partnerPlaces(4)).thenReturn(List.of(new EventPartnerPlaces(4, 7, 5, true)));
        when(events.countPartnerPlaces(4, 7, LocalDate.of(2026, 5, 1)))
                .thenReturn(new EventFederationService.PartnerPlaceCount(2, 5, true));
        var harness =
                RouteHarness.serving(new EventSharingRoutes(crud, events, mock(FederatedRegistrantService.class)));

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
}
