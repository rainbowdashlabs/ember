/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.equipment.EquipmentTestSupport;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.LineTarget;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentAvailabilityRepositoryTest extends RepositoryTestBase {

    private static Station station;
    private static Station partner;
    private static Account account;
    private static StationMember member;
    private static Inventory drawer;
    private static InventoryArt blue;
    private static InventoryItem first;
    private static InventoryItem second;
    private static InventoryItem caseWithoutKind;
    private static LendingRepository lendingRepo;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("availrepo@test.example", "Avail", "Repo");
        station = stationRepo.create("AvailRepoStation");
        partner = stationRepo.create("AvailRepoPartner");
        member = stationMemberRepo.create(station.id(), account.id());
        lendingRepo = new LendingRepository();

        drawer = inventoryRepo.create(station.id(), "AvailRepoFunk", InventoryType.INTERNAL, false, false);
        blue = artRepo.create(drawer.id(), "AvailRepoBlau", "", 0);
        first = item("AVR-01", blue.id());
        second = item("AVR-02", blue.id());
        caseWithoutKind = item("AVR-03", null);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(partner.id());
        accountRepo.delete(account.id());
    }

    private static InventoryItem item(String internalId, Integer artId) {
        return inventoryRepo.createItem(
                drawer.id(), internalId, "Funk", null, artId, InventoryItemMetadata.empty(), null, null);
    }

    @Test
    void stockCountsWhatTheStationCanBringAlong() {
        assertEquals(2, equipmentAvailabilityRepo.stockOf(station.id(), LineTarget.art(blue.id())));
        assertEquals(3, equipmentAvailabilityRepo.stockOf(station.id(), LineTarget.inventory(drawer.id())));
        assertEquals(1, equipmentAvailabilityRepo.stockOf(station.id(), LineTarget.item(first.id())));
        assertEquals(0, equipmentAvailabilityRepo.stockOf(partner.id(), LineTarget.art(blue.id())));
    }

    @Test
    void thePiecesComeBackInAStableOrder() {
        assertEquals(
                java.util.List.of(first.id(), second.id()),
                equipmentAvailabilityRepo.piecesOf(station.id(), LineTarget.art(blue.id())));
    }

    @Test
    void aTargetIsResolvedToTheLevelsAboveIt() {
        var byItem =
                equipmentAvailabilityRepo.resolve(LineTarget.item(first.id())).orElseThrow();
        assertEquals(first.id(), byItem.itemId());
        assertEquals(blue.id(), byItem.artId());
        assertEquals(drawer.id(), byItem.inventoryId());

        var byArt = equipmentAvailabilityRepo.resolve(LineTarget.art(blue.id())).orElseThrow();
        assertEquals(blue.id(), byArt.artId());
        assertEquals(drawer.id(), byArt.inventoryId());
        assertEquals("AvailRepoBlau", byArt.label());

        var byInventory = equipmentAvailabilityRepo
                .resolve(LineTarget.inventory(drawer.id()))
                .orElseThrow();
        assertEquals(drawer.id(), byInventory.inventoryId());
        assertEquals("AvailRepoFunk", byInventory.label());

        var withoutKind = equipmentAvailabilityRepo
                .resolve(LineTarget.item(caseWithoutKind.id()))
                .orElseThrow();
        assertEquals(drawer.id(), withoutKind.inventoryId());
        assertEquals(null, withoutKind.artId());
    }

    @Test
    void aLoanIsRead() {
        var request = lendingRepo.createRequest(
                partner.uid(),
                station.uid(),
                EquipmentTestSupport.SATURDAY,
                EquipmentTestSupport.SATURDAY.plusDays(2),
                member.id(),
                null,
                null,
                "Leistungsmarsch");
        var line = lendingRepo.addRequestItem(request.id(), drawer.id(), null, blue.id(), 2, null);
        lendingRepo.updateRequestStatus(request.id(), LendingStatus.APPROVED);

        var open = equipmentAvailabilityRepo.loanClaims(
                station.id(), EquipmentTestSupport.SATURDAY, EquipmentTestSupport.SATURDAY.plusDays(1));
        assertTrue(open.stream().anyMatch(c -> c.requestItemId() == line.id() && c.assignedItemId() == null));
        assertTrue(open.stream().anyMatch(c -> c.quantity() == 2 && blue.id() == c.artId()));

        lendingRepo.assignItem(line.id(), first.id());
        var assigned = equipmentAvailabilityRepo.loanClaims(
                station.id(), EquipmentTestSupport.SATURDAY, EquipmentTestSupport.SATURDAY.plusDays(1));
        assertTrue(assigned.stream().anyMatch(c -> Integer.valueOf(first.id()).equals(c.assignedItemId())));

        assertTrue(equipmentAvailabilityRepo
                .loanClaims(station.id(), EquipmentTestSupport.SATURDAY.plusDays(30), null)
                .isEmpty());
        assertFalse(
                equipmentAvailabilityRepo.loanClaims(station.id(), null, null).isEmpty());
        lendingRepo.updateRequestStatus(request.id(), LendingStatus.CLOSED);
    }

    @Test
    void aBlockIsRead() {
        var block = lendingRepo.createBlock(
                station.id(),
                drawer.id(),
                null,
                EquipmentTestSupport.SATURDAY,
                EquipmentTestSupport.SATURDAY.plusDays(1),
                "Uebung");
        var claims = equipmentAvailabilityRepo.blockClaims(
                station.id(), EquipmentTestSupport.SATURDAY, EquipmentTestSupport.SATURDAY);
        assertTrue(claims.stream().anyMatch(c -> c.id() == block.id() && "Uebung".equals(c.reason())));
        assertTrue(
                equipmentAvailabilityRepo
                        .blockClaims(station.id(), EquipmentTestSupport.SATURDAY.plusDays(40), null)
                        .stream()
                        .noneMatch(c -> c.id() == block.id()));
        assertNotNull(equipmentAvailabilityRepo.blockClaims(station.id(), null, null));
        lendingRepo.deleteBlock(block.id(), station.id());
    }

    @Test
    void whatIsHereOnLoanAndWhatIsStillOutstandingAreCounted() {
        var event =
                EquipmentTestSupport.oneOff(eventRepo, station.id(), "AvailRepoEvent", EquipmentTestSupport.SATURDAY);
        var need = equipmentNeedRepo.create(event.id(), null, null, blue.id(), null, 4, 0, 0);
        var request = lendingRepo.createRequest(
                station.uid(),
                partner.uid(),
                EquipmentTestSupport.SATURDAY,
                EquipmentTestSupport.SATURDAY,
                member.id(),
                event.id(),
                EquipmentTestSupport.SATURDAY,
                "AvailRepoEvent");
        lendingRepo.addRequestItem(request.id(), drawer.id(), null, blue.id(), 3, need.id());

        assertEquals(0, equipmentAvailabilityRepo.borrowedAgainstNeed(need.id()));
        assertEquals(3, equipmentAvailabilityRepo.outstandingAgainstNeed(need.id()));

        lendingRepo.updateRequestStatus(request.id(), LendingStatus.LENT);
        assertEquals(0, equipmentAvailabilityRepo.outstandingAgainstNeed(need.id()));
        equipmentNeedRepo.deleteByEvent(event.id());
    }

    @Test
    void aTargetThatIsGoneResolvesToNothing() {
        assertTrue(equipmentAvailabilityRepo.resolve(LineTarget.art(-1)).isEmpty());
    }

    @Test
    void aWindowWithoutADayReachesEverything() {
        assertTrue(equipmentAvailabilityRepo
                .loanClaims(station.id(), LocalDate.of(1990, 1, 1), null)
                .isEmpty());
    }
}
