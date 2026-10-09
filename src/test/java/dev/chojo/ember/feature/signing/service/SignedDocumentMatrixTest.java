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
import dev.chojo.ember.feature.generator.service.pdf.SignatureFields;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.signing.entity.ActPicture;
import dev.chojo.ember.feature.signing.entity.ActPictureSource;
import dev.chojo.ember.feature.signing.entity.CompletedSigning;
import dev.chojo.ember.feature.signing.entity.PadesLevel;
import dev.chojo.ember.feature.signing.entity.SealCheck;
import dev.chojo.ember.feature.signing.entity.SealLevel;
import dev.chojo.ember.feature.signing.entity.SignatureLevel;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SigningAct;
import dev.chojo.ember.feature.signing.entity.SigningEvidence;
import dev.chojo.ember.feature.signing.entity.SigningEvidenceFile;
import dev.chojo.ember.feature.signing.entity.SigningStatements;
import dev.chojo.ember.feature.signing.entity.ValidationIndication;
import dev.chojo.ember.feature.signing.entity.ValidationSubIndication;
import dev.chojo.ember.feature.signing.repository.IssuerSignatureRepository;
import dev.chojo.ember.feature.signing.repository.PartnerAuthorityRepository;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.TypstCompiler;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObjectKey;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The signed documents Ember files, end to end and checked the way a reader checks them: a request on a
 * PDF/A-3b letter with signature fields, signed with a picture in each field, its state assembled and
 * sealed with the station's real key, then validated offline by the installation's verifier, which runs
 * the EU DSS validator. Every baseline level the sealer reaches validates at that level; a changed byte in
 * the content, a page added after the seal, a page covered and evidence replaced by a later revision all
 * fail; two signers leave two valid versions, the later superseding the earlier; an outage of every
 * timestamp service falls back to a seal without a timestamp and the record says so; the record and the copy
 * with it, built on download, validate and stay PDF/A-3b; and a document with signature pictures drawn into
 * its fields stays PDF/A-3b ({@link PdfA3b}).
 *
 * <p>The timestamp services run on loopback ({@link LocalTimestampService}); nothing reaches the internet.
 * The documents are kept as {@link SigningSamples} when asked for.
 */
class SignedDocumentMatrixTest extends RepositoryTestBase {
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String BASE_URL = "https://ember.example.org";
    private static final SigningStatements STATEMENTS =
            new SigningStatements("Ich stimme zu.", "Ich bin erziehungsberechtigt und stimme zu.");
    private static final AtomicInteger NAMES = new AtomicInteger();

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
    private static StationSigningKeys stationKeys;
    private static SealVerifier verifier;
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
        stationKeys = new StationSigningKeys(keyRepo, new SigningCertificates(), wrap, stationRepo, BASE_URL);
        verifier = new SealVerifier(
                keyRepo,
                new StationKeyRevocations(keyRepo, new RevocationLists(), wrap),
                versions,
                new PartnerAuthorityRepository(),
                List.of(LocalTimestampService.root()));
        loginPermission = stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow()
                .id();
        station = stationRepo.create("Signing Matrix Station");
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
    void withTimestampsOffTheStateValidatesAtBaselineB() throws IOException {
        var request = signedByOne("Bea", "Basis");

        byte[] sealed = sealLatest(request, SealedPdfs.noTimestamps());

        assertEquals(SealLevel.BASELINE_B, currentVersion(request).sealLevel());
        var check = assertPassed(sealed, PadesLevel.BASELINE_B);
        assertTrue(check.timestamps().isEmpty());
        assertTrue(check.coversWholeFile());
        SigningSamples.write("baseline-b.pdf", sealed);
    }

    @Test
    void withAServiceWhoseRevocationDataCannotBeHadTheStateValidatesAtBaselineT() throws IOException {
        var request = signedByOne("Theo", "Zeit");

        byte[] sealed;
        try (var service = LocalTimestampService.listingAt(LocalTimestampService.unreachableUrl())) {
            sealed = sealLatest(request, timestampsOf(service));
        }

        assertEquals(SealLevel.BASELINE_T, currentVersion(request).sealLevel());
        var check = assertPassed(sealed, PadesLevel.BASELINE_T);
        assertEquals(1, check.timestamps().size());
        assertTrue(check.timestamps().getFirst().intact());
        assertTrue(check.timestamps().getFirst().pinnedAuthority());
        assertTrue(check.coversWholeFile(), "nothing follows the seal at B-T");
        SigningSamples.write("baseline-t.pdf", sealed);
    }

