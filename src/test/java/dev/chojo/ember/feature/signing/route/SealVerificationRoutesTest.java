/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.repository.SealedVersionRepository;
import dev.chojo.ember.feature.signing.entity.RevocationReason;
import dev.chojo.ember.feature.signing.entity.SealedDocument;
import dev.chojo.ember.feature.signing.entity.SealingKey;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.feature.signing.service.PublishedCertificates;
import dev.chojo.ember.feature.signing.service.RevocationLists;
import dev.chojo.ember.feature.signing.service.SealVerifier;
import dev.chojo.ember.feature.signing.service.SigningCertificates;
import dev.chojo.ember.feature.signing.service.SigningKeyWrap;
import dev.chojo.ember.feature.signing.service.StationKeyRevocations;
import dev.chojo.ember.feature.signing.service.StationSigningKeys;
import dev.chojo.ember.feature.signing.service.TestSealing;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.Sha256;
import io.javalin.testtools.Request;
import io.javalin.testtools.Response;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checking the seals of an uploaded PDF over HTTP, through the real services and the database: a
 * document a station sealed here, one changed afterwards, one with a note added after sealing, one sealed
 * with a key revoked later, one sealed by a stranger, one lifted to a timestamp later, an unsigned one, one
 * sent in chunks, and the refusals for files that are no PDF, too large or missing. No answer names the
 * document or the people it concerns.
 */
class SealVerificationRoutesTest extends RepositoryTestBase {
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String VERIFY = PREFIX + "/public/signing/verify";
    private static final String MEMBER_FIRST_NAME = "Mareike";
    private static final String MEMBER_LAST_NAME = "Siegelmann";
    private static final String TITLE = "Einwilligung Zeltlager Siegelmann";
    private static final String FILE_NAME = "einwilligung-mareike.pdf";

    private static TestSealing timestamps;

    private final SigningKeyRepository repository = new SigningKeyRepository();
    private final SigningKeyWrap wrap = new SigningKeyWrap(SECRET);
    private final StationSigningKeys signingKeys =
            new StationSigningKeys(repository, new SigningCertificates(), wrap, stationRepo, new Api());
    private final StationKeyRevocations revocations =
            new StationKeyRevocations(repository, new RevocationLists(), wrap);
    private final SealedVersionRepository versions = new SealedVersionRepository();
    private final SealVerifier verifier =
            new SealVerifier(repository, revocations, versions, List.of(TestSealing.timestampRoot()));
    private final RouteHarness harness = RouteHarness.serving(
            new PublicSigningRoutes(new PublishedCertificates(repository, revocations, stationRepo), verifier));
    private final List<Integer> stations = new ArrayList<>();

    @BeforeAll
    static void startTimestampService() throws Exception {
        timestamps = TestSealing.withTimestamps();
    }

    @AfterAll
    static void stopTimestampService() {
        timestamps.close();
    }

    @BeforeEach
    void startWithoutAuthority() {
        query("DELETE FROM station_signing_key;").single(call()).delete();
        query("DELETE FROM signing_ca;").single(call()).delete();
    }

    @AfterEach
    void removeStations() {
        stations.forEach(stationRepo::delete);
    }

    @Test
    void aDocumentSealedHereEndsInTheInstallationAndIsReportedHeld() throws Exception {
        var station = station("Verified station");
        var key = signingKeys.forStation(station.id());
        var sealed = sealLongTerm(key);
        file(station, sealed);

        var answer = verify(sealed.pdf());

        var body = answer.body().string();
        assertEquals(200, answer.code(), body);
        var report = RouteHarness.body(body);
        var document = report.path("document");
        assertTrue(document.path("held").asBoolean());
        assertEquals("BASELINE_LT", document.path("sealLevel").asString());
        assertFalse(document.path("sealedAt").isNull());
        assertEquals(1, report.path("signatures").size());
        var seal = report.path("signatures").get(0);
        assertTrue(seal.path("issuedHere").asBoolean());
        assertEquals("TOTAL_PASSED", seal.path("indication").asString());
        assertTrue(seal.path("subIndication").isNull());
        assertEquals("TOTAL_PASSED", seal.path("validatorIndication").asString());
        assertEquals("BASELINE_LT", seal.path("level").asString());
        assertTrue(seal.path("intact").asBoolean());
        assertFalse(seal.path("coversWholeFile").asBoolean(), "the validation material follows the seal");
        assertEquals(
                serialOf(key.certificate()),
                seal.path("signer").path("serialNumber").asString());
        assertEquals(
                key.certificate().getSubjectX500Principal().getName(),
                seal.path("signer").path("subject").asString());
        assertEquals(
                serialOf(key.authority()),
                seal.path("issuer").path("serialNumber").asString());
        assertEquals(
                fingerprintOf(key.authority()),
                seal.path("issuer").path("sha256Fingerprint").asString());
        assertEquals("GOOD", seal.path("revocation").path("status").asString());
        assertFalse(seal.path("signingTime").isNull());
        assertEquals(1, seal.path("timestamps").size());
        var timestamp = seal.path("timestamps").get(0);
        assertTrue(timestamp.path("pinnedAuthority").asBoolean());
        assertEquals("PASSED", timestamp.path("indication").asString());
        assertTrue(timestamp.path("intact").asBoolean());
        assertFalse(timestamp.path("time").isNull());
        assertFalse(timestamp.path("authority").path("subject").asString().isBlank());
        assertEquals(0, report.path("documentTimestamps").size());

        for (var personal : List.of(MEMBER_FIRST_NAME, MEMBER_LAST_NAME, TITLE, FILE_NAME)) {
            assertFalse(body.contains(personal), "the answer names " + personal);
        }
    }

