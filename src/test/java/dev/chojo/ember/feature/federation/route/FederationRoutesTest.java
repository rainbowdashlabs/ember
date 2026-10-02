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
import dev.chojo.ember.feature.federation.entity.PairRequest;
import dev.chojo.ember.feature.federation.entity.PairRequestDirection;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.feature.federation.service.FederationEnrollmentService;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.IncomingPairRequestService;
import dev.chojo.ember.feature.federation.service.OutgoingPairRequestService;
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

    private static final PairRequest REMOTE_REQUEST = new PairRequest(
            11,
            STATION,
            PairRequestDirection.INCOMING,
            UUID.fromString("00000000-0000-0000-0000-000000000088"),
            "Wache Fern",
            "https://fern.example:8443",
            "instance-key",
            "station-key",
            null,
            PairRequestStatus.PENDING,
            Instant.EPOCH,
            null,
            null);

    private FederationService federation;
    private IncomingPairRequestService incoming;
    private OutgoingPairRequestService outgoing;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        federation = mock(FederationService.class);
        incoming = mock(IncomingPairRequestService.class);
        outgoing = mock(OutgoingPairRequestService.class);
        harness = RouteHarness.serving(new FederationRoutes(
                federation,
                mock(FederationEnrollmentService.class),
                mock(KnowledgeBaseFederationService.class),
                incoming,
                outgoing));
    }

    @Test
    void requestsFromOtherInstancesNameTheInstanceTheyComeFrom() {
        when(incoming.pending(STATION)).thenReturn(List.of(REMOTE_REQUEST));

        var listed = json(harness.request(client -> client.get(PREFIX + "/federation/remote-requests", manager())))
                .path(0);

        assertEquals("Wache Fern", listed.path("stationName").asString());
        assertEquals("fern.example:8443", listed.path("instanceHost").asString());
    }

    @Test
    void aRequestFromAnotherInstanceIsAcceptedOrDeclinedByTheStationAsked() {
        when(incoming.accept(STATION, 11)).thenReturn(PARTNER);

        harness.run((server, client) -> {
            assertEquals(
                    200,
                    client.post(PREFIX + "/federation/remote-requests/11/accept", null, manager())
                            .code());
            assertEquals(
                    200,
                    client.post(PREFIX + "/federation/remote-requests/11/decline", null, manager())
                            .code());
        });

        verify(incoming).accept(STATION, 11);
        verify(incoming).decline(STATION, 11);
    }

    @Test
    void outgoingRequestsCarryWhereTheyStand() {
        var declined = new PairRequest(
                12,
                STATION,
                PairRequestDirection.OUTGOING,
                UUID.fromString("00000000-0000-0000-0000-000000000077"),
                "Wache Weit",
                "https://weit.example",
                "instance-key",
                null,
                null,
                PairRequestStatus.DECLINED,
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH);
        when(outgoing.outgoing(STATION)).thenReturn(List.of(declined));

        var listed = json(harness.request(client -> client.get(PREFIX + "/federation/outgoing-requests", manager())))
                .path(0);

        assertEquals("Wache Weit", listed.path("stationName").asString());
        assertEquals("weit.example", listed.path("instanceHost").asString());
        assertEquals("DECLINED", listed.path("status").asString());
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
