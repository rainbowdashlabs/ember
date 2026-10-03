/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.MovableClock;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.FieldRect;
import dev.chojo.ember.feature.generator.entity.FormBinding;
import dev.chojo.ember.feature.generator.entity.FormField;
import dev.chojo.ember.feature.generator.entity.FormFieldKind;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfFieldKind;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.entity.TextAlign;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.PdfTemplateRepository;
import dev.chojo.ember.feature.generator.repository.TemplateStationUseRepository;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateResponse;
import dev.chojo.ember.feature.generator.service.pdf.PdfStamper;
import dev.chojo.ember.feature.generator.service.pdf.StampFonts;
import dev.chojo.ember.feature.generator.service.pdf.TestPdfs;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfPictures;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.PdfText;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * PDF templates end to end below the routes: the upload and what is refused at it, the fields checked
 * against the pages, a new version of the PDF keeping the fields, and generating, previewing and self
 * service through the same flow as letters. The PDFs are filled for real and read back.
 */
class PdfTemplateServiceTest extends RepositoryTestBase {
    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");
    private static final FieldRect NAME = new FieldRect(1, 100, 700, 250, 20);

    private static DocumentTemplateService templates;
    private static PdfTemplateService pdfs;
    private static DocumentGenerationService generation;
    private static SelfServiceDocumentService selfService;
    private static DocumentGenerationRepository log;
    private static DocumentService documents;
    private static Station station;
    private static Owner.Station owner;
    private static StationMember manager;
    private static int author;
    private static StationMember lena;
    private static StationMember ren;
    private static int names;

    @BeforeAll
    static void setup() {
        var clock = new MovableClock(NOW);
        station = stationRepo.create("PDF Vorlagen Wache");
        owner = new Owner.Station(station.id());
        manager = member("pdf-manager@test.com", "Nora", "Fülling");
        author = Objects.requireNonNull(manager.accountId());
        lena = member("pdf-lena@test.com", "Lena", "Schmidt");
        ren = member("pdf-ren@test.com", "Ren", "漢字");

        var backend = localStorage();
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        documents = newDocumentService(storage);
        var catalogue = newPlaceholderCatalogue();
        var templateRepository = new DocumentTemplateRepository();
        var pdfTemplates = new PdfTemplateRepository();
        var uses = new TemplateStationUseRepository();
        var media = mock(MediaLibraryService.class);
        var fonts = newFontLibrary(storage);
        var checks = new TemplateChecks(
                templateRepository,
                pdfTemplates,
                new LetterChecks(contentBlocks(), media),
                stationRepo,
                catalogue,
                fonts,
                newOwnerStores());
        templates = new DocumentTemplateService(
                templateRepository, pdfTemplates, uses, checks, restrictionService, catalogue, newOwnerStores());
        pdfs = new PdfTemplateService(
                templates, templateRepository, pdfTemplates, newDocumentIntake(), storage, newOwnerStores());
        var generator = new DocumentGeneratorService(
                templates,
                newPlaceholderResolver(clock),
                catalogue,
                new LetterRenderer(mock(KbPdfPictures.class), media, newStationLogoService(), fonts, newOwnerStores()),
                new PdfTemplateRenderer(pdfs, new PdfStamper(new StampFonts()), fonts),
                stationRepo,
                restrictionService,
                clock);
        log = new DocumentGenerationRepository();
        generation =
                new DocumentGenerationService(templates, generator, documents, newDocumentIntake(), log, checks, clock);
        selfService = new SelfServiceDocumentService(
                templateRepository,
                templates,
                new TemplateStationUseService(templates, uses, restrictionService),
                generator,
                generation,
                log,
                restrictionService,
                new GuardianPolicy(stationMemberRepo),
                documents,
                stationRepo,
                clock);
    }

    private static StationMember member(String email, String first, String last) {
        return stationMemberRepo.create(
                station.id(), accountRepo.create(email, first, last).id());
    }

    private static StationSession as(StationMember member, StationPermission... permissions) {
        return stationSession(member, permissions);
    }

