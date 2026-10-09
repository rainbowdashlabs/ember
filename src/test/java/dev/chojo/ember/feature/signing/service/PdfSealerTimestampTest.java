/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.conf.file.elements.Signing;
import dev.chojo.ember.feature.signing.entity.SealLevel;
import dev.chojo.ember.feature.signing.entity.SealedDocument;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.validation.reports.Reports;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static dev.chojo.ember.feature.signing.service.SealedPdfs.referencedDataIntact;
import static dev.chojo.ember.feature.signing.service.SealedPdfs.validationErrors;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sealing with timestamps against timestamp services on the loopback address. Nothing here reaches the
 * internet: every service is a {@link LocalTimestampService}, a closed port or a socket that never answers.
 * Each service is pinned to the local test root, and a timestamp that does not chain to the pinned root
 * counts as that service failing.
 *
 * <p>The seal certificate here is self-signed and needs no revocation data, so a timestamped seal
 * reaches {@code BASELINE-LT} with the local service's list alone. Station chains, and seals that stop
 * at {@code BASELINE-T}, are {@link PdfSealerLongTermTest}'s part.
 */
class PdfSealerTimestampTest {
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration SHORT_BUDGET = Duration.ofSeconds(1);

    private static KeyPair keys;
    private static X509Certificate certificate;
    private static byte[] pdf;

    private LocalTimestampService service;

