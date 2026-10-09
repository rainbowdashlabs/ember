/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.SealedVersion;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.repository.SealedVersionRepository;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.documents.service.SealedDocumentService;
import dev.chojo.ember.feature.generator.entity.DocumentGeneration;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.service.pdf.PdfFiles;
import dev.chojo.ember.feature.generator.service.pdf.SignatureFields;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.signing.entity.CompletedSigning;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.SealLevel;
import dev.chojo.ember.feature.signing.entity.SignatureLevel;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.SigningAct;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.entity.SigningEvidence;
import dev.chojo.ember.feature.signing.entity.SigningEvidenceFile;
import dev.chojo.ember.feature.signing.entity.SigningStatements;
import dev.chojo.ember.feature.signing.entity.StoredEvidence;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.Sha256;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sealing a request's state into its member document after an act, against the database, real station keys,
 * the record page rendered by Typst and DSS: each state is a fresh sealed version built from the file the
 * document was filed with, a later act supersedes the version before and both stay filed, the record says
 * where its times come from, a state that moved on while it was sealed is dropped for the newer one, an
 * act whose sealing failed stays recorded until a later run seals it, a request whose sealing keeps failing
 * is swept after the others, and two sealings racing on one request file one version. No timestamp service
 * outside loopback is asked.
 */
class SigningStateSealerTest extends RepositoryTestBase {
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String BASE_URL = "https://ember.example.org";
    private static final SigningStatements STATEMENTS =
            new SigningStatements("Ich stimme zu.", "Ich bin erziehungsberechtigt und stimme zu.");
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final Pattern RECORD_HEADING = Pattern.compile("Signaturnachweis(?! ·)");
    private static final Pattern SIGNED_FIELD = Pattern.compile("\"state\"\\s*:\\s*\"SIGNED\"");

    @TempDir
    static Path storageRoot;

    private static final DocumentGenerationRepository generations = new DocumentGenerationRepository();
    private static final SignatureRequestRepository requestRepo = new SignatureRequestRepository();
    private static final SigningEvidenceRepository evidenceRepo = new SigningEvidenceRepository();
    private static final SigningKeyRepository keyRepo = new SigningKeyRepository();
    private static final SigningKeyWrap wrap = new SigningKeyWrap(SECRET);
    private static final SealedVersionRepository versions = new SealedVersionRepository();

    private static DocumentService documents;
    private static SealedDocumentService sealedDocuments;
    private static SignatureRequestService requests;
    private static SignatureFieldService fields;
    private static SignedCopies copies;
    private static StationSigningKeys stationKeys;
    private static Station station;
    private static StationMember manager;
    private static int template;
    private static int loginPermission;

