/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.SealedVersion;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.repository.SealedVersionRepository;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.documents.service.SealedDocumentService;
import dev.chojo.ember.feature.documents.service.SealedDocumentService.SealedFiling;
import dev.chojo.ember.feature.signing.entity.SealLevel;
import dev.chojo.ember.feature.signing.entity.SealedDocument;
import dev.chojo.ember.feature.signing.entity.SealingKey;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.repository.RepositoryTestBase;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Later timestamps for the sealed versions of signed documents, against the database, real station keys,
 * the local timestamp service on loopback and DSS: a version sealed while no service answered is lifted
 * into a new version that validates at {@code BASELINE-LT}, a version whose newest timestamp is about to
 * end is renewed into one that validates at {@code BASELINE-LTA} where the operator switched it on,
 * neither ever files over a version filed in the meantime, and a version that keeps failing is taken after
 * the others. Nothing here reaches the internet.
 */
class SealedVersionTimestampsTest extends RepositoryTestBase {
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(1);

    @TempDir
    static Path storageRoot;

    private static final SealedVersionRepository versions = new SealedVersionRepository();
    private static final SigningKeyRepository keyRepo = new SigningKeyRepository();
    private static final SigningKeyWrap wrap = new SigningKeyWrap(SECRET);

    private static DocumentService documents;
    private static SealedDocumentService sealedDocuments;
    private static StationSigningKeys stationKeys;
    private static StationKeyRevocations revocations;

    private final List<Station> stations = new ArrayList<>();
    private final List<LocalTimestampService> services = new ArrayList<>();

    @BeforeAll
    static void setup() {
        var backend = new LocalStorageBackend(storageRoot);
        documents = newDocumentService(new StorageService(new StorageBackendResolver(backend), backend));
        sealedDocuments = new SealedDocumentService(memberDocumentRepo, versions, documents);
        stationKeys = new StationSigningKeys(
                keyRepo, new SigningCertificates(), wrap, stationRepo, "https://ember.example.org");
        revocations = new StationKeyRevocations(keyRepo, new RevocationLists(), wrap);
    }

    @AfterEach
    void cleanup() {
        services.forEach(LocalTimestampService::close);
        stations.forEach(station -> stationRepo.delete(station.id()));
    }

    @Test
    void aVersionSealedWithoutTimestampIsLiftedIntoANewVersionThatValidatesLongTerm() throws Exception {
        var service = service(LocalTimestampService.start());
        var station = station("Lifting station");
        var key = stationKeys.forStation(station.id());
        var first = file(station, sealedWithout(key));
        assertEquals(SealLevel.BASELINE_B, first.version().sealLevel());

        assertEquals(1, job(asking(service.url()), false).lift());

        var current = versions.current(first.document().id()).orElseThrow();
        assertEquals(2, current.version());
        assertEquals(SealLevel.BASELINE_LT, current.sealLevel());
        assertEquals(service.url(), current.timestampedBy());
        assertCloseTo(Instant.now().plus(Duration.ofDays(30)), current.timestampValidUntil());
        byte[] earlier = documents.read(first.document(), first.version()).orElseThrow();
        byte[] lifted = documents.read(first.document()).orElseThrow();
        assertArrayEquals(earlier, Arrays.copyOf(lifted, earlier.length), "the first seal stays as it was");
        assertEquals(2, documents.sealedVersions(first.document()).size(), "the version lifted from stays filed");
        assertValid(lifted, key, SignatureLevel.PAdES_BASELINE_LT);

        assertEquals(0, job(asking(service.url()), false).lift(), "nothing left without a timestamp");
        assertEquals(1, service.requests());
    }

    @Test
    void liftingRunsOnlyWhileTimestampsAreOn() throws Exception {
        var station = station("No timestamps station");
        var first = file(station, sealedWithout(stationKeys.forStation(station.id())));

        assertEquals(0, job(asking(), false).lift());

        assertEquals(first.version(), currentOf(first));
    }

    @Test
    void aRunStopsWhenNoServiceGivesATimestampAndFilesNothing() throws Exception {
        var station = station("Outage station");
        var key = stationKeys.forStation(station.id());
        var first = file(station, sealedWithout(key));
        var second = file(station, sealedWithout(key));

        assertEquals(
                0, job(asking(LocalTimestampService.unreachableUrl()), false).lift());

        assertEquals(first.version(), currentOf(first));
        assertEquals(second.version(), currentOf(second));
    }

