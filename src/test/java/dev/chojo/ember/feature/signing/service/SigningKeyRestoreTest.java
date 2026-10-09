/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.RevocationReason;
import dev.chojo.ember.feature.signing.entity.SealingKey;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import eu.europa.esig.dss.enumerations.Indication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.feature.signing.service.SigningFixtures.count;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Restoring the signing keys from a database backup: the rows come back exactly as they were stored,
 * wrapped keys included, through the same {@code COPY} data stream {@code pg_dump} writes. With the
 * at-rest secret of the same backup every key opens, each station seals with the key it had and the
 * authority still signs its revocation list; with any other secret nothing opens and the refusal names
 * the key file to restore.
 */
class SigningKeyRestoreTest extends RepositoryTestBase {
    private static final String INSTALLATION = "https://ember.example.org";
    private static final List<String> TABLES_IN_RESTORE_ORDER = List.of("signing_ca", "station_signing_key");
    private static final String STATION_KEYS = "SELECT count(*) AS n FROM station_signing_key;";

    private final SigningKeyRepository repository = new SigningKeyRepository();

    @BeforeEach
    void startWithoutKeys() {
        wipeSigningTables();
    }

    @Test
    void keysRestoredWithTheirKeyFileSealAsBefore() throws Exception {
        var sealing = keysUnder(new SigningKeyWrap(secret(1)));
        var first = stationRepo.create("Restored station one");
        var second = stationRepo.create("Restored station two");
        var firstKey = sealing.forStation(first.id());
        var secondKey = sealing.forStation(second.id());
        var sealedBefore = seal(firstKey);

        restoreFrom(dumpSigningTables());

        var restoredWrap = new SigningKeyWrap(secret(1));
        var restored = keysUnder(restoredWrap);
        var firstAgain = restored.forStation(first.id());
        assertEquals(serialOf(firstKey.certificate()), serialOf(firstAgain.certificate()));
        assertEquals(firstKey.privateKey(), firstAgain.privateKey());
        assertEquals(firstKey.authority(), firstAgain.authority());
        assertEquals(
                serialOf(secondKey.certificate()),
                serialOf(restored.forStation(second.id()).certificate()));
        assertEquals(2, count(STATION_KEYS), "no key was issued anew");

        var revocations = new StationKeyRevocations(repository, new RevocationLists(), restoredWrap);
        assertTrue(revocations.revoke(second.id(), serialOf(secondKey.certificate()), RevocationReason.KEY_COMPROMISE));
        var list = revocations.revocationList(serialOf(firstKey.authority())).orElseThrow();
        var crl = RevocationLists.read(list);
        crl.verify(firstKey.authority().getPublicKey());
        assertNotNull(crl.getRevokedCertificate(secondKey.certificate()));

        var sealedAfter = seal(firstAgain);
        for (var sealed : List.of(sealedBefore, sealedAfter)) {
            var reports = SealedPdfs.validate(sealed, firstKey.authority(), list);
            var signature = reports.getDiagnosticData().getSignatures().getFirst();
            assertEquals(Indication.TOTAL_PASSED, reports.getSimpleReport().getIndication(signature.getId()));
            assertEquals(List.of(), SealedPdfs.validationErrors(reports));
        }
    }

    @Test
    void keysRestoredWithoutTheirKeyFileDoNotOpenAndNameTheFile() throws Exception {
        var sealing = keysUnder(new SigningKeyWrap(secret(1)));
        var station = stationRepo.create("Station whose key file was lost");
        var key = sealing.forStation(station.id());

        restoreFrom(dumpSigningTables());

        var otherWrap = new SigningKeyWrap(secret(2));
        var failure = assertThrows(
                SigningKeyWrapException.class, () -> keysUnder(otherWrap).forStation(station.id()));
        assertTrue(failure.getMessage().contains("encryption.key"), failure.getMessage());
        assertTrue(failure.getMessage().contains("storage.credentialEncryptionKey"), failure.getMessage());
        assertEquals(1, count(STATION_KEYS), "the stored key is kept, not replaced");

        var revocations = new StationKeyRevocations(repository, new RevocationLists(), otherWrap);
        assertThrows(SigningKeyWrapException.class, () -> revocations.revocationList(serialOf(key.authority())));
    }

    private StationSigningKeys keysUnder(SigningKeyWrap wrap) {
        return new StationSigningKeys(repository, new SigningCertificates(), wrap, stationRepo, INSTALLATION);
    }

    private static byte[] seal(SealingKey key) throws Exception {
        return SealedPdfs.sealedWith(key);
    }

    private static String serialOf(X509Certificate certificate) {
        return SigningCertificates.serialOf(certificate);
    }

    private static Map<String, byte[]> dumpSigningTables() throws Exception {
        var dump = new LinkedHashMap<String, byte[]>();
        try (var connection = dataSource.getConnection()) {
            var copy = connection.unwrap(PGConnection.class).getCopyAPI();
            for (var table : TABLES_IN_RESTORE_ORDER) {
                var out = new ByteArrayOutputStream();
                copy.copyOut("COPY " + table + " TO STDOUT (FORMAT binary)", out);
                dump.put(table, out.toByteArray());
            }
        }
        return dump;
    }

    private static void restoreFrom(Map<String, byte[]> dump) throws Exception {
        wipeSigningTables();
        assertEquals(0, count(STATION_KEYS));
        try (var connection = dataSource.getConnection()) {
            var copy = connection.unwrap(PGConnection.class).getCopyAPI();
            for (var table : TABLES_IN_RESTORE_ORDER) {
                copy.copyIn("COPY " + table + " FROM STDIN (FORMAT binary)", new ByteArrayInputStream(dump.get(table)));
            }
        }
    }

    private static void wipeSigningTables() {
        query("DELETE FROM station_signing_key;").single(call()).delete();
        query("DELETE FROM signing_ca;").single(call()).delete();
    }

    private static String secret(int fill) {
        var bytes = new byte[32];
        Arrays.fill(bytes, (byte) fill);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
