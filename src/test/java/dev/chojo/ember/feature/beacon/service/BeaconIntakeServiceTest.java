/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.feature.beacon.entity.BeaconPayloads;
import dev.chojo.ember.feature.beacon.repository.BeaconIntakeRepository;
import dev.chojo.ember.feature.beacon.repository.BeaconReadRepository;
import dev.chojo.ember.feature.discovery.service.DiscoveryKeyService;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules that decide whether a beacon writes anything down at all.
 */
class BeaconIntakeServiceTest extends RepositoryTestBase {

    private static final String OWN_URL = "https://beacon.test";

    private final BeaconIntakeRepository repository = new BeaconIntakeRepository();
    private final BeaconIntakeService service = new BeaconIntakeService(repository);
    private final BeaconReadRepository read = new BeaconReadRepository();

    private static byte[] key() {
        return UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
    }

    private static BeaconPayloads.Envelope envelope(Instant issuedAt, String audience) {
        return new BeaconPayloads.Envelope(1, issuedAt, UUID.randomUUID().toString(), audience);
    }

    /**
     * Who sent a report is worked out from the key that signed it. Nothing in the body is consulted,
     * so no sender can claim to be another and no identifier can be squatted before its owner
     * reports.
     */
    @Test
    void whoSentItComesFromTheKey() {
        byte[] key = key();
        String id = service.accept(key, envelope(Instant.now(), OWN_URL), OWN_URL);
        assertEquals(DiscoveryKeyService.computeInstanceId(key), id);
    }

    /** The same delivery is taken once, so a captured report cannot be replayed into the counts. */
    @Test
    void aDeliveryIsTakenOnce() {
        byte[] key = key();
        var envelope = envelope(Instant.now(), OWN_URL);
        service.accept(key, envelope, OWN_URL);
        assertThrows(ForbiddenResponse.class, () -> service.accept(key, envelope, OWN_URL));
    }

    /** A clock far out of step is refused rather than trusted. */
    @Test
    void aReportFromTooFarAwayInTimeIsRefused() {
        var old = envelope(Instant.now().minus(BeaconIntakeService.DRIFT).minusSeconds(120), OWN_URL);
        assertThrows(ForbiddenResponse.class, () -> service.accept(key(), old, OWN_URL));
    }

    /** A report captured by one beacon cannot be handed to another. */
    @Test
    void aReportMeantForAnotherBeaconIsRefused() {
        var elsewhere = envelope(Instant.now(), "https://other.test");
        assertThrows(ForbiddenResponse.class, () -> service.accept(key(), elsewhere, OWN_URL));
    }

    /** A delivery that says nothing about itself cannot be judged and is refused. */
    @Test
    void anEnvelopeWithoutItsFieldsIsRefused() {
        assertThrows(BadRequestResponse.class, () -> service.accept(key(), null, OWN_URL));
        var noNonce = new BeaconPayloads.Envelope(1, Instant.now(), null, OWN_URL);
        assertThrows(BadRequestResponse.class, () -> service.accept(key(), noNonce, OWN_URL));
    }

    /** A beacon says so rather than guessing when a newer instance speaks a protocol it does not. */
    @Test
    void aNewerProtocolIsRefused() {
        var ahead = new BeaconPayloads.Envelope(
                BeaconPayloads.PROTOCOL_VERSION + 1,
                Instant.now(),
                UUID.randomUUID().toString(),
                OWN_URL);
        assertThrows(BadRequestResponse.class, () -> service.accept(key(), ahead, OWN_URL));
    }

    /**
     * A contact that is not an address, and a version that is not one, are dropped rather than
     * stored and later put on a screen.
     */
    @Test
    void nonsenseInTheContactAndVersionIsDropped() {
        byte[] key = key();
        String id = service.accept(key, envelope(Instant.now(), OWN_URL), OWN_URL);
        String fingerprint = "fp-" + UUID.randomUUID();
        service.storeProblem(
                id,
                "public-key",
                new BeaconPayloads.ProblemPayload(
                        envelope(Instant.now(), OWN_URL),
                        "<script>alert(1)</script>",
                        "Nora",
                        "not-a-mail",
                        fingerprint,
                        "ERROR",
                        "logger",
                        "java.lang.IllegalStateException",
                        "frames",
                        1,
                        Instant.now(),
                        Instant.now()));

        var fault = read.faults(true).stream()
                .filter(f -> f.fingerprint().equals(fingerprint))
                .findFirst()
                .orElseThrow();
        assertTrue(fault.versions().isEmpty(), "a version that is not one should not have been kept");
    }

    /** A fault with no fingerprint cannot be grouped, so it is refused. */
    @Test
    void aFaultWithoutAFingerprintIsRefused() {
        byte[] key = key();
        String id = service.accept(key, envelope(Instant.now(), OWN_URL), OWN_URL);
        var payload = new BeaconPayloads.ProblemPayload(
                envelope(Instant.now(), OWN_URL),
                "26.15.0",
                null,
                null,
                "  ",
                "ERROR",
                "l",
                null,
                "",
                1,
                Instant.now(),
                Instant.now());
        assertThrows(BadRequestResponse.class, () -> service.storeProblem(id, "k", payload));
    }

