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
import eu.europa.esig.dss.enumerations.SubIndication;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.cert.CRLReason;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static dev.chojo.ember.feature.signing.service.SigningFixtures.concurrently;
import static dev.chojo.ember.feature.signing.service.SigningFixtures.count;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Revoking station keys, active and retired, and the revocation lists of the authorities that issued
 * them: what they name, how they are signed and numbered, when they are signed anew, and what an offline
 * DSS validation makes of a sealed document once it has a list to consult.
 */
class StationKeyRevocationsTest extends RepositoryTestBase {
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    private final SigningKeyRepository repository = new SigningKeyRepository();
    private final SigningKeyWrap wrap = new SigningKeyWrap(SECRET);
    private final StationSigningKeys signingKeys = new StationSigningKeys(
            repository, new SigningCertificates(), wrap, stationRepo, "https://ember.example.org");
    private final RevocationLists lists = new RevocationLists();
    private final StationKeyRevocations revocations = new StationKeyRevocations(repository, lists, wrap);

    @BeforeEach
    void startWithoutAuthority() {
        query("DELETE FROM station_signing_key;").single(call()).delete();
        query("DELETE FROM signing_ca;").single(call()).delete();
    }

    @Test
    void revokingTheActiveKeyRetiresItAndTheNextSealGetsANewKey() {
        var station = stationRepo.create("Leaking station");
        var leaked = signingKeys.forStation(station.id());

        assertTrue(revocations.revoke(station.id(), serialOf(leaked), RevocationReason.KEY_COMPROMISE));

        assertEquals(1, count("SELECT count(*) AS n FROM station_signing_key WHERE retired_at IS NOT NULL;"));
        assertEquals(1, count("""
                        SELECT count(*) AS n
                        FROM station_signing_key
                        WHERE revoked_at IS NOT NULL
                          AND revocation_reason = 'KEY_COMPROMISE';"""));
        assertTrue(repository.findActive(station.id()).isEmpty());

        var next = signingKeys.forStation(station.id());
        assertNotEquals(
                leaked.certificate().getSerialNumber(), next.certificate().getSerialNumber());
        assertFalse(revocations.revoke(station.id(), serialOf(leaked), RevocationReason.SUPERSEDED), "revoked once");
        assertEquals(
                List.of(RevocationReason.KEY_COMPROMISE),
                repository
                        .revokedBy(repository
                                .findActive(station.id())
                                .orElseThrow()
                                .authorityId())
                        .stream()
                        .map(key -> key.reason())
                        .toList(),
                "the first reason stays");
    }

    @Test
    void aKeyRetiredByRotationCanStillBeRevoked() {
        var station = stationRepo.create("Late finding station");
        var old = signingKeys.forStation(station.id());
        var current = signingKeys.rotate(station.id());

        assertTrue(
                revocations.revoke(station.id(), serialOf(old).toUpperCase(Locale.ROOT), RevocationReason.SUPERSEDED));

        assertEquals(current.certificate(), signingKeys.forStation(station.id()).certificate(), "active key stays");
        assertEquals(1, count("SELECT count(*) AS n FROM station_signing_key WHERE revoked_at IS NOT NULL;"));
    }

    @Test
    void onlyAKeyOfTheStationItselfCanBeRevoked() {
        var station = stationRepo.create("Own key station");
        var other =
                signingKeys.forStation(stationRepo.create("Other key station").id());
        signingKeys.forStation(station.id());

        assertThrows(
                IllegalArgumentException.class,
                () -> revocations.revoke(station.id(), serialOf(other), RevocationReason.KEY_COMPROMISE));
        assertThrows(
                IllegalArgumentException.class,
                () -> revocations.revoke(station.id(), "1a2b3c", RevocationReason.KEY_COMPROMISE));
        assertThrows(
                IllegalArgumentException.class,
                () -> revocations.revoke(station.id(), "not hex", RevocationReason.KEY_COMPROMISE));
        assertEquals(0, count("SELECT count(*) AS n FROM station_signing_key WHERE revoked_at IS NOT NULL;"));
    }