    @Test
    void withAnAnsweringServiceTheStateValidatesAtBaselineLongTerm() throws IOException {
        var request = signedByOne("Lotte", "Lang");

        byte[] sealed;
        try (var service = LocalTimestampService.start()) {
            sealed = sealLatest(request, timestampsOf(service));
        }

        assertEquals(SealLevel.BASELINE_LT, currentVersion(request).sealLevel());
        var check = assertPassed(sealed, PadesLevel.BASELINE_LT);
        assertEquals(ValidationIndication.PASSED, check.timestamps().getFirst().indication());
        assertFalse(check.coversWholeFile(), "the validation material follows the seal in a revision of its own");
        assertFalse(check.modifiedAfterSealing(), "validation material is no change to the sealed document");
        SigningSamples.write("baseline-lt.pdf", sealed);
    }

    @Test
    void aByteChangedInTheContentFailsTheSeal() throws IOException {
        var request = signedByOne("Tom", "Tausch");
        byte[] sealed;
        try (var service = LocalTimestampService.start()) {
            sealed = sealLatest(request, timestampsOf(service));
        }

        byte[] tampered = withByteFlippedInPageContent(sealed, 0);

        var verification = verifier.verify(tampered);
        var check = verification.signatures().getFirst();
        assertFalse(check.intact());
        assertTrue(check.issuedHere(), "the chain is still the installation's");
        assertEquals(ValidationIndication.TOTAL_FAILED, check.indication());
        assertEquals(ValidationSubIndication.HASH_FAILURE, check.subIndication());
        assertFalse(verification.document().held());
    }

    @Test
    void aPageAddedByAnIncrementalUpdateFailsTheSeal() throws IOException {
        var request = signedByOne("Paula", "Plus");
        byte[] sealed;
        try (var service = LocalTimestampService.start()) {
            sealed = sealLatest(request, timestampsOf(service));
        }

        byte[] extended = withPageAppendedIncrementally(sealed);

        assertArrayEquals(sealed, Arrays.copyOf(extended, sealed.length), "an update keeps the sealed bytes");
        try (var pdf = Loader.loadPDF(extended)) {
            assertEquals(pageCount(sealed) + 1, pdf.getNumberOfPages());
        }
        var verification = verifier.verify(extended);
        var check = verification.signatures().getFirst();
        assertTrue(check.intact(), "the signed bytes themselves are unchanged");
        assertTrue(check.modifiedAfterSealing());
        assertFalse(check.coversWholeFile());
        assertEquals(ValidationIndication.TOTAL_FAILED, check.indication());
        assertEquals(ValidationSubIndication.FORMAT_FAILURE, check.subIndication());
        assertFalse(verification.document().held());
    }

    /**
     * The record and the copy with it, as a reader downloads them, are sealed at baseline long term when a
     * service answers, stay PDF/A-3b, and fail their seal once a byte of the record is changed. Neither is a
     * version the installation holds.
     */
    @Test
    void theRecordAndTheCopyWithItValidateAndAChangedByteFailsThem() throws IOException {
        var request = signedByOne("Rolf", "Rekord");
        byte[] record;
        byte[] copy;
        Document document;
        try (var service = LocalTimestampService.start()) {
            sealLatest(request, timestampsOf(service));
            document = documentOf(request);
            int version = currentVersion(request).version();
            record = records(timestampsOf(service)).record(document, version).pdf();
            copy = records(timestampsOf(service)).withRecord(document, version).pdf();
        }

        for (byte[] built : List.of(record, copy)) {
            var verification = verifier.verify(built);
            assertFalse(verification.document().held(), "a download is no version");
            assertEquals(1, verification.signatures().size());
            assertPassed(verification.signatures().getFirst(), PadesLevel.BASELINE_LT);
            PdfA3b.assertConforms(built);
        }
        assertEquals(pageCount(documents.read(document).orElseThrow()) + pageCount(record), pageCount(copy));
        assertNotNull(verifier.verify(copy).evidence(), "the copy still carries the evidence");

        byte[] tampered = withByteFlippedInPageContent(copy, pageCount(copy) - 1);
        var check = verifier.verify(tampered).signatures().getFirst();
        assertFalse(check.intact());
        assertEquals(ValidationIndication.TOTAL_FAILED, check.indication());
        SigningSamples.write("record.pdf", record);
        SigningSamples.write("with-record.pdf", copy);
    }