    @Test
    void aVersionFiledWhileTheTimestampWasAddedIsNeverSuperseded() throws Exception {
        var service = service(LocalTimestampService.start());
        var station = station("Racing station");
        var key = stationKeys.forStation(station.id());
        var first = file(station, sealedWithout(key));
        var later = sealedWithout(key);
        var asking = asking(service.url());
        var sealer = spy(asking.sealer());
        doAnswer(invocation -> {
                    sealedDocuments.fileVersion(first.document(), later);
                    return invocation.callRealMethod();
                })
                .when(sealer)
                .lift(any());

        assertEquals(0, job(new Asking(asking.timestamps(), sealer), false).lift());

        assertEquals(2, currentOf(first).version());
        assertArrayEquals(later.pdf(), documents.read(first.document()).orElseThrow(), "the later signature stays");
        assertEquals(2, documents.sealedVersions(first.document()).size());
    }

    @Test
    void aVersionThatKeepsFailingIsLiftedAfterTheOthers() throws Exception {
        var service = service(LocalTimestampService.start());
        var station = station("Stuck lifting station");
        var key = stationKeys.forStation(station.id());
        var stuck = file(station, sealedWithout(key));
        var fresh = file(station, sealedWithout(key));
        byte[] stuckPdf = documents.read(stuck.document(), stuck.version()).orElseThrow();
        var asking = asking(service.url());
        var sealer = spy(asking.sealer());
        doAnswer(invocation -> {
                    if (Arrays.equals(invocation.<byte[]>getArgument(0), stuckPdf)) {
                        throw new IllegalStateException("A file DSS cannot read");
                    }
                    return invocation.callRealMethod();
                })
                .when(sealer)
                .lift(any());

        job(new Asking(asking.timestamps(), sealer), false).lift();

        assertEquals(stuck.version(), currentOf(stuck));
        assertTrue(timestampsFailed(stuck.version()));
        assertEquals(2, currentOf(fresh).version(), "the run went on past the failing version");
        var newer = file(station, sealedWithout(key));
        assertBefore(newer.version(), stuck.version(), versions.currentWithoutTimestamp(Integer.MAX_VALUE));
    }

    @Test
    void aVersionWhoseFileIsMissingIsRenewedAfterTheOthers() throws Exception {
        var service = service(LocalTimestampService.start());
        var longLived = service(LocalTimestampService.lastingFor(Duration.ofDays(400)));
        var station = station("Missing file station");
        var key = stationKeys.forStation(station.id());
        var missing = file(station, sealedBy(service, key));
        var present = file(station, sealedBy(service, key));
        var due = Instant.now().plus(Duration.ofDays(60));
        assertBefore(missing.version(), present.version(), versions.currentWithTimestampEndingBefore(due, 1000));
        var store = spy(documents);
        doReturn(Optional.empty()).when(store).read(missing.document(), missing.version());
        var asking = asking(longLived.url());
        var renewing = new SealedVersionTimestamps(
                versions,
                memberDocumentRepo,
                store,
                sealedDocuments,
                asking.sealer(),
                asking.timestamps(),
                true,
                Clock.systemUTC());

        renewing.renew();

        assertEquals(missing.version(), currentOf(missing));
        assertTrue(timestampsFailed(missing.version()));
        assertEquals(2, currentOf(present).version());
        var newer = file(station, sealedBy(service, key));
        assertBefore(newer.version(), missing.version(), versions.currentWithTimestampEndingBefore(due, 1000));
    }

    @Test
    void aStationThatMovedAwayKeepsItsVersionsAsTheyAre() throws Exception {
        var service = service(LocalTimestampService.start());
        var station = station("Moved station");
        var first = file(station, sealedWithout(stationKeys.forStation(station.id())));
        query("UPDATE station SET moved_away_at = now() WHERE id = :id;")
                .single(call().bind("id", station.id()))
                .update();

        assertEquals(0, job(asking(service.url()), false).lift());

        assertEquals(first.version(), currentOf(first));
        assertEquals(0, service.requests());
    }