    @BeforeAll
    static void setup() {
        var backend = new LocalStorageBackend(storageRoot);
        documents = newDocumentService(new StorageService(new StorageBackendResolver(backend), backend));
        sealedDocuments = new SealedDocumentService(memberDocumentRepo, versions, documents);
        var guardianPolicy = new GuardianPolicy(stationMemberRepo);
        var guards = new SigningGuards(
                new DocumentAccessService(memberDocumentRepo, documents, guardianPolicy),
                memberDocumentRepo,
                guardianPolicy);
        requests = new SignatureRequestService(
                requestRepo,
                evidenceRepo,
                generations,
                memberDocumentRepo,
                documents,
                stationMemberRepo,
                memberNameResolver,
                guardianPolicy,
                new SignerResolver(stationMemberRepo, memberNameResolver, memberPermissionResolver),
                guards,
                mock(SignatureNotices.class),
                (template, member, name) -> STATEMENTS);
        fields = new SignatureFieldService(
                requestRepo,
                evidenceRepo,
                requests,
                guards,
                guardianPolicy,
                memberNameResolver,
                mock(SignatureNotices.class),
                completed -> {});
        copies = TestNotices.copies(emailQueueRepo, stationRepo, stationMemberRepo, accountRepo);
        stationKeys = new StationSigningKeys(keyRepo, new SigningCertificates(), wrap, stationRepo, BASE_URL);
        loginPermission = stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow()
                .id();
        station = stationRepo.create("Signing State Station");
        manager = member("Maria", "Leitung");
        int author = accountRepo.create(email(), "Vor", "Lage").id();
        template = new DocumentTemplateRepository()
                .create(new Owner.Station(station.id()), draft(), author)
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    void anActIsSealedIntoItsDocumentWhichKeepsTheFileItWasFiledWith() throws IOException {
        var signer = member("Lea", "Erste");
        var request = ask(signer);
        var evidence = sign(request, signer, "participant");

        assertTrue(sealer(SealedPdfs.noTimestamps()).sealLatest(request.id()));

        var document = documentOf(request);
        assertTrue(document.sealed());
        assertTrue(document.keepOnArchive());
        var version = versions.current(document.id()).orElseThrow();
        assertEquals(1, version.version());
        assertEquals(SealLevel.BASELINE_B, version.sealLevel());
        assertEquals(version.sha256(), sealedHashOf(evidence));
        byte[] sealed = documents.read(document).orElseThrow();
        assertEquals(version.sha256(), Sha256.hex(sealed));
        assertEquals(sealed.length, document.sizeBytes());
        assertEquals(
                request.contentSha256(),
                Sha256.hex(documents.readUploaded(document).orElseThrow()));
        assertSealIntact(sealed);
        try (var pdf = Loader.loadPDF(sealed)) {
            assertEquals(2, pdf.getNumberOfPages(), "the content page and the record after it");
            assertTrue(record(pdf).contains("Beim Versiegeln hat diese Installation keine Zeitstempel eingeholt"));
            assertTrue(attachedEvidence(pdf).contains("\"participant\""));
        }

        assertFalse(sealer(SealedPdfs.noTimestamps()).sealLatest(request.id()), "nothing new to seal");
        assertEquals(1, versions.versionsOf(document.id()).size());
    }

    @Test
    void aLaterActSupersedesTheVersionBeforeAndBothStayFiledAndValid() throws IOException {
        var signer = member("Paul", "Zweiter");
        var request = ask(signer, "participant", "issuer");
        var sealer = sealer(SealedPdfs.noTimestamps());
        var first = sign(request, signer, "participant");
        sealer.sealLatest(request.id());
        var second = sign(request, manager, "issuer");

        assertTrue(sealer.sealLatest(request.id()));

        var document = documentOf(request);
        List<SealedVersion> filed = versions.versionsOf(document.id());
        assertEquals(2, filed.size());
        var current = filed.getFirst();
        var before = filed.getLast();
        assertTrue(current.current());
        assertNotNull(before.supersededAt());
        assertEquals(before.sha256(), sealedHashOf(first));
        assertEquals(current.sha256(), sealedHashOf(second));
        byte[] older = documents.read(document, before).orElseThrow();
        byte[] newer = documents.read(document).orElseThrow();
        assertSealIntact(older);
        assertSealIntact(newer);
        try (var pdf = Loader.loadPDF(older)) {
            assertEquals(1, signedFieldsIn(attachedEvidence(pdf)));
        }
        try (var pdf = Loader.loadPDF(newer)) {
            assertEquals(1, recordsIn(pdf), "built from the content again, not from the version before");
            assertEquals(2, signedFieldsIn(attachedEvidence(pdf)));
        }
        assertEquals(
                request.contentSha256(),
                Sha256.hex(documents.readUploaded(document).orElseThrow()));
    }

    @Test
    void withoutAnAnsweringServiceTheRecordSaysSoAndTheServicesAreAskedOnce() throws IOException {
        var signer = member("Nina", "Ohnezeit");
        var request = ask(signer);
        sign(request, signer, "participant");
        var unanswered = new TimestampServices(
                List.of(LocalTimestampService.pinned(LocalTimestampService.unreachableUrl())),
                TimestampServices.TIMEOUT,
                TimestampServices.BUDGET);
        var pdfSealer = spy(pdfSealer(unanswered));

        assertTrue(sealer(unanswered, pdfSealer).sealLatest(request.id()));

        verify(pdfSealer, times(1)).seal(any(), any(), any());
        verify(pdfSealer, times(1)).sealWithoutTimestamp(any(), any(), any());
        var document = documentOf(request);
        assertEquals(
                SealLevel.BASELINE_B,
                versions.current(document.id()).orElseThrow().sealLevel());
        try (var pdf = Loader.loadPDF(documents.read(document).orElseThrow())) {
            assertTrue(record(pdf).contains("Beim Versiegeln hat kein Zeitstempeldienst geantwortet"));
        }
    }

    @Test
    void withAnAnsweringServiceTheStateIsSealedLongTerm() throws IOException {
        var signer = member("Tim", "Zeitstempel");
        var request = ask(signer);
        sign(request, signer, "participant");

        try (var service = LocalTimestampService.start()) {
            var stamped = new TimestampServices(
                    List.of(service.pinned()), TimestampServices.TIMEOUT, TimestampServices.BUDGET);
            assertTrue(sealer(stamped).sealLatest(request.id()));
        }

        var document = documentOf(request);
        var version = versions.current(document.id()).orElseThrow();
        assertEquals(SealLevel.BASELINE_LT, version.sealLevel());
        assertNotNull(version.timestampedBy());
        try (var pdf = Loader.loadPDF(documents.read(document).orElseThrow())) {
            assertTrue(
                    record(pdf).contains("Das Siegel trägt einen Zeitstempel eines unabhängigen Zeitstempeldienstes"));
        }
    }

    @Test
    void aStateThatMovedOnWhileItWasSealedIsDroppedForTheNewerOne() throws IOException {
        var signer = member("Rita", "Rennen");
        var request = ask(signer, "participant", "issuer");
        var sealer = sealer(SealedPdfs.noTimestamps());
        var first = sign(request, signer, "participant");
        var stale = sealer.unsealedState(request.id()).orElseThrow();
        var staleSeal = sealer.seal(stale);
        var second = sign(request, manager, "issuer");

        assertFalse(sealer.file(stale, staleSeal));
        assertFalse(documentOf(request).sealed());
        assertNull(sealedHashOf(first));

        assertTrue(sealer.sealLatest(request.id()));
        var version = versions.current(documentOf(request).id()).orElseThrow();
        assertEquals(1, version.version());
        assertEquals(version.sha256(), sealedHashOf(first));
        assertEquals(version.sha256(), sealedHashOf(second));
        assertFalse(sealer.file(stale, staleSeal), "a state already carried by a version is never filed again");
    }

    @Test
    void anActWhoseSealingFailedStaysRecordedUntilTheSweepSealsIt() throws IOException {
        var signer = member("Fred", "Fehler");
        var request = ask(signer);
        var evidence = sign(request, signer, "participant");
        var brokenKeys = mock(StationSigningKeys.class);
        when(brokenKeys.forStation(anyInt())).thenThrow(new SigningKeyWrapException("The key does not open"));
        var broken = new SigningStateSealer(
                requestRepo,
                evidenceRepo,
                memberDocumentRepo,
                documents,
                sealedDocuments,
                brokenKeys,
                assembler(),
                pdfSealer(SealedPdfs.noTimestamps()),
                copies,
                SealedStateFollowUp.NONE);

        assertThrows(SigningKeyWrapException.class, () -> broken.sealLatest(request.id()));
        new SigningStateSweeper(evidenceRepo, broken).sweep(Instant.now().plus(Duration.ofHours(1)));
        assertNull(sealedHashOf(evidence));
        assertFalse(documentOf(request).sealed());

        var sweeper = new SigningStateSweeper(evidenceRepo, sealer(SealedPdfs.noTimestamps()));
        sweeper.sweep(Instant.now());
        assertNull(sealedHashOf(evidence), "an act is left to its own sealing for a while");

        sweeper.sweep(Instant.now().plus(SigningStateSweeper.GRACE).plusSeconds(60));
        assertEquals(versions.current(documentOf(request).id()).orElseThrow().sha256(), sealedHashOf(evidence));
    }

    @Test
    void aRequestThatKeepsFailingIsSweptAfterTheOthersUntilItIsSealed() throws IOException {
        var stuckSigner = member("Sina", "Stocken");
        var stuck = ask(stuckSigner);
        sign(stuck, stuckSigner, "participant");
        var laterSigner = member("Lars", "Spaeter");
        var later = ask(laterSigner);
        sign(later, laterSigner, "participant");
        var due = Instant.now().plus(Duration.ofHours(1));
        assertBefore(stuck, later, evidenceRepo.requestsToSeal(due, Integer.MAX_VALUE));

        var failing = mock(SigningStateSealer.class);
        when(failing.sealLatest(stuck.id())).thenThrow(new SigningKeyWrapException("The key does not open"));
        new SigningStateSweeper(evidenceRepo, failing).sweep(due);

        assertTrue(sealFailed(stuck));
        assertFalse(sealFailed(later));
        assertBefore(later, stuck, evidenceRepo.requestsToSeal(due, Integer.MAX_VALUE));

        assertTrue(sealer(SealedPdfs.noTimestamps()).sealLatest(stuck.id()));

        assertTrue(documentOf(stuck).sealed());
        assertFalse(sealFailed(stuck), "a filed version forgets the failure");
        assertFalse(evidenceRepo.requestsToSeal(due, Integer.MAX_VALUE).contains(stuck.id()));
    }

    @Test
    void anActsOwnSealingAndTheSweepRacingFileOneVersion() throws Exception {
        var signer = member("Rena", "Gleichzeitig");
        var request = ask(signer);
        var evidence = sign(request, signer, "participant");
        var racing = sealingInStep(request);
        var onlyThisRequest = spy(evidenceRepo);
        doReturn(List.of(request.id())).when(onlyThisRequest).requestsToSeal(any(), anyInt());
        var sweeper = new SigningStateSweeper(onlyThisRequest, racing);

        var pool = Executors.newFixedThreadPool(2);
        try {
            var own = pool.submit(() -> racing.sealLatest(request.id()));
            var swept = pool.submit(() ->
                    sweeper.sweep(Instant.now().plus(SigningStateSweeper.GRACE).plusSeconds(60)));
            own.get(2, TimeUnit.MINUTES);
            swept.get(2, TimeUnit.MINUTES);
        } finally {
            pool.shutdownNow();
        }

        var document = documentOf(request);
        assertEquals(1, versions.versionsOf(document.id()).size(), "the one that came second filed nothing");
        assertEquals(versions.current(document.id()).orElseThrow().sha256(), sealedHashOf(evidence));
        assertEquals(1, copiesTo(signer));
        assertFalse(sealFailed(request));
    }

    @Test
    void twoActsOnDifferentFieldsAtOnceEndInOneVersionCarryingBoth() throws Exception {
        var signer = member("Tara", "Takt");
        var request = ask(signer, "participant", "issuer");
        var racing = sealingInStep(request);
        var start = new CyclicBarrier(2);

        var pool = Executors.newFixedThreadPool(2);
        try {
            var participant = pool.submit(() -> {
                start.await(30, TimeUnit.SECONDS);
                sign(request, signer, "participant");
                return racing.sealLatest(request.id());
            });
            var issuer = pool.submit(() -> {
                start.await(30, TimeUnit.SECONDS);
                sign(request, manager, "issuer");
                return racing.sealLatest(request.id());
            });
            boolean participantFiled = participant.get(2, TimeUnit.MINUTES);
            boolean issuerFiled = issuer.get(2, TimeUnit.MINUTES);
            assertTrue(participantFiled ^ issuerFiled, "exactly one of the two files a version");
        } finally {
            pool.shutdownNow();
        }

        var document = documentOf(request);
        assertEquals(1, versions.versionsOf(document.id()).size());
        String current = versions.current(document.id()).orElseThrow().sha256();
        var acts = evidenceRepo.evidenceOf(request.id());
        assertEquals(2, acts.size());
        for (var act : acts) assertEquals(current, act.sealedSha256());
        assertEquals(
                RequestState.COMPLETE,
                requestRepo.findById(request.id()).orElseThrow().state());
        try (var pdf = Loader.loadPDF(documents.read(document).orElseThrow())) {
            assertEquals(2, signedFieldsIn(attachedEvidence(pdf)));
        }
    }

    @Test
    void eachSignerGetsTheirOwnCopyWithTheHashOfTheVersionThatFirstCarriesTheirSignature() throws IOException {
        var first = member("Ida", "Kopie");
        var request = ask(first, "participant", "issuer");
        var sealer = sealer(SealedPdfs.noTimestamps());
        sign(request, first, "participant");
        sealer.sealLatest(request.id());
        String firstHash =
                versions.current(documentOf(request).id()).orElseThrow().sha256();

        var copy = copyTo(first);
        assertEquals("Kopie mit Unterschrift: Einverstaendnis", copy.subject());
        assertTrue(copy.body().contains(firstHash));
        assertTrue(copy.body().contains("Ida Kopie"));
        assertTrue(copy.body().contains(TestNotices.BASE_URL + "/verify"));
        assertTrue(copy.body().contains(TestNotices.BASE_URL + "/station/documents?station=" + station.uid()));
        assertTrue(copy.body().contains("weil die Wache diese Art Dokument nicht per E-Mail verschickt"));
        assertEquals(List.of(), emailQueueRepo.attachmentsOf(copy.id()));

        sign(request, manager, "issuer");
        sealer.sealLatest(request.id());

        assertEquals(1, copiesTo(first), "a later version sends the first signer nothing more");
        var issuerCopy = copyTo(manager);
        String secondHash =
                versions.current(documentOf(request).id()).orElseThrow().sha256();
        assertTrue(issuerCopy.body().contains(secondHash));
        assertFalse(issuerCopy.body().contains(firstHash));
        assertTrue(issuerCopy
                .body()
                .contains(
                        TestNotices.BASE_URL + "/station/members/detail/" + first.id() + "?station=" + station.uid()));
    }

    @Test
    void aTemplateThatAllowsItSendsTheSealedPdfWithTheCopy() throws IOException {
        query("UPDATE document_template SET signed_copy_attached = TRUE WHERE id = :id;")
                .single(call().bind("id", template))
                .update();
        try {
            var signer = member("Ola", "Anhang");
            var request = ask(signer);
            assertTrue(request.copyAttached());
            sign(request, signer, "participant");

            sealer(SealedPdfs.noTimestamps()).sealLatest(request.id());

            var copy = copyTo(signer);
            assertTrue(copy.body().contains("Die versiegelte Datei hängt an."));
            var attachments = emailQueueRepo.attachmentsOf(copy.id());
            assertEquals(1, attachments.size());
            assertEquals("e.pdf", attachments.getFirst().fileName());
            assertEquals("application/pdf", attachments.getFirst().contentType());
            assertArrayEquals(
                    documents.read(documentOf(request)).orElseThrow(),
                    attachments.getFirst().content());
        } finally {
            query("UPDATE document_template SET signed_copy_attached = FALSE WHERE id = :id;")
                    .single(call().bind("id", template))
                    .update();
        }
    }

    /**
     * A database failure while queueing the copies fails the filing with it, so nothing of the version is
     * kept and the act waits for the next seal. A copy that cannot be put together for any other reason is
     * left out, and the version stays filed.
     */
    @Test
    void aCopyThatFailsLeavesTheVersionFiledUnlessTheDatabaseFailed() throws IOException {
        var signer = member("Dora", "Datenbank");
        var request = ask(signer);
        var evidence = sign(request, signer, "participant");
        var failingStations = mock(StationRepository.class);
        when(failingStations.findById(anyInt()))
                .thenThrow(new IllegalStateException("The query failed", new SQLException("gone", "08006")))
                .thenThrow(new IllegalStateException("The template does not render"));
        var failingCopies = new SignedCopies(
                TestNotices.emailService(emailQueueRepo),
                new MailRecipientService(accountRepo, stationMemberRepo),
                failingStations,
                new NotificationText());
        var sealer = new SigningStateSealer(
                requestRepo,
                evidenceRepo,
                memberDocumentRepo,
                documents,
                sealedDocuments,
                stationKeys,
                assembler(),
                pdfSealer(SealedPdfs.noTimestamps()),
                failingCopies,
                SealedStateFollowUp.NONE);

        assertThrows(IllegalStateException.class, () -> sealer.sealLatest(request.id()));
        assertNull(sealedHashOf(evidence));
        assertFalse(documentOf(request).sealed());

        assertTrue(sealer.sealLatest(request.id()));
        assertEquals(versions.current(documentOf(request).id()).orElseThrow().sha256(), sealedHashOf(evidence));
        assertEquals(0, copiesTo(signer));
    }

    @Test
    void aFieldSettledByAManagerIsSealedByTheSweepWhereSomebodySignedElectronically() throws IOException {
        var signer = member("Paula", "Papier");
        var request = ask(signer, "participant", "issuer");
        var sealer = sealer(SealedPdfs.noTimestamps());
        sign(request, signer, "participant");
        sealer.sealLatest(request.id());
        fields.confirmOnPaper(managing(), request.uid(), "issuer");
        var sweeper = new SigningStateSweeper(evidenceRepo, sealer);

        var document = documentOf(request);
        String first = versions.current(document.id()).orElseThrow().sha256();
        sweeper.sweep(Instant.now());
        assertEquals(1, versions.versionsOf(document.id()).size(), "a settlement is left alone for a while");

        sweeper.sweep(Instant.now().plus(SigningStateSweeper.GRACE).plusSeconds(60));

        var current = versions.current(document.id()).orElseThrow();
        assertEquals(2, current.version());
        try (var pdf = Loader.loadPDF(documents.read(document).orElseThrow())) {
            String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
            assertTrue(text.contains("Auf Papier unterschrieben"), text);
        }
        var settled = requestRepo.fieldsOf(request.id());
        assertEquals(first, settled.getFirst().sealedSha256(), "the signature was shown by the first version");
        assertEquals(current.sha256(), settled.getLast().sealedSha256(), "the paper confirmation by the second");
        assertEquals(1, copiesTo(signer), "a paper confirmation sends no copy");
        assertFalse(sealer.sealLatest(request.id()), "nothing new to seal");
    }

    @Test
    void aRequestNobodySignedElectronicallyGetsNoSealedVersion() throws IOException {
        var signer = member("Willi", "Verzicht");
        var request = ask(signer);
        fields.waive(managing(), request.uid(), "participant");

        var sweeper = new SigningStateSweeper(evidenceRepo, sealer(SealedPdfs.noTimestamps()));
        sweeper.sweep(Instant.now().plus(Duration.ofHours(1)));

        assertFalse(documentOf(request).sealed());
        assertFalse(sealer(SealedPdfs.noTimestamps()).sealLatest(request.id()));
    }

    /**
     * Withdrawing a signed agreement seals the withdrawal into a version of its own after the one that carries
     * the signature: both versions validate in DSS, the earlier one still shows the signature alone, the new
     * record page and its attachment say who withdrew it, when and why, the open field is withdrawn, the
     * request is revoked, and whoever asked for the signatures is told.
     */
    @Test
    void aWithdrawalIsSealedIntoAVersionOfItsOwnThatValidates() throws IOException {
        var signer = member("Wanda", "Widerruf");
        var request = ask(signer, "participant", "issuer");
        sign(request, signer, "participant");
        var sealer = sealer(SealedPdfs.noTimestamps());
        sealer.sealLatest(request.id());
        var notices = mock(SignatureNotices.class);
        WithdrawalRelay relay = mock(WithdrawalRelay.class);

        var withdrawal = withdrawals(sealer, notices, relay, Clock.systemUTC())
                .requireOwnedThenWithdraw(
                        at(signer),
                        request.uid(),
                        "Ich fahre doch nicht mit.",
                        new SigningCircumstances("2001:db8::7", "Test Browser"));

        var document = documentOf(request);
        List<SealedVersion> filed = versions.versionsOf(document.id());
        assertEquals(2, filed.size());
        byte[] signed = documents.read(document, filed.getLast()).orElseThrow();
        byte[] withdrawn = documents.read(document).orElseThrow();
        assertSealIntact(signed);
        assertSealIntact(withdrawn);
        assertEquals(
                filed.getFirst().sha256(),
                requestRepo.withdrawalOf(request.id()).orElseThrow().sealedSha256());
        try (var pdf = Loader.loadPDF(signed)) {
            assertFalse(record(pdf).contains("Widerruf"));
            assertFalse(attachedEvidence(pdf).contains("withdrawnByName"));
        }
        try (var pdf = Loader.loadPDF(withdrawn)) {
            String record = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
            assertTrue(record.contains("Diese Vereinbarung wurde widerrufen"), record);
            assertTrue(record.contains("Ich fahre doch nicht mit."), record);
            String evidence = attachedEvidence(pdf);
            assertTrue(evidence.contains("\"withdrawnByName\" : \"Wanda Widerruf\""), evidence);
            assertTrue(evidence.contains("\"reason\" : \"Ich fahre doch nicht mit.\""), evidence);
            assertEquals(1, signedFieldsIn(evidence), "the signature stays as evidence");
        }
        assertEquals(
                RequestState.REVOKED,
                requestRepo.findById(request.id()).orElseThrow().state());
        assertEquals(
                FieldState.WITHDRAWN,
                requestRepo.fieldsOf(request.id()).getLast().state());
        assertEquals(SignerCapacity.ACCOUNT_HOLDER, withdrawal.capacity());
        assertEquals("2001:db8:0:0:0:0:0:0", withdrawal.truncatedIp());
        verify(notices).withdrawn(any(), any(), isNull());
        verify(relay).withdrawn(any(), any());
        assertFalse(sealer.sealLatest(request.id()), "nothing new to seal");
    }

    /**
     * A withdrawal whose sealing failed stays recorded and is sealed by the sweep once its grace is over, and
     * the reader's view of the document says who may still withdraw what.
     */
    @Test
    void aWithdrawalWhoseSealFailedIsSealedByTheSweep() throws IOException {
        var signer = member("Sven", "Spaeter");
        var request = ask(signer);
        sign(request, signer, "participant");
        var sealer = sealer(SealedPdfs.noTimestamps());
        sealer.sealLatest(request.id());
        var document = documentOf(request);
        var stranger = member("Fritz", "Fremd");
        var before = withdrawals(sealer, mock(SignatureNotices.class), (r, w) -> {}, Clock.systemUTC());
        assertTrue(before.requireOwnedAgreements(at(signer), document.id())
                .getFirst()
                .withdrawable());
        assertTrue(before.requireOwnedAgreements(at(stranger), document.id()).isEmpty());
        var failing = mock(SigningStateSealer.class);
        when(failing.sealLatest(anyInt())).thenThrow(new IllegalStateException("no key"));

        withdrawals(failing, mock(SignatureNotices.class), (r, w) -> {}, Clock.systemUTC())
                .requireOwnedThenWithdraw(at(signer), request.uid(), " ", new SigningCircumstances(null, null));

        assertEquals(1, versions.versionsOf(document.id()).size());
        assertNull(requestRepo.withdrawalOf(request.id()).orElseThrow().reason(), "a blank reason is none");
        var listed = before.requireOwnedAgreements(at(signer), document.id()).getFirst();
        assertFalse(listed.withdrawable());
        assertNotNull(listed.withdrawnAt());
        var sweeper = new SigningStateSweeper(evidenceRepo, sealer);
        sweeper.sweep(Instant.now());
        assertEquals(1, versions.versionsOf(document.id()).size(), "a withdrawal is left alone for a while");

        sweeper.sweep(Instant.now().plus(SigningStateSweeper.GRACE).plusSeconds(60));

        var current = versions.current(document.id()).orElseThrow();
        assertEquals(2, current.version());
        assertEquals(
                current.sha256(),
                requestRepo.withdrawalOf(request.id()).orElseThrow().sealedSha256());
        assertSealIntact(documents.read(document).orElseThrow());
    }

    private static SignatureWithdrawals withdrawals(
            SigningStateSealer sealer, SignatureNotices notices, WithdrawalRelay relay, Clock clock) {
        var guardianPolicy = new GuardianPolicy(stationMemberRepo);
        return new SignatureWithdrawals(
                requestRepo,
                requests,
                new WithdrawalRights(guardianPolicy),
                memberNameResolver,
                sealer,
                notices,
                eventRepo,
                eventRegistrationRepo,
                mock(AppointmentSignatures.class),
                relay,
                clock);
    }

    @Test
    void theSweepRunsEveryQuarterHourAndSwallowsItsFailures() {
        var sweeper = new SigningStateSweeper(evidenceRepo, sealer(SealedPdfs.noTimestamps()));
        var task = sweeper.scheduledTasks().getFirst();

        assertEquals("signing-state-sweep", task.name());
        assertEquals(Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofMinutes(15)), task.schedule());
        task.work().run();
        new SigningStateSweeper(null, sealer(SealedPdfs.noTimestamps())).sweep();
    }

