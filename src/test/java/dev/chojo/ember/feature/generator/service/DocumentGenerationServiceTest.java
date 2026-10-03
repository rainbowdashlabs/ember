/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.MovableClock;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.documents.service.DocumentIntake;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.generator.entity.DataSubject;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.LetterCell;
import dev.chojo.ember.feature.generator.entity.LetterCellKind;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.Letterhead;
import dev.chojo.ember.feature.generator.entity.MissingValue;
import dev.chojo.ember.feature.generator.entity.PronounForm;
import dev.chojo.ember.feature.generator.entity.PronounSource;
import dev.chojo.ember.feature.generator.entity.SubjectRole;
import dev.chojo.ember.feature.generator.entity.TextAlign;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.PdfTemplateRepository;
import dev.chojo.ember.feature.generator.service.pdf.PdfStamper;
import dev.chojo.ember.feature.generator.service.pdf.StampFonts;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfPictures;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.PdfText;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import tools.jackson.databind.node.StringNode;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Generating a document from a template and filing it: as a manager, through self service for oneself
 * and for a child, and every refusal on the way. The documents are drawn for real through Pandoc and
 * Typst and read back.
 */
class DocumentGenerationServiceTest extends RepositoryTestBase {
    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");
    private static final String PICTURE = "c".repeat(64);

    private static MovableClock clock;
    private static DocumentTemplateService templates;
    private static DocumentGenerationService generation;
    private static PdfTemplateRenderer pdfRenderer;
    private static SelfServiceDocumentService selfService;
    private static DocumentService documents;
    private static DocumentGenerationRepository log;
    private static Station station;
    private static Owner.Station owner;
    private static StationMember manager;
    private static StationMember lena;
    private static StationMember max;
    private static StationMember guardian;
    private static StationMember stranger;
    private static int genderField;
    private static int schoolField;

