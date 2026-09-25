/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.repository;

import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StationRepositoryTest extends RepositoryTestBase {
    private static int stationId;

    @Test
    @Order(1)
    void create() {
        Station station = stationRepo.create("Test Station");
        assertNotNull(station);
        assertTrue(station.id() > 0);
        assertEquals("Test Station", station.name());
        stationId = station.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(stationRepo.findById(stationId).isPresent());
        assertTrue(stationRepo.findById(99999).isEmpty());
    }

    @Test
    @Order(3)
    void findAll() {
        assertFalse(stationRepo.findAll().isEmpty());
    }

    @Test
    @Order(4)
    void update() {
        assertTrue(stationRepo.update(stationId, "Updated Station"));
        assertEquals(
                "Updated Station", stationRepo.findById(stationId).orElseThrow().name());
    }

    @Test
    @Order(5)
    void updateNonExistent() {
        assertFalse(stationRepo.update(99999, "Nope"));
    }

    // -- Logo --

    @Test
    @Order(10)
    void updateLogo() {
        byte[] logo = "fake-png-data".getBytes(StandardCharsets.UTF_8);
        assertTrue(stationRepo.updateLogo(stationId, logo, "image/png"));
    }

    @Test
    @Order(11)
    void findLogo() {
        var logo = stationRepo.findLogo(stationId);
        assertTrue(logo.isPresent());
        assertEquals("image/png", logo.get().contentType());
        assertArrayEquals(
                "fake-png-data".getBytes(StandardCharsets.UTF_8), logo.get().data());
    }

    @Test
    @Order(12)
    void findLogoNotSet() {
        Station other = stationRepo.create("No Logo Station");
        assertTrue(stationRepo.findLogo(other.id()).isEmpty());
        stationRepo.delete(other.id());
    }

    @Test
    @Order(13)
    void deleteLogo() {
        assertTrue(stationRepo.deleteLogo(stationId));
        assertTrue(stationRepo.findLogo(stationId).isEmpty());
    }

    // -- UUID --

    @Test
    @Order(14)
    void findByUid() {
        var station = stationRepo.findById(stationId).orElseThrow();
        assertTrue(stationRepo.findByUid(station.uid()).isPresent());
        assertTrue(stationRepo.findByUid(UUID.randomUUID()).isEmpty());
    }

    @Test
    @Order(15)
    void createWithUid() {
        UUID uid = UUID.randomUUID();
        Station withUid = stationRepo.create("UUID Station", uid);
        assertEquals(uid, withUid.uid());
        stationRepo.delete(withUid.id());
    }

    /**
     * A station decides whether it reads the names its members are called by, and reads them by
     * default: a feature nobody has to switch on is one everybody finds.
     */
    @Test
    @Order(16)
    void nicknamesAreReadUntilAStationSaysOtherwise() {
        assertTrue(
                stationRepo.findById(stationId).orElseThrow().nicknamesEnabled(),
                "a new station reads them without being asked");

        assertTrue(stationRepo.updateNicknamesEnabled(stationId, false));
        assertFalse(stationRepo.findById(stationId).orElseThrow().nicknamesEnabled());

        assertTrue(stationRepo.updateNicknamesEnabled(stationId, true));
        assertTrue(stationRepo.findById(stationId).orElseThrow().nicknamesEnabled());
    }

    // -- Discovery settings --

    @Test
    @Order(20)
    void updateDiscoverySettings() {
        assertTrue(
                stationRepo.updateDiscoverySettings(stationId, DiscoveryVisibility.INSTANCE, "A great station", true));
        var station = stationRepo.findById(stationId).orElseThrow();
        assertEquals(DiscoveryVisibility.INSTANCE, station.discoveryVisibility());
    }

    @Test
    @Order(21)
    void findDiscoverable() {
        var other = stationRepo.create("Other Discoverable Station");
        stationRepo.updateDiscoverySettings(other.id(), DiscoveryVisibility.PUBLIC, "Public", false);
        var results = stationRepo.findDiscoverable(stationId, DiscoveryVisibility.INSTANCE, DiscoveryVisibility.PUBLIC);
        assertTrue(results.stream().anyMatch(s -> s.id() == other.id()));
        stationRepo.delete(other.id());
    }

    @Test
    @Order(22)
    void findWithPublicContent() {
        var other = stationRepo.create("Public Calendar Station");
        stationRepo.updatePublicCalendarEnabled(other.id(), true);
        var results = stationRepo.findWithPublicContent(stationId);
        assertTrue(results.stream().anyMatch(s -> s.id() == other.id()));
        stationRepo.delete(other.id());
    }

    // -- Public calendar --

    @Test
    @Order(23)
    void updatePublicCalendarEnabled() {
        assertTrue(stationRepo.updatePublicCalendarEnabled(stationId, true));
        assertTrue(stationRepo.updatePublicCalendarEnabled(stationId, false));
    }

    // -- Public KB mode --

    @Test
    @Order(24)
    void updatePublicKbMode() {
        assertTrue(stationRepo.updatePublicKbMode(stationId, PublicKbMode.ALLOW_ALL));
        assertTrue(stationRepo.updatePublicKbMode(stationId, PublicKbMode.OFF));
    }

    // -- Theme settings --

    @Test
    @Order(25)
    void updateThemeSettings() {
        assertDoesNotThrow(
                () -> stationRepo.updateThemeSettings(stationId, "ember", true, "{}", ThemeFeel.ROUNDED, false));
    }

    // -- Modules --

    @Test
    @Order(26)
    void setAndFindDisabledModules() {
        stationRepo.setDisabledModules(stationId, Set.of(StationModule.ATTENDANCE));
        assertTrue(stationRepo.findDisabledModules(stationId).contains(StationModule.ATTENDANCE));
        stationRepo.setDisabledModules(stationId, Set.of());
        assertTrue(stationRepo.findDisabledModules(stationId).isEmpty());
    }

    // -- Federation key --

    @Test
    @Order(27)
    void updateFederationPrivateKey() {
        assertTrue(stationRepo.updateFederationPrivateKey(stationId, "mock-private-key"));
    }

    // -- Timezone / Locale --

    @Test
    @Order(28)
    void updateTimezone() {
        assertTrue(stationRepo.updateTimezone(stationId, "Europe/Berlin"));
    }

    @Test
    @Order(29)
    void updateLocale() {
        assertTrue(stationRepo.updateLocale(stationId, "de-DE"));
    }

    // -- Owner --

    @Test
    @Order(30)
    void setOwner() {
        // Need an account+member for this station
        var account = accountRepo.create("owner-test@test.com", "Owner", "Test");
        var member = stationMemberRepo.create(stationId, account.id());
        assertTrue(stationRepo.setOwner(stationId, member.id()));
        var station = stationRepo.findById(stationId).orElseThrow();
        assertEquals(member.id(), station.ownerMemberId());
        // Clear owner
        assertTrue(stationRepo.setOwner(stationId, null));
        assertNull(stationRepo.findById(stationId).orElseThrow().ownerMemberId());
        accountRepo.delete(account.id());
    }

    // -- UID update --

    @Test
    @Order(31)
    void updateUid() {
        UUID newUid = UUID.randomUUID();
        assertDoesNotThrow(() -> stationRepo.updateUid(stationId, newUid));
        var station = stationRepo.findById(stationId).orElseThrow();
        assertEquals(newUid, station.uid());
    }

    @Test
    @Order(40)
    void setupCompletedAtStartsNull() {
        var station = stationRepo.findById(stationId).orElseThrow();
        assertNull(station.setupCompletedAt());
        assertTrue(stationRepo.findSetupCompletedAt(stationId).isEmpty());
    }

    @Test
    @Order(41)
    void markSetupCompleteStampsTimestamp() {
        assertTrue(stationRepo.markSetupComplete(stationId));
        var stamped = stationRepo.findSetupCompletedAt(stationId);
        assertTrue(stamped.isPresent());
        var station = stationRepo.findById(stationId).orElseThrow();
        assertEquals(stamped.get(), station.setupCompletedAt());
    }

    @Test
    @Order(42)
    void markSetupCompleteIsIdempotent() {
        var first = stationRepo.findSetupCompletedAt(stationId).orElseThrow();
        assertFalse(stationRepo.markSetupComplete(stationId));
        assertEquals(first, stationRepo.findSetupCompletedAt(stationId).orElseThrow());
    }

    @Test
    @Order(43)
    void markSetupCompleteOnUnknownStationDoesNothing() {
        assertFalse(stationRepo.markSetupComplete(99999));
        assertTrue(stationRepo.findSetupCompletedAt(99999).isEmpty());
    }

    /**
     * A public address carries whichever name the link was built from, so both have to lead to the
     * same station and neither may lead to another one.
     */
    @Test
    @Order(44)
    void aStationAnswersToItsUidAndToItsReadableName() {
        stationRepo.updatePublicSlug(stationId, "test-station");
        var uid = stationRepo.findById(stationId).orElseThrow().uid();

        assertEquals(
                stationId,
                stationRepo.findByAddress(uid.toString()).orElseThrow().id());
        assertEquals(
                stationId,
                stationRepo.findByAddress("test-station").orElseThrow().id());
        assertEquals(stationId, stationRepo.resolveAddressedId(uid.toString()).orElseThrow());
        assertEquals(stationId, stationRepo.resolveAddressedId("test-station").orElseThrow());

        assertTrue(stationRepo.findByAddress("no-such-station").isEmpty());
        assertTrue(stationRepo.resolveAddressedId("no-such-station").isEmpty());
        assertTrue(stationRepo.findByAddress(UUID.randomUUID().toString()).isEmpty());
        assertTrue(stationRepo.resolveAddressedId(UUID.randomUUID().toString()).isEmpty());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(stationRepo.delete(stationId));
        assertTrue(stationRepo.findById(stationId).isEmpty());
    }
}
