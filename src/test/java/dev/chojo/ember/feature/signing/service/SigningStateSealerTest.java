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
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.signing.entity.CompletedSigning;
import dev.chojo.ember.feature.signing.entity.SealLevel;
import dev.chojo.ember.feature.signing.entity.SignatureLevel;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SigningAct;
import dev.chojo.ember.feature.signing.entity.SigningEvidence;
import dev.chojo.ember.feature.signing.entity.SigningEvidenceFile;
import dev.chojo.ember.feature.signing.entity.SigningStatements;
import dev.chojo.ember.feature.signing.entity.StoredEvidence;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.feature.station.entity.Station;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sealing a request's state into its member document after an act, against the database, real station keys,
 * the record page rendered by Typst and DSS: each state is a fresh sealed version built from the file the
 * document was filed with, a later act supersedes the version before and both stay filed, the record says
 * where its times come from, a state that moved on while it was sealed is dropped for the newer one, and an
 * act whose sealing failed stays recorded until a later run seals it. No timestamp service outside loopback
 * is asked.
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
                mock(SignatureNotices.class));
        fields = new SignatureFieldService(
                requestRepo,
                evidenceRepo,
                requests,
                guards,
                guardianPolicy,
                memberNameResolver,
                mock(SignatureNotices.class));
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
            assertTrue(record(pdf).contains("Diese Installation holt keine Zeitstempel ein"));
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
                assembler(SealedPdfs.noTimestamps()),
                pdfSealer(SealedPdfs.noTimestamps()),
                copies);

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
                assembler(timestamps),
                pdfSealer,
                copies);
    }

    private static PdfSealer pdfSealer(TimestampServices timestamps) {
        return new PdfSealer(timestamps, new StationKeyRevocations(keyRepo, new RevocationLists(), wrap));
    }

    private static SigningStateAssembler assembler(TimestampServices timestamps) {
        return new SigningStateAssembler(stationRepo, timestamps, BASE_URL, Clock.systemUTC());
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
        return requests.request(managing(), generation.id(), STATEMENTS);
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