    @BeforeAll
    static void setup() throws IOException {
        clock = new MovableClock(NOW);
        station = stationRepo.create("Generator Wache");
        stationRepo.updateLocation(station.id(), "Dönhoffstr. 31", "10318", "Berlin", "DE", null, null);
        owner = new Owner.Station(station.id());
        manager = member("gen-manager@test.com", "Nora", "Fülling");
        lena = member("gen-lena@test.com", "Lena", "Sch*midt_#1");
        max = member("gen-max@test.com", "Max", "Weiß");
        guardian = member("gen-guardian@test.com", "Anna", "Schmidt");
        stranger = member("gen-stranger@test.com", "Fremd", "Person");
        stationMemberRepo.addManager(guardian.id(), lena.id());
        stationMemberRepo.setUserType(guardian.id(), StationUserType.GUARDIAN);

        var choices = new ProfileFieldConfig(
                null,
                false,
                false,
                List.of("männlich", "weiblich"),
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        genderField = profileFieldRepo
                .create(station.id(), "Geschlecht", FieldType.CHOICE, choices, false, false, null)
                .id();
        schoolField = profileFieldRepo
                .create(station.id(), "Schule", FieldType.TEXT, ProfileFieldConfig.empty(), false, false, null)
                .id();
        profileFieldRepo.setValue(lena.id(), genderField, StringNode.valueOf("weiblich"));
        profileFieldRepo.setValue(lena.id(), schoolField, StringNode.valueOf("Schule *am* See"));

        var backend = localStorage();
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        documents = newDocumentService(storage);
        StationLogoService logos = newStationLogoService();
        logos.store(station.id(), png(), "image/png");
        var media = mock(MediaLibraryService.class);
        when(media.findByHash(any(), anyString())).thenReturn(Optional.empty());
        when(media.findByHash(eq(station.id()), eq(PICTURE)))
                .thenReturn(Optional.of(new StationFile(
                        1, 0, station.id(), PICTURE, "wappen.png", "image/png", 1, Instant.EPOCH, null, null, null)));
        when(media.readVariant(eq(station.id()), eq(PICTURE), any(), any()))
                .thenReturn(Optional.of(new MediaContent(png(), "image/png")));
        var pictures = mock(KbPdfPictures.class);
        when(pictures.place(anyInt(), anyString()))
                .thenAnswer(call -> new KbPdfPictures.Placed(call.getArgument(1), Map.of()));

        var catalogue = new PlaceholderCatalogue(profileFieldRepo, stationRepo);
        var templateRepository = new DocumentTemplateRepository();
        var pdfTemplates = new PdfTemplateRepository();
        var checks = new TemplateChecks(templateRepository, pdfTemplates, media, profileFieldRepo, catalogue);
        templates =
                new DocumentTemplateService(templateRepository, pdfTemplates, checks, restrictionService, catalogue);
        pdfRenderer = new PdfTemplateRenderer(
                new PdfTemplateService(
                        templates, templateRepository, pdfTemplates, newDocumentIntake(), storage, stationRepo),
                new PdfStamper(new StampFonts()));
        var resolver =
                new PlaceholderResolver(stationRepo, stationMemberRepo, memberNameResolver, profileFieldRepo, clock);
        var generator = new DocumentGeneratorService(
                templates,
                resolver,
                catalogue,
                new LetterRenderer(pictures, media, logos, stationRepo),
                pdfRenderer,
                stationRepo,
                clock);
        log = new DocumentGenerationRepository();
        generation =
                new DocumentGenerationService(templates, generator, documents, newDocumentIntake(), log, checks, clock);
        selfService = new SelfServiceDocumentService(
                templateRepository,
                templates,
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

    private static byte[] png() throws IOException {
        var image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static final Letterhead LETTERHEAD = new Letterhead(
            List.of(
                    new LetterCell(LetterCellKind.LOGO, null, null, TextAlign.LEFT, 15),
                    new LetterCell(LetterCellKind.IMAGE, PICTURE, null, TextAlign.CENTER, 15),
                    new LetterCell(LetterCellKind.TEXT, null, "Jugendfeuerwehr\n{{station.name}}", TextAlign.RIGHT, 0)),
            List.of(new LetterCell(
                    LetterCellKind.TEXT,
                    null,
                    "{{station.address}}, {{station.postalCode}} {{station.city}}",
                    TextAlign.LEFT,
                    0)));

    private static final String BODY = """
            # Bescheinigung

            Hiermit bestätige ich, dass **{{member.fullName}}** die {{profile.%d}} besucht.

            {{pronoun.subject.start}} engagiert sich regelmäßig.""";

    private static int template(String name, boolean selfServiceOn, boolean hidden, int cooldown, String body) {
        var request = new DocumentTemplateRequest(
                null,
                name,
                "%s {{member.fullName}} {{today}}".formatted(name),
                "%s {{member.lastName}}".formatted(name),
                List.of("Bescheinigung"),
                hidden,
                null,
                true,
                selfServiceOn,
                cooldown,
                RestrictionAudience.empty(),
                new PronounSource(genderField, Map.of("weiblich", PronounForm.SIE), PronounForm.NAME),
                LETTERHEAD,
                body,
                null,
                null,
                null);
        return templates.create(owner, request, manager.id()).id();
    }

    private static int certificate(String name) {
        return template(name, true, true, 30, BODY.formatted(schoolField));
    }

    private static StationSession as(StationMember member, StationPermission... permissions) {
        return stationSession(member, permissions);
    }

    private static void refused(Refusal refusal, Executable action) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, action).refusal());
    }

    private static byte[] fileOf(int documentId) {
        var document = memberDocumentRepo.findById(documentId).orElseThrow();
        return documents.read(document).orElseThrow();
    }

    @Test
    void aManagerGeneratesAndFilesADocument() throws IOException {
        int templateId = certificate("Teilnahme");

        var generated = generation.generate(as(manager, StationPermission.DOCUMENT_EDIT_MEMBER), templateId, lena.id());

        var document = memberDocumentRepo.findById(generated.documentId()).orElseThrow();
        assertEquals("Teilnahme Lena Sch*midt_#1 02.10.2026", document.title());
        assertEquals("Teilnahme Sch_midt_#1.pdf", document.fileName());
        assertEquals("application/pdf", document.mimeType());
        assertTrue(document.hidden(), "a manager's document follows the template");
        assertTrue(document.keepOnArchive(), "a legal template keeps its documents");
        assertEquals(manager.id(), document.uploadedBy());
        assertEquals(
                List.of("Bescheinigung"),
                memberDocumentRepo.findTags(document.id()).stream()
                        .map(tag -> tag.name())
                        .toList());
        assertTrue(memberDocumentRepo.isBoundTo(document.id(), lena.id()));

        var entry = log.findById(generated.generationId()).orElseThrow();
        byte[] pdf = fileOf(document.id());
        assertEquals(DocumentGenerationService.sha256(pdf), entry.fileSha256());
        assertEquals(1, entry.templateVersion());
        assertFalse(entry.selfService());
        assertEquals(manager.id(), entry.generatedBy());
        assertEquals(List.of(new DataSubject(lena.id(), SubjectRole.MEMBER)), log.subjects(entry.id()));
    }

    /** Values with markup characters in them print as they are, in the body and in the letterhead. */
    @Test
    void valuesPrintLiterallyAndTheLetterheadIsDrawn() throws IOException {
        int templateId = certificate("Literal");

        var generated = generation.generate(as(manager), templateId, lena.id());

        byte[] pdf = fileOf(generated.documentId());
        String text = PdfText.extract(pdf);
        assertTrue(text.contains("Lena Sch*midt_#1"), text);
        assertTrue(text.contains("Schule *am* See"), text);
        assertTrue(text.contains("Sie engagiert sich"), text);
        assertTrue(text.contains("Generator Wache"), text);
        assertTrue(text.contains("Dönhoffstr. 31, 10318 Berlin"), text);
        try (var document = Loader.loadPDF(pdf)) {
            var resources = document.getPage(0).getResources();
            long pictures = 0;
            for (var name : resources.getXObjectNames()) {
                if (resources.getXObject(name) instanceof PDImageXObject) pictures++;
            }
            assertEquals(2, pictures, "the logo and the library picture");
        }
    }

    /** The file is PDF/A-3b, which a sealed copy stays conformant to when signing adds to it. */
    @Test
    void theFileIsPdfA3b() throws IOException {
        var generated = generation.generate(as(manager), certificate("Archiv"), lena.id());

        try (var document = Loader.loadPDF(fileOf(generated.documentId()))) {
            var metadata = document.getDocumentCatalog().getMetadata();
            String xmp = new String(metadata.toByteArray(), StandardCharsets.UTF_8);
            assertTrue(xmp.contains("pdfaid:part>3<") || xmp.contains("pdfaid:part=\"3\""), xmp);
            assertTrue(xmp.contains("pdfaid:conformance>B<") || xmp.contains("pdfaid:conformance=\"B\""), xmp);
        }
    }

    /** A letter gets the issuer's signature field where its body names it, empty, and stays PDF/A. */
    @Test
    void aLetterPlacesTheIssuersSignatureFieldWhereItsBodyNamesIt() throws IOException {
        int templateId = template(
                "Unterschrieben", false, false, 0, "Für {{member.fullName}}\n\n{{signature.issuer}}\n\nJugendwartin");

        var generated = generation.generate(as(manager), templateId, lena.id());

        assertTrue(generated.missing().isEmpty());
        try (var document = Loader.loadPDF(fileOf(generated.documentId()))) {
            var field = (PDSignatureField)
                    document.getDocumentCatalog().getAcroForm(null).getField("issuer");
            assertNull(field.getSignature());
            var widget = field.getWidgets().getFirst();
            assertEquals(170, widget.getRectangle().getWidth(), 1);
            assertTrue(document.getPage(0).getAnnotations().stream()
                    .noneMatch(annotation -> annotation instanceof PDAnnotationLink));
            String xmp = new String(document.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
            assertTrue(xmp.contains("pdfaid:part>3<") || xmp.contains("pdfaid:part=\"3\""), xmp);
        }
    }

    @Test
    void aLetterWithoutASignatureFieldHasNoForm() throws IOException {
        var generated = generation.generate(as(manager), certificate("Ohne Unterschrift"), lena.id());

        try (var document = Loader.loadPDF(fileOf(generated.documentId()))) {
            assertNull(document.getDocumentCatalog().getAcroForm(null));
        }
    }

    @Test
    void everyMemberGetsTheirOwnDocument() throws IOException {
        int templateId = certificate("Jeder");

        var forLena = generation.generate(as(manager), templateId, lena.id());
        var forMax = generation.generate(as(manager), templateId, max.id());

        assertNotEquals(forLena.documentId(), forMax.documentId());
        assertTrue(PdfText.extract(fileOf(forMax.documentId())).contains("Max Weiß"));
        assertFalse(PdfText.extract(fileOf(forMax.documentId())).contains("Lena"));
        assertTrue(memberDocumentRepo.isBoundTo(forMax.documentId(), max.id()));
    }

    /** A manager is warned and may still generate; the gaps print as lines to fill in. */
    @Test
    void aManagerMayGenerateWithGaps() {
        var generated = generation.generate(as(manager), certificate("Lücke"), max.id());

        assertEquals(List.of(new MissingValue("profile." + schoolField, "Schule")), generated.missing());
    }

    @Test
    void guardiansWhoseDataGoesInAreDataSubjects() {
        int templateId =
                template("Eltern", false, false, 0, "{{member.fullName}}, vertreten durch {{guardian1.fullName}}");

        var generated = generation.generate(as(manager), templateId, lena.id());

        assertEquals(
                List.of(
                        new DataSubject(lena.id(), SubjectRole.MEMBER),
                        new DataSubject(guardian.id(), SubjectRole.GUARDIAN)),
                log.subjects(generated.generationId()));
    }

    @Test
    void aFileTheStationHasNoRoomForIsRefusedAndNothingIsLogged() {
        var intake = mock(DocumentIntake.class);
        when(intake.take(anyInt(), any(), any(), any())).thenThrow(DocumentRefusal.DOCUMENT_UPLOAD_NO_ROOM.raise());
        var full = new DocumentGenerationService(
                templates, generatorOf(), documents, intake, log, mock(TemplateChecks.class));
        int templateId = certificate("Voll");
        int before =
                memberDocumentRepo.findByMember(station.id(), max.id(), true).size();

        refused(DocumentRefusal.DOCUMENT_UPLOAD_NO_ROOM, () -> full.generate(as(manager), templateId, max.id()));
        assertEquals(
                before,
                memberDocumentRepo.findByMember(station.id(), max.id(), true).size());
    }

    private static DocumentGeneratorService generatorOf() {
        var pictures = mock(KbPdfPictures.class);
        when(pictures.place(anyInt(), anyString()))
                .thenAnswer(call -> new KbPdfPictures.Placed(call.getArgument(1), Map.of()));
        var catalogue = new PlaceholderCatalogue(profileFieldRepo, stationRepo);
        return new DocumentGeneratorService(
                templates,
                new PlaceholderResolver(stationRepo, stationMemberRepo, memberNameResolver, profileFieldRepo, clock),
                catalogue,
                new LetterRenderer(pictures, mock(MediaLibraryService.class), newStationLogoService(), stationRepo),
                pdfRenderer,
                stationRepo,
                clock);
    }

    @Test
    void anArchivedTemplateGeneratesNothing() {
        int templateId = certificate("Alt");
        templates.setArchived(owner, templateId, true, manager.id());

        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_ARCHIVED,
                () -> generation.generate(as(manager), templateId, lena.id()));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_ARCHIVED,
                () -> selfService.generate(as(lena), templateId, lena.id()));
    }

