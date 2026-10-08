/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.signing.entity.LockedSigningKey;
import dev.chojo.ember.feature.signing.entity.RevocationReason;
import dev.chojo.ember.feature.signing.entity.SigningAuthorityInfo;
import dev.chojo.ember.feature.signing.entity.SigningKeyKind;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.validation.reports.Reports;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.feature.signing.service.SigningFixtures.count;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recovering from a lost at-rest secret: keys that no longer open are shown and stay exactly as they
 * are until an administrator gives them up with the list they were shown; then the next seal issues a
 * new authority and new station keys, while the old authority's certificate and last revocation list
 * stay published for the documents sealed before.
 */
class SigningKeyRecoveryTest extends RepositoryTestBase {
    private static final String INSTALLATION = "https://ember.example.org";
    private static final String AUTHORITIES = "SELECT count(*) AS n FROM signing_ca;";
    private static final String STATION_KEYS = "SELECT count(*) AS n FROM station_signing_key;";
    private static final String RETIRED = """
            SELECT (SELECT count(*) FROM signing_ca WHERE retired_at IS NOT NULL)
                       + (SELECT count(*) FROM station_signing_key WHERE retired_at IS NOT NULL) AS n;""";
    private static final String GIVEN_UP = """
            SELECT (SELECT count(*) FROM signing_ca WHERE abandoned_at IS NOT NULL)
                       + (SELECT count(*) FROM station_signing_key WHERE abandoned_at IS NOT NULL) AS n;""";
    private static final String RECOVERIES = "SELECT count(*) AS n FROM signing_key_recovery;";

    private final SigningKeyRepository repository = new SigningKeyRepository();
    private final SigningKeyWrap before = new SigningKeyWrap(secret(1));
    private final SigningKeyWrap after = new SigningKeyWrap(secret(2));

    private Account administrator;
    private Station station;

    @BeforeEach
    void startWithoutKeys() {
        query("DELETE FROM signing_key_recovery;").single(call()).delete();
        query("DELETE FROM station_signing_key;").single(call()).delete();
        query("DELETE FROM signing_ca;").single(call()).delete();
        administrator = accountRepo.create("recovery-" + UUID.randomUUID() + "@example.org", "Ada", "Admin");
        station = stationRepo.create("Station " + UUID.randomUUID());
    }

    @Test
    void aWrongSecretChangesNothingOnItsOwn() {
        var key = keysUnder(before).forStation(station.id());
        var revocations = revocationsUnder(after);
        var sealing = keysUnder(after);

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThrows(SigningKeyWrapException.class, () -> sealing.forStation(station.id()));
            assertThrows(SigningKeyWrapException.class, () -> sealing.rotate(station.id()));
            assertThrows(SigningKeyWrapException.class, () -> revocations.revocationList(serialOf(key.authority())));
        }
        var recovery = recoveryUnder(after);
        var status = recovery.status();

