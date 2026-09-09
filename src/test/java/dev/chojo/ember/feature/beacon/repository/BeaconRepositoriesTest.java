/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.beacon.entity.BeaconPayloads;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a beacon writes down and reads back.
 */
class BeaconRepositoriesTest extends RepositoryTestBase {

    private static final BeaconIntakeRepository intake = new BeaconIntakeRepository();
    private static final BeaconReadRepository read = new BeaconReadRepository();
    private static final BeaconMetricsSourceRepository source = new BeaconMetricsSourceRepository();

    private static Station station;
    private static Account account;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Beacon Station");
        account = accountRepo.create("beacon-repo@test.com", "Bea", "Con");
        stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static BeaconPayloads.ProblemPayload problem(String fingerprint, String version, int occurrences) {
        return new BeaconPayloads.ProblemPayload(
                new BeaconPayloads.Envelope(1, Instant.now(), UUID.randomUUID().toString(), "https://beacon.test"),
                version,
                "Nora",
                "nora@example.com",
                fingerprint,
                "ERROR",
                "dev.chojo.ember.Some",
                "java.lang.IllegalStateException",
                "dev.chojo.ember.Some.thing",
                occurrences,
                Instant.now().minusSeconds(600),
                Instant.now());
    }

    /** A first report registers the instance; a later one keeps its contact current. */
    @Test
    void anInstanceIsRecordedAndItsContactStaysCurrent() {
        String id = "inst-" + UUID.randomUUID().toString().substring(0, 8);
        intake.touchInstance(id, "key-one", "Nora", "nora@example.com", "26.15.0");
        assertEquals("key-one", intake.knownKey(id).orElseThrow());

        intake.touchInstance(id, "key-one", null, null, "26.15.1");
        assertTrue(intake.knownKey(id).isPresent());
    }

    /**
     * The same fault from two instances is one fault with two rows beside it, which is the whole
     * reason a beacon gathers them in one place.
     */
    @Test
    void oneFaultFromTwoInstancesCountsAsTwoInstallations() {
        String fingerprint = "fp-" + UUID.randomUUID();
        String first = "inst-" + UUID.randomUUID().toString().substring(0, 8);
        String second = "inst-" + UUID.randomUUID().toString().substring(0, 8);
        intake.touchInstance(first, "k1", null, null, "26.15.0");
        intake.touchInstance(second, "k2", null, null, "26.14.4");

        int problemId = intake.upsertProblem(problem(fingerprint, "26.15.0", 3));
        intake.upsertProblemInstance(problemId, first, "26.15.0", problem(fingerprint, "26.15.0", 3));
        assertEquals(problemId, intake.upsertProblem(problem(fingerprint, "26.14.4", 5)));
        intake.upsertProblemInstance(problemId, second, "26.14.4", problem(fingerprint, "26.14.4", 5));

        var fault = read.faults(true).stream()
                .filter(f -> f.fingerprint().equals(fingerprint))
                .findFirst()
                .orElseThrow();
        assertEquals(2, fault.instances());
        assertEquals(8, fault.occurrences());
        assertTrue(fault.versions().contains("26.15.0"));
        assertTrue(fault.versions().contains("26.14.4"));
    }

    /**
     * The same report arriving twice corrects its row rather than doubling it: the count a sender
     * carries is its own total, not an increment.
     */
    @Test
    void aReportArrivingTwiceCorrectsRatherThanDoubles() {
        String fingerprint = "fp-" + UUID.randomUUID();
        String id = "inst-" + UUID.randomUUID().toString().substring(0, 8);
        intake.touchInstance(id, "k", null, null, "26.15.0");
        int problemId = intake.upsertProblem(problem(fingerprint, "26.15.0", 4));
        intake.upsertProblemInstance(problemId, id, "26.15.0", problem(fingerprint, "26.15.0", 4));
        intake.upsertProblemInstance(problemId, id, "26.15.0", problem(fingerprint, "26.15.0", 9));

        var fault = read.faults(true).stream()
                .filter(f -> f.fingerprint().equals(fingerprint))
                .findFirst()
                .orElseThrow();
        assertEquals(1, fault.instances());
        assertEquals(9, fault.occurrences());
    }