    @Test
    void aPageCoveredByAnIncrementalUpdateFailsTheSeal() throws IOException {
        var request = signedByOne("Rita", "Ruebermalt");
        byte[] sealed;
        try (var service = LocalTimestampService.start()) {
            sealed = sealLatest(request, timestampsOf(service));
        }

        byte[] covered = withLastPageCoveredIncrementally(sealed);

        assertArrayEquals(sealed, Arrays.copyOf(covered, sealed.length), "an update keeps the sealed bytes");
        assertEquals(pageCount(sealed), pageCount(covered));
        assertEquals(lastPageStreams(sealed) + 1, lastPageStreams(covered), "the cover is drawn on that page");
        assertFailsAsModified(covered);
    }

    @Test
    void evidenceReplacedByAnIncrementalUpdateFailsTheSeal() throws IOException {
        var request = signedByOne("Erik", "Ersatz");
        byte[] sealed;
        try (var service = LocalTimestampService.start()) {
            sealed = sealLatest(request, timestampsOf(service));
        }
        byte[] forged;
        try (var pdf = Loader.loadPDF(sealed)) {
            forged = evidenceBytes(pdf).clone();
        }
        String original = new String(forged, StandardCharsets.UTF_8);
        forged = original.replace("Ersatz", "Faelscher").getBytes(StandardCharsets.UTF_8);
        assertNotEquals(original, new String(forged, StandardCharsets.UTF_8));

        byte[] replaced = withEvidenceReplacedIncrementally(sealed, forged);

        assertArrayEquals(sealed, Arrays.copyOf(replaced, sealed.length), "an update keeps the sealed bytes");
        assertEquals(pageCount(sealed), pageCount(replaced));
        try (var pdf = Loader.loadPDF(replaced)) {
            assertArrayEquals(forged, evidenceBytes(pdf), "a reader of the attachment now finds the forgery");
        }
        assertFailsAsModified(replaced);
    }

    @Test
    void twoSignersLeaveTwoValidVersionsTheLaterSupersedingTheEarlier() throws IOException {
        var signer = member("Zoe", "Zwei");
        var request = ask(signer, "participant", "issuer");
        var drawn = picture(TestSignatures.drawn(), ActPictureSource.DRAWN);
        var saved = picture(TestSignatures.photographed(), ActPictureSource.SAVED);

        try (var service = LocalTimestampService.start()) {
            sign(request, signer, "participant", drawn);
            sealLatest(request, timestampsOf(service));
            sign(request, manager, "issuer", saved);
            sealLatest(request, timestampsOf(service));
        }

        var document = documentOf(request);
        List<SealedVersion> filed = versions.versionsOf(document.id());
        assertEquals(2, filed.size());
        var current = filed.getFirst();
        var earlier = filed.getLast();
        assertTrue(current.current());
        assertEquals(2, current.version());
        assertNotNull(earlier.supersededAt());
        assertNull(current.supersededAt());
        byte[] first = documents.read(document, earlier).orElseThrow();
        byte[] latest = documents.read(document).orElseThrow();

        for (byte[] version : List.of(first, latest)) {
            var verification = verifier.verify(version);
            assertTrue(verification.document().held());
            assertEquals(1, verification.signatures().size(), "one station seal per version, never stacked");
            assertPassed(verification.signatures().getFirst(), PadesLevel.BASELINE_LT);
        }
        assertEquals(List.of("issuer"), SignatureFields.unsigned(first));
        assertEquals(List.of(), SignatureFields.unsigned(latest));
        try (var pdf = Loader.loadPDF(latest)) {
            var evidence = attachedEvidence(pdf);
            assertEquals(
                    new SigningEvidenceFile.Picture(drawn.sha256(), ActPictureSource.DRAWN),
                    actOn(evidence, "participant").picture());
            assertEquals(
                    new SigningEvidenceFile.Picture(saved.sha256(), ActPictureSource.SAVED),
                    actOn(evidence, "issuer").picture());
        }
        String record = record(request);
        assertTrue(record.contains("Beim Unterschreiben gezeichnet"), record);
        assertTrue(record.contains("Vorher im Konto gespeichert"), record);
        PdfA3b.assertConforms(first);
        PdfA3b.assertConforms(latest);
        SigningSamples.write("two-signers-v1.pdf", first);
        SigningSamples.write("two-signers-v2.pdf", latest);
    }