        assertEquals(1, count(AUTHORITIES), "no authority was created");
        assertEquals(1, count(STATION_KEYS), "no station key was created");
        assertEquals(0, count(RETIRED), "nothing was retired");
        assertEquals(0, count(GIVEN_UP), "nothing was given up");
        assertEquals(0, count(RECOVERIES));
        assertEquals(2, recovery.lockedCount());
        assertEquals(0, status.openKeys());
        assertEquals(List.of(), status.recoveries());
        assertEquals(
                List.of(
                        new LockedSigningKey(
                                SigningKeyKind.AUTHORITY,
                                serialOf(key.authority()),
                                PublishedCertificates.fingerprintOf(encoded(key.authority())),
                                true,
                                null,
                                key.authority().getNotAfter().toInstant()),
                        new LockedSigningKey(
                                SigningKeyKind.STATION_KEY,
                                serialOf(key.certificate()),
                                PublishedCertificates.fingerprintOf(encoded(key.certificate())),
                                true,
                                station.name(),
                                key.certificate().getNotAfter().toInstant())),
                status.locked());
        assertEquals(
                key.privateKey(), keysUnder(before).forStation(station.id()).privateKey(), "the old key still opens");
    }

    @Test
    void everyKeyOpenGivesNothingUp() {
        keysUnder(before).forStation(station.id());
        var recovery = recoveryUnder(before);

        assertEquals(0, recovery.lockedCount());
        assertEquals(2, recovery.status().openKeys());
        var refused = assertThrows(RefusalResponse.class, () -> recovery.recover(administrator.id(), List.of()));
        assertEquals(DocumentRefusal.SIGNING_KEYS_ALL_OPEN, refused.refusal());
        assertEquals(0, count(GIVEN_UP));
        assertEquals(0, count(RECOVERIES));
    }

    @Test
    void onlyExactlyTheKeysShownAreGivenUp() {
        var key = keysUnder(before).forStation(station.id());
        var recovery = recoveryUnder(after);
        var authority = serialOf(key.authority());
        var stationKey = serialOf(key.certificate());

        for (var confirmed : List.of(
                List.<String>of(),
                List.of(authority),
                List.of(stationKey),
                List.of(authority, stationKey, "abc123"),
                List.of(authority, "not a serial"))) {
            var refused = assertThrows(
                    RefusalResponse.class, () -> recovery.recover(administrator.id(), confirmed), confirmed::toString);
            assertEquals(DocumentRefusal.SIGNING_KEYS_CHANGED, refused.refusal());
        }
        assertEquals(0, count(RETIRED));
        assertEquals(0, count(GIVEN_UP));
        assertEquals(0, count(RECOVERIES));

        var entry = recovery.recover(
                administrator.id(), List.of(stationKey.toUpperCase(Locale.ROOT), " " + authority + " "));
        assertEquals(List.of(authority), entry.authoritySerials());
        assertEquals(List.of(stationKey), entry.stationKeySerials());
    }

    @Test
    void givingUpLetsTheNextSealIssueANewAuthorityAndKey() throws Exception {
        var old = keysUnder(before).forStation(station.id());
        var sealedBefore = SealedPdfs.sealedWith(old);
        var oldList = revocationsUnder(before)
                .revocationList(serialOf(old.authority()))
                .orElseThrow();
        var recovery = recoveryUnder(after);

        var entry =
                recovery.recover(administrator.id(), serialsOf(recovery.status().locked()));

        assertEquals("Ada Admin", entry.recoveredBy());
        assertEquals(List.of(serialOf(old.authority())), entry.authoritySerials());
        assertEquals(List.of(serialOf(old.certificate())), entry.stationKeySerials());
        assertEquals(2, count(GIVEN_UP));
        assertEquals(2, count(RETIRED));
        assertEquals(1, count(RECOVERIES));

        var fresh = keysUnder(after).forStation(station.id());
        assertNotEquals(serialOf(old.authority()), serialOf(fresh.authority()));
        assertNotEquals(serialOf(old.certificate()), serialOf(fresh.certificate()));
        assertEquals(2, count(AUTHORITIES));
        assertEquals(2, count(STATION_KEYS));

        var newList = revocationsUnder(after)
                .revocationList(serialOf(fresh.authority()))
                .orElseThrow();
        var sealedAfter = SealedPdfs.sealedWith(fresh);
        assertPassed(SealedPdfs.validate(sealedAfter, fresh.authority(), newList));
        assertPassed(SealedPdfs.validate(sealedBefore, old.authority(), oldList));

        var published = new PublishedCertificates(repository, revocationsUnder(after), stationRepo);
        var authorities = published.authorities();
        assertEquals(
                List.of(serialOf(fresh.authority()), serialOf(old.authority())),
                authorities.stream().map(SigningAuthorityInfo::serialNumber).toList());
        assertFalse(authorities.get(1).active());
        assertArrayEquals(
                encoded(old.authority()),
                published.authorityCertificate(serialOf(old.authority())).orElseThrow());
        assertEquals(
                List.of(serialOf(fresh.authority()), serialOf(old.authority())),
                published.authoritiesOfStation(station.id()).stream()
                        .map(SigningAuthorityInfo::serialNumber)
                        .toList());

        var status = recovery.status();
        assertEquals(List.of(), status.locked());
        assertEquals(2, status.openKeys());
        assertEquals(List.of(entry), status.recoveries());
        assertEquals(0, recovery.lockedCount());
    }

    @Test
    void theGivenUpAuthorityKeepsServingItsLastListWithTheRevokedKeys() throws Exception {
        var keys = keysUnder(before);
        var first = keys.forStation(station.id());
        keys.rotate(station.id());
        var revocations = revocationsUnder(before);
        assertTrue(revocations.revoke(station.id(), serialOf(first.certificate()), RevocationReason.KEY_COMPROMISE));
        var lastList = revocations.revocationList(serialOf(first.authority())).orElseThrow();
        var recovery = recoveryUnder(after);
        recovery.recover(administrator.id(), serialsOf(recovery.status().locked()));
        query("UPDATE signing_ca SET crl_issued_at = crl_issued_at - INTERVAL '30 days' WHERE crl IS NOT NULL;")
                .single(call())
                .update();

        var served = revocationsUnder(after)
                .revocationList(serialOf(first.authority()))
                .orElseThrow();

        assertArrayEquals(lastList, served, "the list is served as it was, not signed anew");
        var crl = RevocationLists.read(served);
        crl.verify(first.authority().getPublicKey());
        assertNotNull(crl.getRevokedCertificate(first.certificate()));
        assertEquals(1, count("SELECT count(*) AS n FROM station_signing_key WHERE revoked_at IS NOT NULL;"));
        assertThrows(IllegalStateException.class, () -> revocationsUnder(after)
                .revoke(station.id(), serialOf(first.certificate()), RevocationReason.SUPERSEDED));
    }

    @Test
    void aGivenUpAuthorityThatOpensAgainSignsFreshListsAsBefore() {
        var key = keysUnder(before).forStation(station.id());
        var recovery = recoveryUnder(after);
        recovery.recover(administrator.id(), serialsOf(recovery.status().locked()));
        query("UPDATE signing_ca SET crl_issued_at = crl_issued_at - INTERVAL '30 days' WHERE crl IS NOT NULL;")
                .single(call())
                .update();

        var list = revocationsUnder(before)
                .revocationList(serialOf(key.authority()))
                .orElseThrow();

        assertTrue(RevocationLists.read(list)
                .getThisUpdate()
                .toInstant()
                .isAfter(Instant.now().minusSeconds(60)));
    }

    @Test
    void aStationKeyThatAloneNoLongerOpensIsReplacedUnderTheSameAuthority() {
        var old = keysUnder(before).forStation(station.id());
        replaceWrap("station_signing_key", serialOf(old.certificate()), after.wrap(old.privateKey()));
        assertThrows(SigningKeyWrapException.class, () -> keysUnder(before).forStation(station.id()));
        var recovery = recoveryUnder(before);

        var entry = recovery.recover(administrator.id(), List.of(serialOf(old.certificate())));

        assertEquals(List.of(), entry.authoritySerials());
        var fresh = keysUnder(before).forStation(station.id());
        assertEquals(serialOf(old.authority()), serialOf(fresh.authority()));
        assertNotEquals(serialOf(old.certificate()), serialOf(fresh.certificate()));
        assertEquals(1, count(AUTHORITIES));
    }

    @Test
    void aStationKeyOfAGivenUpAuthorityIsRetiredEvenWhenItStillOpens() {
        var old = keysUnder(before).forStation(station.id());
        var authoritySerial = serialOf(old.authority());
        replaceWrap("signing_ca", authoritySerial, wrappedUnderAnotherSecret());
        var recovery = recoveryUnder(before);
        assertEquals(List.of(authoritySerial), serialsOf(recovery.status().locked()));

        recovery.recover(administrator.id(), List.of(authoritySerial));

        assertEquals(
                1,
                count("SELECT count(*) AS n FROM station_signing_key WHERE retired_at IS NOT NULL"
                        + " AND abandoned_at IS NULL;"));
        var fresh = keysUnder(before).forStation(station.id());
        assertNotEquals(authoritySerial, serialOf(fresh.authority()));
        assertNotEquals(serialOf(old.certificate()), serialOf(fresh.certificate()));
    }

    @Test
    void reWrappingPassesOverTheKeysGivenUp() {
        keysUnder(before).forStation(station.id());
        var recovery = recoveryUnder(after);
        recovery.recover(administrator.id(), serialsOf(recovery.status().locked()));
        keysUnder(after).forStation(station.id());

        var later = new SigningKeyWrap(secret(3));
        assertEquals(2, new SigningKeyRewrap(repository, later).rewrapFrom(after));
        keysUnder(later).forStation(station.id());
        assertEquals(2, count(GIVEN_UP));
    }

    @Test
    void aDeletedAccountLeavesTheRecoveryWithoutAName() {
        keysUnder(before).forStation(station.id());
        var recovery = recoveryUnder(after);
        recovery.recover(administrator.id(), serialsOf(recovery.status().locked()));

        query("DELETE FROM account WHERE id = :id;")
                .single(call().bind("id", administrator.id()))
                .delete();

        var entry = recovery.status().recoveries().getFirst();
        assertNull(entry.recoveredBy());
        assertEquals(1, entry.authoritySerials().size());
    }

    private StationSigningKeys keysUnder(SigningKeyWrap wrap) {
        return new StationSigningKeys(repository, new SigningCertificates(), wrap, stationRepo, INSTALLATION);
    }

    private StationKeyRevocations revocationsUnder(SigningKeyWrap wrap) {
        return new StationKeyRevocations(repository, new RevocationLists(), wrap);
    }

    private SigningKeyRecovery recoveryUnder(SigningKeyWrap wrap) {
        return new SigningKeyRecovery(repository, wrap);
    }

    private byte[] wrappedUnderAnotherSecret() {
        return after.wrap(new SigningCertificates().authority(INSTALLATION).privateKey());
    }

    private static void replaceWrap(String table, String serial, byte[] wrapped) {
        query("UPDATE %s SET wrapped_private_key = :wrapped WHERE serial_number = :serial;", table)
                .single(call().bind("wrapped", wrapped).bind("serial", serial))
                .update();
    }

    private static void assertPassed(Reports reports) {
        var signature = reports.getDiagnosticData().getSignatures().getFirst();
        assertEquals(Indication.TOTAL_PASSED, reports.getSimpleReport().getIndication(signature.getId()));
    }

    private static List<String> serialsOf(List<LockedSigningKey> locked) {
        return locked.stream().map(LockedSigningKey::serialNumber).toList();
    }

    private static String serialOf(X509Certificate certificate) {
        return SigningCertificates.serialOf(certificate);
    }

    private static byte[] encoded(X509Certificate certificate) {
        try {
            return certificate.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String secret(int fill) {
        var bytes = new byte[32];
        Arrays.fill(bytes, (byte) fill);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
