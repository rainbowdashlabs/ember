/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.SealingKey;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.cert.CertPathValidator;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static dev.chojo.ember.feature.signing.service.SealedPdfs.indexOf;
import static dev.chojo.ember.feature.signing.service.SealedPdfs.referencedDataIntact;
import static dev.chojo.ember.feature.signing.service.SigningFixtures.concurrently;
import static dev.chojo.ember.feature.signing.service.SigningFixtures.count;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Station signing keys created on first use under the installation's authority, stored wrapped, good
 * for a seal a reader trusting the authority accepts, rotated before they expire, and issued by a
 * renewed authority once the old one could no longer cover a full station term.
 */
class StationSigningKeysTest extends RepositoryTestBase {
    private static final String INSTALLATION = "ember.example.org";
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private static final int DIGITAL_SIGNATURE = 0;
    private static final int NON_REPUDIATION = 1;
    private static final int KEY_CERT_SIGN = 5;
    private static final int CRL_SIGN = 6;

    private final SigningKeyRepository repository = new SigningKeyRepository();
    private final StationSigningKeys signingKeys = new StationSigningKeys(
            repository, new SigningCertificates(), new SigningKeyWrap(SECRET), stationRepo, "https://" + INSTALLATION);

    @BeforeEach
    void startWithoutAuthority() {
        query("DELETE FROM station_signing_key;").single(call()).delete();
        query("DELETE FROM signing_ca;").single(call()).delete();
    }

    @Test
    void firstUseCreatesTheAuthorityAndTheStationKey() {
        var station = stationRepo.create("First use station");
        assertTrue(repository.findActiveAuthority().isEmpty());
        assertTrue(repository.findActive(station.id()).isEmpty());

        var key = signingKeys.forStation(station.id());

        assertEquals(2, key.chain().size());
        var authority = repository.findActiveAuthority().orElseThrow().key();
        var stationKey = repository.findActive(station.id()).orElseThrow().key();
        assertArrayEquals(authority.certificate(), encoded(key.authority()));
        assertArrayEquals(stationKey.certificate(), encoded(key.certificate()));
        assertEquals(SigningCertificates.serialOf(key.certificate()), stationKey.serialNumber());
        assertEquals(key.certificate().getNotAfter().toInstant(), stationKey.validUntil());
    }

    @Test
    void aSecondCallReturnsTheSameKey() {
        var station = stationRepo.create("Repeat station");

        var first = signingKeys.forStation(station.id());
        var second = signingKeys.forStation(station.id());

        assertEquals(first.certificate(), second.certificate());
        assertEquals(first.authority(), second.authority());
        assertArrayEquals(first.privateKey().getEncoded(), second.privateKey().getEncoded());
    }

    @Test
    void twoStationsShareTheAuthorityButNotTheKey() {
        var one = signingKeys.forStation(stationRepo.create("Station one").id());
        var other = signingKeys.forStation(stationRepo.create("Station two").id());

        assertEquals(one.authority(), other.authority());
        assertNotEquals(one.certificate(), other.certificate());
        assertNotEquals(one.certificate().getSerialNumber(), other.certificate().getSerialNumber());
        assertNotEquals(one.certificate().getPublicKey(), other.certificate().getPublicKey());
    }