    @Test
    void whenNoTimestampServiceAnswersTheStateFallsBackToBaselineBAndTheRecordSaysSo() throws IOException {
        var request = signedByOne("Otto", "Ausfall");
        var unanswered = new TimestampServices(
                List.of(LocalTimestampService.pinned(LocalTimestampService.unreachableUrl())),
                TimestampServices.TIMEOUT,
                TimestampServices.BUDGET);

        byte[] sealed = sealLatest(request, unanswered);

        var version = currentVersion(request);
        assertEquals(SealLevel.BASELINE_B, version.sealLevel());
        assertNull(version.timestampedBy());
        var check = assertPassed(sealed, PadesLevel.BASELINE_B);
        assertTrue(check.timestamps().isEmpty());
        String record = record(request);
        assertTrue(record.contains("Alle Zeiten in diesem Nachweis stammen deshalb nur von der Uhr"), record);
        assertTrue(record.contains("Ein Zeitstempel kann später ergänzt werden"));
        SigningSamples.write("timestamp-outage-baseline-b.pdf", sealed);
    }

    @Test
    void signaturePicturesDrawnIntoTheFieldsKeepTheDocumentPdfA3b() throws IOException {
        var request = signedByOne("Anja", "Archiv");
        byte[] content = documents.readUploaded(documentOf(request)).orElseThrow();
        PdfA3b.assertConforms(content);

        byte[] sealed;
        try (var service = LocalTimestampService.start()) {
            sealed = sealLatest(request, timestampsOf(service));
        }

        assertEquals(List.of(), SignatureFields.unsigned(sealed), "the participant field carries the mark");
        PdfA3b.assertConforms(sealed);
        try (var pdf = Loader.loadPDF(sealed)) {
            assertEquals(1, pdf.getDocumentCatalog().getOutputIntents().size());
        }
    }

    /** A request with one participant field, signed by a new member with a picture drawn for the act. */
    private static SignatureRequest signedByOne(String first, String last) throws IOException {
        var signer = member(first, last);
        var request = ask(signer, "participant");
        sign(request, signer, "participant", picture(TestSignatures.drawn(), ActPictureSource.DRAWN));
        return request;
    }

    private static byte[] sealLatest(SignatureRequest request, TimestampServices timestamps) {
        assertTrue(sealer(timestamps).sealLatest(request.id()));
        return documents.read(documentOf(request)).orElseThrow();
    }

    private static SealCheck assertPassed(byte[] sealed, PadesLevel level) {
        var verification = verifier.verify(sealed);
        assertTrue(verification.document().held(), "the installation holds exactly this file");
        assertEquals(1, verification.signatures().size());
        return assertPassed(verification.signatures().getFirst(), level);
    }

    private static SealCheck assertPassed(SealCheck check, PadesLevel level) {
        assertEquals(ValidationIndication.TOTAL_PASSED, check.indication(), () -> describe(check));
        assertEquals(ValidationIndication.TOTAL_PASSED, check.validatorIndication());
        assertEquals(level, check.level());
        assertTrue(check.issuedHere());
        assertTrue(check.intact());
        assertFalse(check.modifiedAfterSealing());
        return check;
    }

    private static String describe(SealCheck check) {
        return check.indication() + " / " + check.subIndication() + " at " + check.level();
    }

    /**
     * A file a later revision changed is no longer the one the station sealed: the check reports the change,
     * which a reader is shown as modified after sealing whatever the validator's indication says, and the
     * installation does not hold the file.
     */
    private static void assertFailsAsModified(byte[] changed) {
        var verification = verifier.verify(changed);
        var check = verification.signatures().getFirst();
        assertTrue(check.intact(), "the signed bytes themselves are unchanged");
        assertTrue(check.modifiedAfterSealing(), () -> describe(check));
        assertFalse(check.coversWholeFile());
        assertFalse(verification.document().held());
    }

