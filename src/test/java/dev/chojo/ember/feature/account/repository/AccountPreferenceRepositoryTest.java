/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.repository;

import dev.chojo.ember.feature.account.entity.SidebarMode;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An account's own choices: one section at a time and not findable by name until it chooses otherwise,
 * its own choice for each account, and gone with the account.
 */
class AccountPreferenceRepositoryTest extends RepositoryTestBase {
    private final AccountPreferenceRepository preferences = new AccountPreferenceRepository();

    private int account(String name) {
        return accountRepo
                .create(name + System.nanoTime() + "@test.com", "Lena", name, true)
                .id();
    }

    @Test
    void anAccountThatNeverChoseSeesOneSectionAtATime() {
        assertEquals(SidebarMode.SECTIONS, preferences.sidebarOf(account("Neu")));
    }

    @Test
    void eachAccountKeepsItsOwnChoiceAndCanChangeIt() {
        int tree = account("Baum");
        int other = account("Andere");

        preferences.setSidebar(tree, SidebarMode.TREE);
        assertEquals(SidebarMode.TREE, preferences.sidebarOf(tree));
        assertEquals(SidebarMode.SECTIONS, preferences.sidebarOf(other));

        preferences.setSidebar(tree, SidebarMode.SECTIONS);
        assertEquals(SidebarMode.SECTIONS, preferences.sidebarOf(tree));
    }

    @Test
    void theChoiceGoesWithTheAccount() {
        int gone = account("Weg");
        preferences.setSidebar(gone, SidebarMode.TREE);

        accountRepo.delete(gone);

        assertEquals(SidebarMode.SECTIONS, preferences.sidebarOf(gone));
    }

    @Test
    void nobodyIsFindableByNameUntilTheyChooseIt() {
        int quiet = account("Still");
        int open = account("Offen");
        preferences.setSidebar(quiet, SidebarMode.TREE);

        preferences.setFindableByName(open, true);

        assertFalse(preferences.findableByName(quiet), "a sidebar choice does not make anybody findable");
        assertTrue(preferences.findableByName(open));
        assertEquals(SidebarMode.SECTIONS, preferences.sidebarOf(open), "and choosing it leaves the sidebar alone");

        preferences.setFindableByName(open, false);
        assertFalse(preferences.findableByName(open));
    }
}