    @Test
    void theStationCertificateChainsToTheAuthority() throws Exception {
        var station = stationRepo.create("Chained station");
        var key = signingKeys.forStation(station.id());
        var leaf = key.certificate();
        var authority = key.authority();

        authority.verify(authority.getPublicKey());
        leaf.verify(authority.getPublicKey());
        assertEquals(authority.getSubjectX500Principal(), leaf.getIssuerX500Principal());
        assertPathValidates(leaf, authority);

        assertTrue(authority.getBasicConstraints() >= 0, "authority is a CA");
        assertTrue(authority.getKeyUsage()[KEY_CERT_SIGN], "keyCertSign");
        assertTrue(authority.getKeyUsage()[CRL_SIGN], "cRLSign");
        assertFalse(authority.getKeyUsage()[DIGITAL_SIGNATURE], "authority does not seal documents");
        assertEquals(-1, leaf.getBasicConstraints(), "station certificate is no CA");
        assertTrue(leaf.getKeyUsage()[DIGITAL_SIGNATURE], "digitalSignature");
        assertTrue(leaf.getKeyUsage()[NON_REPUDIATION], "nonRepudiation");
        assertFalse(leaf.getKeyUsage()[KEY_CERT_SIGN], "station certificate signs no certificates");
        assertTrue(leaf.getCriticalExtensionOIDs().containsAll(Set.of("2.5.29.15", "2.5.29.19")));

        leaf.checkValidity();
        authority.checkValidity();
        assertAbout(Duration.ofDays(365L * SigningCertificates.STATION_YEARS), leaf);
        assertAbout(Duration.ofDays(365L * SigningCertificates.AUTHORITY_YEARS), authority);
        assertFalse(leaf.getNotAfter().after(authority.getNotAfter()));

        assertEquals(3072, ((RSAPublicKey) leaf.getPublicKey()).getModulus().bitLength());
        assertEquals(
                3072, ((RSAPublicKey) authority.getPublicKey()).getModulus().bitLength());
        var subject = leaf.getSubjectX500Principal().getName();
        assertTrue(subject.contains("CN=Chained station"), subject);
        assertTrue(subject.contains("O=" + INSTALLATION), subject);
        assertTrue(subject.contains(station.uid().toString()), subject);
        assertTrue(authority.getSubjectX500Principal().getName().contains("O=" + INSTALLATION));
    }

    @Test
    void theStationCertificateNamesItsAuthoritysRevocationList() throws Exception {
        var key = signingKeys.forStation(
                stationRepo.create("Distribution point station").id());

        assertEquals(
                List.of("https://" + INSTALLATION + "/api/v1/public/signing/ca/"
                        + SigningCertificates.serialOf(key.authority()) + ".crl"),
                SealedPdfs.distributionPointsOf(key.certificate()));
    }

    @Test
    void theStoredPrivateKeysAreNotInClear() {
        var station = stationRepo.create("Wrapped station");
        var key = signingKeys.forStation(station.id());

        var stationColumn = storedBytes("SELECT wrapped_private_key FROM station_signing_key;");
        var authorityColumn = storedBytes("SELECT wrapped_private_key FROM signing_ca;");

        assertEquals(SigningKeyWrap.VERSION, stationColumn[0]);
        assertEquals(SigningKeyWrap.VERSION, authorityColumn[0]);
        assertEquals(-1, indexOf(stationColumn, key.privateKey().getEncoded()));
        assertEquals(-1, indexOf(stationColumn, modulusOf(key.certificate())));
        assertEquals(-1, indexOf(authorityColumn, modulusOf(key.authority())));
        assertEquals(key.privateKey(), new SigningKeyWrap(SECRET).unwrap(stationColumn));
    }

    @Test
    void aDocumentSealedWithTheStationKeyValidatesTrustingTheAuthority() throws Exception {
        var key = signingKeys.forStation(stationRepo.create("Sealing station").id());

        var sealed = SealedPdfs.sealedWith(key);
        var reports = SealedPdfs.validate(sealed, key.authority());

        var signature = reports.getDiagnosticData().getSignatures().getFirst();
        assertEquals(SignatureLevel.PAdES_BASELINE_B, signature.getSignatureFormat());
        assertTrue(signature.isSignatureIntact(), "signature value");
        assertTrue(signature.isSignatureValid(), "signature value and signed data");
        assertTrue(referencedDataIntact(signature), "signed data");
        assertEquals(
                key.certificate().getSerialNumber().toString(),
                signature.getSigningCertificate().getSerialNumber());
        var chain = signature.getCertificateChain();
        assertEquals(2, chain.size());
        assertTrue(chain.getLast().isTrusted(), "the authority is the trust anchor");
        assertEquals(
                Indication.INDETERMINATE,
                reports.getSimpleReport().getIndication(signature.getId()),
                "no revocation data is embedded yet, which is all that keeps it from passing");
        assertEquals(
                List.of(
                        "The certificate validation is not conclusive!",
                        "No revocation data found for the certificate!"),
                SealedPdfs.validationErrors(reports));
    }

    @Test
    void concurrentFirstUseStoresOneKey() throws Exception {
        var station = stationRepo.create("Concurrent station");

        var results = concurrently(4, () -> signingKeys.forStation(station.id()));

        var first = results.getFirst();
        for (var result : results) {
            assertEquals(first.certificate(), result.certificate());
            assertEquals(first.authority(), result.authority());
        }
        assertEquals(1, count("SELECT count(*) AS n FROM station_signing_key;"));
        assertEquals(1, count("SELECT count(*) AS n FROM signing_ca;"));
    }