    @Test
    void aChangedByteReportsTheSealNotIntact() throws Exception {
        var station = station("Tampered station");
        var sealed = sealLongTerm(signingKeys.forStation(station.id())).pdf();
        var tampered = sealed.clone();
        int text = indexOf(tampered, "Ember seal test".getBytes(StandardCharsets.US_ASCII));
        tampered[text] = 'X';

        var report = json(verify(tampered));

        var seal = report.path("signatures").get(0);
        assertFalse(seal.path("intact").asBoolean());
        assertEquals("TOTAL_FAILED", seal.path("indication").asString());
        assertEquals("HASH_FAILURE", seal.path("subIndication").asString());
        assertTrue(seal.path("issuedHere").asBoolean(), "the chain is still the installation's");
        assertFalse(report.path("document").path("held").asBoolean());
    }

    @Test
    void aKeyRevokedAfterTheTimestampIsReportedRevokedAfterIt() throws Exception {
        var station = station("Revoked later station");
        var key = signingKeys.forStation(station.id());
        var sealed = sealLongTerm(key);
        Thread.sleep(1_100);
        revocations.revoke(station.id(), serialOf(key.certificate()), RevocationReason.KEY_COMPROMISE);

        var report = json(verify(sealed.pdf()));

        var seal = report.path("signatures").get(0);
        var revocation = seal.path("revocation");
        assertEquals("REVOKED", revocation.path("status").asString());
        assertEquals("KEY_COMPROMISE", revocation.path("reason").asString());
        var revokedAt = Instant.parse(revocation.path("revokedAt").asString());
        var stampedAt =
                Instant.parse(seal.path("timestamps").get(0).path("time").asString());
        assertTrue(stampedAt.isBefore(revokedAt), "the timestamp proves the seal predates the revocation");
        assertTrue(seal.path("intact").asBoolean());
        assertEquals("TOTAL_PASSED", seal.path("indication").asString());
        assertTrue(seal.path("subIndication").isNull());
    }

    @Test
    void aSealByAStrangersOwnKeyDoesNotEndInThisInstallation() throws Exception {
        signingKeys.forStation(station("Installation with an authority").id());
        var stranger = new SigningCertificates().authority("stranger.example.org");
        var sealed = TestSealing.withoutTimestamps()
                .seal(TestSealing.onePagePdf(), stranger.privateKey(), List.of(stranger.certificate()))
                .pdf();

        var report = json(verify(sealed));

        var seal = report.path("signatures").get(0);
        assertFalse(seal.path("issuedHere").asBoolean());
        assertTrue(seal.path("issuer").isNull());
        assertEquals(
                serialOf(stranger.certificate()),
                seal.path("signer").path("serialNumber").asString());
        assertEquals("UNKNOWN", seal.path("revocation").path("status").asString());
        assertTrue(seal.path("intact").asBoolean());
        assertEquals("BASELINE_B", seal.path("level").asString());
        assertEquals("INDETERMINATE", seal.path("indication").asString());
        assertEquals("NOT_ISSUED_HERE", seal.path("subIndication").asString());
        assertEquals("INDETERMINATE", seal.path("validatorIndication").asString());
        assertEquals(
                "NO_CERTIFICATE_CHAIN_FOUND",
                seal.path("validatorSubIndication").asString());
        assertTrue(seal.path("coversWholeFile").asBoolean());
    }