    private static void refused(Refusal refusal, Executable action) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, action).refusal());
    }

    private static DocumentTemplateRequest request(
            String name, String title, List<PdfField> fields, List<FormBinding> bindings, boolean selfService) {
        return TemplateRequestBuilder.pdf(name)
                .title(title)
                .legal()
                .selfService(selfService)
                .cooldown(30)
                .audience(RestrictionAudience.empty())
                .fields(fields)
                .formBindings(bindings)
                .build();
    }

    private static DocumentTemplateRequest request(String name, PdfField... fields) {
        return request(name, null, List.of(fields), List.of(), false);
    }

    private static PdfField text(FieldRect rect, @Nullable String text) {
        return new PdfField(PdfFieldKind.TEXT, rect, text, 11, TextAlign.LEFT, false, null);
    }

    private static PdfField signature(FieldRect rect, @Nullable SignatureRole role) {
        return new PdfField(PdfFieldKind.SIGNATURE, rect, null, 0, null, false, role);
    }

    /** A PDF template with its PDF, ready for fields. */
    private static DocumentTemplateResponse uploaded(String name, byte[] pdf) {
        var created = templates.create(owner, request(name), author);
        return pdfs.upload(owner, created.id(), TestUploads.of("form.pdf", "application/pdf", pdf), author);
    }

    private static DocumentTemplateResponse withFields(
            DocumentTemplateResponse template, DocumentTemplateRequest change) {
        return templates.update(owner, template.id(), change, author);
    }

    @Test
    void aPdfIsTakenWithItsPagesAndFormFields() throws IOException {
        byte[] form = TestPdfs.withForm();

        var template = uploaded("Formular", form);

        var pdf = Objects.requireNonNull(template.pdf());
        assertEquals(DocumentTemplateKind.PDF, template.kind());
        assertEquals(2, template.version());
        assertEquals(1, pdf.inspection().pages().size());
        assertEquals("form.pdf", pdf.fileName());
        assertEquals(DocumentGenerationService.sha256(form), pdf.sha256());
        assertEquals(
                List.of(
                        new FormField("person.name", FormFieldKind.TEXT, new FieldRect(1, 100, 600, 200, 20)),
                        new FormField("agree", FormFieldKind.CHECK, new FieldRect(1, 100, 500, 20, 20))),
                pdf.inspection().formFields());
        assertArrayEquals(form, pdfs.current(owner, template.id()).orElseThrow().data());
    }

    @Test
    void aTurnedAndCroppedPageIsReadAsItIs() throws IOException {
        var template = uploaded("Quer", TestPdfs.turned(90, new PDRectangle(20, 30, 500, 700)));

        var page = Objects.requireNonNull(template.pdf()).inspection().pages().getFirst();

        assertEquals(90, page.rotation());
        assertEquals(20, page.x(), 0.01);
        assertEquals(30, page.y(), 0.01);
        assertEquals(500, page.width(), 0.01);
    }

    @Test
    void onlyAPdfThatOpensWithoutAPasswordIsTaken() throws IOException {
        var letter = templates.create(
                owner, TemplateRequestBuilder.letter("Brief für PDF").build(), author);
        var template = templates.create(owner, request("Geschützt"), author);
        byte[] locked = TestPdfs.protectedBy("owner", "user");

        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_NOT_PDF,
                () -> pdfs.upload(
                        owner, letter.id(), TestUploads.of("a.pdf", "application/pdf", TestPdfs.plain(1)), 0));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PDF_NOT_A_PDF,
                () -> pdfs.upload(owner, template.id(), TestUploads.of("a.txt", "text/plain", "Hallo".getBytes()), 0));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PDF_PASSWORD,
                () -> pdfs.upload(owner, template.id(), TestUploads.of("a.pdf", "application/pdf", locked), 0));
        refused(DocumentRefusal.DOCUMENT_UPLOAD_MISSING_FILE, () -> pdfs.upload(owner, template.id(), null, author));

        var taken = pdfs.upload(
                owner,
                template.id(),
                TestUploads.of("a.pdf", "application/pdf", TestPdfs.protectedBy("owner", "")),
                author);
        assertNotNull(taken.pdf());
        assertTrue(pdfs.current(owner, letter.id()).isEmpty());
    }

    @Test
    void fieldsWaitForThePdf() {
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PDF_MISSING,
                () -> templates.create(owner, request("Ohne PDF", text(NAME, "x")), author));
        var empty = templates.create(owner, request("Noch leer"), author);
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PDF_MISSING,
                () -> generation.generate(as(manager), empty.id(), lena.id()));
    }

    @Test
    void fieldsAreHeldToThePagesAndTheirKind() throws IOException {
        var template = uploaded("Prüfung", TestPdfs.withForm());
        var tooMany = new ArrayList<PdfField>();
        for (int index = 0; index <= PdfLayoutChecks.MAX_FIELDS; index++) tooMany.add(text(NAME, "x"));

        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_FIELD_OFF_PAGE,
                () -> withFields(template, request("Prüfung", text(new FieldRect(1, 500, 700, 200, 20), "x"))));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_FIELD_OFF_PAGE,
                () -> withFields(template, request("Prüfung", text(new FieldRect(2, 100, 700, 200, 20), "x"))));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_FIELD_INCOMPLETE,
                () -> withFields(template, request("Prüfung", text(NAME, " "))));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_FIELD_INCOMPLETE,
                () -> withFields(template, request("Prüfung", signature(NAME, null))));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_FIELD_SIZE_OUT_OF_BOUNDS,
                () -> withFields(
                        template,
                        request(
                                "Prüfung",
                                new PdfField(PdfFieldKind.TEXT, NAME, "x", 100, TextAlign.LEFT, false, null))));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_SIGNER_TWICE,
                () -> withFields(
                        template,
                        request(
                                "Prüfung",
                                signature(NAME, SignatureRole.PARTICIPANT),
                                signature(NAME, SignatureRole.PARTICIPANT))));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_FORM_FIELD_UNKNOWN,
                () -> withFields(
                        template, request("Prüfung", null, List.of(), List.of(new FormBinding("person", "x")), false)));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_SIGNER_TWICE,
                () -> withFields(
                        template,
                        request(
                                "Prüfung",
                                signature(NAME, SignatureRole.EACH_GUARDIAN),
                                signature(NAME, SignatureRole.GUARDIAN_2))));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PLACEHOLDER_UNKNOWN,
                () -> withFields(template, request("Prüfung", text(NAME, "{{signature.issuer}}"))));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PLACEHOLDER_UNKNOWN,
                () -> withFields(template, request("Prüfung", text(NAME, "{{member.shoeSize}}"))));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_TOO_MANY_FIELDS,
                () -> withFields(template, request("Prüfung", null, tooMany, List.of(), false)));
    }

    @Test
    void whatIsWrittenComesBackAsWritten() throws IOException {
        var template = uploaded("Gespeichert", TestPdfs.withForm());
        var field = new PdfField(PdfFieldKind.CHECK, NAME, "{{member.fullName}}", 0, null, true, null);

        var saved = withFields(
                template,
                request(
                        "Gespeichert",
                        null,
                        List.of(field, signature(new FieldRect(1, 100, 100, 150, 40), SignatureRole.ISSUER)),
                        List.of(new FormBinding("person.name", " {{member.fullName}} "), new FormBinding("agree", "")),
                        false));

        assertEquals(
                List.of(
                        new PdfField(
                                PdfFieldKind.CHECK,
                                NAME,
                                "{{member.fullName}}",
                                PdfLayoutChecks.DEFAULT_FONT_SIZE,
                                TextAlign.LEFT,
                                false,
                                null),
                        new PdfField(
                                PdfFieldKind.SIGNATURE,
                                new FieldRect(1, 100, 100, 150, 40),
                                null,
                                PdfLayoutChecks.DEFAULT_FONT_SIZE,
                                TextAlign.LEFT,
                                false,
                                SignatureRole.ISSUER)),
                saved.fields());
        assertEquals(List.of(new FormBinding("person.name", "{{member.fullName}}")), saved.formBindings());
        assertEquals(3, saved.version());
    }

    /** A new version of the PDF keeps the fields; documents filled before keep naming the old one. */
    @Test
    void aNewUploadKeepsTheFieldsAndTheOldDocumentsTheirPdf() throws IOException {
        var template = withFields(
                uploaded("Neue Fassung", TestPdfs.plain(1)),
                request("Neue Fassung", text(NAME, "{{member.fullName}}")));
        var before = generation.generate(as(manager), template.id(), lena.id());

        var reuploaded = pdfs.upload(
                owner, template.id(), TestUploads.of("neu.pdf", "application/pdf", TestPdfs.plain(2)), author);
        var after = generation.generate(as(manager), template.id(), lena.id());

        var oldOriginal = Objects.requireNonNull(template.pdf()).id();
        var newOriginal = Objects.requireNonNull(reuploaded.pdf()).id();
        assertNotEquals(oldOriginal, newOriginal);
        assertEquals(template.version() + 1, reuploaded.version());
        assertEquals(template.fields(), reuploaded.fields());
        assertEquals(2, reuploaded.pdf().inspection().pages().size());
        assertEquals(
                oldOriginal, log.findById(before.generationId()).orElseThrow().pdfOriginalId());
        assertEquals(
                newOriginal, log.findById(after.generationId()).orElseThrow().pdfOriginalId());
    }

    @Test
    void aPdfTemplateIsGeneratedAndFiledLikeALetter() throws IOException {
        var template = withFields(
                uploaded("Einverständnis", TestPdfs.withForm()),
                request(
                        "Einverständnis",
                        "Einverständnis {{member.fullName}}",
                        List.of(
                                text(NAME, "{{member.fullName}}"),
                                signature(new FieldRect(1, 100, 100, 150, 40), SignatureRole.GUARDIAN_1)),
                        List.of(new FormBinding("person.name", "{{station.name}}")),
                        false));

        var filed = generation.generate(as(manager), template.id(), lena.id());

        assertEquals("Einverständnis Lena Schmidt", filed.title());
        var document = memberDocumentRepo.findById(filed.documentId()).orElseThrow();
        byte[] pdf = documents.open(document, DocumentDoor.STATION).orElseThrow();
        String text = Objects.requireNonNull(PdfText.extract(pdf));
        assertTrue(text.contains("Lena Schmidt"), text);
        assertTrue(text.contains("PDF Vorlagen Wache"), text);
        try (var read = Loader.loadPDF(pdf)) {
            assertNotNull(read.getDocumentCatalog().getAcroForm(null).getField("guardian1"));
        }
        var entry = log.findById(filed.generationId()).orElseThrow();
        assertEquals(DocumentGenerationService.sha256(pdf), entry.fileSha256());
        assertEquals(Objects.requireNonNull(template.pdf()).id(), entry.pdfOriginalId());
    }

    @Test
    void selfServiceFillsAPdfTemplateAndWaitsLikeALetter() throws IOException {
        var template = withFields(
                uploaded("Selbst", TestPdfs.plain(1)),
                request("Selbst", null, List.of(text(NAME, "{{member.fullName}}")), List.of(), true));

        var filed = selfService.generate(as(lena), template.id(), lena.id());

        assertTrue(filed.documentId() > 0);
        refused(
                DocumentRefusal.DOCUMENT_SELF_SERVICE_COOLING_DOWN,
                () -> selfService.generate(as(lena), template.id(), lena.id()));
    }

    /** The preview names the characters no font can print, and shows labels where there is no member. */
    @Test
    void thePreviewNamesWhatCannotBePrinted() throws IOException {
        var template = withFields(
                uploaded("Vorschau PDF", TestPdfs.plain(1)),
                request("Vorschau PDF", text(NAME, "{{member.fullName}}")));

        var forRen = generation.preview(as(manager), template.id(), ren.id());
        var labelled = generation.previewDraft(
                as(manager, StationPermission.DOCUMENT_TEMPLATE_EDIT),
                request("Vorschau PDF", text(NAME, "{{member.fullName}}")),
                template.id(),
                null);

        assertEquals(List.of("漢", "字"), forRen.unprintable());
        String text = Objects.requireNonNull(PdfText.extract(Base64.getDecoder().decode(labelled.pdfBase64())));
        assertTrue(text.contains("[Vor- und Nachname]"), text);
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PDF_MISSING,
                () -> generation.previewDraft(
                        as(manager, StationPermission.DOCUMENT_TEMPLATE_EDIT),
                        request("Vorschau PDF", text(NAME, "{{member.fullName}}")),
                        null,
                        null));
    }
}
