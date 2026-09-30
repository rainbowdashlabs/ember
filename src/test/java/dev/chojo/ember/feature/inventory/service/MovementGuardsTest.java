/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.api.TestSessions.MEMBER_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MovementGuardsTest {
    private static final int STATION = 3;
    private static final int WARD = 12;
    private static final int STRANGER = 13;
    private static final int OWNING_CLUSTER = 7;

    private ItemMovementService movements;
    private InventoryService inventory;
    private GuardianPolicy guardians;
    private MovementGuards guards;

    static ItemMovement movement(int id, int stationId, Integer memberId, Integer outgoingItemId) {
        return new ItemMovement(
                id,
                stationId,
                MovementPurpose.RETURN,
                1,
                null,
                memberId,
                outgoingItemId,
                null,
                null,
                null,
                null,
                MovementState.OPEN,
                "",
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null,
                false);
    }

    private static InventoryItem ownedBy(Integer clusterId) {
        return new InventoryItem(
                40,
                5,
                "J-1",
                "Jacke",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ItemOwner.CLUSTER,
                clusterId,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static UserSession clusterManager(int clusterId, boolean permitted) {
        var base = TestSessions.member(STATION);
        return new UserSession(
                base.account(),
                1,
                base.stationId(),
                base.stationUid(),
                base.member(),
                base.permissions(),
                Set.of(),
                null,
                null,
                null,
                false,
                clusterId,
                UUID.randomUUID(),
                null,
                permitted ? Set.of(ClusterPermission.CLUSTER_INVENTORY_MOVEMENTS) : Set.of());
    }

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    @BeforeEach
    void setup() {
        movements = mock(ItemMovementService.class);
        inventory = mock(InventoryService.class);
        guardians = mock(GuardianPolicy.class);
        when(guardians.household(any())).thenReturn(List.of(MEMBER_ID, WARD));
        when(guardians.mayActFor(any(), anyInt())).thenAnswer(call -> {
            int member = call.getArgument(1);
            return member == MEMBER_ID || member == WARD;
        });
        guards = new MovementGuards(movements, inventory, guardians);
    }

    @Test
    void theQueueSeesEveryMovementOfTheList() {
        var listed = List.of(movement(1, STATION, STRANGER, null), movement(2, STATION, null, null));

        assertEquals(
                listed,
                guards.visibleAmong(TestSessions.member(STATION, StationPermission.INVENTORY_MOVEMENTS), listed));
    }

    @Test
    void everybodyElseSeesTheirOwnAndTheirWards() {
        var own = movement(1, STATION, MEMBER_ID, null);
        var ward = movement(2, STATION, WARD, null);
        var listed = List.of(own, ward, movement(3, STATION, STRANGER, null), movement(4, STATION, null, null));

        assertEquals(List.of(own, ward), guards.visibleAmong(TestSessions.member(STATION), listed));
    }

    @Test
    void aMovementThatIsNotThereIsNotYours() {
        when(movements.findById(9)).thenReturn(Optional.empty());

        assertEquals(
                Refusal.MOVEMENT_NOT_HERE_OR_NOT_YOURS,
                refusalOf(() -> guards.requireVisible(TestSessions.member(STATION), 9)));
    }

    @Test
    void aMovementOfAnotherStationIsNotOpened() {
        when(movements.findById(9)).thenReturn(Optional.of(movement(9, 4, MEMBER_ID, null)));

        assertEquals(
                Refusal.NOT_YOURS_TO_OPEN, refusalOf(() -> guards.requireVisible(TestSessions.member(STATION), 9)));
    }

    @Test
    void theQueueTheMemberAndTheirGuardianSeeAMovementAndNobodyElse() {
        var queued = movement(9, STATION, STRANGER, null);
        var mine = movement(10, STATION, MEMBER_ID, null);
        var wards = movement(11, STATION, WARD, null);
        var store = movement(12, STATION, null, null);
        for (var movement : List.of(queued, mine, wards, store)) {
            when(movements.findById(movement.id())).thenReturn(Optional.of(movement));
        }
        var member = TestSessions.member(STATION);

        assertSame(
                queued, guards.requireVisible(TestSessions.member(STATION, StationPermission.INVENTORY_MOVEMENTS), 9));
        assertSame(mine, guards.requireVisible(member, 10));
        assertSame(wards, guards.requireVisible(member, 11));
        assertEquals(Refusal.MOVEMENT_NOT_HERE_OR_NOT_YOURS, refusalOf(() -> guards.requireVisible(member, 9)));
        assertEquals(Refusal.MOVEMENT_NOT_HERE_OR_NOT_YOURS, refusalOf(() -> guards.requireVisible(member, 12)));
    }

    @Test
    void theOwningClusterSeesItsGearAtAnyStation() {
        var elsewhere = movement(9, 99, STRANGER, 40);
        when(movements.findById(9)).thenReturn(Optional.of(elsewhere));
        when(inventory.findItemById(40)).thenReturn(Optional.of(ownedBy(OWNING_CLUSTER)));

        assertSame(elsewhere, guards.requireVisible(clusterManager(OWNING_CLUSTER, true), 9));
        assertEquals(Refusal.NOT_YOURS_TO_OPEN, refusalOf(() -> guards.requireVisible(clusterManager(8, true), 9)));
        assertEquals(
                Refusal.NOT_YOURS_TO_OPEN,
                refusalOf(() -> guards.requireVisible(clusterManager(OWNING_CLUSTER, false), 9)));
    }

    @Test
    void ownerRightsNeedTheClusterItsPermissionAndItsGear() {
        when(inventory.findItemById(40)).thenReturn(Optional.of(ownedBy(OWNING_CLUSTER)));
        when(inventory.findItemById(41)).thenReturn(Optional.of(ownedBy(null)));

        assertFalse(guards.hasOwnerRights(TestSessions.member(STATION), null));
        assertFalse(guards.hasOwnerRights(clusterManager(OWNING_CLUSTER, false), null));
        assertTrue(guards.hasOwnerRights(clusterManager(OWNING_CLUSTER, true), null));
        assertTrue(guards.hasOwnerRights(clusterManager(OWNING_CLUSTER, true), movement(1, STATION, null, null)));
        assertTrue(guards.hasOwnerRights(clusterManager(OWNING_CLUSTER, true), movement(1, STATION, null, 40)));
        assertFalse(guards.hasOwnerRights(clusterManager(8, true), movement(1, STATION, null, 40)));
        assertTrue(guards.hasOwnerRights(clusterManager(8, true), movement(1, STATION, null, 41)));
        assertTrue(guards.hasOwnerRights(clusterManager(8, true), movement(1, STATION, null, 42)));
    }

    @Test
    void theActorCarriesTheMemberAndBothCapacities() {
        var actor = guards.actorOf(TestSessions.member(STATION, StationPermission.INVENTORY_MOVEMENTS), null);

        assertEquals(new ItemMovementService.Actor(MEMBER_ID, true, false), actor);
        assertEquals(
                new ItemMovementService.Actor(0, false, false), guards.actorOf(TestSessions.administrator(), null));
    }

    @Test
    void aMovementIsStartedOnlyForSomebodyTheCallerActsFor() {
        var member = TestSessions.member(STATION);

        assertDoesNotThrow(() -> guards.requireMayStartFor(member, null));
        assertDoesNotThrow(() -> guards.requireMayStartFor(member, MEMBER_ID));
        assertDoesNotThrow(() -> guards.requireMayStartFor(member, WARD));
        assertDoesNotThrow(() -> guards.requireMayStartFor(
                TestSessions.member(STATION, StationPermission.INVENTORY_MOVEMENTS), STRANGER));
        assertEquals(Refusal.MEMBER_NOT_YOURS_TO_ACT_FOR, refusalOf(() -> guards.requireMayStartFor(member, STRANGER)));
    }
}
