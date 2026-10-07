/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.RevocationReason;
import dev.chojo.ember.feature.signing.entity.SealLevel;
import dev.chojo.ember.feature.signing.entity.SealingKey;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import eu.europa.esig.dss.alert.exception.AlertException;
import eu.europa.esig.dss.diagnostic.CertificateWrapper;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.validation.reports.Reports;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.feature.signing.service.SealedPdfs.embeddedRevocationLists;
import static dev.chojo.ember.feature.signing.service.SealedPdfs.validationErrors;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sealing with station keys up to {@code BASELINE-LT}: the station chain with its authority's list from
 * the database, and the timestamp service's chain with its status answer or its list fetched from a local
 * server, all inside one budget per seal, and never from an address that is not HTTP. Nothing here
 * reaches the internet.
 */
class PdfSealerLongTermTest extends RepositoryTestBase {
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration SHORT_BUDGET = Duration.ofSeconds(1);

    private static byte[] pdf;

    private final SigningKeyRepository repository = new SigningKeyRepository();
    private final SigningKeyWrap wrap = new SigningKeyWrap(SECRET);
    private final StationSigningKeys signingKeys = new StationSigningKeys(
            repository, new SigningCertificates(), wrap, stationRepo, "https://ember.example.org");
    private final StationKeyRevocations revocations =
            new StationKeyRevocations(repository, new RevocationLists(), wrap);

    private LocalTimestampService service;

    @BeforeAll
    static void createDocument() throws Exception {
        pdf = SealedPdfs.onePagePdf();
    }

    @BeforeEach
    void startWithoutAuthority() throws Exception {
        query("DELETE FROM station_signing_key;").single(call()).delete();
        query("DELETE FROM signing_ca;").single(call()).delete();
        service = LocalTimestampService.start();
    }

    @AfterEach
    void stopService() {
        service.close();
    }

    @Test
    void sealReachesLongTermAndValidatesOffline() {
        var key = keyOf("Long term station");

        var result = sealerAsking(service.url()).seal(pdf, key.privateKey(), key.chain());

        assertEquals(SealLevel.BASELINE_LT, result.level());
        assertEquals(service.url(), result.timestampedBy());
        assertEquals(1, service.requests());
        assertEquals(1, service.listRequests(), "the timestamp service's list was fetched once");
        assertValidLongTerm(result.pdf(), key);
        assertArrayEquals(pdf, Arrays.copyOf(result.pdf(), pdf.length), "original bytes untouched");
    }

    @Test
    void theEmbeddedStationListIsTheCurrentOneAndNamesAKeyRevokedEarlier() throws Exception {
        var station = stationRepo.create("Revoking station");
        var leaked = signingKeys.forStation(station.id());
        revocations.revoke(station.id(), serialOf(leaked), RevocationReason.KEY_COMPROMISE);
        var key = signingKeys.forStation(station.id());
        assertEquals(leaked.authority(), key.authority(), "same authority");
        var current = RevocationLists.read(revocations
                .revocationList(SigningCertificates.serialOf(key.authority()))
                .orElseThrow());

        var result = sealerAsking(service.url()).seal(pdf, key.privateKey(), key.chain());

        assertEquals(SealLevel.BASELINE_LT, result.level());
        var stationList = embeddedRevocationLists(result.pdf()).stream()
                .filter(list ->
                        list.getIssuerX500Principal().equals(key.authority().getSubjectX500Principal()))
                .toList();
        assertEquals(1, stationList.size());
        var embedded = stationList.getFirst();
        embedded.verify(key.authority().getPublicKey());
        assertEquals(numberOf(current), numberOf(embedded));
        assertTrue(embedded.isRevoked(leaked.certificate()), "names the key revoked earlier");
        assertFalse(embedded.isRevoked(key.certificate()));
        var timestampTime = validate(result.pdf(), key)
                .getDiagnosticData()
                .getTimestampList()
                .getFirst()
                .getProductionTime();
        assertTrue(embedded.getNextUpdate().after(timestampTime), "still current after the seal");
    }

    @Test
    void theTimestampServicesListBeingDownLeavesTheSealAtT() throws Exception {
        var key = keyOf("Unlisted timestamp station");
        try (var unlisted = LocalTimestampService.listingAt(LocalTimestampService.unreachableUrl())) {
            var result = sealerAsking(unlisted.url()).seal(pdf, key.privateKey(), key.chain());

            assertEquals(SealLevel.BASELINE_T, result.level());
            assertEquals(unlisted.url(), result.timestampedBy());
            assertValidBaselineT(result.pdf(), key);
        }
    }