    @Test
    void aStationWithoutDocumentsGeneratesNone() {
        int templateId = certificate("Abgeschaltet");
        stationRepo.setDisabledModules(station.id(), Set.of(StationModule.DOCUMENTS));
        try {
            refused(
                    DocumentRefusal.DOCUMENTS_SWITCHED_OFF,
                    () -> generation.generate(as(manager), templateId, lena.id()));
        } finally {
            stationRepo.setDisabledModules(station.id(), Set.of());
        }
    }

    /** A legal document names a member officially, whatever state its template got into. */
    @Test
    void aLegalDocumentRefusesTheCalledNameWhenItIsGenerated() {
        var draft = new DocumentTemplateDraft(
                "Alt rechtlich",
                "t",
                "f",
                List.of(),
                false,
                true,
                true,
                false,
                0,
                RestrictionMode.AND,
                null,
                new LetterContent(Letterhead.empty(), "{{member.calledName}}", LetterPage.defaults()));
        var repository = new DocumentTemplateRepository();
        int templateId = repository.create(station.id(), draft, manager.id()).id();
        repository.writeLetter(templateId, (LetterContent) draft.content());

        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_CALLED_NAME_IN_LEGAL,
                () -> generation.generate(as(manager), templateId, lena.id()));
    }

    @Test
    void aPreviewIsDrawnForAMemberAndListsWhatIsMissing() {
        int templateId = certificate("Vorschau");

        var preview = generation.preview(as(manager), templateId, max.id());

        assertTrue(preview.pdfBase64().startsWith("JVBER"), "a PDF");
        assertEquals(List.of(new MissingValue("profile." + schoolField, "Schule")), preview.missing());
    }

    /** The editor draws an unsaved template with its placeholders shown by their labels. */
    @Test
    void aDraftIsDrawnWithItsLabelsOrForAMemberByWhoeverMayFileForThem() {
        var draft = new DocumentTemplateRequest(
                null,
                "Entwurf",
                null,
                null,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                LETTERHEAD,
                "Für {{member.fullName}} am {{today}}",
                null,
                null,
                null);

        var labelled =
                generation.previewDraft(as(manager, StationPermission.DOCUMENT_TEMPLATE_EDIT), draft, null, null);
        var forMember = generation.previewDraft(
                as(manager, StationPermission.DOCUMENT_TEMPLATE_EDIT, StationPermission.DOCUMENT_EDIT_MEMBER),
                draft,
                null,
                lena.id());

        String text = PdfText.extract(Base64.getDecoder().decode(labelled.pdfBase64()));
        assertTrue(text.contains("[Vor- und Nachname]"), text);
        assertTrue(text.contains("02.10.2026"), text);
        assertTrue(forMember.missing().isEmpty());
        refused(
                DocumentRefusal.DOCUMENT_GENERATE_NOT_YOURS,
                () -> generation.previewDraft(
                        as(manager, StationPermission.DOCUMENT_TEMPLATE_EDIT), draft, null, lena.id()));
    }

    @Test
    void aMemberGeneratesTheirOwnDocumentWhichIsNeverHidden() {
        int templateId = certificate("Selbst");

        var generated = selfService.generate(as(lena), templateId, lena.id());

        var document = memberDocumentRepo.findById(generated.documentId()).orElseThrow();
        assertFalse(document.hidden());
        assertEquals(lena.id(), document.uploadedBy());
        assertTrue(log.findById(generated.generationId()).orElseThrow().selfService());
    }

    @Test
    void aGuardianGeneratesForTheirChildButNotForSomebodyElse() {
        int templateId = certificate("Für das Kind");

        var generated = selfService.generate(as(guardian), templateId, lena.id());

        assertTrue(memberDocumentRepo.isBoundTo(generated.documentId(), lena.id()));
        assertEquals(
                guardian.id(),
                log.findById(generated.generationId()).orElseThrow().generatedBy());
        refused(
                DocumentRefusal.DOCUMENT_SELF_SERVICE_NOT_YOURS,
                () -> selfService.generate(as(stranger), templateId, lena.id()));
        refused(DocumentRefusal.DOCUMENT_SELF_SERVICE_NOT_YOURS, () -> selfService.offers(as(stranger), lena.id()));
    }

    /** The wait is counted from the last self service document of that member, and says when it ends. */
    @Test
    void theCooldownRefusesUntilItsDay() {
        int templateId = certificate("Wartezeit");
        selfService.generate(as(lena), templateId, lena.id());

        var refusal = assertThrows(RefusalResponse.class, () -> selfService.generate(as(lena), templateId, lena.id()));
        assertEquals(DocumentRefusal.DOCUMENT_SELF_SERVICE_COOLING_DOWN, refusal.refusal());
        assertEquals(RefusalDetail.text("01.11.2026"), refusal.detail());
        var offer = selfService.offers(as(lena), lena.id()).stream()
                .filter(candidate -> candidate.templateId() == templateId)
                .findFirst()
                .orElseThrow();
        assertEquals(NOW.plus(Duration.ofDays(30)), offer.availableFrom());

        clock.advance(Duration.ofDays(31));
        try {
            assertTrue(selfService.generate(as(lena), templateId, lena.id()).documentId() > 0);
        } finally {
            clock.advance(Duration.ofDays(-31));
        }
    }

    /** A manager's document does not keep the member from generating their own. */
    @Test
    void onlySelfServiceCountsTowardsTheWait() {
        int templateId = certificate("Verwaltung zählt nicht");
        generation.generate(as(manager), templateId, lena.id());

        assertTrue(selfService.generate(as(lena), templateId, lena.id()).documentId() > 0);
    }

    @Test
    void missingDataIsRefusedAndNamed() {
        int templateId = certificate("Unvollständig");

        var refusal = assertThrows(RefusalResponse.class, () -> selfService.generate(as(max), templateId, max.id()));

        assertEquals(DocumentRefusal.DOCUMENT_SELF_SERVICE_VALUES_MISSING, refusal.refusal());
        assertEquals(RefusalDetail.text("Schule"), refusal.detail());
        var offer = selfService.offers(as(max), max.id()).stream()
                .filter(candidate -> candidate.templateId() == templateId)
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(new MissingValue("profile." + schoolField, "Schule")), offer.missing());
        assertNull(offer.availableFrom());
    }

    @Test
    void aTemplateNotForSelfServiceOrNotForTheMemberIsNotOffered() {
        int managersOnly = template("Nur Verwaltung", false, false, 30, "{{member.fullName}}");
        int forGuardians = template("Nur Eltern", true, false, 30, "{{member.fullName}}");
        templates.update(
                owner,
                forGuardians,
                new DocumentTemplateRequest(
                        null,
                        "Nur Eltern",
                        null,
                        null,
                        null,
                        false,
                        null,
                        false,
                        true,
                        30,
                        new RestrictionAudience(
                                List.of(StationUserType.GUARDIAN),
                                List.of(),
                                List.of(),
                                List.of(),
                                RestrictionMode.AND),
                        null,
                        null,
                        "{{member.fullName}}",
                        null,
                        null,
                        null),
                manager.id());

        refused(
                DocumentRefusal.DOCUMENT_SELF_SERVICE_NOT_OFFERED,
                () -> selfService.generate(as(max), managersOnly, max.id()));
        refused(
                DocumentRefusal.DOCUMENT_SELF_SERVICE_NOT_OFFERED,
                () -> selfService.generate(as(max), forGuardians, max.id()));
        assertTrue(selfService.offers(as(max), max.id()).stream()
                .noneMatch(offer -> offer.templateId() == managersOnly || offer.templateId() == forGuardians));
        assertTrue(selfService.offers(as(guardian), guardian.id()).stream()
                .anyMatch(offer -> offer.templateId() == forGuardians));
    }
}
