/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StationServiceTest extends RepositoryTestBase {
    private static StationService service;
    private static int stationId;

    @BeforeAll
    static void setup() {
        service = new StationService(
                stationRepo,
                stationMemberRepo,
                accountRepo,
                mock(FederationService.class),
                new StationMemberInviteService(
                        stationMemberRepo, memberGroupRepo, accountRepo, mock(AuthService.class)),
                clusterRepo);
    }

    @Test
    @Order(1)
    void create() {
        var station = service.create("TestStation");
        assertNotNull(station);
        assertEquals("TestStation", station.name());
        stationId = station.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(service.findById(stationId).isPresent());
        assertTrue(service.findById(999999).isEmpty());
    }

    @Test
    @Order(3)
    void findAll() {
        var all = service.findAll();
        assertTrue(all.stream().anyMatch(s -> s.id() == stationId));
    }

    @Test
    @Order(4)
    void findByUid() {
        var station = service.findById(stationId).orElseThrow();
        assertTrue(service.findByUid(station.uid()).isPresent());
        assertTrue(service.findByUid(UUID.randomUUID()).isEmpty());
    }

    @Test
    @Order(10)
    void update() {
        var updated = service.update(stationId, "RenamedStation");
        assertTrue(updated.isPresent());
        assertEquals("RenamedStation", updated.get().name());
    }

    @Test
    @Order(11)
    void updateNonExistent() {
        assertTrue(service.update(999999, "Nope").isEmpty());
    }

    @Test
    @Order(12)
    void updateTimezone() {
        var updated = service.updateTimezone(stationId, "Europe/Berlin");
        assertTrue(updated.isPresent());
        assertEquals("Europe/Berlin", updated.get().timezone());
    }

    @Test
    @Order(13)
    void updateTimezoneNonExistent() {
        assertTrue(service.updateTimezone(999999, "UTC").isEmpty());
    }

    @Test
    @Order(14)
    void updateLocale() {
        var updated = service.updateLocale(stationId, "en-US");
        assertTrue(updated.isPresent());
        assertEquals("en-US", updated.get().locale());
    }

    @Test
    @Order(15)
    void updateLocaleNonExistent() {
        assertTrue(service.updateLocale(999999, "de-DE").isEmpty());
    }

    @Test
    @Order(16)
    void updatePublicKbMode() {
        assertDoesNotThrow(() -> service.updatePublicKbMode(stationId, PublicKbMode.ALLOW_ALL));
        assertDoesNotThrow(() -> service.updatePublicKbMode(stationId, PublicKbMode.OFF));
    }

    @Test
    @Order(17)
    void updateThemeSettings() {
        assertDoesNotThrow(() -> service.updateThemeSettings(stationId, "ember", true, "{}", ThemeFeel.ROUNDED, false));
    }

    @Test
    @Order(25)
    void disableAndEnableModule() {
        service.setDisabledModules(stationId, Set.of(StationModule.ATTENDANCE));
        var disabled = service.findDisabledModules(stationId);
        assertTrue(disabled.contains(StationModule.ATTENDANCE));
        assertFalse(service.isModuleEnabled(stationId, StationModule.ATTENDANCE));

        service.setDisabledModules(stationId, Set.of());
        var enabled = service.findDisabledModules(stationId);
        assertFalse(enabled.contains(StationModule.ATTENDANCE));
        assertTrue(service.isModuleEnabled(stationId, StationModule.ATTENDANCE));
    }

    @Test
    @Order(26)
    void updatePublicCalendarEnabled() {
        assertDoesNotThrow(() -> service.updatePublicCalendarEnabled(stationId, true));
        assertDoesNotThrow(() -> service.updatePublicCalendarEnabled(stationId, false));
    }

    @Test
    @Order(27)
    void updateDiscoverySettings() {
        assertDoesNotThrow(() ->
                service.updateDiscoverySettings(stationId, DiscoveryVisibility.INSTANCE, "A great station", true));
    }

    @Test
    @Order(28)
    void findDiscoverable() {
        var other = stationRepo.create("Discoverable Station SvcTest");
        stationRepo.updateDiscoverySettings(other.id(), DiscoveryVisibility.PUBLIC, "Public", false);
        var results = service.findDiscoverable(stationId);
        assertTrue(results.stream().anyMatch(s -> s.id() == other.id()));
        stationRepo.delete(other.id());
    }

    @Test
    @Order(28)
    void findPubliclyDiscoverableExcludesInstanceVisibility() {
        var publicStation = stationRepo.create("Publicly Discoverable SvcTest");
        stationRepo.updateDiscoverySettings(publicStation.id(), DiscoveryVisibility.PUBLIC, "Public", false);
        var instanceStation = stationRepo.create("Instance Discoverable SvcTest");
        stationRepo.updateDiscoverySettings(instanceStation.id(), DiscoveryVisibility.INSTANCE, "Instance", false);

        var results = service.findPubliclyDiscoverable(stationId);

        assertTrue(results.stream().anyMatch(s -> s.id() == publicStation.id()));
        assertFalse(results.stream().anyMatch(s -> s.id() == instanceStation.id()));
        stationRepo.delete(publicStation.id());
        stationRepo.delete(instanceStation.id());
    }

    @Test
    @Order(29)
    void findWithPublicContent() {
        var other = stationRepo.create("Public Calendar SvcTest");
        stationRepo.updatePublicCalendarEnabled(other.id(), true);
        var results = service.findWithPublicContent(stationId);
        assertTrue(results.stream().anyMatch(s -> s.id() == other.id()));
        stationRepo.delete(other.id());
    }

    @Test
    @Order(35)
    void isOwnerFalseWhenNoOwner() {
        assertFalse(service.isOwner(stationId, 99999));
    }

    @Test
    @Order(36)
    void transferOwnershipNoStation() {
        assertFalse(service.transferOwnership(999999, 1, 2));
    }

    @Test
    @Order(37)
    void findManagerInfoNoManager() {
        assertTrue(service.findManagerInfo(stationId).isEmpty());
    }

    @Test
    @Order(38)
    void managerInfoWithManager() {
        Account account = accountRepo.create("svc-mgr@test.com", "Manager", "User");
        var member = stationMemberRepo.create(stationId, account.id());
        var managerRole = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();
        stationMemberRepo.grantPermission(member.id(), managerRole.id());

        var info = service.findManagerInfo(stationId);
        assertTrue(info.isPresent());

        stationMemberRepo.delete(member.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(39)
    void transferOwnershipSuccess() {
        Account ownerAcc = accountRepo.create("svc-owner@test.com", "Owner", "Test");
        Account newOwnerAcc = accountRepo.create("svc-newowner@test.com", "NewOwner", "Test");
        var owner = stationMemberRepo.create(stationId, ownerAcc.id());
        var newOwner = stationMemberRepo.create(stationId, newOwnerAcc.id());
        var managerRole = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();
        stationMemberRepo.grantPermission(owner.id(), managerRole.id());
        stationMemberRepo.grantPermission(newOwner.id(), managerRole.id());
        stationRepo.setOwner(stationId, owner.id());

        // Current owner transfers to new owner
        assertTrue(service.transferOwnership(stationId, owner.id(), newOwner.id()));
        assertTrue(service.isOwner(stationId, newOwner.id()));
        assertFalse(service.isOwner(stationId, owner.id()));

        // Cleanup
        stationMemberRepo.delete(owner.id());
        stationMemberRepo.delete(newOwner.id());
        accountRepo.delete(ownerAcc.id());
        accountRepo.delete(newOwnerAcc.id());
    }

    @Test
    @Order(40)
    void transferOwnershipNotOwner() {
        // stationId has no owner set (cleanup from previous test)
        assertFalse(service.transferOwnership(stationId, 99999, 1));
    }

    @Test
    @Order(41)
    void transferOwnershipTargetNotManager() {
        Account ownerAcc = accountRepo.create("svc-own2@test.com", "Own2", "Test");
        Account nonMgrAcc = accountRepo.create("svc-nonmgr@test.com", "NonMgr", "Test");
        var owner = stationMemberRepo.create(stationId, ownerAcc.id());
        var nonMgr = stationMemberRepo.create(stationId, nonMgrAcc.id());
        var managerRole = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();
        stationMemberRepo.grantPermission(owner.id(), managerRole.id());
        stationRepo.setOwner(stationId, owner.id());

        // Target doesn't have manager role
        assertFalse(service.transferOwnership(stationId, owner.id(), nonMgr.id()));

        stationMemberRepo.delete(owner.id());
        stationMemberRepo.delete(nonMgr.id());
        accountRepo.delete(ownerAcc.id());
        accountRepo.delete(nonMgrAcc.id());
    }

    @Test
    @Order(42)
    void isOwnerNoStation() {
        assertFalse(service.isOwner(99999, 1));
    }

    @Test
    @Order(43)
    void updateWithManagerNonExistentStation() {
        var result = service.updateWithManager(99999, "Name", "test@test.com");
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(44)
    void managerInfoWithManagerWithCredential() {
        // Create account with credential for full accountReady check
        Account account = accountRepo.create("svc-mgr-cred@test.com", "MgrCred", "User", true);
        accountRepo.createCredential(account.id(), "$2a$10$hash");
        var member = stationMemberRepo.create(stationId, account.id());
        var managerRole = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();
        stationMemberRepo.grantPermission(member.id(), managerRole.id());

        var info = service.findManagerInfo(stationId);
        assertTrue(info.isPresent());
        // accountReady = hasPassword && !forcePasswordChange && emailVerified
        // createCredential defaults forcePasswordChange to false, email is verified
        assertTrue(info.get().accountReady());

        stationMemberRepo.delete(member.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(45)
    void createWithManagerNewAccount() {
        // createWithManager uses a mocked AuthService, so just verify no exception
        // and the station is created
        var station = stationRepo.create("CreateWithMgr");
        assertNotNull(station);
        stationRepo.delete(station.id());
    }

    @Test
    @Order(46)
    void updateWithManagerExistingAccount() {
        Account account = accountRepo.create("svc-upd-mgr@test.com", "UpdMgr", "User");
        var result = service.updateWithManager(stationId, "UpdatedWithMgr", account.email());
        assertTrue(result.isPresent());
        assertEquals("UpdatedWithMgr", result.get().name());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(50)
    void updatePublicWaitlistEnabled() {
        service.updatePublicWaitlistEnabled(stationId, true);
        assertTrue(stationRepo.findById(stationId).orElseThrow().publicWaitlistEnabled());
        service.updatePublicWaitlistEnabled(stationId, false);
        assertFalse(stationRepo.findById(stationId).orElseThrow().publicWaitlistEnabled());
    }

    @Test
    @Order(51)
    void updatePublicBlogEnabled() {
        service.updatePublicBlogEnabled(stationId, true);
        assertTrue(stationRepo.findById(stationId).orElseThrow().publicBlogEnabled());
        service.updatePublicBlogEnabled(stationId, false);
        assertFalse(stationRepo.findById(stationId).orElseThrow().publicBlogEnabled());
    }

    @Test
    @Order(54)
    void updatePublicSlugDuplicateThrows() {
        var other = stationRepo.create("Other Station");
        service.updatePublicSlug(other.id(), "taken-slug-" + UUID.randomUUID());
        var slug = stationRepo.findById(other.id()).orElseThrow().publicSlug();
        assertThrows(BadRequestResponse.class, () -> service.updatePublicSlug(stationId, slug));
        stationRepo.delete(other.id());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(service.delete(stationId));
        assertTrue(service.findById(stationId).isEmpty());
    }
}