    /**
     * Changes one byte inside a page's content stream, in the revision the seal covers, leaving the file's
     * structure readable.
     */
    private static byte[] withByteFlippedInPageContent(byte[] sealed, int pageIndex) throws IOException {
        long offset;
        try (var pdf = Loader.loadPDF(sealed)) {
            COSObjectKey key = pdf.getPage(pageIndex)
                    .getContentStreams()
                    .next()
                    .getCOSObject()
                    .getKey();
            assertNotNull(key, "the content stream is an object of its own");
            offset = pdf.getDocument().getXrefTable().get(key);
        }
        int stream = SealedPdfs.indexOf(
                Arrays.copyOfRange(sealed, (int) offset, sealed.length), "stream".getBytes(StandardCharsets.US_ASCII));
        assertTrue(stream > 0);
        int target = (int) offset + stream + "stream".length() + 16;
        byte[] tampered = sealed.clone();
        tampered[target] ^= 0x01;
        return tampered;
    }

    /** Appends an empty page to a document by an incremental update, the way a PDF editor saves. */
    private static byte[] withPageAppendedIncrementally(byte[] sealed) throws IOException {
        try (var pdf = Loader.loadPDF(sealed);
                var out = new ByteArrayOutputStream()) {
            var page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            var pages = pdf.getPages().getCOSObject();
            pages.setNeedToBeUpdated(true);
            var kids = pages.getCOSArray(COSName.KIDS);
            if (kids != null) kids.setNeedToBeUpdated(true);
            page.getCOSObject().setNeedToBeUpdated(true);
            pdf.getDocumentCatalog().getCOSObject().setNeedToBeUpdated(true);
            pdf.saveIncremental(out);
            return out.toByteArray();
        }
    }

