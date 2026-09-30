/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.service.BorrowedGearService;
import dev.chojo.ember.feature.inventory.service.GlyphResolver;
import dev.chojo.ember.feature.inventory.service.InventoryCheckService;
import dev.chojo.ember.feature.inventory.service.InventoryContainerService;
import dev.chojo.ember.feature.inventory.service.InventoryExportService;
import dev.chojo.ember.feature.inventory.service.InventoryIntakeService;
import dev.chojo.ember.feature.inventory.service.InventoryLossService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.LossReportService;
import dev.chojo.ember.feature.inventory.service.SelfCheckService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.StationMemberService;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static dev.chojo.ember.api.TestSessions.MEMBER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Losing gear, the station's setting about it, and handing a new piece to a member, over HTTP.
 */
class InventoryRoutesTest {
    private static final int STATION = 3;
    private static final int ITEM = 40;
    private static final int INVENTORY = 5;
    private static final InventoryItem PIECE = new InventoryItem(
            ITEM,
            INVENTORY,
            "J-1",
            "Jacke",
            null,
            null,
            null,
            MEMBER_ID,
            null,
            null,
            null,
            ItemOwner.STATION,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    private InventoryService inventoryService;
    private StationMemberService memberService;
    private InventoryLossService lossService;
    private RouteHarness harness;

    private static StationMember memberAt(int id, int stationId) {
        return new StationMember(
                id, stationId, UUID.randomUUID(), 1, false, null, "Mara", StationUserType.MEMBER, LocalDate.EPOCH);
    }

    @BeforeEach
    void setup() {
        inventoryService = mock(InventoryService.class);
        memberService = mock(StationMemberService.class);
        lossService = mock(InventoryLossService.class);
        when(inventoryService.findItemById(ITEM)).thenReturn(Optional.of(PIECE));
        when(inventoryService.findById(INVENTORY))
                .thenReturn(Optional.of(new Inventory(
                        INVENTORY, STATION, "Jacken", InventoryType.INTERNAL, false, false, false, null, null)));
        harness = RouteHarness.serving(new InventoryRoutes(
                inventoryService,
                mock(InventoryCheckService.class),
                mock(InventoryExportService.class),
                mock(InventoryContainerService.class),
                mock(MemberIdentityFactory.class),
                memberService,
                lossService,
                mock(LossReportService.class),
                mock(InventoryIntakeService.class),
                mock(BorrowedGearService.class),
                mock(SelfCheckService.class),
                mock(GlyphResolver.class)));
    }

    private Response put(HttpClient client, String path, String json, StationPermission permission) {
        return client.put(PREFIX + path, body(json), harness.as(TestSessions.member(STATION, permission)));
    }

    @Test
    void aLossIsReportedThroughTheLossService() {
        when(lossService.markLost(any(), eq(ITEM), any(), any())).thenReturn(PIECE);

        harness.run((server, client) -> {
            var lost = put(
                    client,
                    "/inventory-items/" + ITEM + "/lost",
                    "{\"note\": \"weg\", \"selfCheckId\": 8}",
                    StationPermission.USER);
            assertEquals(ITEM, json(lost).path("id").asInt());
            assertEquals(
                    200,
                    client.put(
                                    PREFIX + "/inventory-items/" + ITEM + "/lost",
                                    null,
                                    harness.as(TestSessions.member(STATION, StationPermission.USER)))
                            .code());
        });

        verify(lossService).markLost(any(), eq(ITEM), eq("weg"), eq(8));
        verify(lossService).markLost(any(), eq(ITEM), eq(null), eq(null));
    }

    @Test
    void aLossOfGearOfAnotherStationIsNotReported() {
        when(inventoryService.findById(INVENTORY))
                .thenReturn(Optional.of(new Inventory(
                        INVENTORY, 4, "Jacken", InventoryType.INTERNAL, false, false, false, null, null)));

        harness.run((server, client) -> assertEquals(
                Refusal.NOT_YOURS_TO_OPEN,
                refusalOf(put(client, "/inventory-items/" + ITEM + "/lost", "{}", StationPermission.USER))));
    }

    @Test
    void theNoteSettingIsReadByMembersAndWrittenByTheManager() {
        when(lossService.lossNoteRequired(STATION)).thenReturn(false);
        when(lossService.requireLossNote(STATION, true)).thenReturn(true);

        harness.run((server, client) -> {
            var read = client.get(
                    PREFIX + "/inventory-settings", harness.as(TestSessions.member(STATION, StationPermission.USER)));
            assertEquals(false, json(read).path("lossNoteRequired").asBoolean());
            var written = put(
                    client, "/inventory-settings", "{\"lossNoteRequired\": true}", StationPermission.INVENTORY_MANAGER);
            assertTrue(json(written).path("lossNoteRequired").asBoolean());
        });
    }

    @Test
    void aNewPieceGoesOnlyToAMemberOfTheStation() {
        when(memberService.findById(MEMBER_ID)).thenReturn(Optional.of(memberAt(MEMBER_ID, STATION)));
        when(memberService.findById(14)).thenReturn(Optional.of(memberAt(14, 4)));
        when(inventoryService.createAndHandOut(eq(INVENTORY), any(), eq(MEMBER_ID), any()))
                .thenReturn(PIECE);
        String request = "{\"inventoryId\": %d}".formatted(INVENTORY);

        harness.run((server, client) -> {
            var handedOut = client.post(
                    PREFIX + "/station-members/" + MEMBER_ID + "/inventory-items",
                    body(request),
                    harness.as(TestSessions.member(STATION, StationPermission.INVENTORY_CREATE_INTERNAL)));
            assertEquals(201, handedOut.code());
            assertEquals(
                    Refusal.NOT_YOURS_TO_OPEN,
                    refusalOf(client.post(
                            PREFIX + "/station-members/14/inventory-items",
                            body(request),
                            harness.as(TestSessions.member(STATION, StationPermission.INVENTORY_CREATE_INTERNAL)))));
            assertEquals(
                    Refusal.MEMBER_NOT_HERE_FOR_GEAR,
                    refusalOf(client.post(
                            PREFIX + "/station-members/15/inventory-items",
                            body(request),
                            harness.as(TestSessions.member(STATION, StationPermission.INVENTORY_CREATE_INTERNAL)))));
        });

        verify(inventoryService).createAndHandOut(eq(INVENTORY), any(), eq(MEMBER_ID), any());
    }
}