    @Test
    void aSealByAStrangerUnderAPinnedTimestampRootNeverPasses() throws Exception {
        signingKeys.forStation(station("Installation beside a timestamp root").id());
        var stranger = timestamps.strangerUnderTimestampRoot("Stranger under the timestamp root");
        var sealed = timestamps
                .sealer(revocations)
                .seal(
                        TestSealing.onePagePdf(),
                        stranger.privateKey(),
                        List.of(stranger.certificate(), TestSealing.timestampRoot()));
        assertEquals("BASELINE_LT", sealed.level().name());

        var report = json(verify(sealed.pdf()));

        var seal = report.path("signatures").get(0);
        assertFalse(seal.path("issuedHere").asBoolean());
        assertEquals("TOTAL_PASSED", seal.path("validatorIndication").asString(), "the validator alone passes it");
        assertEquals("INDETERMINATE", seal.path("indication").asString());
        assertEquals("NOT_ISSUED_HERE", seal.path("subIndication").asString());
        assertEquals("UNKNOWN", seal.path("revocation").path("status").asString());
        assertEquals(
                serialOf(TestSealing.timestampRoot()),
                seal.path("issuer").path("serialNumber").asString());
        assertTrue(seal.path("timestamps").get(0).path("pinnedAuthority").asBoolean());
    }

    @Test
    void aTimestampAddedLaterIsReportedOnTheDocument() throws Exception {
        var station = station("Lifted station");
        var key = signingKeys.forStation(station.id());
        var unstamped = TestSealing.withoutTimestamps()
                .seal(TestSealing.onePagePdf(), key.privateKey(), key.chain())
                .pdf();
        var lifted = timestamps.sealer(revocations).lift(unstamped).pdf();

        var report = json(verify(lifted));

        assertEquals(1, report.path("signatures").size());
        assertEquals(0, report.path("signatures").get(0).path("timestamps").size());
        assertEquals(1, report.path("documentTimestamps").size());
        var stamp = report.path("documentTimestamps").get(0);
        assertFalse(stamp.path("coversWholeFile").asBoolean(), "the validation material follows the stamp");
        assertTrue(stamp.path("timestamp").path("pinnedAuthority").asBoolean());
        assertTrue(stamp.path("timestamp").path("intact").asBoolean());
        assertEquals("PASSED", stamp.path("timestamp").path("indication").asString());
    }

    @Test
    void anUnsignedPdfIsAnsweredWithNothingFound() throws Exception {
        var report = json(verify(TestSealing.onePagePdf()));

        assertEquals(0, report.path("signatures").size());
        assertEquals(0, report.path("documentTimestamps").size());
        assertFalse(report.path("document").path("held").asBoolean());
        assertTrue(report.path("document").path("sealedAt").isNull());
    }

    @Test
    void aFileThatIsNoPdfIsRefused() {
        assertRefused(verify("just text".getBytes(StandardCharsets.UTF_8)), 415, DocumentRefusal.SEAL_CHECK_NOT_A_PDF);
        assertRefused(
                verify("%PDF-1.7\nnothing a reader could open".getBytes(StandardCharsets.US_ASCII)),
                415,
                DocumentRefusal.SEAL_CHECK_NOT_A_PDF);
    }

    @Test
    void aFileOverTheLimitIsRefused() {
        var tooLarge = new byte[SealVerifier.MAX_BYTES + 1];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, tooLarge, 0, 5);
        assertRefused(verify(tooLarge), 413, DocumentRefusal.SEAL_CHECK_TOO_LARGE);

