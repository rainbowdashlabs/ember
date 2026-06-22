/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.repository;

import dev.chojo.ember.feature.discovery.entity.BlocklistKind;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveryBlocklistRepositoryTest extends RepositoryTestBase {

    @Test
    void addAndContains() {
        discoveryBlocklistRepo.add(BlocklistKind.BASE_URL, "https://evil.example", "Spammy");
        assertTrue(discoveryBlocklistRepo.contains(BlocklistKind.BASE_URL, "https://evil.example"));
        assertFalse(discoveryBlocklistRepo.contains(BlocklistKind.PUBLIC_KEY, "https://evil.example"));
        assertFalse(discoveryBlocklistRepo.contains(BlocklistKind.BASE_URL, "https://good.example"));
    }

    @Test
    void addUpsertsKindAndNote() {
        discoveryBlocklistRepo.add(BlocklistKind.BASE_URL, "https://shifty.example", "initial");
        discoveryBlocklistRepo.add(BlocklistKind.PUBLIC_KEY, "https://shifty.example", "updated");
        assertTrue(discoveryBlocklistRepo.contains(BlocklistKind.PUBLIC_KEY, "https://shifty.example"));
        // The entry survives but its kind has been updated.
        assertFalse(discoveryBlocklistRepo.contains(BlocklistKind.BASE_URL, "https://shifty.example"));
    }

    @Test
    void findAllReturnsRows() {
        discoveryBlocklistRepo.add(BlocklistKind.BASE_URL, "https://list-a.example", null);
        discoveryBlocklistRepo.add(BlocklistKind.PUBLIC_KEY, "list-b-key", "abusive");
        var all = discoveryBlocklistRepo.findAll();
        assertTrue(all.stream().anyMatch(e -> e.value().equals("https://list-a.example")));
        assertTrue(all.stream().anyMatch(e -> e.value().equals("list-b-key")));
    }

    @Test
    void remove() {
        discoveryBlocklistRepo.add(BlocklistKind.BASE_URL, "https://remove.example", null);
        assertTrue(discoveryBlocklistRepo.remove("https://remove.example"));
        assertFalse(discoveryBlocklistRepo.remove("https://remove.example"));
        assertFalse(discoveryBlocklistRepo.contains(BlocklistKind.BASE_URL, "https://remove.example"));
    }
}
