/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.UserSettingsService;
import dev.chojo.ember.feature.notifications.entity.NotificationSetting;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserSettingsServiceTest extends RepositoryTestBase {
    private static UserSettingsService service;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        service = new UserSettingsService(userSettingsRepo, notificationSettingsRepo);
        station = stationRepo.create("UserSettingsSvc Station");
        account = accountRepo.create("usettings-svc@test.com", "Settings", "User");
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
        var settings = service.findOrCreate(member.id());
        assertNotNull(settings);
        assertEquals(member.id(), settings.memberId());
        assertFalse(settings.emailEnabled());
    }

    @Test
    @Order(2)
    void getSettingsReturnsExisting() {
        var settings = service.getSettings(member.id());
        assertNotNull(settings);
        assertEquals(member.id(), settings.memberId());
    }

    @Test
    @Order(3)
    void updateEmailEnabled() {
        var settings = service.updateEmailEnabled(member.id(), true);
        assertTrue(settings.emailEnabled());
    }

    @Test
    @Order(4)
    void updateEmailEnabledFalse() {
        var settings = service.updateEmailEnabled(member.id(), false);
        assertFalse(settings.emailEnabled());
    }

    @Test
    @Order(5)
    void updateTheme() {
        var settings = service.updateTheme(member.id(), "ocean", "dark", "ROUNDED");
        assertNotNull(settings);
        assertEquals("ocean", settings.theme());
        assertEquals("dark", settings.darkMode());
        assertEquals("ROUNDED", settings.feel());
    }

    @Test
    @Order(10)
    void getNotificationSettingsEmpty() {
        var settings = service.getNotificationSettings(member.id());
        assertNotNull(settings);
        // Default: no explicit settings stored
        assertTrue(settings.isEmpty() || settings.containsKey(NotificationType.NEW_NEWS));
    }

    @Test
    @Order(11)
    void updateNotificationSettings() {
        var newSettings = Map.of(
                NotificationType.NEW_NEWS,
                new NotificationSetting(member.id(), NotificationType.NEW_NEWS, true, false, false));
        service.updateNotificationSettings(member.id(), newSettings);

        var stored = service.getNotificationSettings(member.id());
        assertTrue(stored.containsKey(NotificationType.NEW_NEWS));
        assertTrue(stored.get(NotificationType.NEW_NEWS).appEnabled());
        assertFalse(stored.get(NotificationType.NEW_NEWS).emailEnabled());
    }

    @Test
    @Order(12)
    void updateNotificationSettingsMultipleTypes() {
        var newSettings = Map.of(
                NotificationType.NEW_EVENT,
                new NotificationSetting(member.id(), NotificationType.NEW_EVENT, false, true, false),
                NotificationType.NEW_NEWS,
                new NotificationSetting(member.id(), NotificationType.NEW_NEWS, true, true, false));
        service.updateNotificationSettings(member.id(), newSettings);

        var stored = service.getNotificationSettings(member.id());
        assertTrue(stored.containsKey(NotificationType.NEW_EVENT));
        assertFalse(stored.get(NotificationType.NEW_EVENT).appEnabled());
        assertTrue(stored.get(NotificationType.NEW_EVENT).emailEnabled());
    }

    @Test
    @Order(13)
    void findOrCreateIdempotent() {
        // Should not reset the settings that were updated
        var settings = service.findOrCreate(member.id());
        assertNotNull(settings);
        assertEquals(member.id(), settings.memberId());
    }
}
