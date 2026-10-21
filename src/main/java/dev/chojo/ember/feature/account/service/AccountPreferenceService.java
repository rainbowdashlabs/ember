/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.feature.account.entity.SidebarMode;
import dev.chojo.ember.feature.account.repository.AccountPreferenceRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** How an account likes the app laid out, which follows it to every device it signs in on. */
@Singleton
public class AccountPreferenceService {
    private final AccountPreferenceRepository preferences;

    @Inject
    public AccountPreferenceService(AccountPreferenceRepository preferences) {
        this.preferences = preferences;
    }

    /**
     * @param accountId the account
     * @param mode      how its sidebar lists its sections from now on
     */
    public void setSidebar(int accountId, SidebarMode mode) {
        preferences.setSidebar(accountId, mode);
    }
}