    private static SigningStateSealer sealer(TimestampServices timestamps) {
        return sealer(timestamps, pdfSealer(timestamps));
    }

    private static SigningStateSealer sealer(TimestampServices timestamps, PdfSealer pdfSealer) {
        return new SigningStateSealer(
                requestRepo,
                evidenceRepo,
                memberDocumentRepo,
                documents,
                sealedDocuments,
                stationKeys,
                assembler(),
                pdfSealer,
                copies,
                SealedStateFollowUp.NONE);
    }

    /**
     * A sealer whose two sealings of the given request each wait for the other once they read the state, so
     * both have read it before either files.
     */
    private static SigningStateSealer sealingInStep(SignatureRequest request) {
        var bothRead = new CyclicBarrier(2);
        var sealer = spy(sealer(SealedPdfs.noTimestamps()));
        doAnswer(invocation -> {
                    SigningStateSealer.UnsealedState state = invocation.getArgument(0);
                    if (state.view().request().id() == request.id()) bothRead.await(30, TimeUnit.SECONDS);
                    return invocation.callRealMethod();
                })
                .when(sealer)
                .seal(any());
        return sealer;
    }

    private static void assertBefore(SignatureRequest first, SignatureRequest second, List<Integer> order) {
        assertTrue(order.contains(first.id()));
        assertTrue(order.contains(second.id()));
        assertTrue(order.indexOf(first.id()) < order.indexOf(second.id()), order::toString);
    }

