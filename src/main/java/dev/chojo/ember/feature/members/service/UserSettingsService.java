/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.feature.members.entity.UserSettings;
import dev.chojo.ember.feature.members.repository.UserSettingsRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for managing per-member user settings: the mail switch and the look of the app. The
 * choices per notification type belong to the notifications and are kept there.
 */
@Singleton
public class UserSettingsService {
    private static final Logger log = LoggerFactory.getLogger(UserSettingsService.class);

    private final UserSettingsRepository settingsRepository;

    @Inject
    public UserSettingsService(UserSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public UserSettings getSettings(int memberId) {
        return settingsRepository.findOrCreate(memberId);
    }

    public UserSettings findOrCreate(int memberId) {
        return settingsRepository.findOrCreate(memberId);
    }

    public UserSettings updateEmailEnabled(int memberId, boolean emailEnabled) {
        log.info("User email-enabled updated: member={}, emailEnabled={}", memberId, emailEnabled);
        return settingsRepository.updateEmailEnabled(memberId, emailEnabled);
    }

    public UserSettings updateTheme(int memberId, String theme, String darkMode, String feel) {
        log.info("User theme updated: member={}, theme={}, darkMode={}, feel={}", memberId, theme, darkMode, feel);
        return settingsRepository.updateTheme(memberId, theme, darkMode, feel);
    }
}
