/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemCustodyServiceTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int mixedInventoryId;
    private static Station holdingStation;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("CustodyStation");
        account = accountRepo.create("custody@test.com", "Cus", "Tody");
        member = stationMemberRepo.create(station.id(), account.id());
        mixedInventoryId = inventoryRepo
                .create(station.id(), "Handschuhe", InventoryType.MIXED, false)
                .id();
        holdingStation = stationRepo.create("CustodyHoldingStation");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(holdingStation.id());
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private int stationOwned(String code) {
        return inventoryRepo
                .createItem(mixedInventoryId, code, "Glove", null, null, ItemOwner.STATION, null)
                .id();
    }

    private int ownerOwned(String code) {
        return inventoryRepo
                .createItem(mixedInventoryId, code, "Glove", null, null, ItemOwner.CLUSTER, null)
                .id();
    }

    private ItemCustody custodyOf(int itemId) {
        return inventoryRepo.findItemById(itemId).orElseThrow().custody();
    }

    @Test
    void newGearRestsWhereItsHolderIs() {
        int own = stationOwned("C-1");
        int theirs = ownerOwned("C-2");

        assertEquals(ItemCustody.WITH_OWNER, custodyOf(own));
        assertNull(inventoryRepo.findItemById(own).orElseThrow().custodyStationId());

        // The station does not own it, so the station is holding it rather than its owner
        assertEquals(ItemCustody.AT_STATION, custodyOf(theirs));
        assertEquals(
                station.id(), inventoryRepo.findItemById(theirs).orElseThrow().custodyStationId());
    }

    @Test
    void handingOutAndTakingBackMovesCustodyBothWays() {
        int own = stationOwned("C-3");
        int theirs = ownerOwned("C-4");

        itemCustodyService.assignToMember(own, member.id(), "Cus Tody");
        itemCustodyService.assignToMember(theirs, member.id(), "Cus Tody");
        assertEquals(ItemCustody.WITH_MEMBER, custodyOf(own));
        assertEquals(ItemCustody.WITH_MEMBER, custodyOf(theirs));

        // Each goes back to the store it rests in, which is not the same store for the two
        itemCustodyService.takeBack(own);
        itemCustodyService.takeBack(theirs);
        assertEquals(ItemCustody.WITH_OWNER, custodyOf(own));
        assertEquals(ItemCustody.AT_STATION, custodyOf(theirs));
    }

    @Test
    void aLostItemStaysWithTheMemberButLeavesTheFreeStock() {
        int itemId = stationOwned("C-5");
        itemCustodyService.assignToMember(itemId, member.id(), "Cus Tody");

        itemCustodyService.markLost(itemId, null, null);

        var lost = inventoryRepo.findItemById(itemId).orElseThrow();
        assertEquals(ItemCustody.LOST, lost.custody());
        assertNotNull(lost.lostAt());
        assertEquals(member.id(), lost.assignedTo(), "gear a member is short of stays theirs until replaced");
        assertEquals(station.id(), lost.custodyStationId());
        assertTrue(inventoryRepo.findItemsByMember(member.id()).stream().anyMatch(i -> i.id() == itemId));
        assertFalse(
                inventoryRepo.findUnassignedItems(mixedInventoryId).stream().anyMatch(i -> i.id() == itemId),
                "nobody can hand out what nobody can find");

        // The spell stays open, because the member has not given anything back
        var history = inventoryRepo.findHistory(itemId);
        assertFalse(history.isEmpty());
        assertNull(history.getFirst().returned());
    }

    /**
     * Losing track of something is the station's own business. Nothing reaches the association until the
     * station wants something done about it, which is a separate act with its own note and its own record.
     */
    @Test
    void markingClusterGearLostTellsTheClusterNothingAndKeepsWhatWasWritten() {
        var home = stationRepo.create("Träger Verlust");
        int clusterId = clusterRepo.create("Kreisverband", null, home.id()).id();
        int clusterGear = inventoryRepo
                .createItem(mixedInventoryId, "C-LOST-1", "Jacke", null, null, ItemOwner.CLUSTER, clusterId)
                .id();

        itemCustodyService.markLost(clusterGear, "Auf dem Rückweg verloren", member.id());

        var lost = inventoryRepo.findItemById(clusterGear).orElseThrow();
        assertEquals(ItemCustody.LOST, lost.custody());
        assertEquals("Auf dem Rückweg verloren", lost.lostNote());
        assertEquals(member.id(), lost.lostNoteBy());

        itemCustodyService.markFound(clusterGear);
        var back = inventoryRepo.findItemById(clusterGear).orElseThrow();
        assertNull(back.lostNote(), "a note about a loss that did not last is a note about nothing");
        assertNull(back.lostNoteBy());
    }

    @Test
    void aFoundItemGoesBackToWhoeverItWasStillOnTheRecordOf() {
        int itemId = ownerOwned("C-6");
        itemCustodyService.assignToMember(itemId, member.id(), "Cus Tody");
        itemCustodyService.markLost(itemId, null, null);

        itemCustodyService.markFound(itemId);

        var found = inventoryRepo.findItemById(itemId).orElseThrow();
        assertEquals(ItemCustody.WITH_MEMBER, found.custody());
        assertNull(found.lostAt());
        assertEquals(member.id(), found.assignedTo());
    }

    @Test
    void aFoundItemNobodyHadComesBackToItsStore() {
        int itemId = ownerOwned("C-15");
        itemCustodyService.markLost(itemId, null, null);

        itemCustodyService.markFound(itemId);

        var found = inventoryRepo.findItemById(itemId).orElseThrow();
        assertEquals(ItemCustody.AT_STATION, found.custody());
        assertNull(found.lostAt());
        assertNull(found.assignedTo());
    }

    @Test
    void gearNobodyCanFindCannotBeHandedOut() {
        int itemId = stationOwned("C-7");
        itemCustodyService.markLost(itemId, null, null);

        var thrown = assertThrows(
                BadRequestResponse.class, () -> itemCustodyService.assignToMember(itemId, member.id(), "Cus Tody"));
        assertTrue(thrown.getMessage().contains("LOST"));
    }

    @Test
    void gearAPartnerHasCannotBeHandedOutAndComesBackOnReturn() {
        int itemId = stationOwned("C-8");
        itemCustodyService.lendToPartner(itemId, null);

        assertEquals(ItemCustody.WITH_PARTNER, custodyOf(itemId));
        assertThrows(
                BadRequestResponse.class, () -> itemCustodyService.assignToMember(itemId, member.id(), "Cus Tody"));
        assertFalse(inventoryRepo.findUnassignedItems(mixedInventoryId).stream().anyMatch(i -> i.id() == itemId));

        itemCustodyService.returnFromPartner(itemId);
        assertEquals(ItemCustody.WITH_OWNER, custodyOf(itemId));
    }

    @Test
    void aContainerSaysWhereInTheStoreNotWhoHasIt() {
        int itemId = ownerOwned("C-9");
        int containerId = containerRepo
                .create(station.id(), null, null, "Regal", null, null, null)
                .id();

        itemCustodyService.placeInContainer(itemId, containerId);

        var placed = inventoryRepo.findItemById(itemId).orElseThrow();
        assertEquals(containerId, placed.containerId());
        assertEquals(ItemCustody.AT_STATION, placed.custody(), "putting gear on a shelf does not change who has it");
        assertTrue(inventoryRepo.findItemsByStation(station.id()).stream().anyMatch(i -> i.id() == itemId));

        containerRepo.delete(containerId);
    }

    @Test
    void puttingGearOnAShelfTakesItBackFromTheMember() {
        int itemId = stationOwned("C-10");
        int containerId = containerRepo
                .create(station.id(), null, null, "Regal 2", null, null, null)
                .id();
        itemCustodyService.assignToMember(itemId, member.id(), "Cus Tody");

        itemCustodyService.placeInContainer(itemId, containerId);

        var placed = inventoryRepo.findItemById(itemId).orElseThrow();
        assertEquals(ItemCustody.WITH_OWNER, placed.custody());
        assertNull(placed.assignedTo());
        assertEquals(containerId, placed.containerId());

        containerRepo.delete(containerId);
    }

    @Test
    void everyMoveOfAnItemThatIsNotThereReportsNothingRatherThanFailing() {
        int gone = 9_999_999;

        assertTrue(
                itemCustodyService.assignToMember(gone, member.id(), "Cus Tody").isEmpty());
        assertTrue(itemCustodyService.takeBack(gone).isEmpty());
        assertTrue(itemCustodyService.placeInContainer(gone, null).isEmpty());
        assertTrue(itemCustodyService.markLost(gone, null, null).isEmpty());
        assertTrue(itemCustodyService.markFound(gone).isEmpty());
        assertTrue(itemCustodyService.lendToPartner(gone, null).isEmpty());
        assertTrue(itemCustodyService.returnFromPartner(gone).isEmpty());
    }

    @Test
    void onlyGearAPartnerActuallyHasComesBackFromOne() {
        int itemId = stationOwned("C-13");

        assertTrue(itemCustodyService.returnFromPartner(itemId).isEmpty());
        assertEquals(ItemCustody.WITH_OWNER, custodyOf(itemId));
    }

    @Test
    void takingGearOutOfAContainerLeavesItWhereItIs() {
        int itemId = ownerOwned("C-14");
        int containerId = containerRepo
                .create(station.id(), null, null, "Regal 3", null, null, null)
                .id();
        itemCustodyService.placeInContainer(itemId, containerId);

        itemCustodyService.placeInContainer(itemId, null);

        var cleared = inventoryRepo.findItemById(itemId).orElseThrow();
        assertNull(cleared.containerId());
        assertEquals(ItemCustody.AT_STATION, cleared.custody());

        containerRepo.delete(containerId);
    }

    @Test
    void aStationSeesWhatItHoldsNotWhatItOwns() {
        int own = stationOwned("C-11");
        int theirs = ownerOwned("C-12");

        var held = inventoryRepo.findItemsByStation(station.id());
        assertTrue(held.stream().anyMatch(i -> i.id() == own));
        assertTrue(held.stream().anyMatch(i -> i.id() == theirs), "gear the body above owns but the station holds");

        // The scanner reads the same rule
        assertTrue(inventoryRepo.findByInternalId(station.id(), "C-12").isPresent());

        var otherStation = stationRepo.create("CustodyOtherStation");
        assertTrue(inventoryRepo.findItemsByStation(otherStation.id()).isEmpty());
        assertTrue(inventoryRepo.findByInternalId(otherStation.id(), "C-12").isEmpty());
        stationRepo.delete(otherStation.id());
    }

    @Test
    void aStepLeavesGearAtTheStationRunningTheMovement() {
        int item = ownerOwned("CUSTODY-STEP-1");

        itemCustodyService.applyStepCustody(item, ItemCustody.AT_STATION, null, null, holdingStation.id());

        var stored = inventoryRepo.findItemById(item).orElseThrow();
        assertEquals(ItemCustody.AT_STATION, stored.custody());
        assertEquals(
                holdingStation.id(),
                stored.custodyStationId(),
                "Gear the owner keeps elsewhere is at the station that ran the step, not the one holding the list");
    }

    @Test
    void aStepWithNoStationOfItsOwnFallsBackToTheItemsOwn() {
        int item = ownerOwned("CUSTODY-STEP-2");

        itemCustodyService.applyStepCustody(item, ItemCustody.AT_STATION, null, null);

        assertEquals(
                station.id(),
                inventoryRepo.findItemById(item).orElseThrow().custodyStationId(),
                "Without a movement to say otherwise the item is where its inventory is");
    }
}
