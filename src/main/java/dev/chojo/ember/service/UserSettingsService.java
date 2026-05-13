/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.entity.UserSettings;
import dev.chojo.ember.repository.UserSettingsRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class UserSettingsService {
    private final UserSettingsRepository settingsRepository;

    @Inject
    public UserSettingsService(UserSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public UserSettings getSettings(int memberId) {
        return settingsRepository.findOrCreate(memberId);
    }

    public UserSettings updateSettings(
            int memberId,
            boolean emailEnabled,
            boolean notifyNews,
            boolean notifyNewEvents,
            boolean notifyEventStatus) {
        return settingsRepository.update(memberId, emailEnabled, notifyNews, notifyNewEvents, notifyEventStatus);
    }
}
