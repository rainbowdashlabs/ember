/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.repository;

import dev.chojo.ember.feature.signing.entity.RevocationReason;
import dev.chojo.ember.feature.signing.entity.RevokedKey;
import dev.chojo.ember.feature.signing.entity.StoredRevocationList;
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
 * first stored one kept when two arrive, retiring only once, wrapped keys replaced in place, and keys
 * outliving the station they belonged to.
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
    void aRevokedKeyOfADeletedStationStaysListedForItsAuthority() {
        var station = stationRepo.create("Deleted signing station");
        int authorityId = authorityId();
        var revoked = key("revoked before the deletion");
        repository.storeActive(station.id(), authorityId, revoked);
        repository.revoke(authorityId, revoked.serialNumber(), RevocationReason.KEY_COMPROMISE, Instant.now());
        var active = key("active at the deletion");
        repository.storeActive(station.id(), authorityId, active);

        stationRepo.delete(station.id());

        assertEquals(
                List.of(revoked.serialNumber()),
                repository.revokedBy(authorityId).stream()
                        .map(RevokedKey::serialNumber)
                        .toList());
        assertEquals(Optional.of(authorityId), repository.authorityOfDeletedStationKey(revoked.serialNumber()));
        assertEquals(Optional.of(authorityId), repository.authorityOfDeletedStationKey(active.serialNumber()));
        assertTrue(repository
                .authorityOfStationKey(station.id(), active.serialNumber())
                .isEmpty());
        assertTrue(repository.findActive(station.id()).isEmpty());
        assertTrue(
                Transactions.call(repository::lockStationKeys).isEmpty(),
                "without a private key there is nothing to re-wrap");
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
        var first = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var second = first.plusSeconds(1);

        assertTrue(repository.revoke(authorityId, active.serialNumber(), RevocationReason.KEY_COMPROMISE, first));
        assertFalse(repository.revoke(authorityId, active.serialNumber(), RevocationReason.SUPERSEDED, second));
        assertTrue(repository.findActive(station.id()).isEmpty(), "an active key is retired with it");
        assertTrue(repository.revoke(authorityId, retired.serialNumber(), RevocationReason.SUPERSEDED, second));
        assertFalse(repository.revoke(authorityId, "ffff", RevocationReason.SUPERSEDED, second));
        assertFalse(
                repository.revoke(Integer.MAX_VALUE, retired.serialNumber(), RevocationReason.SUPERSEDED, second),
                "only through the authority that issued it");

        var revoked = repository.revokedBy(authorityId);
        assertEquals(
                List.of(active.serialNumber(), retired.serialNumber()),
                revoked.stream().map(RevokedKey::serialNumber).toList());
        assertEquals(
                List.of(RevocationReason.KEY_COMPROMISE, RevocationReason.SUPERSEDED),
                revoked.stream().map(RevokedKey::reason).toList());
        assertEquals(
                List.of(first, second),
                revoked.stream().map(RevokedKey::revokedAt).toList());
        assertEquals(List.of(), repository.revokedBy(Integer.MAX_VALUE));
    }

    @Test
    void aKeyIsFoundOnlyThroughItsOwnStation() {
        var station = stationRepo.create("Holding station");
        var other = stationRepo.create("Other holding station");
        int authorityId = authorityId();
        var held = key("held");
        repository.storeActive(station.id(), authorityId, held);

        assertEquals(Optional.of(authorityId), repository.authorityOfStationKey(station.id(), held.serialNumber()));
        assertTrue(repository
                .authorityOfStationKey(other.id(), held.serialNumber())
                .isEmpty());
        assertTrue(repository.authorityOfStationKey(station.id(), "ffff").isEmpty());
        assertTrue(repository.authorityOfDeletedStationKey(held.serialNumber()).isEmpty(), "its station exists");
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
        var issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        repository.storeRevocationList(authorityId, 1, new StoredRevocationList(bytes("list one"), issuedAt));

        var stored = repository.findRevocationList(authorityId).orElseThrow();
        assertArrayEquals(bytes("list one"), stored.list());
        assertEquals(issuedAt, stored.issuedAt());
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
