/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.CheckResult;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryCheck;
import dev.chojo.ember.feature.inventory.entity.InventoryCheckItem;
import dev.chojo.ember.feature.inventory.entity.InventoryCheckScope;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InventoryCheckRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account1;
    private static Account account2;
    private static StationMember member1;
    private static StationMember member2;
    private static Inventory inventory;
    private static InventoryItem item;
    private static int checkId;

    /** Both members get the user permission, without which the next-unchecked-member lookup skips them. */
    @BeforeAll
    static void setup() {
        station = stationRepo.create("Check Station");
        account1 = accountRepo.create("check1@test.com", "Check", "Member");
        account2 = accountRepo.create("check2@test.com", "Check", "Checker");
        member1 = stationMemberRepo.create(station.id(), account1.id());
        member2 = stationMemberRepo.create(station.id(), account2.id());
        stationMemberRepo.findPermissionByName(StationPermission.USER).ifPresent(r -> {
            stationMemberRepo.grantPermission(member1.id(), r.id());
            stationMemberRepo.grantPermission(member2.id(), r.id());
        });
        inventory = inventoryRepo.create(station.id(), "Check Inv", InventoryType.EXTERNAL, false);
        item = inventoryRepo.createItem(inventory.id(), "C-001", "Check Item", null, null);
        itemCustodyService.assignToMember(item.id(), member1.id(), "");
    }

    @AfterAll
    static void cleanup() {
        inventoryRepo.delete(inventory.id());
        stationRepo.delete(station.id());
        accountRepo.delete(account1.id());
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(1)
    void acquireLock() {
        var lock = inventoryCheckRepo.acquireLock(station.id(), member1.id(), member2.id());
        assertTrue(lock.isPresent());
        assertEquals(member1.id(), lock.get().memberId());
        assertEquals(member2.id(), lock.get().lockedBy());
    }

    @Test
    @Order(2)
    void acquireLockConflict() {
        var lock = inventoryCheckRepo.acquireLock(station.id(), member1.id(), member1.id());
        assertTrue(lock.isEmpty());
    }

    @Test
    @Order(3)
    void findLock() {
        assertTrue(inventoryCheckRepo.findLock(member1.id()).isPresent());
        assertTrue(inventoryCheckRepo.findLock(member2.id()).isEmpty());
    }

    @Test
    @Order(4)
    void releaseLock() {
        assertTrue(inventoryCheckRepo.releaseLock(member1.id()));
        assertTrue(inventoryCheckRepo.findLock(member1.id()).isEmpty());
    }

    @Test
    @Order(5)
    void releaseLockByLocker() {
        inventoryCheckRepo.acquireLock(station.id(), member1.id(), member2.id());
        assertTrue(inventoryCheckRepo.releaseLockByLocker(member2.id()));
        assertTrue(inventoryCheckRepo.findLock(member1.id()).isEmpty());
    }

    @Test
    @Order(10)
    void createCheck() {
        InventoryCheck check = inventoryCheckRepo.createCheck(station.id(), member1.id(), member2.id());
        assertNotNull(check);
        assertEquals(member1.id(), check.memberId());
        assertEquals(member2.id(), check.checkedBy());
        assertNotNull(check.checkedAt());
        checkId = check.id();
    }

    @Test
    @Order(11)
    void latestCheckForMember() {
        var latest = inventoryCheckRepo.latestCheckForMember(member1.id());
        assertTrue(latest.isPresent());
        assertEquals(checkId, latest.get().id());
    }

    @Test
    @Order(12)
    void latestCheckForMemberNotFound() {
        assertTrue(inventoryCheckRepo.latestCheckForMember(member2.id()).isEmpty());
    }

    @Test
    @Order(13)
    void checkOverview() {
        var overview = inventoryCheckRepo.checkOverview(station.id());
        assertFalse(overview.isEmpty());
        var m1 = overview.stream()
                .filter(s -> s.memberId() == member1.id())
                .findFirst()
                .orElseThrow();
        assertNotNull(m1.lastCheckedAt());
        assertEquals("Check", m1.checkerFirstName());
    }

    @Test
    @Order(20)
    void createCheckItem() {
        InventoryCheckItem checkItem =
                inventoryCheckRepo.createCheckItem(checkId, item.id(), item.inventoryId(), CheckResult.CONFIRMED, "");
        assertNotNull(checkItem);
        assertEquals(checkId, checkItem.checkId());
        assertEquals(item.id(), checkItem.itemId());
        assertEquals(CheckResult.CONFIRMED, checkItem.result());
    }

    @Test
    @Order(21)
    void findCheckItems() {
        var items = inventoryCheckRepo.findCheckItems(checkId);
        assertEquals(1, items.size());
        assertEquals(CheckResult.CONFIRMED, items.getFirst().result());
    }

    @Test
    @Order(30)
    void nextUncheckedMember() {
        var next = inventoryCheckRepo.nextUncheckedMember(station.id(), member1.id(), false);
        assertTrue(next.isPresent());
        assertEquals(member2.id(), next.get());
    }

    @Test
    @Order(31)
    void nextUncheckedMemberSkipsLocked() {
        inventoryCheckRepo.acquireLock(station.id(), member2.id(), member1.id());
        var next = inventoryCheckRepo.nextUncheckedMember(station.id(), member1.id(), false);
        assertTrue(next.isEmpty());
        inventoryCheckRepo.releaseLock(member2.id());
    }

    @Test
    @Order(40)
    void containerCheckCrud() {
        var container = containerRepo.create(station.id(), null, null, "CheckRoom", null, "", null);
        InventoryCheck created =
                inventoryCheckRepo.createContainerCheck(station.id(), container.id(), member2.id(), true);
        assertEquals(InventoryCheckScope.CONTAINER, created.scope());
        assertEquals(Integer.valueOf(container.id()), created.containerId());
        assertTrue(created.deep());
        assertEquals(member2.id(), created.checkedBy());

        var latest = inventoryCheckRepo.latestCheckForContainer(container.id());
        assertTrue(latest.isPresent());
        assertEquals(created.id(), latest.get().id());
        assertTrue(inventoryCheckRepo.latestCheckForContainer(987654321).isEmpty());

        containerRepo.delete(container.id());
    }

    /** Builds on the earlier check of member1 by member2, which recorded one item. */
    @Test
    @Order(50)
    void latestCheckDetail() {
        var detail = inventoryCheckRepo.latestCheckDetail(member1.id());
        assertTrue(detail.isPresent());
        assertEquals(checkId, detail.get().check().id());
        assertEquals("Check", detail.get().checkerFirstName());
        assertEquals("Checker", detail.get().checkerLastName());
        assertEquals(1, detail.get().items().size());
        assertEquals(CheckResult.CONFIRMED, detail.get().items().getFirst().result());
    }

    @Test
    @Order(51)
    void latestCheckDetailMissing() {
        assertTrue(inventoryCheckRepo.latestCheckDetail(member2.id()).isEmpty());
    }

    @Test
    @Order(60)
    void latestCheckPerItemEmpty() {
        assertTrue(inventoryCheckRepo.latestCheckPerItem(List.of()).isEmpty());
    }

    @Test
    @Order(61)
    void latestCheckPerItem() {
        var results = inventoryCheckRepo.latestCheckPerItem(List.of(item.id()));
        assertEquals(1, results.size());
        assertEquals(item.id(), results.getFirst().itemId());
        assertEquals(CheckResult.CONFIRMED, results.getFirst().result());
        assertEquals("Check Checker", results.getFirst().checkerName());
    }

    /**
     * The item already carries member1's earlier member-scope check; a container-scope check of the
     * same item makes the history show the container name and both scopes, newest first.
     */
    @Test
    @Order(70)
    void findCheckHistoryForItem() {
        var container = containerRepo.create(station.id(), null, null, "HistoryRoom", null, "", null);
        var containerCheck = inventoryCheckRepo.createContainerCheck(station.id(), container.id(), member2.id(), false);
        inventoryCheckRepo.createCheckItem(
                containerCheck.id(), item.id(), item.inventoryId(), CheckResult.NOT_IN_POSSESSION, "left at home");

        var history = inventoryCheckRepo.findCheckHistoryForItem(item.id());
        assertEquals(2, history.size());
        assertEquals(containerCheck.id(), history.getFirst().checkId());
        assertEquals(CheckResult.NOT_IN_POSSESSION, history.getFirst().result());
        assertEquals(InventoryCheckScope.CONTAINER, history.getFirst().scope());
        assertEquals("HistoryRoom", history.get(0).containerName());
        assertEquals("left at home", history.get(0).note());
        assertEquals(InventoryCheckScope.MEMBER, history.get(1).scope());
        assertNull(history.get(1).containerName());

        containerRepo.delete(container.id());
    }

    @Test
    @Order(80)
    void releaseExpiredLocks() {
        inventoryCheckRepo.acquireLock(station.id(), member1.id(), member2.id());
        inventoryCheckRepo.releaseExpiredLocks(60);
        assertTrue(inventoryCheckRepo.findLock(member1.id()).isPresent());
        inventoryCheckRepo.releaseExpiredLocks(0);
        assertTrue(inventoryCheckRepo.findLock(member1.id()).isEmpty());
    }

    @Test
    @Order(81)
    void nextUncheckedMemberTeamOnly() {
        assertTrue(inventoryCheckRepo
                .nextUncheckedMember(station.id(), member1.id(), true)
                .isEmpty());

        stationMemberRepo.setUserType(member2.id(), StationUserType.TEAM);
        var picked = inventoryCheckRepo.nextUncheckedMember(station.id(), member1.id(), true);
        assertTrue(picked.isPresent());
        assertEquals(member2.id(), picked.get());
        stationMemberRepo.setUserType(member2.id(), StationUserType.MEMBER);
    }
}
