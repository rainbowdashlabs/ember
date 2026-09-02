/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AccountInviteService;
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
                        stationMemberRepo,
                        memberGroupRepo,
                        new AccountInviteService(accountRepo, mock(AuthService.class))),
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

    /**
     * A made-up address is not a key to the account behind it. Naming one as a station's manager is
     * refused, and the refusal has to carry its reason: an operator who sees only a fault presses the
     * button again, which is exactly what happened.
     */
    @Test
    @Order(47)
    void updateWithManagerRefusesAMadeUpAddressOfAnotherAccount() {
        Account other = accountRepo.create("svc-upd-taken@ember.local", "Taken", "Local");

        var refused = assertThrows(
                StationMemberInviteService.ProvisionException.class,
                () -> service.updateWithManager(stationId, "UpdatedWithTaken", other.email()));
        assertTrue(refused.getMessage().contains(other.email()));

        accountRepo.delete(other.id());
    }

    @Test
    @Order(48)
    void namingAnotherManagerHandsTheStationOverAndKeepsThePreviousOne() {
        var station = stationRepo.create("Wache Übergabe");
        Account first = accountRepo.create("svc-mgr-first@test.com", "First", "Manager", true);
        Account second = accountRepo.create("svc-mgr-second@test.com", "Second", "Manager", true);
        var managerRole = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();

        service.updateWithManager(station.id(), "Wache Übergabe", first.email());
        int firstMemberId = stationMemberRepo
                .findByStationAndAccount(station.id(), first.id())
                .orElseThrow()
                .id();

        service.updateWithManager(station.id(), "Wache Übergabe", second.email());
        int secondMemberId = stationMemberRepo
                .findByStationAndAccount(station.id(), second.id())
                .orElseThrow()
                .id();

        assertEquals(
                secondMemberId, stationRepo.findById(station.id()).orElseThrow().ownerMemberId());
        assertEquals(
                second.email(),
                service.findManagerInfo(station.id()).orElseThrow().email());
        assertTrue(stationMemberRepo.findPermissions(firstMemberId).stream().anyMatch(r -> r.id() == managerRole.id()));

        stationMemberRepo.delete(secondMemberId);
        stationMemberRepo.delete(firstMemberId);
        stationRepo.delete(station.id());
        accountRepo.delete(second.id());
        accountRepo.delete(first.id());
    }

    @Test
    @Order(49)
    void anAdministratorWithoutAnAccountIsPassedOver() {
        var station = stationRepo.create("Wache Ehemalige");
        Account gone = accountRepo.create("svc-mgr-gone@test.com", "Gone", "Manager", true);
        Account present = accountRepo.create("svc-mgr-present@test.com", "Present", "Manager", true);
        var managerRole = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();

        var former = stationMemberRepo.create(station.id(), gone.id());
        stationMemberRepo.grantPermission(former.id(), managerRole.id());
        stationMemberRepo.setDisplayNameAndClearAccount(former.id(), "Gone Manager");

        var current = stationMemberRepo.create(station.id(), present.id());
        stationMemberRepo.grantPermission(current.id(), managerRole.id());

        assertEquals(
                present.email(),
                service.findManagerInfo(station.id()).orElseThrow().email());

        stationMemberRepo.delete(current.id());
        stationMemberRepo.delete(former.id());
        stationRepo.delete(station.id());
        accountRepo.delete(present.id());
        accountRepo.delete(gone.id());
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

    /**
     * What a cluster locks, the station keeps whatever it sends. The settings it left alone stay the
     * station's own, in the same save, which is what makes a lock different from a takeover.
     */
    @Test
    @Order(60)
    void aLockedLookIsKeptAndTheRestStaysTheStationsOwn() {
        var home = stationRepo.create("Träger Farbe");
        var cluster = clusterRepo.create("Kreisverband Farbe", null, home.id());
        var member = stationRepo.create("Wache Farbe");
        stationRepo.setCluster(member.id(), cluster.id());

        stationRepo.updateThemeSettings(member.id(), "ember", true, "{\"light\":{}}", ThemeFeel.ROUNDED, true);
        clusterRepo.setLookAndFeel(
                cluster.id(), "ember", "{\"light\":{}}", ThemeFeel.ROUNDED, false, true, false, true);

        var locks = service.lookAndFeelLocks(member.id());
        assertTrue(locks.colors(), "the cluster locked the colours");
        assertTrue(locks.logo());
        assertFalse(locks.theme());
        assertFalse(locks.feel());
        assertEquals("Kreisverband Farbe", service.clusterNameOf(member.id()).orElseThrow());

        service.updateThemeSettings(
                member.id(), "aurora", false, "{\"light\":{\"primary\":\"#fff\"}}", ThemeFeel.CORNERS, false);

        var after = stationRepo.findById(member.id()).orElseThrow();
        // Compared without the spacing the database writes back, which is not what the cluster locked
        assertEquals(
                "{\"light\":{}}", after.customThemeColors().replace(" ", ""), "the locked colours are the cluster's");
        assertEquals("aurora", after.defaultTheme(), "what it did not lock is still the station's");
        assertEquals(ThemeFeel.CORNERS, after.defaultFeel());

        stationRepo.setCluster(member.id(), null);
        stationRepo.delete(member.id());
        clusterRepo.delete(cluster.id());
        stationRepo.delete(home.id());
    }

    /** A station in no cluster has nothing locked and nobody to name. */
    @Test
    @Order(61)
    void aStationUnderNobodyLocksNothing() {
        var lone = stationRepo.create("Wache Allein");
        var locks = service.lookAndFeelLocks(lone.id());
        assertFalse(locks.theme() || locks.colors() || locks.feel() || locks.logo());
        assertTrue(service.clusterNameOf(lone.id()).isEmpty());
        assertTrue(service.findEffectiveDisabledModules(lone.id()).isEmpty());
        stationRepo.delete(lone.id());
    }

    /**
     * A module the cluster denied is as gone as one the station switched off itself, which is what the
     * shell has to go by.
     */
    @Test
    @Order(62)
    void anEffectiveDisabledModuleCountsBothSides() {
        var home = stationRepo.create("Träger Module");
        var cluster = clusterRepo.create("Kreisverband Module", null, home.id());
        var member = stationRepo.create("Wache Module");
        stationRepo.setCluster(member.id(), cluster.id());

        stationRepo.setDisabledModules(member.id(), Set.of(StationModule.NEWS));
        clusterRepo.setDeniedModules(cluster.id(), null, Set.of(StationModule.BOARDS));

        assertEquals(Set.of(StationModule.NEWS), service.findDisabledModules(member.id()));
        assertEquals(
                Set.of(StationModule.NEWS, StationModule.BOARDS), service.findEffectiveDisabledModules(member.id()));

        stationRepo.setCluster(member.id(), null);
        stationRepo.delete(member.id());
        clusterRepo.delete(cluster.id());
        stationRepo.delete(home.id());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(service.delete(stationId));
        assertTrue(service.findById(stationId).isEmpty());
    }
}
