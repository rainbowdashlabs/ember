/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.feature.members.entity.UserSettings;
import dev.chojo.ember.feature.members.repository.UserSettingsRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationSetting;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.repository.NotificationSettingsRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Service for managing per-member user settings including notification preferences
 * and email notification toggles.
 */
@Singleton
public class UserSettingsService {
    private final UserSettingsRepository settingsRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;

    @Inject
    public UserSettingsService(
            UserSettingsRepository settingsRepository, NotificationSettingsRepository notificationSettingsRepository) {
        this.settingsRepository = settingsRepository;
        this.notificationSettingsRepository = notificationSettingsRepository;
    }

    public UserSettings getSettings(int memberId) {
        return settingsRepository.findOrCreate(memberId);
    }

    public UserSettings updateEmailEnabled(int memberId, boolean emailEnabled) {
        return settingsRepository.updateEmailEnabled(memberId, emailEnabled);
    }

    public Map<NotificationType, NotificationSetting> getNotificationSettings(int memberId) {
        return notificationSettingsRepository.findByMemberAsMap(memberId);
    }

    public void updateNotificationSettings(int memberId, Map<NotificationType, NotificationSetting> settings) {
        notificationSettingsRepository.upsertAll(memberId, settings);
    }
}