    @Test
    void theListNamesExactlyTheRevokedKeysOfItsAuthority() throws Exception {
        var leaking = stationRepo.create("Listed station");
        var leaked = signingKeys.forStation(leaking.id());
        var untouched =
                signingKeys.forStation(stationRepo.create("Unlisted station").id());
        revocations.revoke(leaking.id(), serialOf(leaked), RevocationReason.KEY_COMPROMISE);
        var revokedAt = query("SELECT revoked_at FROM station_signing_key WHERE revoked_at IS NOT NULL;")
                .single(call())
                .map(row -> row.get("revoked_at", INSTANT_TIMESTAMP))
                .first()
                .orElseThrow();

        var list = listOf(leaked.authority());

        list.verify(leaked.authority().getPublicKey());
        assertEquals(2, list.getVersion());
        assertEquals(leaked.authority().getSubjectX500Principal(), list.getIssuerX500Principal());
        assertEquals("SHA256withRSA", list.getSigAlgName());
        assertEquals(1L, numberOf(list));
        assertArrayEquals(subjectKeyIdOf(leaked.authority()), authorityKeyIdOf(list));
        assertEquals(
                RevocationLists.VALIDITY,
                Duration.between(
                        list.getThisUpdate().toInstant(), list.getNextUpdate().toInstant()));
        assertFalse(list.getThisUpdate().toInstant().isAfter(Instant.now()));

        var entries = entriesOf(list);
        assertEquals(Set.of(serialOf(leaked)), entries.keySet());
        var entry = entries.get(serialOf(leaked));
        assertEquals(CRLReason.KEY_COMPROMISE, entry.getRevocationReason());
        assertEquals(
                revokedAt.truncatedTo(ChronoUnit.SECONDS),
                entry.getRevocationDate().toInstant());
        assertTrue(list.isRevoked(leaked.certificate()));
        assertFalse(list.isRevoked(untouched.certificate()));
    }

    @Test
    void theListIsKeptUntilARevocationAndThenNumberedOnward() throws Exception {
        var station = stationRepo.create("Numbered station");
        var first = signingKeys.forStation(station.id());
        var authoritySerial = SigningCertificates.serialOf(first.authority());
        revocations.revoke(station.id(), serialOf(first), RevocationReason.SUPERSEDED);

        var before = revocations.revocationList(authoritySerial).orElseThrow();
        assertArrayEquals(before, revocations.revocationList(authoritySerial).orElseThrow(), "kept, not signed anew");
        assertArrayEquals(
                before,
                revocations
                        .revocationList(authoritySerial.toUpperCase(Locale.ROOT))
                        .orElseThrow());

        var second = signingKeys.forStation(station.id());
        revocations.revoke(station.id(), serialOf(second), RevocationReason.CESSATION_OF_OPERATION);
        var after =
                RevocationLists.read(revocations.revocationList(authoritySerial).orElseThrow());

        assertEquals(numberOf(RevocationLists.read(before)) + 1, numberOf(after));
        var entries = entriesOf(after);
        assertEquals(Set.of(serialOf(first), serialOf(second)), entries.keySet());
        assertEquals(CRLReason.SUPERSEDED, entries.get(serialOf(first)).getRevocationReason());
        assertEquals(
                CRLReason.CESSATION_OF_OPERATION, entries.get(serialOf(second)).getRevocationReason());
    }