    /** A report with nothing written in it is refused. */
    @Test
    void anEmptyReportIsRefused() {
        byte[] key = key();
        String id = service.accept(key, envelope(Instant.now(), OWN_URL), OWN_URL);
        var payload = new BeaconPayloads.ReportPayload(
                envelope(Instant.now(), OWN_URL), "26.15.0", null, null, "   ", null, Instant.now());
        assertThrows(BadRequestResponse.class, () -> service.storeReport(id, "k", payload));
    }

    /** A day of numbers is written for every subject the batch carries. */
    @Test
    void aBatchWritesEverySubject() {
        String day = LocalDate.now().toString();
        var batch = new BeaconPayloads.MetricsBatch(
                1,
                "26.15.0",
                day,
                List.of(
                        new BeaconPayloads.MetricsSubject(
                                UUID.randomUUID().toString(), "INSTANCE", "10-50", "<10", "<10", "0"),
                        new BeaconPayloads.MetricsSubject(
                                UUID.randomUUID().toString(), "STATION", "<10", null, null, "0")));
        assertEquals(2, service.storeMetrics(batch));
    }

    /** A batch that says nothing, or says it is from the future, is refused. */
    @Test
    void anImpossibleBatchIsRefused() {
        assertThrows(
                BadRequestResponse.class,
                () -> service.storeMetrics(new BeaconPayloads.MetricsBatch(
                        1, "26.15.0", LocalDate.now().toString(), List.of())));
        var future = new BeaconPayloads.MetricsBatch(
                1,
                "26.15.0",
                LocalDate.now().plusDays(5).toString(),
                List.of(new BeaconPayloads.MetricsSubject(
                        UUID.randomUUID().toString(), "STATION", "<10", null, null, "0")));
        assertThrows(BadRequestResponse.class, () -> service.storeMetrics(future));
        var noDay = new BeaconPayloads.MetricsBatch(
                1,
                "26.15.0",
                "not-a-day",
                List.of(new BeaconPayloads.MetricsSubject(
                        UUID.randomUUID().toString(), "STATION", "<10", null, null, "0")));
        assertThrows(BadRequestResponse.class, () -> service.storeMetrics(noDay));
    }

    /** A report that says something is stored, with the contact of whoever sent it. */
    @Test
    void aReportIsStored() {
        byte[] key = key();
        String id = service.accept(key, envelope(Instant.now(), OWN_URL), OWN_URL);
        String message = "Der Knopf tut nichts " + UUID.randomUUID();
        service.storeReport(
                id,
                "public-key",
                new BeaconPayloads.ReportPayload(
                        envelope(Instant.now(), OWN_URL),
                        "26.15.0",
                        "Nora",
                        "nora@example.com",
                        message,
                        "/station/events",
                        Instant.now()));

        assertTrue(read.reports(true).stream().anyMatch(r -> r.message().equals(message)));
    }

    /** An audience that is not an address at all is refused rather than parsed hopefully. */
    @Test
    void anAudienceThatIsNotAnAddressIsRefused() {
        var nonsense =
                new BeaconPayloads.Envelope(1, Instant.now(), UUID.randomUUID().toString(), ":::not a url");
        assertThrows(ForbiddenResponse.class, () -> service.accept(key(), nonsense, OWN_URL));
    }

    /** A subject with no name at all is skipped rather than taking the whole batch down. */
    @Test
    void aSubjectWithoutANameIsSkipped() {
        var batch = new BeaconPayloads.MetricsBatch(
                1,
                "26.15.0",
                LocalDate.now().toString(),
                List.of(
                        new BeaconPayloads.MetricsSubject(null, "STATION", "<10", null, null, "0"),
                        new BeaconPayloads.MetricsSubject(
                                UUID.randomUUID().toString(), "STATION", "<10", null, null, "0")));
        assertEquals(1, service.storeMetrics(batch));
    }

    /** More subjects than a beacon accepts is refused outright. */
    @Test
    void anEnormousBatchIsRefused() {
        var many = new java.util.ArrayList<BeaconPayloads.MetricsSubject>();
        for (int i = 0; i < 501; i++) {
            many.add(
                    new BeaconPayloads.MetricsSubject(UUID.randomUUID().toString(), "STATION", "<10", null, null, "0"));
        }
        var batch =
                new BeaconPayloads.MetricsBatch(1, "26.15.0", LocalDate.now().toString(), List.copyOf(many));
        assertThrows(BadRequestResponse.class, () -> service.storeMetrics(batch));
    }

    /** The longest message a report may carry is bounded, so prose cannot fill a disk. */
    @Test
    void aMessageIsBounded() {
        String long_ = "x".repeat(9000);
        assertEquals(4000, BeaconIntakeService.clampMessage(long_).length());
    }
}
