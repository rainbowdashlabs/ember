/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.repository;

import dev.chojo.ember.feature.signing.entity.StoredSigningKey;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stored authority and station keys: one authority, one active key per station, the first stored
 * one kept when two arrive.
 */
class SigningKeyRepositoryTest extends RepositoryTestBase {
    private final SigningKeyRepository repository = new SigningKeyRepository();

    private static StoredSigningKey key(String marker) {
        return new StoredSigningKey(
                UUID.randomUUID().toString().replace("-", ""),
                ("certificate " + marker).getBytes(StandardCharsets.UTF_8),
                ("wrapped " + marker).getBytes(StandardCharsets.UTF_8),
                Instant.now().plus(365, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void theFirstAuthorityStaysAndASecondIsNotStored() {
        assertTrue(repository.findAuthority().isEmpty());
        var first = key("first authority");

        assertTrue(repository.storeAuthority(first));
        assertFalse(repository.storeAuthority(key("second authority")));

        var stored = repository.findAuthority().orElseThrow();
        assertEquals(first.serialNumber(), stored.serialNumber());
        assertArrayEquals(first.certificate(), stored.certificate());
        assertArrayEquals(first.wrappedPrivateKey(), stored.wrappedPrivateKey());
        assertEquals(first.validUntil(), stored.validUntil());
    }

    @Test
    void aStationHasOneActiveKeyAndTheFirstStoredWins() {
        var station = stationRepo.create("Signing key station");
        assertTrue(repository.findActive(station.id()).isEmpty());
        var first = key("first station key");

        assertTrue(repository.storeActive(station.id(), first));
        assertFalse(repository.storeActive(station.id(), key("second station key")));

        var stored = repository.findActive(station.id()).orElseThrow();
        assertEquals(first.serialNumber(), stored.serialNumber());
        assertArrayEquals(first.wrappedPrivateKey(), stored.wrappedPrivateKey());
    }

    @Test
    void aRetiredKeyMakesRoomForANewActiveOne() {
        var station = stationRepo.create("Rotated signing station");
        repository.storeActive(station.id(), key("old"));
        query("UPDATE station_signing_key SET retired_at = now() WHERE station_id = :station_id;")
                .single(call().bind("station_id", station.id()))
                .update();
        assertTrue(repository.findActive(station.id()).isEmpty());

        var next = key("new");
        assertTrue(repository.storeActive(station.id(), next));

        assertEquals(
                next.serialNumber(),
                repository.findActive(station.id()).orElseThrow().serialNumber());
    }

    @Test
    void keysOfOneStationAreNotAnotherStationsKeys() {
        var one = stationRepo.create("First signing station");
        var other = stationRepo.create("Second signing station");
        var key = key("one");

        repository.storeActive(one.id(), key);

        assertTrue(repository.findActive(other.id()).isEmpty());
        assertTrue(repository.storeActive(other.id(), key("other")));
    }

    @Test
    void deletingAStationTakesItsKeys() {
        var station = stationRepo.create("Deleted signing station");
        repository.storeActive(station.id(), key("gone"));

        stationRepo.delete(station.id());

        assertEquals(
                0,
                query("SELECT count(*) AS n FROM station_signing_key WHERE station_id = :station_id;")
                        .single(call().bind("station_id", station.id()))
                        .map(row -> row.getInt("n"))
                        .first()
                        .orElseThrow());
    }
}
