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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.feature.signing.service.SealedPdfs.indexOf;
import static dev.chojo.ember.feature.signing.service.SealedPdfs.referencedDataIntact;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Station signing keys created on first use under the installation's authority, stored wrapped, and
 * good for a seal a reader trusting the authority accepts.
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
            repository, new SigningCertificates(), new SigningKeyWrap(SECRET), stationRepo, INSTALLATION);

    @BeforeEach
    void startWithoutAuthority() {
        query("DELETE FROM station_signing_key;").single(call()).delete();
        query("DELETE FROM signing_ca;").single(call()).delete();
    }

    @Test
    void firstUseCreatesTheAuthorityAndTheStationKey() {
        var station = stationRepo.create("First use station");
        assertTrue(repository.findAuthority().isEmpty());
        assertTrue(repository.findActive(station.id()).isEmpty());

        var key = signingKeys.forStation(station.id());

        assertEquals(2, key.chain().size());
        var authority = repository.findAuthority().orElseThrow();
        var stationKey = repository.findActive(station.id()).orElseThrow();
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

        var sealed = new PdfSealer().seal(SealedPdfs.onePagePdf(), key.privateKey(), key.chain());
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
        int callers = 4;
        var start = new CountDownLatch(1);
        var results = new ArrayList<CompletableFuture<SealingKey>>();
        try (var executor = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                results.add(CompletableFuture.supplyAsync(
                        () -> {
                            awaitQuietly(start);
                            return signingKeys.forStation(station.id());
                        },
                        executor));
            }
            start.countDown();
            CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).get(2, TimeUnit.MINUTES);
        }

        var first = results.getFirst().join();
        for (var result : results) {
            assertEquals(first.certificate(), result.join().certificate());
            assertEquals(first.authority(), result.join().authority());
        }
        assertEquals(1, count("SELECT count(*) AS n FROM station_signing_key;"));
        assertEquals(1, count("SELECT count(*) AS n FROM signing_ca;"));
    }

    @Test
    void anUnknownStationIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> signingKeys.forStation(Integer.MAX_VALUE));
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

    private static int count(String sql) {
        return query(sql).single(call()).map(row -> row.getInt("n")).first().orElseThrow();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
