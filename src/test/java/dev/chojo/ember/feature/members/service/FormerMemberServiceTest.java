/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FormerMemberServiceTest extends RepositoryTestBase {
    private static FormerMemberService service;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        service = new FormerMemberService(
                stationMemberRepo,
                accountRepo,
                inventoryRepo,
                exchangeRepo,
                memberGroupRepo,
                userTagRepo,
                attendanceRepo,
                profileFieldRepo,
                mock(MemberDocumentService.class));
        station = stationRepo.create("FormerStation");
        account = accountRepo.create("former@test.com", "Former", "Member");
        member = stationMemberRepo.create(station.id(), account.id());

        // Assign MEMBER + LOGIN roles
        stationMemberRepo
                .findPermissionByName(StationPermission.USER)
                .ifPresent(r -> stationMemberRepo.grantPermission(member.id(), r.id()));
        stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .ifPresent(r -> stationMemberRepo.grantPermission(member.id(), r.id()));
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void canMarkFormerReturnsNullForValidMember() {
        assertNull(service.canMarkFormer(member.id()));
    }

    @Test
    @Order(2)
    void canMarkFormerRejectsNonExistent() {
        assertNotNull(service.canMarkFormer(999999));
    }

    @Test
    @Order(10)
    void markFormerSetsFlag() {
        service.markFormer(member.id());
        var updated = stationMemberRepo.findById(member.id()).orElseThrow();
        assertTrue(updated.former());
    }

    @Test
    @Order(11)
    void markedFormerHasNoRoles() {
        var roles = stationMemberRepo.findPermissions(member.id());
        assertTrue(roles.isEmpty());
    }

    @Test
    @Order(12)
    void canMarkFormerRejectsAlreadyFormer() {
        assertNotNull(service.canMarkFormer(member.id()));
    }

    @Test
    @Order(13)
    void formerMemberPreservesDisplayName() {
        var updated = stationMemberRepo.findById(member.id()).orElseThrow();
        assertEquals("Former Member", updated.displayName());
    }

    @Test
    @Order(20)
    void reactivateRestoresRoles() {
        service.reactivate(member.id());
        var updated = stationMemberRepo.findById(member.id()).orElseThrow();
        assertFalse(updated.former());
        var roles = stationMemberRepo.findPermissions(member.id());
        assertTrue(roles.stream().anyMatch(r -> r.permission() == StationPermission.LOGIN));
        assertTrue(roles.stream().anyMatch(r -> r.permission() == StationPermission.USER));
    }

    @Test
    @Order(21)
    void reactivateRejectsNonFormer() {
        assertThrows(IllegalStateException.class, () -> service.reactivate(member.id()));
    }

    @Test
    @Order(22)
    void reactivateNonExistentMember() {
        assertThrows(IllegalStateException.class, () -> service.reactivate(999999));
    }

    @Test
    @Order(30)
    void canMarkFormerRejectsWithInventoryAssigned() {
        // Create a new member and assign an inventory item to them
        var acc = accountRepo.create("former-inv@test.com", "Inv", "Member");
        var mem = stationMemberRepo.create(station.id(), acc.id());
        stationMemberRepo
                .findPermissionByName(StationPermission.USER)
                .ifPresent(r -> stationMemberRepo.grantPermission(mem.id(), r.id()));

        var inv = inventoryRepo.create(station.id(), "FormerTestInv", InventoryType.INTERNAL, false);
        var item = inventoryRepo.createItem(inv.id(), "FT-001", "Former Test Item", null, null);
        inventoryRepo.assignItem(item.id(), mem.id());

        String result = service.canMarkFormer(mem.id());
        assertNotNull(result);
        assertTrue(result.contains("inventory"));

        // Cleanup
        inventoryRepo.delete(inv.id());
        stationMemberRepo.delete(mem.id());
        accountRepo.delete(acc.id());
    }

    @Test
    @Order(31)
    void canMarkFormerRejectsWithForbiddenRole() {
        var acc = accountRepo.create("former-mgr@test.com", "Mgr", "Member");
        var mem = stationMemberRepo.create(station.id(), acc.id());
        stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .ifPresent(r -> stationMemberRepo.grantPermission(mem.id(), r.id()));

        String result = service.canMarkFormer(mem.id());
        assertNotNull(result);

        stationMemberRepo.delete(mem.id());
        accountRepo.delete(acc.id());
    }

    @Test
    @Order(32)
    void markFormerThrowsWhenCannotMarkFormer() {
        // member is now not former (was reactivated in order 20), so they are valid.
        // Force a failure by using a non-existent member ID
        assertThrows(IllegalStateException.class, () -> service.markFormer(999999));
    }
}