        var announcedTooLarge = new byte[SealVerifier.MAX_BYTES + 2 * PublicSigningRoutes.FORM_ALLOWANCE_BYTES];
        assertRefused(verify(announcedTooLarge), 413, DocumentRefusal.SEAL_CHECK_TOO_LARGE);
    }

    @Test
    void aFileSentInChunksWithoutALengthIsChecked() throws Exception {
        byte[] unsigned = TestSealing.onePagePdf();
        var answer =
                harness.request(client -> client.request(VERIFY, TestUploads.chunkedMultipart(FILE_NAME, unsigned)));

        var body = answer.body().string();
        assertEquals(200, answer.code(), body);
        assertEquals(0, RouteHarness.body(body).path("signatures").size());
    }

    @Test
    void aFileSentInChunksIsRefusedAsSoonAsItGrowsPastTheLimit() {
        var tooLarge = new byte[SealVerifier.MAX_BYTES + 2 * PublicSigningRoutes.FORM_ALLOWANCE_BYTES];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, tooLarge, 0, 5);

        assertRefused(
                harness.request(client -> client.request(VERIFY, TestUploads.chunkedMultipart(FILE_NAME, tooLarge))),
                413,
                DocumentRefusal.SEAL_CHECK_TOO_LARGE);
    }

    @Test
    void aSealedDocumentChangedAfterwardsIsReportedModifiedAfterSealing() throws Exception {
        var station = station("Annotated station");
        var sealed = sealLongTerm(signingKeys.forStation(station.id())).pdf();
        assertFalse(
                json(verify(sealed))
                        .path("signatures")
                        .get(0)
                        .path("modifiedAfterSealing")
                        .asBoolean(),
                "the validation material added after the seal is no change");

        var seal = json(verify(annotatedAfterwards(sealed))).path("signatures").get(0);

        assertTrue(seal.path("intact").asBoolean(), "the sealed revision itself is untouched");
        assertTrue(seal.path("modifiedAfterSealing").asBoolean());
        assertFalse(seal.path("coversWholeFile").asBoolean());
    }

    @Test
    void aRequestWithoutAFileIsRefused() {
        Consumer<Request.Builder> empty =
                builder -> builder.header("Content-Type", "multipart/form-data; boundary=ember-test-boundary")
                        .post(HttpRequest.BodyPublishers.ofString("--ember-test-boundary--\r\n"));

        assertRefused(
                harness.request(client -> client.request(VERIFY, empty)), 400, DocumentRefusal.SEAL_CHECK_NO_FILE);
    }

    /** The file with a note put on its first page in a revision of its own, as a reader could add one. */
    private static byte[] annotatedAfterwards(byte[] sealed) throws IOException {
        try (var pdf = Loader.loadPDF(sealed)) {
            var page = pdf.getPage(0);
            var note = new PDAnnotationText();
            note.setRectangle(new PDRectangle(20, 20, 40, 40));
            note.setContents("Added after sealing");
            var annotations = new ArrayList<>(page.getAnnotations());
            annotations.add(note);
            page.setAnnotations(annotations);
            page.getCOSObject().setNeedToBeUpdated(true);
            var out = new ByteArrayOutputStream();
            pdf.saveIncremental(out);
            return out.toByteArray();
        }
    }

    private SealedDocument sealLongTerm(SealingKey key) throws Exception {
        var sealed = timestamps.sealer(revocations).seal(TestSealing.onePagePdf(), key.privateKey(), key.chain());
        assertEquals("BASELINE_LT", sealed.level().name());
        return sealed;
    }

    private void file(Station station, SealedDocument sealed) {
        var account = accountRepo.create(
                "seal-check-" + System.nanoTime() + "@test.com", MEMBER_FIRST_NAME, MEMBER_LAST_NAME);
        int member = stationMemberRepo.create(station.id(), account.id()).id();
        var document = memberDocumentRepo.create(
                station.id(),
                TITLE,
                FILE_NAME,
                "application/pdf",
                sealed.pdf().length,
                false,
                true,
                Uploader.nobody(),
                List.of(member));
        memberDocumentRepo.seal(document.id());
        versions.add(
                document.id(),
                Sha256.hex(sealed.pdf()),
                sealed.pdf().length,
                sealed.level(),
                sealed.timestampedBy(),
                sealed.timestampValidUntil());
    }

    private Station station(String name) {
        var station = stationRepo.create(name + " " + System.nanoTime());
        stations.add(station.id());
        return station;
    }

    private Response verify(byte[] file) {
        return harness.request(client -> client.request(VERIFY, TestUploads.multipart(FILE_NAME, file)));
    }

    private static void assertRefused(Response answer, int status, DocumentRefusal refusal) {
        assertEquals(status, answer.code());
        assertEquals(refusal, refusalOf(answer));
    }

    private static String serialOf(X509Certificate certificate) {
        return SigningCertificates.serialOf(certificate);
    }

    private static String fingerprintOf(X509Certificate certificate) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        return HexFormat.ofDelimiter(":").withUpperCase().formatHex(digest);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            if (Arrays.equals(haystack, i, i + needle.length, needle, 0, needle.length)) return i;
        }
        throw new AssertionError("not found");
    }
}
