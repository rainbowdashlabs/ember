/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.repository;

import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stored form of a station's federation key: written once unless replaced on purpose, and
 * replaced by a conversion only while it still holds what the conversion read.
 */
class StationKeyRepositoryTest extends RepositoryTestBase {
    private final StationKeyRepository repository = new StationKeyRepository();

    private int newStation() {
        return stationRepo.create("KeyRepo" + UUID.randomUUID()).id();
    }

    @Test
    void aStationWithoutAKeyHasNone() {
        assertTrue(repository.find(newStation()).isEmpty());
    }

    @Test
    void storeIfAbsentWritesOnceAndNeverOverwrites() {
        int stationId = newStation();

        assertTrue(repository.storeIfAbsent(stationId, "enc:v1:first"));
        assertFalse(repository.storeIfAbsent(stationId, "enc:v1:second"));
        assertEquals("enc:v1:first", repository.find(stationId).orElseThrow());
    }

    @Test
    void replaceOverwritesWhateverWasThere() {
        int stationId = newStation();
        repository.storeIfAbsent(stationId, "enc:v1:first");

        repository.replace(stationId, "enc:v1:second");

        assertEquals("enc:v1:second", repository.find(stationId).orElseThrow());
    }

    @Test
    void replaceIfUnchangedLeavesAValueSomebodyElseWroteMeanwhile() {
        int stationId = newStation();
        repository.replace(stationId, "plain");

        assertFalse(repository.replaceIfUnchanged(stationId, "stale", "enc:v1:x"));
        assertEquals("plain", repository.find(stationId).orElseThrow());
        assertTrue(repository.replaceIfUnchanged(stationId, "plain", "enc:v1:x"));
        assertEquals("enc:v1:x", repository.find(stationId).orElseThrow());
    }

    @Test
    void findWithoutPrefixListsOnlyValuesNotYetSealed() {
        int plain = newStation();
        int sealed = newStation();
        repository.replace(plain, "plaintext-key");
        repository.replace(sealed, "enc:v1:sealed");

        var unsealed = repository.findWithoutPrefix("enc:v1:");

        assertTrue(unsealed.stream()
                .anyMatch(row -> row.stationId() == plain && row.stored().equals("plaintext-key")));
        assertTrue(unsealed.stream().noneMatch(row -> row.stationId() == sealed));
    }
}