    private static boolean sealFailed(SignatureRequest request) {
        return query("SELECT seal_failed_at IS NOT NULL AS failed FROM signing_request WHERE id = :id;")
                .single(call().bind("id", request.id()))
                .map(row -> row.getBoolean("failed"))
                .first()
                .orElseThrow();
    }

    private static PdfSealer pdfSealer(TimestampServices timestamps) {
        return new PdfSealer(timestamps, new StationKeyRevocations(keyRepo, new RevocationLists(), wrap));
    }

    private static SigningStateAssembler assembler() {
        return new SigningStateAssembler(Clock.systemUTC());
    }

    private static void assertSealIntact(byte[] sealed) {
        var authority = stationKeys.forStation(station.id()).authority();
        var signatures =
                SealedPdfs.validate(sealed, authority).getDiagnosticData().getSignatures();
        assertEquals(1, signatures.size());
        assertTrue(signatures.getFirst().isSignatureValid());
        assertTrue(SealedPdfs.referencedDataIntact(signatures.getFirst()));
    }

    /** How many record pages begin in the document, by their heading rather than the line atop each page. */
    private static long recordsIn(PDDocument pdf) throws IOException {
        String text = new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
        return RECORD_HEADING.matcher(text).results().count();
    }

    private static String record(PDDocument pdf) throws IOException {
        var stripper = new PDFTextStripper();
        stripper.setStartPage(pdf.getNumberOfPages());
        return stripper.getText(pdf).replaceAll("\\s+", " ");
    }

