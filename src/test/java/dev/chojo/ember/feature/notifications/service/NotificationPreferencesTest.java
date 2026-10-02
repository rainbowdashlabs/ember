/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.StationMember;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** A station member's choices per notification type, read and written in the one place that keeps them. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationPreferencesTest extends RepositoryTestBase {
    private static NotificationPreferences preferences;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        preferences = new NotificationPreferences(notificationSettingsRepo, clusterRepo, mock(EmailService.class));
        station = stationRepo.create("Preferences Station");
        account = accountRepo.create("preferences@test.com", "Pref", "Erence");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void aMemberWhoChoseNothingHasNoChoices() {
        assertTrue(preferences.settingsOf(member.id()).isEmpty());
    }

    @Test
    @Order(2)
    void aChoiceIsReadBackAsStored() {
        preferences.updateSettings(
                member.id(),
                Map.of(
                        NotificationType.NEW_NEWS,
                        new NotificationSetting(member.id(), NotificationType.NEW_NEWS, true, false, false)));

        var stored = preferences.settingsOf(member.id()).get(NotificationType.NEW_NEWS);
        assertTrue(stored.appEnabled());
        assertFalse(stored.emailEnabled());
    }

    @Test
    @Order(3)
    void choosingForSomeTypesKeepsTheOthers() {
        preferences.updateSettings(
                member.id(),
                Map.of(
                        NotificationType.NEW_EVENT,
                        new NotificationSetting(member.id(), NotificationType.NEW_EVENT, false, true, false)));

        var stored = preferences.settingsOf(member.id());
        assertFalse(stored.get(NotificationType.NEW_EVENT).appEnabled());
        assertTrue(stored.get(NotificationType.NEW_EVENT).emailEnabled());
        assertTrue(stored.get(NotificationType.NEW_NEWS).appEnabled());
    }
}
