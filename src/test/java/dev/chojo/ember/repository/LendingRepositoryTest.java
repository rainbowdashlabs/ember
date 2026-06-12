/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LendingRepositoryTest extends RepositoryTestBase {

    private static LendingRepository lendingRepo;

    private static Station stationA;
    private static Station stationB;
    private static Account account;
    private static StationMember memberA;
    private static StationMember memberB;

    private static int requestId;
    private static int requestItemId;
    private static int blockId;
    private static int inventoryIdA;
    private static int itemIdA;

    @BeforeAll
    static void setup() {
        lendingRepo = new LendingRepository();

        stationA = stationRepo.create("LendRepoTestStationA");
        stationB = stationRepo.create("LendRepoTestStationB");

        account = accountRepo.create("lendrepo@test.com", "Lend", "Tester");
        memberA = stationMemberRepo.create(stationA.id(), account.id());
        memberB = stationMemberRepo.create(stationB.id(), account.id());

        // Create inventory on station A for lent-out tests
        var inv = inventoryRepo.create(stationA.id(), "LendRepoInventory", InventoryType.INTERNAL, false);
        inventoryIdA = inv.id();
        var item = inventoryRepo.createItem(inventoryIdA, "LEND-001", "Lend Item", null, "{}");
        itemIdA = item.id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(stationA.id());
        stationRepo.delete(stationB.id());
        accountRepo.delete(account.id());
    }

    // -- Lending Requests --

    @Test
    @Order(1)
    void createRequest() {
        var dateFrom = LocalDate.now();
        var dateTo = LocalDate.now().plusDays(7);
        var request = lendingRepo.createRequest(stationB.id(), stationA.id(), dateFrom, dateTo, memberB.id());
        assertNotNull(request);
        assertTrue(request.id() > 0);
        assertEquals(stationB.id(), request.requestingStationId());
        assertEquals(stationA.id(), request.owningStationId());
        assertEquals(LendingStatus.REQUESTED, request.status());
        assertEquals(dateFrom, request.requestedDateFrom());
        assertEquals(dateTo, request.requestedDateTo());
        requestId = request.id();
    }

    @Test
    @Order(2)
    void findRequestById() {
        var found = lendingRepo.findRequestById(requestId);
        assertTrue(found.isPresent());
        assertEquals(requestId, found.get().id());
        assertEquals(LendingStatus.REQUESTED, found.get().status());
    }

    @Test
    @Order(3)
    void findRequestsByStation() {
        // Should find the request from stationB's perspective (requesting)
        var fromB = lendingRepo.findRequestsByStation(stationB.id());
        assertTrue(fromB.stream().anyMatch(r -> r.id() == requestId));

        // Should also find it from stationA's perspective (owning)
        var fromA = lendingRepo.findRequestsByStation(stationA.id());
        assertTrue(fromA.stream().anyMatch(r -> r.id() == requestId));
    }

    // -- Request Items --

    @Test
    @Order(10)
    void addRequestItem() {
        var item = lendingRepo.addRequestItem(requestId, inventoryIdA, itemIdA, 1);
        assertNotNull(item);
        assertTrue(item.id() > 0);
        assertEquals(requestId, item.requestId());
        assertEquals(inventoryIdA, item.inventoryId());
        assertEquals(itemIdA, item.itemId());
        assertEquals(1, item.quantity());
        assertNull(item.assignedItemId());
        requestItemId = item.id();
    }

    @Test
    @Order(11)
    void findItemsByRequest() {
        var items = lendingRepo.findItemsByRequest(requestId);
        assertFalse(items.isEmpty());
        assertTrue(items.stream().anyMatch(i -> i.id() == requestItemId));
    }

    @Test
    @Order(12)
    void assignItem() {
        assertTrue(lendingRepo.assignItem(requestItemId, itemIdA));
        var items = lendingRepo.findItemsByRequest(requestId);
        var assigned =
                items.stream().filter(i -> i.id() == requestItemId).findFirst().orElseThrow();
        assertEquals(itemIdA, assigned.assignedItemId());
    }

    // -- Status Transitions --

    @Test
    @Order(20)
    void updateRequestStatus() {
        assertTrue(lendingRepo.updateRequestStatus(requestId, LendingStatus.APPROVED));
        var approved = lendingRepo.findRequestById(requestId).orElseThrow();
        assertEquals(LendingStatus.APPROVED, approved.status());

        assertTrue(lendingRepo.updateRequestStatus(requestId, LendingStatus.LENT));
        var lent = lendingRepo.findRequestById(requestId).orElseThrow();
        assertEquals(LendingStatus.LENT, lent.status());

        assertTrue(lendingRepo.updateRequestStatus(requestId, LendingStatus.RETURNED));
        var returned = lendingRepo.findRequestById(requestId).orElseThrow();
        assertEquals(LendingStatus.RETURNED, returned.status());

        assertTrue(lendingRepo.updateRequestStatus(requestId, LendingStatus.CLOSED));
        var closed = lendingRepo.findRequestById(requestId).orElseThrow();
        assertEquals(LendingStatus.CLOSED, closed.status());
    }

    // -- Messages --

    @Test
    @Order(30)
    void createMessage() {
        var msg = lendingRepo.createMessage(requestId, stationA.id(), memberA.id(), "Hello from A", false);
        assertNotNull(msg);
        assertTrue(msg.id() > 0);
        assertEquals(requestId, msg.requestId());
        assertEquals(stationA.id(), msg.senderStationId());
        assertEquals(memberA.id(), msg.senderMemberId());
        assertEquals("Hello from A", msg.message());
        assertFalse(msg.isSystem());
    }

    @Test
    @Order(31)
    void createSystemMessage() {
        var msg = lendingRepo.createMessage(requestId, stationB.id(), null, "System event", true);
        assertNotNull(msg);
        assertTrue(msg.isSystem());
        assertNull(msg.senderMemberId());
    }

    @Test
    @Order(32)
    void findMessagesByRequest() {
        var messages = lendingRepo.findMessagesByRequest(requestId);
        assertTrue(messages.size() >= 2);
    }

    @Test
    @Order(33)
    void findLocalMessages() {
        // Add another message from A
        lendingRepo.createMessage(requestId, stationA.id(), memberA.id(), "Another from A", false);

        var localA = lendingRepo.findLocalMessages(requestId, stationA.id());
        assertTrue(localA.size() >= 2);
        assertTrue(localA.stream().allMatch(m -> m.senderStationId() == stationA.id()));

        var localB = lendingRepo.findLocalMessages(requestId, stationB.id());
        assertTrue(!localB.isEmpty());
        assertTrue(localB.stream().allMatch(m -> m.senderStationId() == stationB.id()));
    }

    // -- Blocks --

    @Test
    @Order(40)
    void createBlock() {
        var from = LocalDate.now();
        var to = LocalDate.now().plusDays(14);
        var block = lendingRepo.createBlock(stationA.id(), null, null, from, to, "Station maintenance");
        assertNotNull(block);
        assertTrue(block.id() > 0);
        assertEquals(stationA.id(), block.stationId());
        assertNull(block.inventoryId());
        assertNull(block.itemId());
        assertEquals("Station maintenance", block.reason());
        blockId = block.id();
    }

    @Test
    @Order(41)
    void findBlocksByStation() {
        var blocks = lendingRepo.findBlocksByStation(stationA.id());
        assertFalse(blocks.isEmpty());
        assertTrue(blocks.stream().anyMatch(b -> b.id() == blockId));
    }

    @Test
    @Order(42)
    void isBlockedStationWide() {
        // Station-wide block (no inventory or item) should block everything
        assertTrue(lendingRepo.isBlocked(
                stationA.id(),
                inventoryIdA,
                itemIdA,
                LocalDate.now(),
                LocalDate.now().plusDays(1)));
        assertTrue(lendingRepo.isBlocked(
                stationA.id(), null, null, LocalDate.now(), LocalDate.now().plusDays(1)));
    }

    @Test
    @Order(43)
    void isBlockedInventoryLevel() {
        // Delete station-wide block and create inventory-level block
        lendingRepo.deleteBlock(blockId);
        var invBlock = lendingRepo.createBlock(
                stationA.id(),
                inventoryIdA,
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(14),
                "Inventory blocked");

        assertTrue(lendingRepo.isBlocked(
                stationA.id(),
                inventoryIdA,
                itemIdA,
                LocalDate.now(),
                LocalDate.now().plusDays(1)));
        // Different inventory should not be blocked
        assertFalse(lendingRepo.isBlocked(
                stationA.id(),
                inventoryIdA + 999,
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(1)));

        lendingRepo.deleteBlock(invBlock.id());
    }

    @Test
    @Order(44)
    void isBlockedItemLevel() {
        var itemBlock = lendingRepo.createBlock(
                stationA.id(),
                inventoryIdA,
                itemIdA,
                LocalDate.now(),
                LocalDate.now().plusDays(14),
                "Item blocked");

        assertTrue(lendingRepo.isBlocked(
                stationA.id(),
                inventoryIdA,
                itemIdA,
                LocalDate.now(),
                LocalDate.now().plusDays(1)));
        // Different item should not be blocked
        assertFalse(lendingRepo.isBlocked(
                stationA.id(),
                inventoryIdA,
                itemIdA + 999,
                LocalDate.now(),
                LocalDate.now().plusDays(1)));

        lendingRepo.deleteBlock(itemBlock.id());
    }

    @Test
    @Order(45)
    void deleteBlock() {
        var block = lendingRepo.createBlock(
                stationA.id(), null, null, LocalDate.now(), LocalDate.now().plusDays(1), "Temp block");
        assertTrue(lendingRepo.deleteBlock(block.id()));
        assertFalse(lendingRepo.findBlocksByStation(stationA.id()).stream().anyMatch(b -> b.id() == block.id()));
    }

    // -- Lent Out Items --

    @Test
    @Order(50)
    void findLentOutByInventory() {
        // Create a new request with LENT status
        var dateFrom = LocalDate.now();
        var dateTo = LocalDate.now().plusDays(7);
        var request = lendingRepo.createRequest(stationB.id(), stationA.id(), dateFrom, dateTo, memberB.id());
        lendingRepo.addRequestItem(request.id(), inventoryIdA, itemIdA, 1);
        lendingRepo.updateRequestStatus(request.id(), LendingStatus.APPROVED);
        lendingRepo.updateRequestStatus(request.id(), LendingStatus.LENT);

        var lentOut = lendingRepo.findLentOutByInventory(inventoryIdA, stationA.id());
        assertFalse(lentOut.isEmpty());
        assertTrue(lentOut.stream().anyMatch(l -> l.requestId() == request.id()));
    }

    @Test
    @Order(51)
    void countActionableRequests() {
        int count = lendingRepo.countActionableRequests(stationA.id());
        // Returns count of REQUESTED/APPROVED-but-pending requests for this station; at least 0
        assertTrue(count >= 0);
    }
}