    private static String attachedEvidence(PDDocument pdf) throws IOException {
        var spec = pdf.getDocumentCatalog()
                .getNames()
                .getEmbeddedFiles()
                .getNames()
                .get(SigningEvidenceFile.FILE_NAME);
        assertNotNull(spec);
        return new String(spec.getEmbeddedFile().toByteArray(), StandardCharsets.UTF_8);
    }

    private static long signedFieldsIn(String evidenceJson) {
        return SIGNED_FIELD.matcher(evidenceJson).results().count();
    }

    private static Document documentOf(SignatureRequest request) {
        Integer documentId = request.documentId();
        assertNotNull(documentId);
        return memberDocumentRepo.findById(documentId).orElseThrow();
    }

    private static String sealedHashOf(StoredEvidence stored) {
        return evidenceRepo.evidenceOf(requestOf(stored)).stream()
                .filter(evidence -> evidence.id() == stored.id())
                .findFirst()
                .orElseThrow()
                .sealedSha256();
    }

    private static int requestOf(StoredEvidence stored) {
        var uid = stored.evidence().act().requestUid();
        return requestRepo.findByUid(uid).orElseThrow().id();
    }

    private static SignatureRequest ask(StationMember member, String... fieldNames) throws IOException {
        String[] names = fieldNames.length == 0 ? new String[] {"participant"} : fieldNames;
        byte[] pdf = pdfWith(names);
        var document = documents.store(
                station.id(),
                List.of(member.id()),
                "Einverstaendnis",
                "e.pdf",
                "application/pdf",
                pdf,
                false,
                false,
                Uploader.nobody(),
                List.of());
        var generation = generations.log(
                new DocumentGeneration(
                        0,
                        station.id(),
                        template,
                        1,
                        member.id(),
                        null,
                        Instant.now(),
                        false,
                        document.id(),
                        Sha256.hex(pdf),
                        null,
                        null,
                        null,
                        manager.id(),
                        null,
                        false,
                        true),
                List.of());
        return requests.request(managing(), generation.id());
    }

