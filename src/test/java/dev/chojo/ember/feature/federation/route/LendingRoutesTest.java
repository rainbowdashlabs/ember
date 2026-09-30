/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.entity.LentOutItem;
import dev.chojo.ember.feature.federation.service.LendingRequestViewService;
import dev.chojo.ember.feature.federation.service.LendingRequestViewService.AvailableItemDetail;
import dev.chojo.ember.feature.federation.service.LendingRequestViewService.EnrichedMessage;
import dev.chojo.ember.feature.federation.service.LendingRequestViewService.LendingRequestResponse;
import dev.chojo.ember.feature.federation.service.LendingService;
import io.javalin.testtools.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

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
 * The lending screens over HTTP: who may see and act on a request is the view service's to say,
 * and every answer is the request as it describes it.
 */
class LendingRoutesTest {
    private static final int STATION = 3;
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final LendingRequest REQUEST = request(LendingStatus.APPROVED);
    private static final LendingRequestResponse VIEW =
            new LendingRequestResponse(REQUEST, "Wache Süd", "Wache Nord", true, "2 Zelte", false);

    private LendingService lending;
    private LendingRequestViewService views;
    private RouteHarness harness;

    private static LendingRequest request(LendingStatus status) {
        return new LendingRequest(
                5,
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                OWNER,
                status,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 3),
                11,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null,
                "");
    }

    @BeforeEach
    void setup() {
        lending = mock(LendingService.class);
        views = mock(LendingRequestViewService.class);
        when(views.describe(any(LendingRequest.class), eq(STATION))).thenReturn(VIEW);
        when(views.requireParty(5, STATION)).thenReturn(REQUEST);
        when(views.requireOwner(5, STATION)).thenReturn(REQUEST);
        when(lending.findRequest(5)).thenReturn(Optional.of(REQUEST));
        harness = RouteHarness.serving(new LendingRoutes(lending, views));
    }

    private Consumer<Request.Builder> manager() {
        return harness.as(TestSessions.member(
                STATION, StationPermission.INVENTORY_LENDING_MANAGER, StationPermission.INVENTORY_READ));
    }

    @Test
    void theListIsTheOneTheViewServiceGivesForTheReadersRights() {
        when(views.requestsFor(STATION, true)).thenReturn(List.of(VIEW));
        when(views.requestsFor(STATION, false)).thenReturn(List.of());

        harness.run((server, client) -> {
            assertEquals(
                    "Wache Nord",
                    json(client.get(PREFIX + "/lending/requests", manager()))
                            .path(0)
                            .path("owningStationName")
                            .asString());
            var asker = harness.as(TestSessions.member(STATION, StationPermission.INVENTORY_LENDING_REQUEST));
            assertEquals(
                    0, json(client.get(PREFIX + "/lending/requests", asker)).size());
        });
    }

    @Test
    void aRequestIsNotAskedOfTheStationItself() {
        when(views.isOwnStation(STATION, OWNER)).thenReturn(true);
        var asker = harness.as(TestSessions.member(STATION, StationPermission.INVENTORY_LENDING_REQUEST));

        var answer = harness.request(client -> client.post(
                PREFIX + "/lending/requests",
                body("{\"owningStationId\": \"%s\", \"dateFrom\": \"2026-05-01\"}".formatted(OWNER)),
                asker));

        assertEquals(Refusal.LENDING_FROM_OWN_STATION, refusalOf(answer));
    }

    @Test
    void aRequestIsCreatedForTheOccasionTheViewServiceNames() {
        when(views.occasionOf(STATION, 4)).thenReturn("Zeltlager");
        when(lending.createRequest(
                        eq(STATION),
                        eq(OWNER),
                        eq(LocalDate.of(2026, 5, 1)),
                        eq(LocalDate.of(2026, 5, 1)),
                        eq(TestSessions.MEMBER_ID),
                        eq(4),
                        any(),
                        eq("Zeltlager"),
                        anyList()))
                .thenReturn(REQUEST);
        var asker = harness.as(TestSessions.member(STATION, StationPermission.INVENTORY_LENDING_REQUEST));

        var answer = harness.request(client -> client.post(
                PREFIX + "/lending/requests",
                body("{\"owningStationId\": \"%s\", \"dateFrom\": \"2026-05-01\", \"eventId\": 4}".formatted(OWNER)),
                asker));

        assertEquals(201, answer.code());
    }

    @Test
    void aRequestIsReadWithItsLines() {
        when(views.describeItems(5)).thenReturn(List.of());

        var answer = harness.request(client -> client.get(PREFIX + "/lending/requests/5", manager()));

        assertEquals("2 Zelte", json(answer).path("request").path("itemSummary").asString());
    }

    @Test
    void theOwnerDecidesLendsAndAssigns() {
        when(views.availableItems(5, STATION))
                .thenReturn(List.of(new AvailableItemDetail(9, 2, "Zelte", "Z-1", "Zelt", null, 1, true)));

        harness.run((server, client) -> {
            assertEquals(
                    200,
                    client.post(PREFIX + "/lending/requests/5/approve", null, manager())
                            .code());
            assertEquals(
                    200,
                    client.post(PREFIX + "/lending/requests/5/decline", body("{\"reason\": \"voll\"}"), manager())
                            .code());
            assertEquals(
                    200,
                    client.post(PREFIX + "/lending/requests/5/lent", null, manager())
                            .code());
            assertEquals(
                    9,
                    json(client.get(PREFIX + "/lending/requests/5/available-items", manager()))
                            .path(0)
                            .path("itemId")
                            .asInt());
            assertEquals(
                    204,
                    client.post(
                                    PREFIX + "/lending/requests/5/assign-items",
                                    body("{\"items\": [{\"requestItemId\": 1, \"itemId\": 9}]}"),
                                    manager())
                            .code());
        });

        verify(lending).approveRequest(5, STATION);
        verify(lending).declineRequest(5, STATION, "voll");
        verify(lending).markLent(5, STATION);
        verify(lending).assignItem(1, 9, STATION);
    }

    @Test
    void itemsAreAssignedOnlyToAnApprovedRequest() {
        when(views.requireOwner(5, STATION)).thenReturn(request(LendingStatus.REQUESTED));

        var answer = harness.request(
                client -> client.post(PREFIX + "/lending/requests/5/assign-items", body("{\"items\": []}"), manager()));

        assertEquals(Refusal.LENDING_REQUEST_NOT_APPROVED, refusalOf(answer));
        verify(lending, never()).assignItem(anyInt(), anyInt(), anyInt());
    }

    @Test
    void eitherPartyReturnsClosesAndTalks() {
        var message = new LendingMessage(1, 5, OWNER, 11, "Hallo", false, Instant.EPOCH);
        when(lending.getMessages(5, STATION)).thenReturn(List.of(message));
        when(views.describe(message, STATION)).thenReturn(new EnrichedMessage(message, "Mara", "Wache Nord"));
        when(lending.sendMessage(eq(5), eq(STATION), eq(TestSessions.MEMBER_ID), any(), eq("Hallo")))
                .thenReturn(message);

        harness.run((server, client) -> {
            assertEquals(
                    200,
                    client.post(PREFIX + "/lending/requests/5/returned", null, manager())
                            .code());
            assertEquals(
                    200,
                    client.post(PREFIX + "/lending/requests/5/close", null, manager())
                            .code());
            assertEquals(
                    "Mara",
                    json(client.get(PREFIX + "/lending/requests/5/messages", manager()))
                            .path(0)
                            .path("senderName")
                            .asString());
            assertEquals(
                    201,
                    client.post(PREFIX + "/lending/requests/5/messages", body("{\"message\": \"Hallo\"}"), manager())
                            .code());
        });

        verify(lending).markReturned(5, STATION);
        verify(lending).closeRequest(5, STATION);
    }

    @Test
    void aStationThatIsNoPartyIsRefusedBeforeAnythingHappens() {
        when(views.requireParty(6, STATION)).thenThrow(Refusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS.raise());

        var answer = harness.request(client -> client.post(PREFIX + "/lending/requests/6/close", null, manager()));

        assertEquals(Refusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS, refusalOf(answer));
        verify(lending, never()).closeRequest(anyInt(), anyInt());
    }

    @Test
    void whatIsLentOutOfAnInventoryIsTheStationsOwn() {
        when(views.lentOut(2, STATION))
                .thenReturn(List.of(new LentOutItem(1, 5, 9, 1, 9, "LENT", null, null, "Wache Süd")));

        var answer = harness.request(client -> client.get(PREFIX + "/lending/inventory/2/lent-out", manager()));

        assertEquals(
                "Wache Süd", json(answer).path(0).path("requestingStationName").asString());
    }
}
