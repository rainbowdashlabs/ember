/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.CheckItemRequest;
import dev.chojo.ember.feature.inventory.entity.CheckResult;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.service.InventoryCheckService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.ConflictResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

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

    @Test
    @Order(40)
    void completeCheck() {
        // Start a check first
        service.startCheck(station.id(), target.id(), checker.id());

        var results = List.of(new CheckItemRequest(itemId, inventoryId, CheckResult.CONFIRMED, "OK"));
        var check = service.completeCheck(station.id(), target.id(), checker.id(), results);
        assertNotNull(check);
        assertEquals(target.id(), check.memberId());

        // Lock should be released after complete
        var overview = service.getCheckOverview(station.id());
        var unlocked = overview.stream()
                .filter(s -> s.memberId() == target.id())
                .findFirst()
                .orElseThrow();
        assertFalse(unlocked.locked());
    }

    @Test
    @Order(41)
    void lastCheckDetail() {
        var detail = service.lastCheckDetail(target.id());
        assertTrue(detail.isPresent());
        assertNotNull(detail.get().check());
        assertFalse(detail.get().items().isEmpty());
    }

    @Test
    @Order(42)
    void lastCheckDetailForUncheckedMember() {
        assertTrue(service.lastCheckDetail(checker.id()).isEmpty());
    }

    @Test
    @Order(50)
    void completeCheckWithLostItem() {
        service.startCheck(station.id(), target.id(), checker.id());

        var results = List.of(new CheckItemRequest(itemId, inventoryId, CheckResult.LOST, "Lost it"));
        service.completeCheck(station.id(), target.id(), checker.id(), results);

        // Item should be marked as lost
        var item = inventoryRepo.findItemById(itemId);
        assertTrue(item.isPresent());
        assertNotNull(item.get().lostAt());
        // Restore
        inventoryRepo.markFound(itemId);
    }

    @Test
    @Order(55)
    void completeCheckWithMissingItemId() {
        service.startCheck(station.id(), target.id(), checker.id());

        // Check result with null itemId and null inventoryId
        var results = List.of(new CheckItemRequest(null, inventoryId, CheckResult.NOT_IN_POSSESSION, ""));
        var check = service.completeCheck(station.id(), target.id(), checker.id(), results);
        assertNotNull(check);
    }

    @Test
    @Order(60)
    void getRequiredItems() {
        // Create a requirement for MEMBER role
        var memberRole = stationMemberRepo.findRoleByName(Roles.MEMBER).orElseThrow();
        var req = inventoryRepo.createRequirement(inventoryId, memberRole.id(), 0, 2);

        var required = service.getRequiredItems(station.id(), target.id());
        assertTrue(required.stream().anyMatch(r -> r.inventoryId() == inventoryId && r.requiredQuantity() == 2));

        inventoryRepo.deleteRequirement(req.id());
    }

    @Test
    @Order(61)
    void getRequiredItemsEmpty() {
        // No requirements — should be empty
        var required = service.getRequiredItems(station.id(), target.id());
        assertTrue(required.isEmpty());
    }

    @Test
    @Order(70)
    void startCheckConflict() {
        // checker locks target
        service.startCheck(station.id(), target.id(), checker.id());

        // Create a third member to act as a different checker
        var otherAccount = accountRepo.create("other-checker-svc@test.com", "Other", "Checker");
        var otherMember = stationMemberRepo.create(station.id(), otherAccount.id());

        // Different checker should get ConflictResponse
        assertThrows(ConflictResponse.class, () -> service.startCheck(station.id(), target.id(), otherMember.id()));

        service.cancelCheck(target.id(), checker.id());
        stationMemberRepo.delete(otherMember.id());
        accountRepo.delete(otherAccount.id());
    }

    @Test
    @Order(71)
    void cancelCheckWrongLockerDoesNothing() {
        service.startCheck(station.id(), target.id(), checker.id());
        // Try to cancel with wrong locker — should not release
        service.cancelCheck(target.id(), target.id());
        var overview = service.getCheckOverview(station.id());
        var locked = overview.stream()
                .filter(s -> s.memberId() == target.id())
                .findFirst()
                .orElseThrow();
        assertTrue(locked.locked());
        service.cancelCheck(target.id(), checker.id());
    }

    @Test
    @Order(72)
    void nextMemberTeamOnly() {
        var next = service.nextMember(station.id(), checker.id(), true);
        // Result may be empty or present depending on team composition — just verify no exception
        assertNotNull(next);
    }

    @Test
    @Order(73)
    void lastCheckDetailWithNullItemId() {
        // Create a check with a null itemId (no specific item assigned)
        service.startCheck(station.id(), target.id(), checker.id());
        // Use a result with null itemId and non-null inventoryId — tests the else branch in lastCheckDetail
        var results = List.of(new CheckItemRequest(null, inventoryId, CheckResult.NOT_IN_POSSESSION, "missing"));
        service.completeCheck(station.id(), target.id(), checker.id(), results);

        var detail = service.lastCheckDetail(target.id());
        assertTrue(detail.isPresent());
        // The item with null itemId should have null itemName
        assertTrue(detail.get().items().stream().anyMatch(i -> i.itemId() == null));
    }

    @Test
    @Order(74)
    void cancelCheckWhenNoLockDoesNothing() {
        // Make sure target is not locked
        service.cancelCheck(target.id(), checker.id()); // Ensure unlocked first
        // Cancel when no lock exists — should not throw
        assertDoesNotThrow(() -> service.cancelCheck(target.id(), checker.id()));
    }

    @Test
    @Order(75)
    void getRequiredItemsWithGroupRequirement() {
        // Create a group and add member to it, then create a requirement for that group
        var group = memberGroupRepo.create(station.id(), "CheckGroup");
        memberGroupRepo.addMember(group.id(), target.id());

        var req = inventoryRepo.createRequirement(inventoryId, 0, group.id(), 1);
        var required = service.getRequiredItems(station.id(), target.id());
        assertTrue(required.stream().anyMatch(r -> r.inventoryId() == inventoryId));

        inventoryRepo.deleteRequirement(req.id());
        memberGroupRepo.removeMember(group.id(), target.id());
        memberGroupRepo.delete(group.id());
    }
}
