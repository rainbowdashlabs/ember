/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.InventoryShareRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.InventoryShareService;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The whole life of a borrowed piece, from the handover that gives it a row at the borrower to the
 * return that takes the row away again.
 */
class BorrowedGearServiceTest extends RepositoryTestBase {

    private static LendingService lending;
    private static FederationService federationService;
    private static FederationRepository federationRepo;

    private static Station owner;
    private static Station borrower;
    private static Account account;
    private static StationMember borrowerMember;
    private static int inventoryId;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        lending = new LendingService(
                new LendingRepository(),
                mock(FederationHttpClient.class),
                federationService,
                stationRepo,
                inventoryRepo,
                clusterRepo,
                itemCustodyService,
                borrowedGearService,
                new InventoryShareService(new InventoryShareRepository(), federationService, inventoryRepo, artRepo),
                new DomainEventBus(Set.of()));

        owner = stationRepo.create("BorrowedGearOwner");
        borrower = stationRepo.create("BorrowedGearBorrower");
        account = accountRepo.create("borrowedgear@test.com", "Bor", "Rower");
        borrowerMember = stationMemberRepo.create(borrower.id(), account.id());

        inventoryId = inventoryRepo
                .create(owner.id(), "BorrowedGearStock", InventoryType.INTERNAL, false)
                .id();

        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                borrower.id(), owner.id(), federationService.encodePublicKey(keyPair), null, null);
    }

    @AfterAll
    static void cleanup() {
        for (var p : federationService.findPartners(owner.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(borrower.id())) federationRepo.deletePartner(p.id());
        stationRepo.delete(owner.id());
        stationRepo.delete(borrower.id());
        accountRepo.delete(account.id());
    }

    @Test
    void borrowingWritesAnOrdinaryRowAtTheBorrowerAndNamesThePartnerAtTheOwner() {
        InventoryItem source = ownedPiece("BG-001", "Funkgerät");
        int requestId = lend(source);

        InventoryItem copy = onlyBorrowedRow();
        assertEquals(ItemOwner.PARTNER_STATION, copy.ownerKind());
        assertEquals(owner.id(), copy.ownerStationId());
        assertNotNull(copy.loanRequestItemId());
        // The snapshot: name and identifier as they stood when the gear changed hands
        assertEquals("Funkgerät", copy.name());
        assertEquals("BG-001", copy.internalId());
        // It rests at the station that has it, exactly as a cluster's jacket does
        assertEquals(ItemCustody.AT_STATION, copy.custody());
        assertEquals(borrower.id(), copy.custodyStationId());
        // And it sits on a shelf of that station's own, made for the occasion
        var shelf = inventoryRepo.findById(copy.inventoryId()).orElseThrow();
        assertTrue(shelf.borrowed());
        assertFalse(shelf.homogeneous());
        assertEquals(borrower.id(), shelf.stationId());

        InventoryItem atOwner = inventoryRepo.findItemById(source.id()).orElseThrow();
        assertEquals(ItemCustody.WITH_PARTNER, atOwner.custody());
        assertEquals(owner.id(), atOwner.custodyStationId());
        assertEquals(borrower.id(), atOwner.custodyPartnerStationId());

        handBack(requestId, source);
    }

    @Test
    void aSecondLoanLandsOnTheSameShelf() {
        InventoryItem first = ownedPiece("BG-010", "Erste Leihgabe");
        int firstRequest = lend(first);
        int shelfId = onlyBorrowedRow().inventoryId();

        InventoryItem second = ownedPiece("BG-011", "Zweite Leihgabe");
        int secondRequest = lend(second);

        var rows = inventoryRepo.findItems(shelfId);
        assertEquals(2, rows.size());
        assertEquals(
                1,
                inventoryRepo.findByStation(borrower.id()).stream()
                        .filter(inv -> inv.borrowed())
                        .count());

        handBack(firstRequest, first);
        handBack(secondRequest, second);
    }

    @Test
    void borrowedGearGoesInAContainerAndOntoAMember() {
        InventoryItem source = ownedPiece("BG-020", "Schlauch");
        int requestId = lend(source);
        InventoryItem copy = onlyBorrowedRow();

        var container = containerRepo.create(borrower.id(), null, "BG-BOX", "Regal", null, "", null);
        itemCustodyService.placeInContainer(copy.id(), container.id());
        assertEquals(
                container.id(),
                inventoryRepo.findItemById(copy.id()).orElseThrow().containerId());

        itemCustodyService.assignToMember(copy.id(), borrowerMember.id(), "Bor Rower");
        InventoryItem held = inventoryRepo.findItemById(copy.id()).orElseThrow();
        assertEquals(ItemCustody.WITH_MEMBER, held.custody());
        assertEquals(borrowerMember.id(), held.assignedTo());

        handBack(requestId, source);
    }

    @Test
    void aLoanThatEndsWhileAMemberStillHasItTakesTheRowAnyway() {
        InventoryItem source = ownedPiece("BG-030", "Helm");
        int requestId = lend(source);
        InventoryItem copy = onlyBorrowedRow();
        itemCustodyService.assignToMember(copy.id(), borrowerMember.id(), "Bor Rower");

        assertTrue(lending.markReturned(requestId, owner.id()));

        assertTrue(inventoryRepo.findItemById(copy.id()).isEmpty());
        assertTrue(inventoryRepo.findItemsByMember(borrowerMember.id()).stream()
                .noneMatch(item -> item.id() == copy.id()));
        assertEquals(
                ItemCustody.WITH_OWNER,
                inventoryRepo.findItemById(source.id()).orElseThrow().custody());
    }

    @Test
    void theOwnersRulesReachTheBorrowedRowWithoutASecondCaseBeingWritten() {
        InventoryItem source = ownedPiece("BG-040", "Pumpe");
        int requestId = lend(source);
        InventoryItem copy = onlyBorrowedRow();

        assertThrows(
                ForbiddenResponse.class, () -> inventoryService.updateItem(copy.id(), "X", "Neu", null, null, null));
        assertThrows(ForbiddenResponse.class, () -> inventoryService.deleteItem(copy.id(), null));
        assertThrows(ForbiddenResponse.class, () -> inventoryService.moveItem(copy.id(), inventoryId, null));
        // A borrower cannot lend a partner's radio on to a third station
        assertTrue(
                lending.findAssignableItems(borrower.id(), copy.inventoryId()).isEmpty());

        handBack(requestId, source);
    }

    @Test
    void aWalkFindingBorrowedGearMissingDoesNotMarkSomebodyElsesRadioLost() {
        InventoryItem source = ownedPiece("BG-050", "Lampe");
        int requestId = lend(source);
        InventoryItem copy = onlyBorrowedRow();

        assertThrows(BadRequestResponse.class, () -> itemCustodyService.markLost(copy.id(), "weg", null));
        assertNull(inventoryRepo.findItemById(copy.id()).orElseThrow().lostAt());

        handBack(requestId, source);
    }

    @Test
    void theShelfIsRenameableAndCannotBeDeletedWhileAnythingIsOnIt() {
        InventoryItem source = ownedPiece("BG-060", "Leiter");
        int requestId = lend(source);
        int shelfId = onlyBorrowedRow().inventoryId();

        var renamed = inventoryService
                .update(shelfId, "Von anderen", InventoryType.MIXED, false, false)
                .orElseThrow();
        assertEquals("Von anderen", renamed.name());
        assertTrue(renamed.borrowed());

        assertThrows(BadRequestResponse.class, () -> inventoryService.delete(shelfId));
        // Nothing of the station's own may be filed on it either
        assertThrows(BadRequestResponse.class, () -> inventoryService.createItem(shelfId, "X", "Eigenes", null, null));

        handBack(requestId, source);
        assertTrue(inventoryService.delete(shelfId));
    }

    @Test
    void borrowedGearIsListedWithThePartnerItBelongsToAndTheDayItGoesBack() {
        InventoryItem source = ownedPiece("BG-070", "Tragetuch");
        int requestId = lend(source);

        var listed = borrowedGearService.borrowedAt(borrower.id());
        assertEquals(1, listed.size());
        assertEquals("BorrowedGearOwner", listed.getFirst().ownerStationName());
        assertEquals(owner.id(), listed.getFirst().ownerStationId());
        assertEquals(requestId, listed.getFirst().loanRequestId());
        assertEquals(LocalDate.now().plusDays(7), listed.getFirst().dueOn());
        assertNotEquals(0, listed.getFirst().item().id());

        handBack(requestId, source);
        assertTrue(borrowedGearService.borrowedAt(borrower.id()).isEmpty());
    }

    // -- helpers --

    private static InventoryItem ownedPiece(String internalId, String name) {
        return inventoryRepo.createItem(inventoryId, internalId, name, null, null);
    }

    /** Takes one piece all the way from asking for it to holding it. */
    private static int lend(InventoryItem item) {
        var request = lending.createRequest(
                borrower.id(), owner.id(), LocalDate.now(), LocalDate.now().plusDays(7), borrowerMember.id());
        var line = lending.addRequestItem(request.id(), inventoryId, item.id(), 1);
        lending.assignItem(line.id(), item.id(), owner.id());
        assertTrue(lending.markLent(request.id(), owner.id()));
        return request.id();
    }

    private static void handBack(int requestId, InventoryItem source) {
        assertTrue(lending.markReturned(requestId, owner.id()));
        assertEquals(
                LendingStatus.RETURNED,
                lending.findRequest(requestId).orElseThrow().status());
        assertEquals(
                ItemCustody.WITH_OWNER,
                inventoryRepo.findItemById(source.id()).orElseThrow().custody());
        assertNull(inventoryRepo.findItemById(source.id()).orElseThrow().custodyPartnerStationId());
    }

    private static InventoryItem onlyBorrowedRow() {
        var rows = borrowedGearService.borrowedAt(borrower.id());
        assertEquals(1, rows.size());
        return rows.getFirst().item();
    }
}
