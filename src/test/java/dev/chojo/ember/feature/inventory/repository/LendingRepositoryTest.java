/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.entity.LendingRequestItem;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

        var inv = inventoryRepo.create(stationA.id(), "LendRepoInventory", InventoryType.INTERNAL, false);
        inventoryIdA = inv.id();
        var item = inventoryRepo.createItem(inventoryIdA, "LEND-001", "Lend Item", null, null);
        itemIdA = item.id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(stationA.id());
        stationRepo.delete(stationB.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void createRequest() {
        var dateFrom = LocalDate.now();
        var dateTo = LocalDate.now().plusDays(7);
        var request = lendingRepo.createRequest(
                stationB.uid(), stationA.uid(), dateFrom, dateTo, memberB.id(), null, null, "");
        assertNotNull(request);
        assertTrue(request.id() > 0);
        assertEquals(stationB.uid(), request.requestingStationUid());
        assertEquals(stationA.uid(), request.owningStationUid());
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
        var fromB = lendingRepo.findRequestsByStation(stationB.uid());
        assertTrue(fromB.stream().anyMatch(r -> r.id() == requestId));

        var fromA = lendingRepo.findRequestsByStation(stationA.uid());
        assertTrue(fromA.stream().anyMatch(r -> r.id() == requestId));
    }

    @Test
    @Order(10)
    void addRequestItem() {
        var item = lendingRepo.addRequestItem(requestId, inventoryIdA, itemIdA, null, 1, null);
        assertNotNull(item);
        assertTrue(item.id() > 0);
        assertEquals(requestId, item.requestId());
        assertEquals(inventoryIdA, item.inventoryId());
        assertEquals(itemIdA, item.itemId());
        assertEquals(1, item.quantity());
        assertTrue(lendingRepo.findAssignedItems(item.id()).isEmpty());
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
        assertEquals(List.of(itemIdA), lendingRepo.findAssignedItems(requestItemId));
    }

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

    @Test
    @Order(30)
    void createMessage() {
        var msg = lendingRepo.createMessage(requestId, stationA.uid(), memberA.id(), "Hello from A", false);
        assertNotNull(msg);
        assertTrue(msg.id() > 0);
        assertEquals(requestId, msg.requestId());
        assertEquals(stationA.uid(), msg.senderStationUid());
        assertEquals(memberA.id(), msg.senderMemberId());
        assertEquals("Hello from A", msg.message());
        assertFalse(msg.isSystem());
    }

    @Test
    @Order(31)
    void createSystemMessage() {
        var msg = lendingRepo.createMessage(requestId, stationB.uid(), null, "System event", true);
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
        lendingRepo.createMessage(requestId, stationA.uid(), memberA.id(), "Another from A", false);

        var localA = lendingRepo.findLocalMessages(requestId, stationA.uid());
        assertTrue(localA.size() >= 2);
        assertTrue(localA.stream().allMatch(m -> stationA.uid().equals(m.senderStationUid())));

        var localB = lendingRepo.findLocalMessages(requestId, stationB.uid());
        assertFalse(localB.isEmpty());
        assertTrue(localB.stream().allMatch(m -> stationB.uid().equals(m.senderStationUid())));
    }

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
        lendingRepo.deleteBlock(blockId, stationA.id());
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
        assertFalse(lendingRepo.isBlocked(
                stationA.id(),
                inventoryIdA + 999,
                null,
                LocalDate.now(),
                LocalDate.now().plusDays(1)));

        lendingRepo.deleteBlock(invBlock.id(), stationA.id());
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
        assertFalse(lendingRepo.isBlocked(
                stationA.id(),
                inventoryIdA,
                itemIdA + 999,
                LocalDate.now(),
                LocalDate.now().plusDays(1)));

        lendingRepo.deleteBlock(itemBlock.id(), stationA.id());
    }

    @Test
    @Order(45)
    void deleteBlock() {
        var block = lendingRepo.createBlock(
                stationA.id(), null, null, LocalDate.now(), LocalDate.now().plusDays(1), "Temp block");
        assertTrue(lendingRepo.deleteBlock(block.id(), stationA.id()));
        assertFalse(lendingRepo.findBlocksByStation(stationA.id()).stream().anyMatch(b -> b.id() == block.id()));
    }

    /**
     * The station is part of the delete, so a block another station owns survives an attempt to
     * remove it: a deleted block would silently reopen that station's inventory to lending.
     */
    @Test
    @Order(46)
    void deleteBlockOfAnotherStationDoesNothing() {
        var block = lendingRepo.createBlock(
                stationA.id(), null, null, LocalDate.now(), LocalDate.now().plusDays(1), "Foreign block");

        assertFalse(lendingRepo.deleteBlock(block.id(), stationB.id()));
        assertTrue(lendingRepo.findBlocksByStation(stationA.id()).stream().anyMatch(b -> b.id() == block.id()));

        assertTrue(lendingRepo.deleteBlock(block.id(), stationA.id()));
    }

    @Test
    @Order(50)
    void findLentOutByInventory() {
        var dateFrom = LocalDate.now();
        var dateTo = LocalDate.now().plusDays(7);
        var request = lendingRepo.createRequest(
                stationB.uid(), stationA.uid(), dateFrom, dateTo, memberB.id(), null, null, "");
        lendingRepo.addRequestItem(request.id(), inventoryIdA, itemIdA, null, 1, null);
        lendingRepo.updateRequestStatus(request.id(), LendingStatus.APPROVED);
        lendingRepo.updateRequestStatus(request.id(), LendingStatus.LENT);

        var lentOut = lendingRepo.findLentOutByInventory(inventoryIdA, stationA.uid());
        assertFalse(lentOut.isEmpty());
        assertTrue(lentOut.stream().anyMatch(l -> l.requestId() == request.id()));
    }

    @Test
    @Order(51)
    void countActionableRequests() {
        int count = lendingRepo.countActionableRequests(stationA.uid());
        assertTrue(count >= 0);
    }

    /**
     * A copy of a request from a station on another instance: found by the identity both copies
     * carry, its lines named in the lending station's words, and gone again when it could not be
     * delivered.
     */
    @Test
    @Order(60)
    void aRequestCopyIsFoundByItsSharedIdentity() {
        UUID uid = UUID.randomUUID();
        UUID elsewhere = UUID.randomUUID();
        var copy = lendingRepo.createRequest(
                uid, stationA.uid(), elsewhere, LocalDate.now(), null, null, null, null, "Übung");
        assertEquals(uid, copy.uid());
        assertNull(copy.createdBy());
        assertEquals(copy.id(), lendingRepo.findRequestByUid(uid).orElseThrow().id());
        assertTrue(lendingRepo.findRequestByUid(UUID.randomUUID()).isEmpty());

        lendingRepo.addRequestItem(copy.id(), null, null, null, 2, null);
        lendingRepo.addRequestItem(copy.id(), null, null, null, 1, null);
        lendingRepo.labelItems(copy.id(), List.of("Funkgeräte", "Zelt", "ignored"));
        assertEquals(
                List.of("Funkgeräte", "Zelt"),
                lendingRepo.findItemsByRequest(copy.id()).stream()
                        .map(LendingRequestItem::label)
                        .toList());

        assertTrue(lendingRepo.deleteRequest(uid));
        assertFalse(lendingRepo.deleteRequest(uid));
        assertTrue(lendingRepo.findRequestByUid(uid).isEmpty());
    }

    /** A line is labelled with what it names when it is asked for. */
    @Test
    @Order(61)
    void aLineIsLabelledWhenItIsAskedFor() {
        var request = lendingRepo.createRequest(
                UUID.randomUUID(), stationB.uid(), stationA.uid(), LocalDate.now(), null, null, null, null, "");

        assertEquals(
                "Lend Item",
                lendingRepo
                        .addRequestItem(request.id(), inventoryIdA, itemIdA, null, 1, null)
                        .label());
        assertEquals(
                "LendRepoInventory",
                lendingRepo
                        .addRequestItem(request.id(), inventoryIdA, null, null, 1, null)
                        .label());
        assertEquals(
                "",
                lendingRepo
                        .addRequestItem(request.id(), null, null, null, 1, null)
                        .label());

        lendingRepo.deleteRequest(request.uid());
    }

    /**
     * A copy that arrived with a moved station joins the copy its partner keeps: lines by position,
     * the pieces and messages over, the empty fields filled, extra lines moved, the arrived copy gone.
     */
    @Test
    @Order(62)
    void anArrivedCopyJoinsTheCopyThePartnerKeeps() {
        var kept = lendingRepo.createRequest(
                UUID.randomUUID(), stationB.uid(), stationA.uid(), LocalDate.now(), null, memberB.id(), null, null, "");
        int keptLine =
                lendingRepo.addRequestItem(kept.id(), null, null, null, 1, null).id();
        lendingRepo.labelItems(kept.id(), List.of("Lend Item"));
        var arrived = lendingRepo.createRequest(
                UUID.randomUUID(), stationB.uid(), stationA.uid(), LocalDate.now(), null, null, null, null, "Übung");
        int arrivedLine = lendingRepo
                .addRequestItem(arrived.id(), inventoryIdA, itemIdA, null, 1, null)
                .id();
        int extraLine = lendingRepo
                .addRequestItem(arrived.id(), inventoryIdA, null, null, 2, null)
                .id();
        lendingRepo.assignItem(arrivedLine, itemIdA);
        lendingRepo.createMessage(arrived.id(), stationA.uid(), null, "Liegt bereit", false);
        lendingRepo.updateRequestStatus(arrived.id(), LendingStatus.LENT);

        lendingRepo.joinLine(arrivedLine, keptLine);
        lendingRepo.moveLine(extraLine, kept.id());
        lendingRepo.joinRequest(arrived.id(), kept.id());
        lendingRepo.nameStationsHere(kept.id());

        var lines = lendingRepo.findItemsByRequest(kept.id());
        assertEquals(
                List.of(keptLine, extraLine),
                lines.stream().map(LendingRequestItem::id).toList());
        assertEquals(itemIdA, lines.getFirst().itemId());
        assertEquals("Lend Item", lines.getFirst().label());
        assertEquals(List.of(itemIdA), lendingRepo.findAssignedItems(keptLine));
        var joined = lendingRepo.findRequestById(kept.id()).orElseThrow();
        assertEquals(memberB.id(), joined.createdBy());
        assertEquals("Übung", joined.occasion());
        assertEquals(LendingStatus.LENT, joined.status(), "the state changed later wins");
        assertEquals(1, lendingRepo.findMessagesByRequest(kept.id()).size());
        assertTrue(lendingRepo.findRequestById(arrived.id()).isEmpty());

        lendingRepo.deleteRequest(kept.uid());
    }
}