    /** A fault can be marked seen and told which version put it right. */
    @Test
    void aFaultCanBeResolved() {
        String fingerprint = "fp-" + UUID.randomUUID();
        int problemId = intake.upsertProblem(problem(fingerprint, "26.15.0", 1));
        assertTrue(read.resolveFault(problemId, true, "26.15.1"));

        var resolved = read.faults(true).stream()
                .filter(f -> f.id() == problemId)
                .findFirst()
                .orElseThrow();
        assertTrue(resolved.acknowledged());
        assertEquals("26.15.1", resolved.resolvedIn());
        assertTrue(read.faults(false).stream().noneMatch(f -> f.id() == problemId));
    }

    /** A forwarded report keeps the contact of whoever sent it, so somebody can be written to. */
    @Test
    void aForwardedReportCarriesTheSendersContact() {
        String id = "inst-" + UUID.randomUUID().toString().substring(0, 8);
        intake.touchInstance(id, "k", "Nora", "nora@example.com", "26.15.0");
        String message = "Speichern tut nichts " + UUID.randomUUID();
        intake.insertReport(
                id,
                new BeaconPayloads.ReportPayload(
                        new BeaconPayloads.Envelope(
                                1, Instant.now(), UUID.randomUUID().toString(), "https://b.test"),
                        "26.15.0",
                        "Nora",
                        "nora@example.com",
                        message,
                        "/station/events",
                        Instant.now()));

        var stored = read.reports(true).stream()
                .filter(r -> r.message().equals(message))
                .findFirst()
                .orElseThrow();
        assertEquals("nora@example.com", stored.contactMail());
        assertTrue(read.acknowledgeReport(stored.id()));
    }

    /** A day's numbers are corrected by the next honest report rather than added to. */
    @Test
    void theLastReportForADayWins() {
        String uid = UUID.randomUUID().toString();
        var day = LocalDate.now();
        intake.upsertMetrics(new BeaconPayloads.MetricsSubject(uid, "STATION", "<10", null, null, "0"), day, "26.15.0");
        intake.upsertMetrics(
                new BeaconPayloads.MetricsSubject(uid, "STATION", "10-50", null, null, "<10"), day, "26.15.0");

        var rows =
                read.metrics(2).stream().filter(r -> r.metricsUid().equals(uid)).toList();
        assertEquals(1, rows.size());
        assertEquals("10-50", rows.getFirst().members());
    }

    /**
     * A nonce is taken once. Without that a captured report could be replayed straight into the
     * count of how many installations met a fault.
     */
    @Test
    void aDeliveryIsAcceptedOnlyOnce() {
        String id = "inst-" + UUID.randomUUID().toString().substring(0, 8);
        String nonce = UUID.randomUUID().toString();
        assertTrue(intake.recordNonce(id, nonce, Instant.now()));
        assertFalse(intake.recordNonce(id, nonce, Instant.now()));
    }

    /** Nonces too old to be replayed are swept. */
    @Test
    void oldNoncesArePruned() {
        String id = "inst-" + UUID.randomUUID().toString().substring(0, 8);
        String nonce = UUID.randomUUID().toString();
        intake.recordNonce(id, nonce, Instant.now().minusSeconds(7200));
        intake.pruneNonces(Instant.now().minusSeconds(3600));
        assertTrue(intake.recordNonce(id, nonce, Instant.now()));
    }

    /** How many instances have ever reported, which is the headline the numbers screen opens with. */
    @Test
    void theInstancesHeardFromAreCounted() {
        intake.touchInstance("inst-" + UUID.randomUUID().toString().substring(0, 8), "k", null, null, "26.15.0");
        assertTrue(read.instanceCount() > 0);
    }

    /** Every station is counted, and each is named only by the identifier it uses for this. */
    @Test
    void everyStationIsCountedUnderItsMetricsName() {
        List<BeaconMetricsSourceRepository.StationCounts> counts = source.stationCounts();
        assertFalse(counts.isEmpty());
        assertNotNull(counts.getFirst().metricsUid());
        assertTrue(source.stationCount() > 0);
        assertTrue(source.accountCount() > 0);
        assertTrue(source.memberCount() > 0);
        assertTrue(source.inventoryCount() >= 0);
    }
}