    @Test
    void aListOlderThanADayIsSignedAnew() throws Exception {
        var key =
                signingKeys.forStation(stationRepo.create("Stale list station").id());
        var authority = repository.findActiveAuthority().orElseThrow();
        var stale = lists.issue(
                wrap.open(authority.key()),
                5,
                List.of(),
                Instant.now()
                        .minus(StationKeyRevocations.REISSUE_AFTER)
                        .minusSeconds(60)
                        .truncatedTo(ChronoUnit.SECONDS));
        repository.storeRevocationList(authority.id(), 5, stale.getEncoded());

        var fresh = listOf(key.authority());

        assertEquals(6L, numberOf(fresh));
        assertTrue(fresh.getThisUpdate().after(stale.getThisUpdate()));
        assertEquals(6L, numberOf(listOf(key.authority())), "the new one is kept");
    }

    @Test
    void aRetiredAuthorityStillListsTheKeysItIssued() throws Exception {
        var station = stationRepo.create("Old authority station");
        var old = signingKeys.forStation(station.id());
        query("UPDATE signing_ca SET valid_until = now() + INTERVAL '4 years';")
                .single(call())
                .update();
        var newcomer = signingKeys.forStation(
                stationRepo.create("New authority station").id());
        assertNotEquals(old.authority(), newcomer.authority());

        revocations.revoke(station.id(), serialOf(old), RevocationReason.KEY_COMPROMISE);

        var oldList = listOf(old.authority());
        oldList.verify(old.authority().getPublicKey());
        assertEquals(Set.of(serialOf(old)), entriesOf(oldList).keySet());
        var newList = listOf(newcomer.authority());
        newList.verify(newcomer.authority().getPublicKey());
        assertEquals(Set.of(), entriesOf(newList).keySet());
        assertTrue(revocations.revocationList("ffff").isEmpty(), "no such authority");
        assertTrue(revocations.revocationList("../etc").isEmpty(), "not a serial number");
    }

    @Test
    void aDocumentSealedWithARevokedKeyIsReportedRevoked() throws Exception {
        var station = stationRepo.create("Revoked seal station");
        var key = signingKeys.forStation(station.id());
        var sealed = SealedPdfs.sealedWith(key);
        revocations.revoke(station.id(), serialOf(key), RevocationReason.KEY_COMPROMISE);

        var reports = SealedPdfs.validate(sealed, key.authority(), listBytes(key.authority()));

        var signature = reports.getDiagnosticData().getSignatures().getFirst();
        assertTrue(signature.isSignatureValid(), "the signature itself is intact");
        assertEquals(Indication.INDETERMINATE, reports.getSimpleReport().getIndication(signature.getId()));
        assertEquals(SubIndication.REVOKED_NO_POE, reports.getSimpleReport().getSubIndication(signature.getId()));
        var revocation = reports.getDiagnosticData()
                .getCertificateById(signature.getSigningCertificate().getId())
                .getCertificateRevocationData()
                .getFirst();
        assertFalse(revocation.getStatus().isGood());
        assertEquals(eu.europa.esig.dss.enumerations.RevocationReason.KEY_COMPROMISE, revocation.getReason());
    }

    @Test
    void aListThatDoesNotNameTheKeyGivesTheValidationItsRevocationData() throws Exception {
        var station = stationRepo.create("Good seal station");
        var key = signingKeys.forStation(station.id());
        var sealed = SealedPdfs.sealedWith(key);
        var other = stationRepo.create("Other revoked station");
        revocations.revoke(other.id(), serialOf(signingKeys.forStation(other.id())), RevocationReason.KEY_COMPROMISE);

        var reports = SealedPdfs.validate(sealed, key.authority(), listBytes(key.authority()));

        var signature = reports.getDiagnosticData().getSignatures().getFirst();
        var errors = SealedPdfs.validationErrors(reports);
        assertFalse(errors.contains("No revocation data found for the certificate!"), errors.toString());
        var revocation = reports.getDiagnosticData()
                .getCertificateById(signature.getSigningCertificate().getId())
                .getCertificateRevocationData()
                .getFirst();
        assertTrue(revocation.getStatus().isGood());
        assertEquals(Indication.TOTAL_PASSED, reports.getSimpleReport().getIndication(signature.getId()));
        assertEquals(List.of(), errors);
    }

