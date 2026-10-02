/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.feature.inventory.entity.MovementFlow;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.inventory.service.MovementFlowService;
import dev.chojo.ember.feature.inventory.service.MovementTargeting;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Whether a chain waits for the member's receipt, read and saved over HTTP: the chains carry it, and only a
 * chain of the caller's own station can be told otherwise.
 */
class MovementFlowRoutesTest {
    private static final int STATION = 3;
    private static final int FLOW = 5;
    private static final int ELSEWHERE = 6;

    private final AtomicBoolean skips = new AtomicBoolean();
    private MovementFlowService flowService;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        flowService = mock(MovementFlowService.class);
        when(flowService.findFlow(FLOW)).thenAnswer(call -> Optional.of(flow(FLOW, STATION, skips.get())));
        when(flowService.findFlow(ELSEWHERE)).thenReturn(Optional.of(flow(ELSEWHERE, STATION + 1, false)));
        when(flowService.findFlows(STATION)).thenAnswer(call -> List.of(flow(FLOW, STATION, skips.get())));
        when(flowService.problemOf(anyInt())).thenReturn(Optional.empty());
        when(flowService.findAllSteps(anyInt())).thenReturn(List.of());
        when(flowService.setSkipMemberReceipt(eq(FLOW), anyBoolean())).thenAnswer(call -> {
            skips.set(call.getArgument(1));
            return true;
        });
        harness = RouteHarness.serving(
                new MovementFlowRoutes(flowService, mock(ItemMovementService.class), mock(MovementTargeting.class)));
    }

    private static MovementFlow flow(int id, int stationId, boolean skipMemberReceipt) {
        return new MovementFlow(id, stationId, null, "Tausch", MovementPurpose.EXCHANGE, false, skipMemberReceipt);
    }

    private Response put(HttpClient client, int flowId, boolean skip, StationPermission permission) {
        return client.put(
                PREFIX + "/movement-flows/%d/member-receipt".formatted(flowId),
                body("{\"skipMemberReceipt\": %s}".formatted(skip)),
                harness.as(TestSessions.member(STATION, permission)));
    }

    @Test
    void theChainsSayWhetherTheyWaitForTheReceipt() {
        skips.set(true);

        harness.run((server, client) -> assertTrue(json(client.get(
                        PREFIX + "/movement-flows",
                        harness.as(TestSessions.member(STATION, StationPermission.INVENTORY_MANAGER))))
                .path(0)
                .path("skipMemberReceipt")
                .asBoolean()));
    }

    @Test
    void savingTheToggleAnswersWithTheChainAsItNowStands() {
        harness.run((server, client) -> {
            var switchedOn = put(client, FLOW, true, StationPermission.INVENTORY_MANAGER);
            assertEquals(200, switchedOn.code());
            assertTrue(json(switchedOn).path("skipMemberReceipt").asBoolean());

            var switchedOff = put(client, FLOW, false, StationPermission.INVENTORY_MANAGER);
            assertFalse(json(switchedOff).path("skipMemberReceipt").asBoolean());
        });

        verify(flowService).setSkipMemberReceipt(FLOW, true);
        verify(flowService).setSkipMemberReceipt(FLOW, false);
    }

    @Test
    void aChainOfAnotherStationIsNotHere() {
        harness.run((server, client) -> assertEquals(
                InventoryRefusal.FLOW_NOT_HERE,
                refusalOf(put(client, ELSEWHERE, true, StationPermission.INVENTORY_MANAGER))));

        verify(flowService, never()).setSkipMemberReceipt(eq(ELSEWHERE), anyBoolean());
    }

    @Test
    void aChainGoneBeforeTheSettingWasWrittenIsSaidToBeGone() {
        when(flowService.setSkipMemberReceipt(FLOW, true)).thenReturn(false);

        harness.run((server, client) -> assertEquals(
                InventoryRefusal.FLOW_RECEIPT_NOT_CHANGED,
                refusalOf(put(client, FLOW, true, StationPermission.INVENTORY_MANAGER))));
    }

    @Test
    void onlyAnInventoryManagerMaySaveIt() {
        harness.run((server, client) -> assertEquals(
                403, put(client, FLOW, true, StationPermission.USER).code()));

        verify(flowService, never()).setSkipMemberReceipt(anyInt(), anyBoolean());
    }
}
