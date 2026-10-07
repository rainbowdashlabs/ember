/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.repository;

import dev.chojo.ember.feature.signing.entity.RevocationReason;
import dev.chojo.ember.feature.signing.entity.RevokedKey;
import dev.chojo.ember.feature.signing.entity.StoredSigningKey;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.sql.Transactions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stored authorities and station keys: one active authority and one active key per station, the
 * first stored one kept when two arrive, retiring only once, and wrapped keys replaced in place.
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

    @BeforeEach
    void startWithoutKeys() {
        query("DELETE FROM station_signing_key;").single(call()).delete();
        query("DELETE FROM signing_ca;").single(call()).delete();
    }

    private int authorityId() {
        repository.storeAuthority(key("authority"));
        return repository.findActiveAuthority().orElseThrow().id();
    }

    @Test
    void theFirstAuthorityStaysAndASecondIsNotStored() {
        assertTrue(repository.findActiveAuthority().isEmpty());
        var first = key("first authority");

        assertTrue(repository.storeAuthority(first));
        assertFalse(repository.storeAuthority(key("second authority")));

        var stored = repository.findActiveAuthority().orElseThrow().key();
        assertEquals(first.serialNumber(), stored.serialNumber());
        assertArrayEquals(first.certificate(), stored.certificate());
        assertArrayEquals(first.wrappedPrivateKey(), stored.wrappedPrivateKey());
        assertEquals(first.validUntil(), stored.validUntil());
    }

    @Test
    void aRetiredAuthorityStaysReadableAndMakesRoomForANewOne() {
        var old = key("old authority");
        repository.storeAuthority(old);
        int oldId = repository.findActiveAuthority().orElseThrow().id();

        assertTrue(repository.retireAuthority(oldId));
        assertFalse(repository.retireAuthority(oldId), "retired only once");
        assertTrue(repository.findActiveAuthority().isEmpty());

        var next = key("new authority");
        assertTrue(repository.storeAuthority(next));
        var active = repository.findActiveAuthority().orElseThrow();
        assertEquals(next.serialNumber(), active.key().serialNumber());
        assertEquals(
                old.serialNumber(),
                repository.findAuthority(oldId).orElseThrow().key().serialNumber());
        assertTrue(repository.findAuthority(Integer.MAX_VALUE).isEmpty());
    }

    @Test
    void aStationHasOneActiveKeyAndTheFirstStoredWins() {
        var station = stationRepo.create("Signing key station");
        int authorityId = authorityId();
        assertTrue(repository.findActive(station.id()).isEmpty());
        var first = key("first station key");

        assertTrue(repository.storeActive(station.id(), authorityId, first));
        assertFalse(repository.storeActive(station.id(), authorityId, key("second station key")));

        var stored = repository.findActive(station.id()).orElseThrow();
        assertEquals(first.serialNumber(), stored.key().serialNumber());
        assertArrayEquals(first.wrappedPrivateKey(), stored.key().wrappedPrivateKey());
        assertEquals(authorityId, stored.authorityId());
    }

    @Test
    void aRetiredKeyMakesRoomForANewActiveOne() {
        var station = stationRepo.create("Rotated signing station");
        int authorityId = authorityId();
        repository.storeActive(station.id(), authorityId, key("old"));
        int oldId = repository.findActive(station.id()).orElseThrow().id();

        assertTrue(repository.retire(oldId));
        assertFalse(repository.retire(oldId), "retired only once");
        assertTrue(repository.findActive(station.id()).isEmpty());

        var next = key("new");
        assertTrue(repository.storeActive(station.id(), authorityId, next));

        assertEquals(
                next.serialNumber(),
                repository.findActive(station.id()).orElseThrow().key().serialNumber());
    }

    @Test
    void keysOfOneStationAreNotAnotherStationsKeys() {
        var one = stationRepo.create("First signing station");
        var other = stationRepo.create("Second signing station");
        int authorityId = authorityId();

        repository.storeActive(one.id(), authorityId, key("one"));

        assertTrue(repository.findActive(other.id()).isEmpty());
        assertTrue(repository.storeActive(other.id(), authorityId, key("other")));
    }

    @Test
    void everyKeyIsListedAndItsWrapReplacedInPlace() {
        var station = stationRepo.create("Rewrapped signing station");
        int oldAuthority = authorityId();
        repository.storeActive(station.id(), oldAuthority, key("retired station key"));
        repository.retire(repository.findActive(station.id()).orElseThrow().id());
        repository.retireAuthority(oldAuthority);
        repository.storeAuthority(key("active authority"));
        int newAuthority = repository.findActiveAuthority().orElseThrow().id();
        repository.storeActive(station.id(), newAuthority, key("active station key"));

        Transactions.run(() -> {
            var authorities = repository.lockAuthorities();
            var stationKeys = repository.lockStationKeys();
            assertEquals(2, authorities.size());
            assertEquals(2, stationKeys.size());
            authorities.forEach(a -> repository.replaceAuthorityWrap(a.id(), bytes("new " + a.id())));
            stationKeys.forEach(k -> repository.replaceStationKeyWrap(k.id(), bytes("new " + k.id())));
        });

        assertArrayEquals(
                bytes("new " + oldAuthority),
                repository.findAuthority(oldAuthority).orElseThrow().key().wrappedPrivateKey());
        var active = repository.findActive(station.id()).orElseThrow();
        assertArrayEquals(bytes("new " + active.id()), active.key().wrappedPrivateKey());
    }

    @Test
    void deletingAStationTakesItsKeys() {
        var station = stationRepo.create("Deleted signing station");
        repository.storeActive(station.id(), authorityId(), key("gone"));

        stationRepo.delete(station.id());

        assertEquals(
                0,
                query("SELECT count(*) AS n FROM station_signing_key WHERE station_id = :station_id;")
                        .single(call().bind("station_id", station.id()))
                        .map(row -> row.getInt("n"))
                        .first()
                        .orElseThrow());
    }

    @Test
    void aKeyIsRevokedOnceRetiredWithItAndListedForItsAuthority() {
        var station = stationRepo.create("Revoked signing station");
        int authorityId = authorityId();
        var retired = key("retired");
        repository.storeActive(station.id(), authorityId, retired);
        repository.retire(repository.findActive(station.id()).orElseThrow().id());
        var active = key("active");
        repository.storeActive(station.id(), authorityId, active);

        assertEquals(
                Optional.of(authorityId),
                repository.revoke(station.id(), active.serialNumber(), RevocationReason.KEY_COMPROMISE));
        assertEquals(
                Optional.empty(), repository.revoke(station.id(), active.serialNumber(), RevocationReason.SUPERSEDED));
        assertTrue(repository.findActive(station.id()).isEmpty(), "an active key is retired with it");
        assertEquals(
                Optional.of(authorityId),
                repository.revoke(station.id(), retired.serialNumber(), RevocationReason.SUPERSEDED));
        assertEquals(Optional.empty(), repository.revoke(station.id(), "ffff", RevocationReason.SUPERSEDED));

        var revoked = repository.revokedBy(authorityId);
        assertEquals(
                List.of(active.serialNumber(), retired.serialNumber()),
                revoked.stream().map(RevokedKey::serialNumber).toList());
        assertEquals(
                List.of(RevocationReason.KEY_COMPROMISE, RevocationReason.SUPERSEDED),
                revoked.stream().map(RevokedKey::reason).toList());
        assertFalse(revoked.getFirst().revokedAt().isAfter(revoked.getLast().revokedAt()));
        assertEquals(List.of(), repository.revokedBy(Integer.MAX_VALUE));
    }

    @Test
    void holdsOnlyTheStationsOwnKeys() {
        var station = stationRepo.create("Holding station");
        var other = stationRepo.create("Other holding station");
        var held = key("held");
        repository.storeActive(station.id(), authorityId(), held);

        assertTrue(repository.holds(station.id(), held.serialNumber()));
        assertFalse(repository.holds(other.id(), held.serialNumber()));
        assertFalse(repository.holds(station.id(), "ffff"));
    }

    @Test
    void anAuthorityIsFoundByItsSerial() {
        var authority = key("serial authority");
        repository.storeAuthority(authority);

        assertEquals(
                repository.findActiveAuthority().orElseThrow().id(),
                repository
                        .findAuthorityBySerial(authority.serialNumber())
                        .orElseThrow()
                        .id());
        assertTrue(repository.findAuthorityBySerial("ffff").isEmpty());
    }

    @Test
    void theRevocationListIsStoredNumberedAndForgotten() {
        int authorityId = authorityId();
        assertTrue(repository.findRevocationList(authorityId).isEmpty());

        Transactions.run(() -> assertEquals(0L, repository.lockRevocationListNumber(authorityId)));
        repository.storeRevocationList(authorityId, 1, bytes("list one"));

        assertArrayEquals(
                bytes("list one"), repository.findRevocationList(authorityId).orElseThrow());
        Transactions.run(() -> assertEquals(1L, repository.lockRevocationListNumber(authorityId)));

        repository.forgetRevocationList(authorityId);
        assertTrue(repository.findRevocationList(authorityId).isEmpty());
        Transactions.run(() -> assertEquals(1L, repository.lockRevocationListNumber(authorityId), "number kept"));
        assertThrows(IllegalArgumentException.class, () -> repository.lockRevocationListNumber(Integer.MAX_VALUE));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