    @Test
    void aTimestampAboutToEndIsRenewedIntoAVersionThatValidatesAtLta() throws Exception {
        var shortLived = service(LocalTimestampService.start());
        var longLived = service(LocalTimestampService.lastingFor(Duration.ofDays(400)));
        var station = station("Archive station");
        var key = stationKeys.forStation(station.id());
        var first = file(station, sealedBy(shortLived, key));
        assertEquals(SealLevel.BASELINE_LT, first.version().sealLevel());
        assertCloseTo(Instant.now().plus(Duration.ofDays(30)), first.version().timestampValidUntil());

        assertEquals(1, job(asking(longLived.url()), true).renew());

        var current = currentOf(first);
        assertEquals(2, current.version());
        assertEquals(SealLevel.BASELINE_LTA, current.sealLevel());
        assertEquals(longLived.url(), current.timestampedBy());
        assertCloseTo(Instant.now().plus(Duration.ofDays(400)), current.timestampValidUntil());
        byte[] earlier = documents.read(first.document(), first.version()).orElseThrow();
        byte[] renewed = documents.read(first.document()).orElseThrow();
        assertArrayEquals(earlier, Arrays.copyOf(renewed, earlier.length), "every earlier revision stays");
        assertValid(renewed, key, SignatureLevel.PAdES_BASELINE_LTA);

        assertEquals(0, job(asking(longLived.url()), true).renew(), "the new timestamp is not due");
        assertEquals(1, longLived.requests());
    }

    @Test
    void renewalIsOffUnlessSwitchedOnAndNeedsTimestamps() throws Exception {
        var service = service(LocalTimestampService.start());
        var station = station("Not archiving station");
        var first = file(station, sealedBy(service, stationKeys.forStation(station.id())));

        assertEquals(0, job(asking(service.url()), false).renew(), "off by default");
        assertEquals(0, job(asking(), true).renew(), "on, but timestamps are off");

        assertEquals(first.version(), currentOf(first));
        assertEquals(1, service.requests(), "only the seal itself was stamped");
    }

    @Test
    void aRenewalThatEndsNoLaterThanTheTimestampItCoversIsNotFiled() throws Exception {
        var longLived = service(LocalTimestampService.lastingFor(Duration.ofDays(400)));
        var shortLived = service(LocalTimestampService.start());
        var station = station("No gain station");
        var key = stationKeys.forStation(station.id());
        var first = file(station, sealedBy(longLived, key));
        var second = file(station, sealedBy(longLived, key));
        var later = Clock.fixed(Instant.now().plus(Duration.ofDays(300)), ZoneOffset.UTC);

        assertEquals(0, job(asking(shortLived.url()), true, later).renew());

        assertEquals(first.version(), currentOf(first));
        assertEquals(second.version(), currentOf(second));
        assertEquals(1, shortLived.requests(), "asked once, then the run stopped");
    }

    @Test
    void noRenewalIsFiledWhenNoServiceAnswers() throws Exception {
        var service = service(LocalTimestampService.start());
        var station = station("Renewal outage station");
        var first = file(station, sealedBy(service, stationKeys.forStation(station.id())));

        assertEquals(
                0, job(asking(LocalTimestampService.unreachableUrl()), true).renew());

        assertEquals(first.version(), currentOf(first));
    }

    @Test
    void schedule() {
        var tasks = job(asking(), true).scheduledTasks();

        assertEquals(
                List.of("sealed-version-lift", "sealed-version-renewal"),
                tasks.stream().map(ScheduledTask::name).toList());
        assertEquals(
                Schedule.fixedDelay(Duration.ofMinutes(30), Duration.ofHours(1)),
                tasks.get(0).schedule());
        assertEquals(
                Schedule.fixedDelay(Duration.ofMinutes(45), Duration.ofDays(1)),
                tasks.get(1).schedule());
        tasks.forEach(task -> task.work().run());
        new SealedVersionTimestamps(null, null, null, null, null, null, true, Clock.systemUTC())
                .scheduledTasks()
                .forEach(task -> task.work().run());
    }

    private SealedVersionTimestamps job(Asking asking, boolean archive) {
        return job(asking, archive, Clock.systemUTC());
    }

