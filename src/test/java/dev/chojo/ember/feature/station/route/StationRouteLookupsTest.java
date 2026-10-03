/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationApplicationService;
import dev.chojo.ember.feature.station.service.StationDiscoveryService;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.feature.system.service.InstanceSettingsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The station routes that asked a repository before: the discovery page, the application form and
 * deleting a station, over HTTP.
 */
class StationRouteLookupsTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void discoveryAnswersStrangersAndStationsAndSendsFederationRequestsForTheStationAsking() {
        var discovery = mock(StationDiscoveryService.class);
        when(discovery.list(any())).thenReturn(List.of());
        when(discovery.inviteCode(false, TARGET)).thenReturn("CODE");
        var harness = RouteHarness.serving(new DiscoveryRoutes(discovery));
        var request = body("{\"stationUid\": \"" + TARGET + "\"}");

        harness.run((server, client) -> {
            assertEquals(200, client.get(PREFIX + "/public/discovery").code());
            var federator = harness.as(TestSessions.member(3, StationPermission.STATION_FEDERATION));
            assertEquals(
                    200, client.get(PREFIX + "/public/discovery", federator).code());
            assertEquals(
                    "CODE",
                    json(client.post(PREFIX + "/public/discovery/invite", request))
                            .path("inviteCode")
                            .asString());
            assertEquals(
                    200,
                    client.post(PREFIX + "/discovery/request", request, federator)
                            .code());
        });

        verify(discovery).list(StationDiscoveryService.Viewer.anonymous());
        verify(discovery).list(new StationDiscoveryService.Viewer(true, 3, true));
        verify(discovery).requestFederation(3, TARGET);
    }

    @Test
    void anApplicationIsRefusedWhileStationRegistrationIsSwitchedOff() {
        var settings = mock(InstanceSettingsService.class);
        var harness =
                RouteHarness.serving(new StationApplicationRoutes(mock(StationApplicationService.class), settings));

        var refused = harness.request(
                client -> client.post(
                        PREFIX + "/station-applications",
                        body(
                                "{\"firstName\": \"Mara\", \"lastName\": \"Nager\", \"email\": \"m@test\", \"stationName\": \"Nord\"}")));

        assertEquals(StationRefusal.STATION_REGISTRATION_SWITCHED_OFF, refusalOf(refused));
    }

    @Test
    void aStationIsDeletedThroughItsServiceOrRefusedByName() {
        var stations = mock(StationService.class);
        var station = mock(Station.class);
        when(station.id()).thenReturn(5);
        when(stations.findByUid(TARGET)).thenReturn(Optional.of(station));
        when(stations.delete(anyInt())).thenReturn(true, false);
        var harness = RouteHarness.serving(new StationRoutes(stations));

        harness.run((server, client) -> {
            var admin = harness.as(TestSessions.administrator());
            assertEquals(
                    204,
                    client.delete(PREFIX + "/stations/" + TARGET, null, admin).code());
            assertEquals(
                    StationRefusal.STATION_NOT_DELETED,
                    refusalOf(client.delete(PREFIX + "/stations/" + TARGET, null, admin)));
        });

        verify(stations, times(2)).delete(5);
    }
}
