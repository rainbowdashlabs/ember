/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Re-wrapping moves every stored key, retired ones included, from one at-rest secret to another, and
 * changes nothing at all when a single key does not open with the old secret.
 */
class SigningKeyRewrapTest extends RepositoryTestBase {
    private static final String INSTALLATION = "ember.example.org";
    private static final SigningKeyWrap SECRET_A = new SigningKeyWrap(secret(1));
    private static final SigningKeyWrap SECRET_B = new SigningKeyWrap(secret(2));
    private static final SigningKeyWrap SECRET_C = new SigningKeyWrap(secret(3));
    private static final String ALL_WRAPPED_KEYS = """
            SELECT 'ca ' || id AS row_key, wrapped_private_key FROM signing_ca
            UNION ALL
            SELECT 'station ' || id AS row_key, wrapped_private_key FROM station_signing_key;""";

    private final SigningKeyRepository repository = new SigningKeyRepository();
    private final SigningCertificates certificates = new SigningCertificates();

    @BeforeEach
    void startWithoutKeys() {
        query("DELETE FROM station_signing_key;").single(call()).delete();
        query("DELETE FROM signing_ca;").single(call()).delete();
    }

    @Test
    void everyKeyMovesFromTheOldSecretToTheNewOne() {
        var underA = keysUnder(SECRET_A);
        var station = stationRepo.create("Rewrap station");
        var retired = underA.forStation(station.id());
        underA.rotate(station.id());
        query("UPDATE signing_ca SET valid_until = now() + INTERVAL '1 year';")
                .single(call())
                .update();
        var later = stationRepo.create("Rewrap station under the new authority");
        var activeBefore = underA.forStation(later.id());
        var before = wrappedKeys();
        assertEquals(5, before.size(), "two authorities, three station keys");

        int rewrapped = new SigningKeyRewrap(repository, SECRET_B).rewrapFrom(SECRET_A);

        assertEquals(before.size(), rewrapped);
        var after = wrappedKeys();
        assertEquals(before.keySet(), after.keySet());
        for (var wrapped : after.values()) {
            assertEquals(SigningKeyWrap.VERSION, wrapped[0]);
            SECRET_B.unwrap(wrapped);
            assertThrows(SigningKeyWrapException.class, () -> SECRET_A.unwrap(wrapped));
        }
        for (var entry : before.entrySet()) {
            assertEquals(SECRET_A.unwrap(entry.getValue()), SECRET_B.unwrap(after.get(entry.getKey())));
        }
        var underB = keysUnder(SECRET_B);
        assertEquals(activeBefore.privateKey(), underB.forStation(later.id()).privateKey());
        assertTrue(after.values().stream().map(SECRET_B::unwrap).anyMatch(key -> key.equals(retired.privateKey())));
    }

    @Test
    void aWrongOldSecretChangesNothing() {
        keysUnder(SECRET_A)
                .forStation(stationRepo.create("Wrong secret station").id());
        var before = wrappedKeys();

        var failure = assertThrows(
                SigningKeyWrapException.class, () -> new SigningKeyRewrap(repository, SECRET_B).rewrapFrom(SECRET_C));

        assertTrue(failure.getMessage().contains("nothing was changed"), failure.getMessage());
        assertUnchanged(before, wrappedKeys());
    }

    @Test
    void oneKeyUnderAnotherSecretRollsBackTheKeysAlreadyRewrapped() {
        var underA = keysUnder(SECRET_A);
        underA.forStation(stationRepo.create("Mixed station one").id());
        var odd = stationRepo.create("Mixed station two");
        underA.forStation(odd.id());
        var stray = SECRET_C.wrap(certificates.authority(INSTALLATION).privateKey());
        query("UPDATE station_signing_key SET wrapped_private_key = :wrapped WHERE station_id = :station_id;")
                .single(call().bind("wrapped", stray).bind("station_id", odd.id()))
                .update();
        var before = wrappedKeys();

        var failure = assertThrows(
                SigningKeyWrapException.class, () -> new SigningKeyRewrap(repository, SECRET_B).rewrapFrom(SECRET_A));

        assertTrue(failure.getMessage().contains("station key"), failure.getMessage());
        assertUnchanged(before, wrappedKeys());
    }

    private StationSigningKeys keysUnder(SigningKeyWrap wrap) {
        return new StationSigningKeys(repository, certificates, wrap, stationRepo, INSTALLATION);
    }

    private static void assertUnchanged(Map<String, byte[]> before, Map<String, byte[]> after) {
        assertEquals(before.keySet(), after.keySet());
        before.forEach((row, wrapped) -> assertArrayEquals(wrapped, after.get(row), row));
    }

    private static Map<String, byte[]> wrappedKeys() {
        List<Map.Entry<String, byte[]>> rows = query(ALL_WRAPPED_KEYS)
                .single(call())
                .map(row -> Map.entry(row.getString("row_key"), row.getBytes("wrapped_private_key")))
                .all();
        return rows.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String secret(int fill) {
        var bytes = new byte[32];
        Arrays.fill(bytes, (byte) fill);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