    @Test
    void anUnknownStationIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> signingKeys.forStation(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> signingKeys.rotate(Integer.MAX_VALUE));
    }

    @Test
    void rotationRetiresTheOldKeyAndDocumentsSealedWithItStillValidate() throws Exception {
        var station = stationRepo.create("Rotating station");
        var old = signingKeys.forStation(station.id());
        var sealedBefore = SealedPdfs.sealedWith(old);

        var rotated = signingKeys.rotate(station.id());

        assertNotEquals(
                old.certificate().getSerialNumber(), rotated.certificate().getSerialNumber());
        assertNotEquals(old.certificate().getPublicKey(), rotated.certificate().getPublicKey());
        assertEquals(old.authority(), rotated.authority());
        assertEquals(rotated.certificate(), signingKeys.forStation(station.id()).certificate());
        assertEquals(2, count("SELECT count(*) AS n FROM station_signing_key;"));
        assertEquals(1, count("SELECT count(*) AS n FROM station_signing_key WHERE retired_at IS NOT NULL;"));
        assertEquals(
                SigningCertificates.serialOf(old.certificate()),
                query("SELECT serial_number FROM station_signing_key WHERE retired_at IS NOT NULL;")
                        .single(call())
                        .map(row -> row.getString("serial_number"))
                        .first()
                        .orElseThrow());

        assertValidatesAsBefore(sealedBefore, old);
        var sealedAfter = SealedPdfs.sealedWith(rotated);
        assertValidatesAsBefore(sealedAfter, rotated);
    }

    @Test
    void aKeyCloseToExpiryIsRotatedOnItsNextUse() {
        var station = stationRepo.create("Expiring station");
        var old = signingKeys.forStation(station.id());

        setStationKeyValidity(StationSigningKeys.ROTATION_MARGIN.plusDays(1));
        assertEquals(old.certificate(), signingKeys.forStation(station.id()).certificate(), "not yet due");

        setStationKeyValidity(StationSigningKeys.ROTATION_MARGIN.minusDays(1));
        var next = signingKeys.forStation(station.id());

        assertNotEquals(old.certificate().getSerialNumber(), next.certificate().getSerialNumber());
        assertEquals(2, count("SELECT count(*) AS n FROM station_signing_key;"));
        assertEquals(1, count("SELECT count(*) AS n FROM station_signing_key WHERE retired_at IS NULL;"));
        assertEquals(next.certificate(), signingKeys.forStation(station.id()).certificate());
    }

    @Test
    void concurrentRotationLeavesOneActiveKey() throws Exception {
        var station = stationRepo.create("Concurrent rotation station");
        var old = signingKeys.forStation(station.id());

        var results = concurrently(4, () -> signingKeys.rotate(station.id()));

        var first = results.getFirst();
        assertNotEquals(old.certificate(), first.certificate());
        for (var result : results) assertEquals(first.certificate(), result.certificate());
        assertEquals(2, count("SELECT count(*) AS n FROM station_signing_key;"));
        assertEquals(1, count("SELECT count(*) AS n FROM station_signing_key WHERE retired_at IS NULL;"));
    }

    @Test
    void anAuthorityThatCannotCoverAFullStationTermIsRenewed() throws Exception {
        var existing = stationRepo.create("Station under the old authority");
        var before = signingKeys.forStation(existing.id());
        var oldAuthority = before.authority();
        query("UPDATE signing_ca SET valid_until = now() + INTERVAL '4 years';")
                .single(call())
                .update();

        assertEquals(oldAuthority, signingKeys.forStation(existing.id()).authority(), "no issuing, no renewal");
        assertEquals(1, count("SELECT count(*) AS n FROM signing_ca;"));

        var newcomer = signingKeys.forStation(
                stationRepo.create("Station under the new authority").id());

        var newAuthority = newcomer.authority();
        assertNotEquals(oldAuthority, newAuthority);
        assertEquals(2, count("SELECT count(*) AS n FROM signing_ca;"));
        assertEquals(1, count("SELECT count(*) AS n FROM signing_ca WHERE retired_at IS NULL;"));
        assertArrayEquals(
                encoded(newAuthority),
                repository.findActiveAuthority().orElseThrow().key().certificate());
        assertPathValidates(newcomer.certificate(), newAuthority);
        assertAbout(Duration.ofDays(365L * SigningCertificates.STATION_YEARS), newcomer.certificate());

        var stillOld = signingKeys.forStation(existing.id());
        assertEquals(before.certificate(), stillOld.certificate());
        assertEquals(oldAuthority, stillOld.authority());
        assertPathValidates(stillOld.certificate(), oldAuthority);

        var rotated = signingKeys.rotate(existing.id());
        assertEquals(newAuthority, rotated.authority());
        assertPathValidates(rotated.certificate(), newAuthority);
    }

    @Test
    void concurrentRenewalStoresOneNewAuthority() throws Exception {
        signingKeys.forStation(stationRepo.create("Renewal seed station").id());
        query("UPDATE signing_ca SET valid_until = now() + INTERVAL '1 year';")
                .single(call())
                .update();
        var stations = List.of(
                stationRepo.create("Renewal station one").id(),
                stationRepo.create("Renewal station two").id(),
                stationRepo.create("Renewal station three").id());
        var next = new AtomicInteger();

        var results = concurrently(stations.size(), () -> signingKeys.forStation(stations.get(next.getAndIncrement())));

        for (var result : results) assertEquals(results.getFirst().authority(), result.authority());
        assertEquals(2, count("SELECT count(*) AS n FROM signing_ca;"));
        assertEquals(1, count("SELECT count(*) AS n FROM signing_ca WHERE retired_at IS NULL;"));
    }

    @Test
    void theInstallationIsNamedByTheHostOfTheBaseAddress() {
        assertEquals("ember.example.org", StationSigningKeys.installationOf("https://ember.example.org/app"));
        assertEquals("localhost", StationSigningKeys.installationOf("http://localhost:3000"));
        assertEquals("no address", StationSigningKeys.installationOf("no address"));
        assertEquals("bad uri", StationSigningKeys.installationOf("bad uri"));
    }

    private static void assertPathValidates(X509Certificate leaf, X509Certificate authority) throws Exception {
        var path = CertificateFactory.getInstance("X.509").generateCertPath(List.of(leaf));
        var parameters = new PKIXParameters(Set.of(new TrustAnchor(authority, null)));
        parameters.setRevocationEnabled(false);
        CertPathValidator.getInstance("PKIX").validate(path, parameters);
    }

    private static void assertAbout(Duration expected, X509Certificate certificate) {
        var actual = Duration.between(
                certificate.getNotBefore().toInstant(),
                certificate.getNotAfter().toInstant());
        assertTrue(actual.minus(expected).abs().compareTo(Duration.ofDays(6)) < 0, actual.toString());
        assertTrue(certificate.getNotBefore().toInstant().isBefore(Instant.now()));
    }

    private static byte[] modulusOf(X509Certificate certificate) {
        return ((RSAPublicKey) certificate.getPublicKey()).getModulus().toByteArray();
    }

    private static byte[] encoded(X509Certificate certificate) {
        try {
            return certificate.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] storedBytes(String sql) {
        return query(sql)
                .single(call())
                .map(row -> row.getBytes("wrapped_private_key"))
                .first()
                .orElseThrow();
    }

    private static void assertValidatesAsBefore(byte[] sealed, SealingKey key) {
        var reports = SealedPdfs.validate(sealed, key.authority());
        var signature = reports.getDiagnosticData().getSignatures().getFirst();
        assertTrue(signature.isSignatureValid(), "signature value and signed data");
        assertTrue(referencedDataIntact(signature), "signed data");
        assertEquals(
                key.certificate().getSerialNumber().toString(),
                signature.getSigningCertificate().getSerialNumber());
        assertTrue(signature.getCertificateChain().getLast().isTrusted(), "the issuing authority is the anchor");
        assertEquals(Indication.INDETERMINATE, reports.getSimpleReport().getIndication(signature.getId()));
        assertEquals(
                List.of(
                        "The certificate validation is not conclusive!",
                        "No revocation data found for the certificate!"),
                SealedPdfs.validationErrors(reports));
    }

    private static void setStationKeyValidity(Duration left) {
        query("UPDATE station_signing_key SET valid_until = :valid_until WHERE retired_at IS NULL;")
                .single(call().bind("valid_until", Instant.now().plus(left), INSTANT_TIMESTAMP))
                .update();
    }
}
