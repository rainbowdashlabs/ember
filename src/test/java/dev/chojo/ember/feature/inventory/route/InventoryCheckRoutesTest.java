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
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.feature.inventory.service.InventoryCheckService;
import dev.chojo.ember.feature.inventory.service.InventoryContainerService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.StationMemberService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static dev.chojo.ember.api.TestSessions.MEMBER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The member a check is about is looked up through the member service and has to be of the
 * checker's station.
 */
class InventoryCheckRoutesTest {
    private static final int STATION = 3;

    private static StationMember memberAt(int id, int stationId) {
        return new StationMember(
                id, stationId, UUID.randomUUID(), 1, false, null, "Mara", StationUserType.MEMBER, LocalDate.EPOCH);
    }

    @Test
    void onlyAMemberOfTheStationIsChecked() {
        var checks = mock(InventoryCheckService.class);
        var members = mock(StationMemberService.class);
        when(members.findById(MEMBER_ID)).thenReturn(Optional.of(memberAt(MEMBER_ID, STATION)));
        when(members.findById(14)).thenReturn(Optional.of(memberAt(14, 4)));
        var harness = RouteHarness.serving(new InventoryCheckRoutes(
                checks,
                mock(InventoryService.class),
                mock(InventoryContainerService.class),
                members,
                mock(MemberIdentityFactory.class)));
        var checker = harness.as(TestSessions.member(STATION, StationPermission.INVENTORY_CHECK));

        harness.run((server, client) -> {
            assertEquals(
                    204,
                    client.post(PREFIX + "/inventory-checks/" + MEMBER_ID + "/cancel", null, checker)
                            .code());
            assertEquals(
                    GeneralRefusal.NOT_YOURS_TO_OPEN,
                    refusalOf(client.post(PREFIX + "/inventory-checks/14/cancel", null, checker)));
            assertEquals(
                    InventoryRefusal.MEMBER_NOT_HERE_ON_CHECK,
                    refusalOf(client.post(PREFIX + "/inventory-checks/15/cancel", null, checker)));
        });

        verify(checks).cancelCheck(MEMBER_ID, MEMBER_ID);
    }
}