    @Test
    void concurrentRevocationsRevokeOnce() throws Exception {
        var station = stationRepo.create("Concurrent revocation station");
        var key = signingKeys.forStation(station.id());

        var results =
                concurrently(4, () -> revocations.revoke(station.id(), serialOf(key), RevocationReason.KEY_COMPROMISE));

        assertEquals(1, results.stream().filter(revoked -> revoked).count());
        assertEquals(1, count("SELECT count(*) AS n FROM station_signing_key WHERE revoked_at IS NOT NULL;"));
        assertEquals(Set.of(serialOf(key)), entriesOf(listOf(key.authority())).keySet());
    }

    @Test
    void aRotationRacingARevocationStillLeavesOneUnrevokedActiveKey() throws Exception {
        var station = stationRepo.create("Racing station");
        var key = signingKeys.forStation(station.id());
        var turn = new AtomicInteger();

        SigningFixtures.<Object>concurrently(
                2,
                () -> turn.getAndIncrement() == 0
                        ? revocations.revoke(station.id(), serialOf(key), RevocationReason.KEY_COMPROMISE)
                        : signingKeys.rotate(station.id()));

        var active = signingKeys.forStation(station.id());
        assertNotEquals(key.certificate(), active.certificate());
        assertEquals(1, count("SELECT count(*) AS n FROM station_signing_key WHERE retired_at IS NULL;"));
        assertEquals(
                0,
                count(
                        "SELECT count(*) AS n FROM station_signing_key WHERE retired_at IS NULL AND revoked_at IS NOT NULL;"));
    }

    @Test
    void aListSignedWhileAKeyIsRevokedNeverHidesTheRevocation() throws Exception {
        var station = stationRepo.create("Listing race station");
        var key = signingKeys.forStation(station.id());
        var turn = new AtomicInteger();

        SigningFixtures.<Object>concurrently(
                2,
                () -> turn.getAndIncrement() == 0
                        ? revocations.revoke(station.id(), serialOf(key), RevocationReason.KEY_COMPROMISE)
                        : revocations.revocationList(SigningCertificates.serialOf(key.authority())));

        assertEquals(Set.of(serialOf(key)), entriesOf(listOf(key.authority())).keySet());
    }

    private static String serialOf(SealingKey key) {
        return SigningCertificates.serialOf(key.certificate());
    }

    private byte[] listBytes(X509Certificate authority) {
        return revocations
                .revocationList(SigningCertificates.serialOf(authority))
                .orElseThrow();
    }

    private X509CRL listOf(X509Certificate authority) {
        return RevocationLists.read(listBytes(authority));
    }

    private static Map<String, X509CRLEntry> entriesOf(X509CRL list) {
        var entries = list.getRevokedCertificates();
        if (entries == null) return Map.of();
        return entries.stream()
                .collect(Collectors.toMap(entry -> entry.getSerialNumber().toString(16), entry -> entry));
    }

    private static long numberOf(X509CRL list) throws Exception {
        var value = JcaX509ExtensionUtils.parseExtensionValue(list.getExtensionValue(Extension.cRLNumber.getId()));
        return ASN1Integer.getInstance(value).getValue().longValueExact();
    }

    private static byte[] authorityKeyIdOf(X509CRL list) throws Exception {
        var value = JcaX509ExtensionUtils.parseExtensionValue(
                list.getExtensionValue(Extension.authorityKeyIdentifier.getId()));
        return AuthorityKeyIdentifier.getInstance(value).getKeyIdentifierOctets();
    }

    private static byte[] subjectKeyIdOf(X509Certificate certificate) throws Exception {
        var value = JcaX509ExtensionUtils.parseExtensionValue(
                certificate.getExtensionValue(Extension.subjectKeyIdentifier.getId()));
        return SubjectKeyIdentifier.getInstance(value).getKeyIdentifier();
    }
}