    private static StoredEvidence sign(SignatureRequest request, StationMember signer, String fieldName) {
        var field = requestRepo.fieldsOf(request.id()).stream()
                .filter(asked -> asked.fieldName().equals(fieldName))
                .findFirst()
                .orElseThrow();
        var act = new SigningAct(
                request.uid(),
                Signer.accountHolder(signer.accountId()),
                memberNameResolver.official(signer.id()),
                null,
                fieldName,
                field.statement(),
                HexFormat.of().parseHex(request.contentSha256()),
                List.of(),
                new byte[32],
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                "203.0.113.0",
                "Test Browser");
        return fields.record(
                at(signer), new CompletedSigning(SignatureLevel.SIMPLE, new SigningEvidence.TotpUnbound(act)));
    }

    private static byte[] pdfWith(String... fieldNames) throws IOException {
        try (var pdf = new PDDocument()) {
            pdf.getDocumentInformation().setTitle(UUID.randomUUID().toString());
            var page = new PDPage();
            pdf.addPage(page);
            float x = 40;
            for (String name : fieldNames) {
                SignatureFields.add(pdf, page, new PDRectangle(x, 60, 120, 40), name);
                x += 130;
            }
            return PdfFiles.save(pdf);
        }
    }

    private static StationMember member(String first, String last) {
        var account = accountRepo.create(email(), first, last);
        var member = stationMemberRepo.create(station.id(), account.id());
        stationMemberRepo.grantPermission(member.id(), loginPermission);
        return member;
    }

