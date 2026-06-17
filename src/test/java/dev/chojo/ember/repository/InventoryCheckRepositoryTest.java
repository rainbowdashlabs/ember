/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.CheckResult;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryCheck;
import dev.chojo.ember.feature.inventory.entity.InventoryCheckItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Check Station");
        account1 = accountRepo.create("check1@test.com", "Check", "Member");
        account2 = accountRepo.create("check2@test.com", "Check", "Checker");
        member1 = stationMemberRepo.create(station.id(), account1.id());
        member2 = stationMemberRepo.create(station.id(), account2.id());
        // Assign MEMBER permission so nextUncheckedMember filter works
        stationMemberRepo.findPermissionByName(StationPermission.USER).ifPresent(r -> {
            stationMemberRepo.grantPermission(member1.id(), r.id());
            stationMemberRepo.grantPermission(member2.id(), r.id());
        });
        inventory = inventoryRepo.create(station.id(), "Check Inv", InventoryType.EXTERNAL, false);
        item = inventoryRepo.createItem(inventory.id(), "C-001", "Check Item", null, null);
        inventoryRepo.assignItem(item.id(), member1.id());
    }

    @AfterAll
    static void cleanup() {
        inventoryRepo.delete(inventory.id());
        stationRepo.delete(station.id());
        accountRepo.delete(account1.id());
        accountRepo.delete(account2.id());
    }

    // -- Locks --

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

    // -- Checks --

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
        // member1 was checked, member2 was not
        var m1 = overview.stream()
                .filter(s -> s.memberId() == member1.id())
                .findFirst()
                .orElseThrow();
        assertNotNull(m1.lastCheckedAt());
        assertEquals("Check", m1.checkerFirstName());
    }

    // -- Check Items --

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

    // -- Navigation --

    @Test
    @Order(30)
    void nextUncheckedMember() {
        // member1 was checked, member2 was not, so member2 should be next
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
}