    @Test
    void aListThatNeverAnswersIsLeftWhenTheBudgetIsSpent() throws Exception {
        var key = keyOf("Silent list station");
        try (var silent = LocalTimestampService.silent();
                var stamping = LocalTimestampService.listingAt(LocalTimestampService.urlOf(silent))) {
            long started = System.nanoTime();
            var result = sealerWithBudget(stamping.url()).seal(pdf, key.privateKey(), key.chain());
            var elapsed = Duration.ofNanos(System.nanoTime() - started);

            assertEquals(SealLevel.BASELINE_T, result.level());
            assertValidBaselineT(result.pdf(), key);
            assertTrue(
                    elapsed.compareTo(TimestampServices.TIMEOUT) < 0,
                    "cut by the budget, not by the timeout: " + elapsed);
        }
    }

    @Test
    void theTimestampAndTheListShareOneBudget() throws Exception {
        var key = keyOf("Slow service station");
        var delay = SHORT_BUDGET.multipliedBy(3).dividedBy(5);
        try (var slow = LocalTimestampService.slow(delay)) {
            long started = System.nanoTime();
            var result = sealerWithBudget(slow.url()).seal(pdf, key.privateKey(), key.chain());
            var elapsed = Duration.ofNanos(System.nanoTime() - started);

            assertEquals(SealLevel.BASELINE_T, result.level(), "each answer fits the budget, both do not");
            assertEquals(slow.url(), result.timestampedBy());
            assertValidBaselineT(result.pdf(), key);
            assertTrue(elapsed.compareTo(SHORT_BUDGET.multipliedBy(3)) < 0, "gave up within the budget: " + elapsed);
        }
    }

    @Test
    void aStatusResponderThatAnswersGivesLongTermWithoutTheList() throws Exception {
        var key = keyOf("Status station");
        try (var answering = LocalTimestampService.withStatusResponder()) {
            var result = sealerAsking(answering.url()).seal(pdf, key.privateKey(), key.chain());

            assertEquals(SealLevel.BASELINE_LT, result.level());
            assertTrue(answering.statusRequests() >= 1, "the status responder was asked");
            assertEquals(0, answering.listRequests(), "its answer made the list unnecessary");
            assertValidLongTerm(result.pdf(), key);
        }
    }

    @Test
    void aStatusResponderThatIsDownFallsBackToTheList() throws Exception {
        var key = keyOf("Status down station");
        try (var down = LocalTimestampService.withStatusResponderAt(LocalTimestampService.unreachableUrl())) {
            var result = sealerAsking(down.url()).seal(pdf, key.privateKey(), key.chain());

            assertEquals(SealLevel.BASELINE_LT, result.level());
            assertEquals(1, down.listRequests(), "the list stood in for the status answer");
            assertValidLongTerm(result.pdf(), key);
        }
    }

    @Test
    void revocationAddressesThatAreNotHttpAreNeverRead(@TempDir Path directory) throws Exception {
        var key = keyOf("Directory list station");
        var file = directory.resolve("list.crl");
        Files.write(file, LocalTimestampService.revocationList());
        try (var directoryService = LocalTimestampService.silent();
                var stamping = LocalTimestampService.listingAt(
                        "ldap://127.0.0.1:" + directoryService.getLocalPort() + "/cn=list",
                        file.toUri().toString())) {
            var result = sealerAsking(stamping.url()).seal(pdf, key.privateKey(), key.chain());

            assertEquals(SealLevel.BASELINE_T, result.level(), "the valid list in the file was not read");
            assertValidBaselineT(result.pdf(), key);
            directoryService.setSoTimeout(100);
            assertThrows(
                    SocketTimeoutException.class,
                    directoryService::accept,
                    "the directory address was never connected to");
        }
    }

    @Test
    void timestampsOffSealAtBWithoutAnyRequest() throws Exception {
        var key = keyOf("Offline station");

        var result = sealerAsking().seal(pdf, key.privateKey(), key.chain());

        assertEquals(SealLevel.BASELINE_B, result.level());
        assertNull(result.timestampedBy());
        assertEquals(0, service.requests());
        assertEquals(0, service.listRequests());
        assertEquals(List.of(), embeddedRevocationLists(result.pdf()));
    }

