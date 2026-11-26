/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.repository;

import dev.chojo.ember.feature.account.entity.SidebarMode;
import jakarta.inject.Singleton;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * An account's own choices: how it likes the app laid out and whether it can be found by name. An
 * account that never changed anything has no row and reads as the defaults.
 */
@Singleton
public class AccountPreferenceRepository {

    /**
     * @param accountId the account
     * @return how its sidebar lists its sections, one at a time where it never chose
     */
    public SidebarMode sidebarOf(int accountId) {
        return query("SELECT sidebar FROM account_preference WHERE account_id = :account;")
                .single(call().bind("account", accountId))
                .map(row -> row.getEnum("sidebar", SidebarMode.class))
                .first()
                .orElse(SidebarMode.SECTIONS);
    }

    /**
     * @param accountId the account
     * @param mode      how its sidebar lists its sections from now on
     */
    public void setSidebar(int accountId, SidebarMode mode) {
        query("""
                        INSERT INTO account_preference (account_id, sidebar)
                        VALUES (:account, :sidebar)
                        ON CONFLICT (account_id) DO UPDATE SET sidebar = EXCLUDED.sidebar;""")
                .single(call().bind("account", accountId).bind("sidebar", mode))
                .insert();
    }

    /**
     * @param accountId the account
     * @return whether member managers of any station of this instance may find it by its name
     */
    public boolean findableByName(int accountId) {
        return query("SELECT findable_by_name FROM account_preference WHERE account_id = :account;")
                .single(call().bind("account", accountId))
                .map(row -> row.getBoolean("findable_by_name"))
                .first()
                .orElse(false);
    }

    /**
     * @param accountId the account
     * @param findable  whether member managers of any station of this instance may find it by its name
     */
    public void setFindableByName(int accountId, boolean findable) {
        query("""
                        INSERT INTO account_preference (account_id, findable_by_name)
                        VALUES (:account, :findable)
                        ON CONFLICT (account_id) DO UPDATE SET findable_by_name = EXCLUDED.findable_by_name;""")
                .single(call().bind("account", accountId).bind("findable", findable))
                .insert();
    }
}
