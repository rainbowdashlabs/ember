/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationEnrollmentService;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import io.javalin.testtools.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The federation screen names partners and requests through the service and answers a pair request
 * only when it asks this station.
 */
class FederationRoutesTest {
    private static final int STATION = 3;
    private static final FederationPartner PARTNER = new FederationPartner(
            7,
            STATION,
            UUID.fromString("00000000-0000-0000-0000-000000000099"),
            null,
            null,
            null,
            FederationPartner.FederationStatus.ACTIVE,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
            null,
            "Nachbarwache");

    private FederationService federation;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        federation = mock(FederationService.class);
        harness = RouteHarness.serving(new FederationRoutes(
                federation, mock(FederationEnrollmentService.class), mock(KnowledgeBaseFederationService.class)));
    }

    private Consumer<Request.Builder> manager() {
        return harness.as(TestSessions.member(STATION, StationPermission.STATION_FEDERATION));
    }

    @Test
    void partnersAreListedAndReadUnderTheNameTheServiceGives() {
        when(federation.findPartners(STATION)).thenReturn(List.of(PARTNER));
        when(federation.findPartner(7)).thenReturn(Optional.of(PARTNER));
        when(federation.partnerName(PARTNER)).thenReturn("Wache Nord");

        harness.run((server, client) -> {
            assertEquals(
                    "Wache Nord",
                    json(client.get(PREFIX + "/federation/partners", manager()))
                            .path(0)
                            .path("partnerStationName")
                            .asString());
            assertEquals(
                    "Wache Nord",
                    json(client.get(PREFIX + "/federation/partners/7", manager()))
                            .path("partnerStationName")
                            .asString());
        });
    }

    @Test
    void anInviteNeedsTheStationToExist() {
        when(federation.generateStationInvite(STATION)).thenReturn(Optional.of("ember-code"));

        harness.run((server, client) -> {
            assertEquals(
                    "ember-code",
                    json(client.post(PREFIX + "/federation/invite", null, manager()))
                            .path("inviteCode")
                            .asString());
        });

        when(federation.generateStationInvite(STATION)).thenReturn(Optional.empty());
        assertEquals(
                FederationRefusal.FEDERATION_STATION_NOT_HERE,
                refusalOf(harness.request(client -> client.post(PREFIX + "/federation/invite", null, manager()))));
    }

    @Test
    void pendingRequestsCarryTheRequestingStationsName() {
        when(federation.findPendingRequests(STATION)).thenReturn(List.of(PARTNER));
        when(federation.requesterName(PARTNER)).thenReturn("Wache Süd");

        var answer = harness.request(client -> client.get(PREFIX + "/federation/requests", manager()));

        assertEquals("Wache Süd", json(answer).path(0).path("stationName").asString());
    }

    @Test
    void aRequestToThisStationIsAcceptedOrDeclined() {
        when(federation.findRequestTo(7, STATION)).thenReturn(Optional.of(PARTNER));
        when(federation.acceptPairRequest(7)).thenReturn(PARTNER);

        harness.run((server, client) -> {
            assertEquals(
                    200,
                    client.post(PREFIX + "/federation/requests/7/accept", null, manager())
                            .code());
            assertEquals(
                    200,
                    client.post(PREFIX + "/federation/requests/7/decline", null, manager())
                            .code());
        });

        verify(federation).acceptPairRequest(7);
        verify(federation).declinePairRequest(7);
    }

    @Test
    void aRequestToAnotherStationIsNeitherAcceptedNorDeclined() {
        when(federation.findRequestTo(8, STATION)).thenReturn(Optional.empty());

        harness.run((server, client) -> {
            assertEquals(
                    FederationRefusal.PAIR_REQUEST_NOT_HERE_TO_ACCEPT,
                    refusalOf(client.post(PREFIX + "/federation/requests/8/accept", null, manager())));
            assertEquals(
                    FederationRefusal.PAIR_REQUEST_NOT_HERE_TO_DECLINE,
                    refusalOf(client.post(PREFIX + "/federation/requests/8/decline", null, manager())));
        });

        verify(federation, never()).acceptPairRequest(anyInt());
        verify(federation, never()).declinePairRequest(anyInt());
    }
}
