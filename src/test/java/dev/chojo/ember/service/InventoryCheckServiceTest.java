/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.service.InventoryCheckService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InventoryCheckServiceTest extends RepositoryTestBase {
    private static InventoryCheckService service;
    private static Station station;
    private static Account checkerAccount;
    private static Account targetAccount;
    private static StationMember checker;
    private static StationMember target;
    private static int inventoryId;
    private static int itemId;

    @BeforeAll
    static void setup() {
        service = new InventoryCheckService(
                inventoryCheckRepo, inventoryRepo, stationMemberRepo, memberGroupRepo, accountRepo);
        station = stationRepo.create("CheckSvcStation");
        checkerAccount = accountRepo.create("checker-svc@test.com", "Check", "Er");
        targetAccount = accountRepo.create("target-svc@test.com", "Target", "Member");
        checker = stationMemberRepo.create(station.id(), checkerAccount.id());
        target = stationMemberRepo.create(station.id(), targetAccount.id());

        stationMemberRepo.findRoleByName(Roles.MEMBER).ifPresent(r -> {
            stationMemberRepo.addRole(checker.id(), r.id());
            stationMemberRepo.addRole(target.id(), r.id());
        });

        var inv = inventoryRepo.create(station.id(), "CheckSvcInv", InventoryType.EXTERNAL, false);
        inventoryId = inv.id();
        var item = inventoryRepo.createItem(inv.id(), "CS-001", "Check Item", null, "{}");
        itemId = item.id();
        inventoryRepo.assignItem(item.id(), target.id());
    }

    @AfterAll
    static void cleanup() {
        inventoryRepo.delete(inventoryId);
        stationRepo.delete(station.id());
        accountRepo.delete(checkerAccount.id());
        accountRepo.delete(targetAccount.id());
    }

    @Test
    @Order(1)
    void getCheckOverview() {
        var overview = service.getCheckOverview(station.id());
        assertFalse(overview.isEmpty());
        assertTrue(overview.stream().anyMatch(s -> s.memberId() == target.id()));
    }

    @Test
    @Order(2)
    void overviewIncludesRoles() {
        var summary = service.getCheckOverview(station.id()).stream()
                .filter(s -> s.memberId() == target.id())
                .findFirst()
                .orElseThrow();
        assertFalse(summary.roles().isEmpty());
    }

    @Test
    @Order(10)
    void startCheckLocksAndReturnsState() {
        var state = service.startCheck(station.id(), target.id(), checker.id());
        assertNotNull(state);
        assertFalse(state.assigned().isEmpty());

        // Verify lock
        var overview = service.getCheckOverview(station.id());
        var locked = overview.stream()
                .filter(s -> s.memberId() == target.id())
                .findFirst()
                .orElseThrow();
        assertTrue(locked.locked());
        assertEquals(checker.id(), locked.lockedBy());
    }

    @Test
    @Order(11)
    void startCheckOnLockedMemberReturnsStateForSameChecker() {
        // Same checker can resume
        var state = service.startCheck(station.id(), target.id(), checker.id());
        assertNotNull(state);
    }

    @Test
    @Order(20)
    void cancelCheckReleasesLock() {
        service.cancelCheck(target.id(), checker.id());
        var overview = service.getCheckOverview(station.id());
        var unlocked = overview.stream()
                .filter(s -> s.memberId() == target.id())
                .findFirst()
                .orElseThrow();
        assertFalse(unlocked.locked());
    }

    @Test
    @Order(30)
    void nextMemberFindsUnchecked() {
        var next = service.nextMember(station.id(), checker.id(), false);
        assertTrue(next.isPresent());
    }
}