    @Test
    void liftingABaselineBDocumentReachesLongTerm() throws Exception {
        var key = keyOf("Lifted station");
        var unstamped = sealerAsking(LocalTimestampService.unreachableUrl())
                .seal(pdf, key.privateKey(), key.chain())
                .pdf();

        var result = sealerAsking(service.url()).lift(unstamped);

        assertEquals(SealLevel.BASELINE_LT, result.level());
        assertEquals(service.url(), result.timestampedBy());
        assertArrayEquals(unstamped, Arrays.copyOf(result.pdf(), unstamped.length), "first seal untouched");
        assertValidLongTerm(result.pdf(), key);
    }

    @Test
    void aRevokedKeyCannotSeal() {
        var station = stationRepo.create("Revoked key station");
        var revoked = signingKeys.forStation(station.id());
        revocations.revoke(station.id(), serialOf(revoked), RevocationReason.KEY_COMPROMISE);

        var next = signingKeys.forStation(station.id());

        assertNotEquals(revoked.certificate(), next.certificate(), "the station is handed a new key");
        assertThrows(
                AlertException.class,
                () -> sealerAsking().seal(pdf, revoked.privateKey(), revoked.chain()),
                "a key held from before the revocation is refused");
        assertThrows(AlertException.class, () -> sealerAsking(service.url())
                .seal(pdf, revoked.privateKey(), revoked.chain()));
        assertEquals(0, service.requests(), "refused before anything was stamped");
    }

    private SealingKey keyOf(String stationName) {
        return signingKeys.forStation(stationRepo.create(stationName).id());
    }

    private PdfSealer sealerAsking(String... urls) {
        var pinned = Arrays.stream(urls).map(LocalTimestampService::pinned).toList();
        return new PdfSealer(new TimestampServices(pinned, SHORT_TIMEOUT, TimestampServices.BUDGET), revocations);
    }

    private PdfSealer sealerWithBudget(String url) {
        return new PdfSealer(
                new TimestampServices(
                        List.of(LocalTimestampService.pinned(url)), TimestampServices.TIMEOUT, SHORT_BUDGET),
                revocations);
    }

    private static void assertValidLongTerm(byte[] sealed, SealingKey key) {
        var reports = validate(sealed, key);
        var signature = onlySignature(reports);
        assertEquals(SignatureLevel.PAdES_BASELINE_LT, signature.getSignatureFormat());
        assertTrue(signature.isSignatureValid(), "signature value and signed data");
        assertEquals(Indication.TOTAL_PASSED, reports.getSimpleReport().getIndication(signature.getId()));
        assertEquals(List.of(), validationErrors(reports));
        var diagnostic = reports.getDiagnosticData();
        assertFalse(diagnostic.getTimestampList().isEmpty());
        for (var timestamp : diagnostic.getTimestampList()) {
            assertTrue(timestamp.isSignatureValid(), "timestamp signature");
            assertEquals(
                    Indication.PASSED,
                    reports.getDetailedReport().getBasicTimestampValidationIndication(timestamp.getId()));
        }
        for (CertificateWrapper certificate : diagnostic.getUsedCertificates()) {
            if (certificate.isSelfSigned()) continue;
            assertFalse(
                    certificate.getCertificateRevocationData().isEmpty(),
                    "revocation data for " + certificate.getCommonName());
            assertTrue(certificate
                    .getCertificateRevocationData()
                    .getFirst()
                    .getStatus()
                    .isGood());
        }
    }

    private static void assertValidBaselineT(byte[] sealed, SealingKey key) {
        var reports = validate(sealed, key);
        var signature = onlySignature(reports);
        assertEquals(SignatureLevel.PAdES_BASELINE_T, signature.getSignatureFormat());
        assertTrue(signature.isSignatureValid(), "signature value and signed data");
        var timestamp = reports.getDiagnosticData().getTimestampList().getFirst();
        assertTrue(timestamp.isSignatureValid(), "timestamp signature");
        assertTrue(timestamp.isMessageImprintDataIntact(), "timestamped data");
    }

    private static SignatureWrapper onlySignature(Reports reports) {
        var signatures = reports.getDiagnosticData().getSignatures();
        assertEquals(1, signatures.size());
        return signatures.getFirst();
    }

    private static Reports validate(byte[] sealed, SealingKey key) {
        return SealedPdfs.validate(sealed, List.<X509Certificate>of(key.authority(), LocalTimestampService.root()));
    }

    private static String serialOf(SealingKey key) {
        return SigningCertificates.serialOf(key.certificate());
    }

    private static long numberOf(X509CRL list) throws Exception {
        var value = JcaX509ExtensionUtils.parseExtensionValue(list.getExtensionValue(Extension.cRLNumber.getId()));
        return ASN1Integer.getInstance(value).getValue().longValueExact();
    }
}