    /** Paints the last page white by an incremental update, the way an editor covers what a page says. */
    private static byte[] withLastPageCoveredIncrementally(byte[] sealed) throws IOException {
        try (var pdf = Loader.loadPDF(sealed);
                var out = new ByteArrayOutputStream()) {
            var page = pdf.getPage(pdf.getNumberOfPages() - 1);
            var box = page.getMediaBox();
            try (var cover = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, false)) {
                cover.setNonStrokingColor(1f, 1f, 1f);
                cover.addRect(box.getLowerLeftX(), box.getLowerLeftY(), box.getWidth(), box.getHeight());
                cover.fill();
            }
            page.getCOSObject().setNeedToBeUpdated(true);
            pdf.saveIncremental(out);
            return out.toByteArray();
        }
    }

    /**
     * Puts other evidence in place of the attached one by an incremental update, leaving every page as it
     * was.
     */
    private static byte[] withEvidenceReplacedIncrementally(byte[] sealed, byte[] json) throws IOException {
        try (var pdf = Loader.loadPDF(sealed);
                var out = new ByteArrayOutputStream()) {
            var spec = evidenceSpec(pdf);
            var file = new PDEmbeddedFile(pdf, new ByteArrayInputStream(json));
            file.setSubtype("application/json");
            file.setSize(json.length);
            spec.setEmbeddedFile(file);
            spec.setEmbeddedFileUnicode(file);
            spec.getCOSObject().setNeedToBeUpdated(true);
            var embedded = spec.getCOSObject().getCOSDictionary(COSName.EF);
            if (embedded != null) embedded.setNeedToBeUpdated(true);
            pdf.saveIncremental(out);
            return out.toByteArray();
        }
    }

    private static int lastPageStreams(byte[] pdf) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            int streams = 0;
            var contents = document.getPage(document.getNumberOfPages() - 1).getContentStreams();
            for (; contents.hasNext(); contents.next()) streams++;
            return streams;
        }
    }

    private static int pageCount(byte[] pdf) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    private static ActPicture picture(byte[] made, ActPictureSource source) {
        return new ActPicture(SignatureImages.clean(made).png(), source);
    }

    private static SigningEvidenceFile attachedEvidence(PDDocument pdf) throws IOException {
        return SigningEvidenceFiles.read(evidenceBytes(pdf));
    }

    private static byte[] evidenceBytes(PDDocument pdf) throws IOException {
        return evidenceSpec(pdf).getEmbeddedFile().toByteArray();
    }

    private static PDComplexFileSpecification evidenceSpec(PDDocument pdf) throws IOException {
        var spec = pdf.getDocumentCatalog()
                .getNames()
                .getEmbeddedFiles()
                .getNames()
                .get(SigningEvidenceFile.FILE_NAME);
        assertNotNull(spec);
        return spec;
    }

    private static SigningEvidenceFile.Act actOn(SigningEvidenceFile evidence, String fieldName) {
        var act = evidence.fields().stream()
                .filter(field -> field.fieldName().equals(fieldName))
                .findFirst()
                .orElseThrow()
                .act();
        assertNotNull(act);
        return act;
    }

    /** The text of the current version's record as a reader downloads it. */
    private static String record(SignatureRequest request) throws IOException {
        var document = documentOf(request);
        try (var pdf = Loader.loadPDF(records(SealedPdfs.noTimestamps())
                .record(document, currentVersion(request).version())
                .pdf())) {
            return new PDFTextStripper().getText(pdf).replaceAll("\\s+", " ");
        }
    }

    private static SignatureRecords records(TimestampServices timestamps) {
        return new SignatureRecords(
                documents,
                stationRepo,
                stationKeys,
                new PdfSealer(timestamps, new StationKeyRevocations(keyRepo, new RevocationLists(), wrap)),
                timestamps,
                BASE_URL);
    }

    private static SigningStateSealer sealer(TimestampServices timestamps) {
        return new SigningStateSealer(
                requestRepo,
                evidenceRepo,
                memberDocumentRepo,
                documents,
                sealedDocuments,
                stationKeys,
                new SigningStateAssembler(Clock.systemUTC()),
                new PdfSealer(timestamps, new StationKeyRevocations(keyRepo, new RevocationLists(), wrap)),
                mock(SignedCopies.class),
                SealedStateFollowUp.NONE,
                new IssuedSignatures(new IssuerSignatureRepository(), memberNameResolver));
    }

    private static TimestampServices timestampsOf(LocalTimestampService service) {
        return new TimestampServices(List.of(service.pinned()), TimestampServices.TIMEOUT, TimestampServices.BUDGET);
    }

    private static SealedVersion currentVersion(SignatureRequest request) {
        return versions.current(documentOf(request).id()).orElseThrow();
    }

    private static Document documentOf(SignatureRequest request) {
        Integer documentId = request.documentId();
        assertNotNull(documentId);
        return memberDocumentRepo.findById(documentId).orElseThrow();
    }

    private static SignatureRequest ask(StationMember member, String... fieldNames) throws IOException {
        byte[] pdf = letterWith(fieldNames);
        var document = documents.store(
                station.id(),
                List.of(member.id()),
                "Einverstaendnis",
                "einverstaendnis.pdf",
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

    private static void sign(SignatureRequest request, StationMember signer, String fieldName, ActPicture picture) {
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
        var recorded = fields.record(
                at(signer), new CompletedSigning(SignatureLevel.SIMPLE, new SigningEvidence.TotpUnbound(act)));
        evidenceRepo.storeMark(recorded.id(), picture);
    }

    /** A letter as the generator writes one: Typst, PDF/A-3b, with an empty signature field per name. */
    private static byte[] letterWith(String... fieldNames) throws IOException {
        byte[] letter;
        try {
            letter = TypstCompiler.compile(
                    "#set document(title: \"Einverständnis\")\n#set text(lang: \"de\")\n= Einverständnis\n"
                            + "Ich bin mit der Teilnahme am Zeltlager einverstanden. " + UUID.randomUUID() + "\n",
                    TypstCompiler.Output.PDF_A_3B);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while rendering the letter", e);
        }
        try (var document = Loader.loadPDF(letter);
                var out = new ByteArrayOutputStream()) {
            var page = document.getPage(0);
            float x = 72;
            for (String name : fieldNames) {
                SignatureFields.add(document, page, new PDRectangle(x, 100, 200, 50), name);
                x += 220;
            }
            document.save(out);
            return out.toByteArray();
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
        return "signing-matrix-" + NAMES.incrementAndGet() + "-" + System.nanoTime() + "@test.com";
    }
}