    private SealedVersionTimestamps job(Asking asking, boolean archive, Clock clock) {
        return new SealedVersionTimestamps(
                versions,
                memberDocumentRepo,
                documents,
                sealedDocuments,
                asking.sealer(),
                asking.timestamps(),
                archive,
                clock);
    }

    private static Asking asking(String... urls) {
        var pinned = Arrays.stream(urls).map(LocalTimestampService::pinned).toList();
        var timestamps = new TimestampServices(pinned, SHORT_TIMEOUT, TimestampServices.BUDGET);
        return new Asking(timestamps, new PdfSealer(timestamps, revocations));
    }

    private static SealedDocument sealedWithout(SealingKey key) throws IOException {
        return asking().sealer().seal(SealedPdfs.onePagePdf(), key.privateKey(), key.chain());
    }

    private static SealedDocument sealedBy(LocalTimestampService service, SealingKey key) throws IOException {
        return asking(service.url()).sealer().seal(SealedPdfs.onePagePdf(), key.privateKey(), key.chain());
    }

    private LocalTimestampService service(LocalTimestampService service) {
        services.add(service);
        return service;
    }

    private Station station(String name) {
        var station = stationRepo.create(name + " " + System.nanoTime());
        stations.add(station);
        return station;
    }

    private static Filed file(Station station, SealedDocument sealed) {
        var account = accountRepo.create("timestamps-" + System.nanoTime() + "@test.com", "Zeit", "Stempel");
        int member = stationMemberRepo.create(station.id(), account.id()).id();
        var document = sealedDocuments.file(
                station.id(),
                new SealedFiling(List.of(member), "Einverständnis", "e.pdf", false, Uploader.nobody(), List.of()),
                sealed);
        return new Filed(document, versions.current(document.id()).orElseThrow());
    }

    private static SealedVersion currentOf(Filed filed) {
        return versions.current(filed.document().id()).orElseThrow();
    }

    private static void assertValid(byte[] sealed, SealingKey key, SignatureLevel level) {
        var reports =
                SealedPdfs.validate(sealed, List.<X509Certificate>of(key.authority(), LocalTimestampService.root()));
        var signatures = reports.getDiagnosticData().getSignatures();
        assertEquals(1, signatures.size());
        var signature = signatures.getFirst();
        assertEquals(level, signature.getSignatureFormat());
        assertTrue(signature.isSignatureValid(), "signature value and signed data");
        assertEquals(Indication.TOTAL_PASSED, reports.getSimpleReport().getIndication(signature.getId()));
        assertEquals(List.of(), SealedPdfs.validationErrors(reports));
        var timestamps = reports.getDiagnosticData().getTimestampList();
        assertFalse(timestamps.isEmpty());
        for (var timestamp : timestamps) {
            assertTrue(timestamp.isSignatureValid(), "timestamp signature");
            assertEquals(
                    Indication.PASSED,
                    reports.getDetailedReport().getBasicTimestampValidationIndication(timestamp.getId()));
        }
    }

    private static void assertBefore(SealedVersion first, SealedVersion second, List<SealedVersion> order) {
        var ids = order.stream().map(SealedVersion::id).toList();
        assertTrue(ids.contains(first.id()));
        assertTrue(ids.contains(second.id()));
        assertTrue(ids.indexOf(first.id()) < ids.indexOf(second.id()), ids::toString);
    }

    private static boolean timestampsFailed(SealedVersion version) {
        return query("""
                        SELECT timestamps_failed_at IS NOT NULL AS failed
                        FROM member_document_version
                        WHERE id = :id;""")
                .single(call().bind("id", version.id()))
                .map(row -> row.getBoolean("failed"))
                .first()
                .orElseThrow();
    }

    private static void assertCloseTo(Instant expected, Instant actual) {
        assertNotNull(actual);
        assertTrue(
                Math.abs(ChronoUnit.SECONDS.between(expected, actual)) < 120,
                "expected about " + expected + ", was " + actual);
    }

    /** The timestamp services a test asks and a sealer that asks them. */
    private record Asking(TimestampServices timestamps, PdfSealer sealer) {}

    /** A sealed document as filed, with its first version. */
    private record Filed(Document document, SealedVersion version) {}
}