    @BeforeAll
    static void createKeyAndDocument() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        certificate = PdfSealerTest.selfSigned(keys);
        pdf = SealedPdfs.onePagePdf();
    }

    @BeforeEach
    void startService() throws Exception {
        service = LocalTimestampService.start();
    }

    @AfterEach
    void stopService() {
        service.close();
    }

    @Test
    void sealCarriesAValidTimestampFromTheService() {
        var result = sealerAsking(service.url()).seal(pdf, keys.getPrivate(), List.of(certificate));

        assertEquals(SealLevel.BASELINE_LT, result.level());
        assertEquals(service.url(), result.timestampedBy());
        assertEquals(1, service.requests());
        assertValidTimestamped(result.pdf());
    }

    @Test
    void serviceThatIsDownIsSkippedForTheNextOne() throws Exception {
        var result = sealerAsking(LocalTimestampService.unreachableUrl(), service.url())
                .seal(pdf, keys.getPrivate(), List.of(certificate));

        assertEquals(SealLevel.BASELINE_LT, result.level());
        assertEquals(service.url(), result.timestampedBy());
        assertValidTimestamped(result.pdf());
    }

    @Test
    void noServiceAnsweringSealsWithoutATimestamp() throws Exception {
        var result = sealerAsking(LocalTimestampService.unreachableUrl(), LocalTimestampService.unreachableUrl())
                .seal(pdf, keys.getPrivate(), List.of(certificate));

        assertEquals(SealLevel.BASELINE_B, result.level());
        assertNull(result.timestampedBy());
        assertValidBaselineB(result.pdf());
    }

    @Test
    void timestampsSwitchedOffAskNoService() {
        var config = new Signing() {
            @Override
            public boolean timestamps() {
                return false;
            }

            @Override
            public List<String> timestampUrls() {
                return List.of(service.url());
            }
        };

        var result =
                SealedPdfs.sealer(new TimestampServices(config)).seal(pdf, keys.getPrivate(), List.of(certificate));

        assertEquals(SealLevel.BASELINE_B, result.level());
        assertEquals(0, service.requests());
        assertValidBaselineB(result.pdf());
    }

    @Test
    void emptyServiceListAsksNoService() {
        var config = new Signing() {
            @Override
            public List<String> timestampUrls() {
                return List.of();
            }
        };

        var result =
                SealedPdfs.sealer(new TimestampServices(config)).seal(pdf, keys.getPrivate(), List.of(certificate));

        assertEquals(SealLevel.BASELINE_B, result.level());
        assertEquals(0, service.requests());
    }

    @Test
    void serviceThatNeverAnswersIsLeftAfterTheTimeout() throws Exception {
        try (var silent = LocalTimestampService.silent()) {
            long started = System.nanoTime();
            var result = sealerAsking(LocalTimestampService.urlOf(silent), service.url())
                    .seal(pdf, keys.getPrivate(), List.of(certificate));
            var elapsed = Duration.ofNanos(System.nanoTime() - started);

            assertEquals(SealLevel.BASELINE_LT, result.level());
            assertEquals(service.url(), result.timestampedBy());
            assertTrue(elapsed.compareTo(SHORT_TIMEOUT) >= 0, "waited for the silent service: " + elapsed);
            assertTrue(elapsed.compareTo(SHORT_TIMEOUT.multipliedBy(4)) < 0, "gave up in time: " + elapsed);
        }
    }

    @Test
    void sealWithoutTimestampIsLiftedLater() throws Exception {
        var unstamped = sealerAsking(LocalTimestampService.unreachableUrl())
                .seal(pdf, keys.getPrivate(), List.of(certificate))
                .pdf();

        var result = sealerAsking(service.url()).lift(unstamped);

        assertEquals(SealLevel.BASELINE_LT, result.level());
        assertEquals(service.url(), result.timestampedBy());
        assertArrayEquals(unstamped, Arrays.copyOf(result.pdf(), unstamped.length), "first seal untouched");
        assertValidTimestamped(result.pdf());
    }

    @Test
    void liftingWithoutAServiceLeavesTheDocumentAsItIs() throws Exception {
        var unstamped = sealerAsking()
                .seal(pdf, keys.getPrivate(), List.of(certificate))
                .pdf();

        SealedDocument offline = sealerAsking().lift(unstamped);
        SealedDocument down =
                sealerAsking(LocalTimestampService.unreachableUrl()).lift(unstamped);

        assertSame(unstamped, offline.pdf());
        assertEquals(SealLevel.BASELINE_B, offline.level());
        assertSame(unstamped, down.pdf());
        assertEquals(SealLevel.BASELINE_B, down.level());
        assertNull(down.timestampedBy());
    }

    @Test
    void sealStopsAskingWhenTheBudgetIsSpent() throws Exception {
        try (var first = LocalTimestampService.hanging();
                var second = LocalTimestampService.hanging();
                var third = LocalTimestampService.hanging()) {
            var hanging = List.of(first, second, third);
            long started = System.nanoTime();
            var result = sealerWithBudget(hanging).seal(pdf, keys.getPrivate(), List.of(certificate));
            var elapsed = Duration.ofNanos(System.nanoTime() - started);

            assertEquals(SealLevel.BASELINE_B, result.level());
            assertNull(result.timestampedBy());
            assertValidBaselineB(result.pdf());
            assertWithinBudget(elapsed, hanging);
        }
    }

    @Test
    void liftingStopsAskingWhenTheBudgetIsSpent() throws Exception {
        var unstamped = sealerAsking()
                .seal(pdf, keys.getPrivate(), List.of(certificate))
                .pdf();
        try (var first = LocalTimestampService.hanging();
                var second = LocalTimestampService.hanging();
                var third = LocalTimestampService.hanging()) {
            var hanging = List.of(first, second, third);
            long started = System.nanoTime();
            var result = sealerWithBudget(hanging).lift(unstamped);
            var elapsed = Duration.ofNanos(System.nanoTime() - started);

            assertSame(unstamped, result.pdf());
            assertEquals(SealLevel.BASELINE_B, result.level());
            assertWithinBudget(elapsed, hanging);
        }
    }

    @Test
    void aTimestampFromAnotherRootIsRefusedAndTheNextServiceAsked() throws Exception {
        try (var impostor = LocalTimestampService.start()) {
            var services = List.of(
                    new TimestampServices.Service(impostor.url(), LocalTimestampService.otherRoot()), service.pinned());

            var result = SealedPdfs.sealer(new TimestampServices(services, SHORT_TIMEOUT, TimestampServices.BUDGET))
                    .seal(pdf, keys.getPrivate(), List.of(certificate));

            assertEquals(1, impostor.requests(), "asked, and its answer refused");
            assertEquals(SealLevel.BASELINE_LT, result.level());
            assertEquals(service.url(), result.timestampedBy());
            assertValidTimestamped(result.pdf());
        }
    }

    @Test
    void onlyTimestampsFromAnotherRootSealWithoutATimestamp() {
        var services = List.of(new TimestampServices.Service(service.url(), LocalTimestampService.otherRoot()));

        var result = SealedPdfs.sealer(new TimestampServices(services, SHORT_TIMEOUT, TimestampServices.BUDGET))
                .seal(pdf, keys.getPrivate(), List.of(certificate));

        assertEquals(1, service.requests());
        assertEquals(SealLevel.BASELINE_B, result.level());
        assertNull(result.timestampedBy());
    }

    @Test
    void aConfiguredServiceWithoutAPinnedRootIsNeverAsked(@TempDir Path directory) throws Exception {
        var rootFile = directory.resolve("root.pem");
        Files.write(rootFile, LocalTimestampService.root().getEncoded());
        try (var unpinned = LocalTimestampService.start()) {
            var config = new Signing() {
                @Override
                public List<String> timestampUrls() {
                    return List.of(unpinned.url(), service.url());
                }

                @Override
                public Map<String, String> timestampRoots() {
                    return Map.of(service.url(), rootFile.toString());
                }
            };

            var result =
                    SealedPdfs.sealer(new TimestampServices(config)).seal(pdf, keys.getPrivate(), List.of(certificate));

            assertEquals(0, unpinned.requests(), "no root, never asked");
            assertEquals(SealLevel.BASELINE_LT, result.level());
            assertEquals(service.url(), result.timestampedBy());
        }
    }

    @Test
    void liftingATimestampedDocumentAddsAnotherDocumentTimestamp() {
        var stamped = sealerAsking(service.url())
                .seal(pdf, keys.getPrivate(), List.of(certificate))
                .pdf();

        var result = sealerAsking(service.url()).lift(stamped);

        assertEquals(SealLevel.BASELINE_LT, result.level());
        assertEquals(2, service.requests());
        assertArrayEquals(stamped, Arrays.copyOf(result.pdf(), stamped.length), "the stamped seal untouched");
        var reports = validate(result.pdf());
        var timestamps = reports.getDiagnosticData().getTimestampList();
        assertEquals(2, timestamps.size(), "the signature's timestamp and a document timestamp");
        for (var timestamp : timestamps) {
            assertTrue(timestamp.isSignatureValid(), "timestamp signature");
            assertEquals(
                    Indication.PASSED,
                    reports.getDetailedReport().getBasicTimestampValidationIndication(timestamp.getId()));
        }
        assertEquals(List.of(), validationErrors(reports));
    }

    private static PdfSealer sealerWithBudget(List<LocalTimestampService> services) {
        var pinned = services.stream().map(LocalTimestampService::pinned).toList();
        return SealedPdfs.sealer(new TimestampServices(pinned, TimestampServices.TIMEOUT, SHORT_BUDGET));
    }

    private static void assertWithinBudget(Duration elapsed, List<LocalTimestampService> services) {
        int asked = services.stream().mapToInt(LocalTimestampService::requests).sum();
        assertTrue(asked >= 1 && asked < services.size(), "services asked: " + asked);
        assertTrue(elapsed.compareTo(SHORT_BUDGET.multipliedBy(3)) < 0, "gave up within the budget: " + elapsed);
    }

    private static PdfSealer sealerAsking(String... urls) {
        var pinned = Arrays.stream(urls).map(LocalTimestampService::pinned).toList();
        return SealedPdfs.sealer(new TimestampServices(pinned, SHORT_TIMEOUT, TimestampServices.BUDGET));
    }

    private static void assertValidTimestamped(byte[] sealed) {
        var reports = validate(sealed);
        var signature = onlySignature(reports);
        assertEquals(SignatureLevel.PAdES_BASELINE_LT, signature.getSignatureFormat());
        assertTrue(signature.isSignatureValid(), "signature value and signed data");
        assertTrue(referencedDataIntact(signature), "signed data");
        var timestamps = reports.getDiagnosticData().getTimestampList();
        assertEquals(1, timestamps.size());
        var timestamp = timestamps.getFirst();
        assertTrue(timestamp.isSignatureValid(), "timestamp signature");
        assertTrue(timestamp.isMessageImprintDataIntact(), "timestamped data");
        assertEquals(
                Indication.PASSED,
                reports.getDetailedReport().getBasicTimestampValidationIndication(timestamp.getId()));
        assertEquals(List.of(), validationErrors(reports));
    }

    private static void assertValidBaselineB(byte[] sealed) {
        var reports = validate(sealed);
        var signature = onlySignature(reports);
        assertEquals(SignatureLevel.PAdES_BASELINE_B, signature.getSignatureFormat());
        assertTrue(signature.isSignatureValid(), "signature value and signed data");
        assertEquals(List.of(), reports.getDiagnosticData().getTimestampList());
    }

    private static SignatureWrapper onlySignature(Reports reports) {
        var signatures = reports.getDiagnosticData().getSignatures();
        assertEquals(1, signatures.size());
        return signatures.getFirst();
    }

    private static Reports validate(byte[] sealed) {
        return SealedPdfs.validate(sealed, List.of(certificate, LocalTimestampService.root()));
    }
}
