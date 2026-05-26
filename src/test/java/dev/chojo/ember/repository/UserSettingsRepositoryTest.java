/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
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
class UserSettingsRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("UserSettings Station");
        account = accountRepo.create("usettings@test.com", "US", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void findOrCreate() {
        var settings = userSettingsRepo.findOrCreate(member.id());
        assertNotNull(settings);
        assertEquals(member.id(), settings.memberId());
        assertFalse(settings.emailEnabled()); // default
    }

    @Test
    @Order(2)
    void findByMemberId() {
        var settings = userSettingsRepo.findByMemberId(member.id());
        assertTrue(settings.isPresent());
    }

    @Test
    @Order(3)
    void findByMemberIdNotFound() {
        assertTrue(userSettingsRepo.findByMemberId(99999).isEmpty());
    }

    @Test
    @Order(4)
    void updateEmailEnabled() {
        var settings = userSettingsRepo.updateEmailEnabled(member.id(), true);
        assertTrue(settings.emailEnabled());
    }

    @Test
    @Order(5)
    void findOrCreateIdempotent() {
        var settings = userSettingsRepo.findOrCreate(member.id());
        // Should return existing settings, not create new ones
        assertTrue(settings.emailEnabled()); // preserved from previous update
    }

    @Test
    @Order(6)
    void updateTheme() {
        var settings = userSettingsRepo.updateTheme(member.id(), "ember", "dark", "ROUNDED");
        assertNotNull(settings);
        assertEquals("ember", settings.theme());
        assertEquals("dark", settings.darkMode());
        assertEquals("ROUNDED", settings.feel());
    }

    @Test
    @Order(7)
    void updateThemePreservedOnFindOrCreate() {
        var settings = userSettingsRepo.findOrCreate(member.id());
        // Should return existing settings, not reset theme
        assertEquals("ember", settings.theme());
    }

    @Test
    @Order(8)
    void updateEmailEnabledFalse() {
        var settings = userSettingsRepo.updateEmailEnabled(member.id(), false);
        assertFalse(settings.emailEnabled());
    }
}
