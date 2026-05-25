/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationSetting;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationSettingsRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("NotifSettings Station");
        account = accountRepo.create("notifsettings@test.com", "NS", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void upsert() {
        var setting = notificationSettingsRepo.upsert(member.id(), NotificationType.NEW_NEWS, true, true, true);
        assertNotNull(setting);
        assertEquals(member.id(), setting.memberId());
        assertEquals(NotificationType.NEW_NEWS, setting.notificationType());
        assertTrue(setting.appEnabled());
        assertTrue(setting.emailEnabled());
    }

    @Test
    @Order(2)
    void upsertUpdate() {
        var setting = notificationSettingsRepo.upsert(member.id(), NotificationType.NEW_NEWS, false, false, true);
        assertFalse(setting.appEnabled());
        assertFalse(setting.emailEnabled());
    }

    @Test
    @Order(3)
    void findByMember() {
        var settings = notificationSettingsRepo.findByMember(member.id());
        assertEquals(1, settings.size());
    }

    @Test
    @Order(4)
    void findByMemberAsMap() {
        var map = notificationSettingsRepo.findByMemberAsMap(member.id());
        assertTrue(map.containsKey(NotificationType.NEW_NEWS));
    }

    @Test
    @Order(5)
    void isAppEnabled() {
        assertFalse(notificationSettingsRepo.isAppEnabled(member.id(), NotificationType.NEW_NEWS));
        // Default for unconfigured type
        assertTrue(notificationSettingsRepo.isAppEnabled(member.id(), NotificationType.NEW_EVENT));
    }

    @Test
    @Order(6)
    void isEmailEnabled() {
        assertFalse(notificationSettingsRepo.isEmailEnabled(member.id(), NotificationType.NEW_NEWS));
        // Default for unconfigured type
        assertFalse(notificationSettingsRepo.isEmailEnabled(member.id(), NotificationType.NEW_EVENT));
    }

    @Test
    @Order(7)
    void upsertAll() {
        var settings = Map.of(
                NotificationType.NEW_NEWS,
                new NotificationSetting(member.id(), NotificationType.NEW_NEWS, true, true, true),
                NotificationType.NEW_EVENT,
                new NotificationSetting(member.id(), NotificationType.NEW_EVENT, true, false, true));
        notificationSettingsRepo.upsertAll(member.id(), settings);
        var map = notificationSettingsRepo.findByMemberAsMap(member.id());
        assertEquals(2, map.size());
        assertTrue(map.get(NotificationType.NEW_NEWS).appEnabled());
        assertTrue(map.get(NotificationType.NEW_EVENT).appEnabled());
    }
}
