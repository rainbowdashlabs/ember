/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.Procurement;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.ProcurementService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
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
import static dev.chojo.ember.api.TestSessions.MEMBER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Orders over HTTP, named by the member and inventory services rather than read off their tables.
 */
class ProcurementRoutesTest {
    private static final int STATION = 3;
    private static final int INVENTORY = 5;

    @Test
    void anOrderIsListedWithItsMemberInventoryAndSizeByNameAndRefusedForNoInventory() {
        var procurements = mock(ProcurementService.class);
        var members = mock(StationMemberService.class);
        var names = mock(MemberNameResolver.class);
        var inventories = mock(InventoryService.class);
        var forMara = new Procurement(1, STATION, INVENTORY, MEMBER_ID, 2, "L", Instant.EPOCH, null);
        var forTheStore = new Procurement(2, STATION, INVENTORY, null, null, "", Instant.EPOCH, null);
        when(procurements.findByStation(STATION)).thenReturn(List.of(forMara, forTheStore));
        when(procurements.create(anyInt(), anyInt(), any(), any(), any())).thenReturn(forMara);
        when(procurements.create(STATION, 99, null, null, null))
                .thenThrow(InventoryRefusal.INVENTORY_NOT_HERE_ON_PROCUREMENT.raise());
        when(members.findById(MEMBER_ID))
                .thenReturn(Optional.of(new StationMember(
                        MEMBER_ID,
                        STATION,
                        UUID.randomUUID(),
                        1,
                        false,
                        null,
                        "Mara",
                        StationUserType.MEMBER,
                        LocalDate.EPOCH)));
        when(names.called(MEMBER_ID)).thenReturn("Mara");
        when(inventories.findById(INVENTORY))
                .thenReturn(Optional.of(new Inventory(
                        INVENTORY, STATION, "Jacken", InventoryType.INTERNAL, true, false, false, null, null)));
        when(inventories.findSizes(INVENTORY)).thenReturn(List.of(new InventorySize(2, INVENTORY, "L", 0, null)));
        var harness = RouteHarness.serving(
                new ProcurementRoutes(procurements, members, names, inventories, mock(MemberIdentityFactory.class)));
        var buyer = harness.as(TestSessions.member(STATION, StationPermission.INVENTORY_PROCUREMENT));

        harness.run((server, client) -> {
            var listed = json(client.get(PREFIX + "/procurement", buyer));
            assertEquals("Mara", listed.path(0).path("memberName").asString());
            assertEquals("Jacken", listed.path(0).path("inventoryName").asString());
            assertEquals("L", listed.path(0).path("sizeLabel").asString());
            assertEquals("", listed.path(1).path("memberName").asString());
            assertEquals(
                    201,
                    client.post(PREFIX + "/procurement", body("{\"inventoryId\": %d}".formatted(INVENTORY)), buyer)
                            .code());
            assertEquals(
                    InventoryRefusal.INVENTORY_NOT_HERE_ON_PROCUREMENT,
                    refusalOf(client.post(PREFIX + "/procurement", body("{\"inventoryId\": 99}"), buyer)));
        });
    }
}