    private static StationSession at(StationMember member) {
        return stationSession(member, StationPermission.LOGIN);
    }

    private static StationSession managing() {
        return stationSession(manager, StationPermission.DOCUMENT_EDIT_MEMBER, StationPermission.DOCUMENT_READ_MEMBER);
    }

    private static DocumentTemplateDraft draft() {
        return new DocumentTemplateDraft(
                "Einverstaendnis",
                "Einverstaendnis",
                "Einverstaendnis",
                List.of(),
                false,
                true,
                true,
                false,
                false,
                30,
                RestrictionMode.AND,
                DocumentLanguage.DE,
                null,
                null,
                LetterContent.blank());
    }

    private static String email() {
        return "signing-state-" + NAMES.incrementAndGet() + "-" + System.nanoTime() + "@test.com";
    }

    private static String emailOf(StationMember member) {
        return accountRepo.findById(member.accountId()).orElseThrow().email();
    }

    private static EmailQueueRepository.QueuedEmail copyTo(StationMember member) {
        return emailQueueRepo.findLatestFor(emailOf(member), null, null).orElseThrow();
    }

    private static int copiesTo(StationMember member) {
        return query("SELECT count(*) AS count FROM email_queue WHERE recipient = :recipient;")
                .single(call().bind("recipient", emailOf(member)))
                .map(row -> row.getInt("count"))
                .first()
                .orElse(0);
    }
}
