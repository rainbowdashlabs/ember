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
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.GuardianCondition;
import dev.chojo.ember.feature.content.route.BlockRowRequest;
import dev.chojo.ember.feature.documents.service.DocumentIntake;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.generator.entity.DataSubject;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.FillInField;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.MissingValue;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.entity.SubjectRole;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.PdfTemplateRepository;
import dev.chojo.ember.feature.generator.repository.TemplateStationUseRepository;
import dev.chojo.ember.feature.generator.service.font.DefaultFont;
import dev.chojo.ember.feature.generator.service.font.FontLibrary;
import dev.chojo.ember.feature.generator.service.pdf.FillInFields;
import dev.chojo.ember.feature.generator.service.pdf.PdfStamper;
import dev.chojo.ember.feature.generator.service.pdf.StampFonts;
import dev.chojo.ember.feature.generator.service.pdf.TestPdfs;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfPictures;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.PronounSet;
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
import dev.chojo.ember.util.Sha256;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.jspecify.annotations.Nullable;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.imageio.ImageIO;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.divider;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.fillIn;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.image;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.lined;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.row;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.rowsOf;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.signature;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.spacer;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.text;
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
    private static FontLibrary fonts;
    private static SelfServiceDocumentService selfService;
    private static DocumentService documents;
    private static DocumentGenerationRepository log;
    private static Station station;
    private static Owner.Station owner;
    private static StationMember manager;
    private static int author;
    private static TemplateStationUseService stationUses;
    private static StationMember lena;
    private static StationMember max;
    private static StationMember guardian;
    private static StationMember stranger;
    private static int schoolField;

    @BeforeAll
    static void setup() throws IOException {
        clock = new MovableClock(NOW);
        station = stationRepo.create("Generator Wache");
        stationRepo.updateLocation(station.id(), "Dönhoffstr. 31", "10318", "Berlin", "DE", null, null);
        owner = new Owner.Station(station.id());
        manager = member("gen-manager@test.com", "Nora", "Fülling");
        author = Objects.requireNonNull(manager.accountId());
        lena = member("gen-lena@test.com", "Lena", "Sch*midt_#1");
        max = member("gen-max@test.com", "Max", "Weiß");
        guardian = member("gen-guardian@test.com", "Anna", "Schmidt");
        stranger = member("gen-stranger@test.com", "Fremd", "Person");
        stationMemberRepo.addManager(guardian.id(), lena.id());
        stationMemberRepo.setUserType(guardian.id(), StationUserType.GUARDIAN);

        var genders = new ProfileFieldConfig(
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
                Map.of("weiblich", Map.of("de", new PronounSet("sie", "sie", "ihr", "ihr"))));
        int genderField = profileFieldRepo
                .create(station.id(), "Geschlecht", FieldType.GENDER, genders, false, false, null)
                .id();
        schoolField = profileFieldRepo
                .create(station.id(), "Schule", FieldType.TEXT, ProfileFieldConfig.empty(), false, false, null)
                .id();
        profileFieldRepo.assignToRole(schoolField, ProfileFieldScope.MEMBER, 0, null, null, null);
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
        when(pictures.place(anyInt(), anyString(), anyString()))
                .thenAnswer(call -> new KbPdfPictures.Placed(call.getArgument(1), Map.of()));

        var catalogue = newPlaceholderCatalogue();
        var templateRepository = new DocumentTemplateRepository();
        var pdfTemplates = new PdfTemplateRepository();
        var uses = new TemplateStationUseRepository();
        fonts = newFontLibrary(storage);
        var issuers = new DocumentIssuerService(stationMemberRepo, uses);
        var checks = new TemplateChecks(
                templateRepository,
                pdfTemplates,
                new LetterChecks(contentBlocks(), media),
                stationRepo,
                catalogue,
                fonts,
                newOwnerStores(),
                issuers);
        templates = new DocumentTemplateService(
                templateRepository, pdfTemplates, uses, checks, restrictionService, catalogue, newOwnerStores());
        stationUses = new TemplateStationUseService(templates, uses, restrictionService, issuers);
        pdfRenderer = new PdfTemplateRenderer(
                new PdfTemplateService(
                        templates, templateRepository, pdfTemplates, newDocumentIntake(), storage, newOwnerStores()),
                new PdfStamper(new StampFonts(DefaultFont.absent())),
                fonts);
        var generator = new DocumentGeneratorService(
                templates,
                newPlaceholderResolver(clock),
                catalogue,
                new LetterRenderer(pictures, media, logos, fonts, newOwnerStores()),
                pdfRenderer,
                stationRepo,
                restrictionService,
                clock);
        log = new DocumentGenerationRepository();
        generation = new DocumentGenerationService(
                templates, generator, documents, newDocumentIntake(), log, checks, issuers, clock);
        selfService = new SelfServiceDocumentService(
                templateRepository,
                templates,
                stationUses,
                generator,
                generation,
                log,
                restrictionService,
                new GuardianPolicy(stationMemberRepo),
                documents,
                stationRepo,
                issuers,
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

    private static final List<BlockRowRequest> HEADER =
            List.of(row(image(ContentCell.STATION_LOGO), image(PICTURE), text("Jugendfeuerwehr\n\n{{station.name}}")));

    private static final List<BlockRowRequest> FOOTER =
            rowsOf("{{station.address}}, {{station.postalCode}} {{station.city}}");

    private static final String BODY = """
            # Bescheinigung

            Hiermit bestätige ich, dass **{{member.fullName}}** die {{profile.%d}} besucht.

            {{pronoun.subject.start}} engagiert sich regelmäßig.""";

    private static TemplateRequestBuilder letterOf(
            String name, boolean selfServiceOn, boolean hidden, int cooldown, List<BlockRowRequest> body) {
        return letter(name)
                .title("%s {{member.fullName}} {{today}}".formatted(name))
                .fileName("%s {{member.lastName}}".formatted(name))
                .tags(List.of("Bescheinigung"))
                .hidden(hidden)
                .legal()
                .selfService(selfServiceOn)
                .cooldown(cooldown)
                .audience(RestrictionAudience.empty())
                .header(HEADER)
                .footer(FOOTER)
                .body(body);
    }

    private static int template(String name, boolean selfServiceOn, boolean hidden, int cooldown, String body) {
        var request =
                letterOf(name, selfServiceOn, hidden, cooldown, rowsOf(body)).build();
        return templates.create(owner, request, author).id();
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

        var generated =
                generation.generate(as(manager, StationPermission.DOCUMENT_EDIT_MEMBER), templateId, lena.id(), null);

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
        assertEquals(Sha256.hex(pdf), entry.fileSha256());
        assertEquals(1, entry.templateVersion());
        assertFalse(entry.selfService());
        assertEquals(manager.id(), entry.generatedBy());
        assertEquals(List.of(new DataSubject(lena.id(), SubjectRole.MEMBER)), log.subjects(entry.id()));
    }

    /** Values with markup characters in them print as they are, in the body and in the letterhead. */
    @Test
    void valuesPrintLiterallyAndTheLetterheadIsDrawn() throws IOException {
        int templateId = certificate("Literal");

        var generated = generation.generate(as(manager), templateId, lena.id(), null);

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
        var generated = generation.generate(as(manager), certificate("Archiv"), lena.id(), null);

        try (var document = Loader.loadPDF(fileOf(generated.documentId()))) {
            var metadata = document.getDocumentCatalog().getMetadata();
            String xmp = new String(metadata.toByteArray(), StandardCharsets.UTF_8);
            assertTrue(xmp.contains("pdfaid:part>3<") || xmp.contains("pdfaid:part=\"3\""), xmp);
            assertTrue(xmp.contains("pdfaid:conformance>B<") || xmp.contains("pdfaid:conformance=\"B\""), xmp);
        }
    }

    private static int templateOf(String name, BlockRowRequest... body) {
        var request = letterOf(name, false, false, 0, List.of(body))
                .issuer(manager.id(), "Jugendwartin")
                .build();
        return templates.create(owner, request, author).id();
    }

    /**
     * A self service letter naming its issuer by name and function, with the issuer's signature line.
     *
     * @param issuer the member the template names as its issuer, or null for nobody
     */
    private static int issued(String name, @Nullable StationMember issuer) {
        var builder = letterOf(
                name,
                true,
                false,
                0,
                List.of(
                        row(text("Ausgestellt von {{issuer.fullName}}, {{issuer.function}}")),
                        row(signature(SignatureRole.ISSUER, "Unterschrift"))));
        if (issuer != null) builder.issuer(issuer.id(), "Jugendwartin");
        return templates.create(owner, builder.build(), author).id();
    }

    private static String textOf(int documentId) {
        return Objects.requireNonNull(PdfText.extract(fileOf(documentId)));
    }

    @Test
    void theTemplatesIssuerIsNamedAndOwnsTheSignatureField() throws IOException {
        var warden = member("gen-warden@test.com", "Erika", "Wehr");
        stationMemberRepo.setNickname(warden.id(), "Eri", warden.id());
        int templateId = issued("Ausgestellt", warden);

        var generated = generation.generate(as(manager), templateId, lena.id(), null);

        assertTrue(generated.missing().isEmpty(), generated.missing().toString());
        assertTrue(textOf(generated.documentId()).contains("Ausgestellt von Erika Wehr, Jugendwartin"));
        assertEquals(List.of("issuer"), signatureFields(generated.documentId()));
        var logged = log.findById(generated.generationId()).orElseThrow();
        assertEquals(warden.id(), logged.issuerId());
        assertEquals("Jugendwartin", logged.issuerFunction());
        assertTrue(logged.issuerFixed());
        assertTrue(logged.issuerSigns());
    }

    @Test
    void aManagerMayPickAnotherIssuerForOneDocument() {
        int templateId = issued("Vertretung", guardian);
        var instead = new DocumentIssuerService.IssuerChoice(max.id(), " Kassenwart ");

        var preview = generation.preview(as(manager), templateId, lena.id(), instead);
        var generated = generation.generate(as(manager), templateId, lena.id(), instead);

        assertEquals(
                new DocumentGeneratorService.PreviewIssuer(max.id(), "Max Weiß", "Kassenwart", false),
                preview.issuer());
        assertTrue(textOf(generated.documentId()).contains("Ausgestellt von Max Weiß, Kassenwart"));
        var logged = log.findById(generated.generationId()).orElseThrow();
        assertEquals(max.id(), logged.issuerId());
        assertFalse(logged.issuerFixed(), "picked for this document");
        assertEquals(
                new DocumentGeneratorService.PreviewIssuer(guardian.id(), "Anna Schmidt", "Jugendwartin", true),
                generation.preview(as(manager), templateId, lena.id(), null).issuer());
        var elsewhere = stationMemberRepo.create(
                stationRepo.create("Fremde Generator Wache").id(),
                accountRepo.create("gen-elsewhere@test.com", "Fred", "Fremd").id());
        refused(
                DocumentRefusal.DOCUMENT_ISSUER_NOT_HERE,
                () -> generation.generate(
                        as(manager),
                        templateId,
                        lena.id(),
                        new DocumentIssuerService.IssuerChoice(elsewhere.id(), null)));
    }

    @Test
    void selfServiceAlwaysTakesTheTemplatesIssuer() {
        int templateId = issued("Selbst ausgestellt", guardian);

        var generated = selfService.generate(as(lena), templateId, lena.id());

        assertTrue(textOf(generated.documentId()).contains("Ausgestellt von Anna Schmidt, Jugendwartin"));
        var logged = log.findById(generated.generationId()).orElseThrow();
        assertEquals(guardian.id(), logged.issuerId());
        assertTrue(logged.issuerFixed());
        assertTrue(logged.selfService());
    }

    /** A document that names its issuer, or asks them to sign, misses the issuer where nobody is named. */
    @Test
    void anIssuerNobodyIsNamedForIsMissingData() {
        int named = issued("Ohne Aussteller", null);
        var signedOnly = letterOf(
                        "Nur Unterschrift",
                        false,
                        false,
                        0,
                        List.of(row(signature(SignatureRole.ISSUER, "Unterschrift"))))
                .build();
        int onlySigned = templates.create(owner, signedOnly, author).id();
        var issuerMissing = new MissingValue("issuer.fullName", "Ausstellende Person: Name");

        var preview = generation.preview(as(manager), named, lena.id(), null);
        var signedPreview = generation.preview(as(manager), onlySigned, lena.id(), null);

        assertTrue(preview.missing().contains(issuerMissing), preview.missing().toString());
        assertTrue(preview.missing().contains(new MissingValue("issuer.function", "Ausstellende Person: Funktion")));
        assertEquals(List.of(issuerMissing), signedPreview.missing());
        assertEquals(new DocumentGeneratorService.PreviewIssuer(null, null, null, true), signedPreview.issuer());
        var refusal = assertThrows(RefusalResponse.class, () -> selfService.generate(as(lena), named, lena.id()));
        assertEquals(DocumentRefusal.DOCUMENT_SELF_SERVICE_VALUES_MISSING, refusal.refusal());
        var filed = generation.generate(as(manager), onlySigned, lena.id(), null);
        assertNull(log.findById(filed.generationId()).orElseThrow().issuerId());
    }

    /**
     * An issuer who left is missing from then on: the template keeps naming them and can still be saved
     * so, but nobody who left can be named anew. A deleted issuer leaves the template naming nobody.
     */
    @Test
    void anIssuerWhoLeftOrWasDeletedIsMissing() {
        var leaving = member("gen-leaving@test.com", "Lars", "Geht");
        var deleted = member("gen-deleted@test.com", "Dora", "Weg");
        int templateId = issued("Gegangen", leaving);
        int deletedTemplate = issued("Gelöscht", deleted);
        stationMemberRepo.setFormer(leaving.id(), true);
        stationMemberRepo.delete(deleted.id());
        var issuerMissing = new MissingValue("issuer.fullName", "Ausstellende Person: Name");

        assertTrue(generation
                .preview(as(manager), templateId, lena.id(), null)
                .missing()
                .contains(issuerMissing));
        assertTrue(generation
                .preview(as(manager), deletedTemplate, lena.id(), null)
                .missing()
                .contains(issuerMissing));
        assertNull(templates.detail(owner, deletedTemplate).issuerId());
        var unchanged = templates.detail(owner, templateId);
        var again = letterOf("Gegangen", true, true, 0, List.of(row(text("{{issuer.fullName}}"))))
                .issuer(leaving.id(), "Jugendwart")
                .build();
        assertEquals(
                leaving.id(), templates.update(owner, templateId, again, author).issuerId());
        assertEquals(
                unchanged.version() + 1, templates.detail(owner, templateId).version());
        refused(
                DocumentRefusal.DOCUMENT_ISSUER_NOT_HERE,
                () -> templates.create(
                        owner,
                        letterOf("Neu mit Gegangenem", false, false, 0, List.of(row(text("x"))))
                                .issuer(leaving.id(), null)
                                .build(),
                        author));
    }

    /** The names of the signature fields of a filed document, in the order the form lists them. */
    private static List<String> signatureFields(int documentId) throws IOException {
        try (var document = Loader.loadPDF(fileOf(documentId))) {
            var form = document.getDocumentCatalog().getAcroForm(null);
            if (form == null) return List.of();
            return form.getFields().stream()
                    .filter(field -> field instanceof PDSignatureField)
                    .map(field -> field.getPartialName())
                    .toList();
        }
    }

    /** A member with two guardians of their own, made for the test that needs one. */
    private static StationMember withTwoGuardians(String name) {
        var child = member("gen-" + name + "@test.com", name, "Zwei");
        stationMemberRepo.addManager(
                member("gen-" + name + "-a@test.com", "Erste", "Zwei").id(), child.id());
        stationMemberRepo.addManager(
                member("gen-" + name + "-b@test.com", "Zweite", "Zwei").id(), child.id());
        return child;
    }

    /**
     * A signature line holds the issuer's signature field, empty, with its short text under it, and the
     * letter stays PDF/A. The marker that placed the field is gone.
     */
    @Test
    void aSignatureLineHoldsAnEmptyFieldWithItsTextBelow() throws IOException {
        int templateId = templateOf(
                "Unterschrieben",
                row(text("Für {{member.fullName}}")),
                row(
                        text("Berlin, {{today}}"),
                        signature(SignatureRole.ISSUER, "{{generatedBy.fullName}}, Jugendwartin")));

        var generated = generation.generate(as(manager), templateId, lena.id(), null);

        assertTrue(generated.missing().isEmpty());
        try (var document = Loader.loadPDF(fileOf(generated.documentId()))) {
            var field = (PDSignatureField)
                    document.getDocumentCatalog().getAcroForm(null).getField("issuer");
            assertNull(field.getSignature());
            var widget = field.getWidgets().getFirst();
            assertTrue(widget.getRectangle().getWidth() > 200, "the line spans its column");
            assertTrue(document.getPage(0).getAnnotations().stream()
                    .noneMatch(annotation -> annotation instanceof PDAnnotationLink));
            String xmp = new String(document.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
            assertTrue(xmp.contains("pdfaid:part>3<") || xmp.contains("pdfaid:part=\"3\""), xmp);
        }
        String text = PdfText.extract(fileOf(generated.documentId()));
        assertTrue(text.contains("Nora Fülling, Jugendwartin"), text);
        assertEquals(1, text.split("Nora Fülling", -1).length - 1, "the line's text already names the issuer");
    }

    /** Under the issuer's line the issuer's name, under any one guardian's the member they sign for. */
    @Test
    void everySignatureLineNamesItsSigner() throws IOException {
        int templateId = templateOf(
                "Mit Namen",
                row(signature(SignatureRole.ISSUER, "Unterschrift"), signature(SignatureRole.ANY_GUARDIAN, "")));

        var generated = generation.generate(
                as(manager), templateId, withTwoGuardians("Uwe").id(), null);

        String text = PdfText.extract(fileOf(generated.documentId()));
        assertTrue(text.contains("Nora Fülling"), text);
        assertTrue(text.contains("Eine erziehungsberechtigte Person von Uwe Zwei"), text);
    }

    /**
     * Every guardian signs in a field of their own, each named under it; a member without any keeps a line
     * for the first, which names the role.
     */
    @Test
    void eachGuardianSignsInAFieldOfTheirOwn() throws IOException {
        int templateId =
                templateOf("Alle Eltern", row(signature(SignatureRole.EACH_GUARDIAN, "Erziehungsberechtigte")));
        var kim = withTwoGuardians("Kim");

        var forKim = generation.generate(as(manager), templateId, kim.id(), null);
        assertEquals(List.of("guardian1", "guardian2"), signatureFields(forKim.documentId()));
        String kimText = PdfText.extract(fileOf(forKim.documentId()));
        assertTrue(kimText.contains("Erste Zwei"), kimText);
        assertTrue(kimText.contains("Zweite Zwei"), kimText);
        assertEquals(
                List.of("guardian1"),
                signatureFields(generation
                        .generate(as(manager), templateId, lena.id(), null)
                        .documentId()));
        var forMax = generation.generate(as(manager), templateId, max.id(), null);
        assertEquals(List.of("guardian1"), signatureFields(forMax.documentId()));
        String maxText = PdfText.extract(fileOf(forMax.documentId()));
        assertTrue(maxText.contains("Erziehungsberechtigte Person 1"), maxText);
    }

    /**
     * A box to fill in for every guardian becomes one empty text field per guardian, each named after that
     * guardian's signature field and carrying label, required flag and length; the label prints above the
     * box, the marker that placed it is gone, and the letter stays PDF/A.
     */
    @Test
    void aBoxToFillInBecomesATextFieldForEachOfItsSigners() throws IOException {
        int templateId = templateOf(
                "Notfallkontakt",
                row(fillIn(SignatureRole.EACH_GUARDIAN, "Telefon im Notfall", true, 40)),
                row(signature(SignatureRole.EACH_GUARDIAN, "Erziehungsberechtigte")));
        var kim = withTwoGuardians("Pia");

        byte[] pdf = fileOf(
                generation.generate(as(manager), templateId, kim.id(), null).documentId());

        assertEquals(
                List.of(
                        new FillInField("fill-guardian1-0", "guardian1", "Telefon im Notfall", true, 40),
                        new FillInField("fill-guardian2-0", "guardian2", "Telefon im Notfall", true, 40)),
                FillInFields.of(pdf));
        try (var document = Loader.loadPDF(pdf)) {
            assertTrue(document.getPage(0).getAnnotations().stream()
                    .noneMatch(annotation -> annotation instanceof PDAnnotationLink));
            String xmp = new String(document.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
            assertTrue(xmp.contains("pdfaid:part>3<") || xmp.contains("pdfaid:part=\"3\""), xmp);
        }
        assertTrue(PdfText.extract(pdf).contains("Telefon im Notfall *"));
    }

    @Test
    void anyOneGuardianSignsInASingleField() throws IOException {
        int templateId = templateOf("Ein Elternteil", row(signature(SignatureRole.ANY_GUARDIAN, "")));

        var generated = generation.generate(
                as(manager), templateId, withTwoGuardians("Ole").id(), null);

        assertEquals(List.of("anyGuardian"), signatureFields(generated.documentId()));
    }

    @Test
    void theParticipantSignsInTheirField() throws IOException {
        int templateId = templateOf(
                "Teilnahme unterschrieben", row(signature(SignatureRole.PARTICIPANT, "{{member.fullName}}")));

        var generated = generation.generate(as(manager), templateId, lena.id(), null);

        assertEquals(List.of("participant"), signatureFields(generated.documentId()));
    }

    /**
     * A member with one guardian is complete: the second guardian's values print empty, their line is
     * left out, and a block on the second guardian follows suit. A member without any guardian misses
     * the first one.
     */
    @Test
    void aMemberWithOneGuardianIsComplete() throws IOException {
        int templateId = templateOf(
                "Ein oder zwei Elternteile",
                row(text("Vertreten durch {{guardian1.fullName}} und {{guardian2.fullName}}.")),
                row(text("Beide unterschreiben.", GuardianCondition.SECOND_GUARDIAN)),
                row(text("Eine Unterschrift genügt.", GuardianCondition.NO_SECOND_GUARDIAN)),
                row(
                        signature(SignatureRole.GUARDIAN_1, "{{guardian1.fullName}}"),
                        signature(SignatureRole.GUARDIAN_2, "{{guardian2.fullName}}")));
        var kim = withTwoGuardians("Ina");

        var forLena = generation.generate(as(manager), templateId, lena.id(), null);
        var forKim = generation.generate(as(manager), templateId, kim.id(), null);
        var forMax = generation.generate(as(manager), templateId, max.id(), null);

        assertTrue(forLena.missing().isEmpty());
        assertEquals(List.of("guardian1"), signatureFields(forLena.documentId()));
        String lenaText = PdfText.extract(fileOf(forLena.documentId()));
        assertTrue(lenaText.contains("Eine Unterschrift genügt."), lenaText);
        assertFalse(lenaText.contains("Beide"), lenaText);

        assertTrue(forKim.missing().isEmpty());
        assertEquals(List.of("guardian1", "guardian2"), signatureFields(forKim.documentId()));
        String kimText = PdfText.extract(fileOf(forKim.documentId()));
        assertTrue(kimText.contains("Erste Zwei und Zweite Zwei"), kimText);
        assertTrue(kimText.contains("Beide unterschreiben."), kimText);

        assertEquals(
                List.of("guardian1.fullName"),
                forMax.missing().stream().map(MissingValue::key).toList());
    }

    /**
     * Two lines for the issuer may stand as alternatives; a member both apply to is refused, when the
     * document is generated and when it is looked at.
     */
    @Test
    void aSignerTwiceIsRefusedForTheMemberWhoGetsBoth() {
        var guardians = new RestrictionAudience(
                List.of(StationUserType.GUARDIAN), List.of(), List.of(), List.of(), RestrictionMode.AND);
        int templateId = templateOf(
                "Doppelt",
                row(signature(SignatureRole.ISSUER, "Jugendwartin")),
                row(signature(SignatureRole.ISSUER, "Für Eltern", guardians)));

        refused(
                DocumentRefusal.DOCUMENT_SIGNER_TWICE_FOR_MEMBER,
                () -> generation.generate(as(manager), templateId, guardian.id(), null));
        refused(
                DocumentRefusal.DOCUMENT_SIGNER_TWICE_FOR_MEMBER,
                () -> generation.preview(as(manager), templateId, guardian.id(), null));
        assertTrue(generation
                .preview(as(manager), templateId, lena.id(), null)
                .pdfBase64()
                .startsWith("JVBER"));
    }

    /** A divider prints its label between two lines, and a row asking for lines has one between its columns. */
    @Test
    void dividersGapsAndLinesBetweenColumnsArePrinted() throws IOException {
        int lined = templates
                .create(
                        owner,
                        letter("Linien")
                                .body(List.of(
                                        lined(text("Links"), text("Rechts")),
                                        row(spacer(48)),
                                        row(divider("Termine")),
                                        row(text("Darunter"))))
                                .build(),
                        author)
                .id();
        int plain = templates
                .create(
                        owner,
                        letter("Ohne Linien")
                                .body(List.of(row(text("Links"), text("Rechts"))))
                                .build(),
                        author)
                .id();

        byte[] withLines =
                fileOf(generation.generate(as(manager), lined, lena.id(), null).documentId());
        byte[] without =
                fileOf(generation.generate(as(manager), plain, lena.id(), null).documentId());

        String text = PdfText.extract(withLines);
        assertTrue(text.contains("TERMINE"), text);
        assertTrue(text.contains("Darunter"), text);
        assertTrue(inkDownTheMiddle(withLines), "a line between the two columns");
        assertFalse(inkDownTheMiddle(without), "no line where the row asks for none");
    }

    /** Whether anything is drawn down the middle of the page, near the top of the body. */
    private static boolean inkDownTheMiddle(byte[] pdf) throws IOException {
        var picture = TestPdfs.picture(pdf);
        float scale = picture.getWidth() / 595.28f;
        int x = Math.round(595.28f / 2 * scale);
        int top = Math.round((40 - 1) / 25.4f * 72 * scale);
        for (int y = top; y < top + Math.round(20 * scale); y++) {
            if (((picture.getRGB(x, y) >> 16) & 0xff) < 230) return true;
        }
        return false;
    }

    @Test
    void aLetterWithoutASignatureFieldHasNoForm() throws IOException {
        var generated = generation.generate(as(manager), certificate("Ohne Unterschrift"), lena.id(), null);

        try (var document = Loader.loadPDF(fileOf(generated.documentId()))) {
            assertNull(document.getDocumentCatalog().getAcroForm(null));
        }
    }

    @Test
    void everyMemberGetsTheirOwnDocument() throws IOException {
        int templateId = certificate("Jeder");

        var forLena = generation.generate(as(manager), templateId, lena.id(), null);
        var forMax = generation.generate(as(manager), templateId, max.id(), null);

        assertNotEquals(forLena.documentId(), forMax.documentId());
        assertTrue(PdfText.extract(fileOf(forMax.documentId())).contains("Max Weiß"));
        assertFalse(PdfText.extract(fileOf(forMax.documentId())).contains("Lena"));
        assertTrue(memberDocumentRepo.isBoundTo(forMax.documentId(), max.id()));
    }

    /** A manager is warned and may still generate; the gaps print as lines to fill in. */
    @Test
    void aManagerMayGenerateWithGaps() {
        var generated = generation.generate(as(manager), certificate("Lücke"), max.id(), null);

        assertEquals(List.of(new MissingValue("profile." + schoolField, "Schule")), generated.missing());
    }

    /**
     * A block meant for other members is left out of a member's letter, and so are the data it asks
     * for: they are not missing for a member who never sees them.
     */
    @Test
    void aBlockForOtherMembersIsLeftOutWithTheDataItAsksFor() throws IOException {
        var trialOnly = new RestrictionAudience(
                List.of(StationUserType.TRIAL), List.of(), List.of(), List.of(), RestrictionMode.AND);
        var request = letterOf(
                        "Bedingt",
                        false,
                        false,
                        0,
                        List.of(
                                row(text("Für alle: {{member.fullName}}")),
                                row(text("Nur zur Probe an der {{profile.%d}}".formatted(schoolField), trialOnly))))
                .build();
        int templateId = templates.create(owner, request, author).id();

        var generated = generation.generate(as(manager), templateId, max.id(), null);

        String text = PdfText.extract(fileOf(generated.documentId()));
        assertTrue(text.contains("Für alle: Max Weiß"), text);
        assertFalse(text.contains("Nur zur Probe"), text);
        assertTrue(generated.missing().isEmpty(), "the school is asked for in a block Max does not see");

        stationMemberRepo.setUserType(max.id(), StationUserType.TRIAL);
        try {
            var forTrial = generation.preview(as(manager), templateId, max.id(), null);
            assertEquals(List.of(new MissingValue("profile." + schoolField, "Schule")), forTrial.missing());
        } finally {
            stationMemberRepo.setUserType(max.id(), StationUserType.MEMBER);
        }
    }

    @Test
    void guardiansWhoseDataGoesInAreDataSubjects() {
        int templateId =
                template("Eltern", false, false, 0, "{{member.fullName}}, vertreten durch {{guardian1.fullName}}");

        var generated = generation.generate(as(manager), templateId, lena.id(), null);

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
                templates,
                generatorOf(),
                documents,
                intake,
                log,
                mock(TemplateChecks.class),
                new DocumentIssuerService(stationMemberRepo, new TemplateStationUseRepository()));
        int templateId = certificate("Voll");
        int before =
                memberDocumentRepo.findByMember(station.id(), max.id(), true).size();

        refused(DocumentRefusal.DOCUMENT_UPLOAD_NO_ROOM, () -> full.generate(as(manager), templateId, max.id(), null));
        assertEquals(
                before,
                memberDocumentRepo.findByMember(station.id(), max.id(), true).size());
    }

    private static DocumentGeneratorService generatorOf() {
        var pictures = mock(KbPdfPictures.class);
        when(pictures.place(anyInt(), anyString(), anyString()))
                .thenAnswer(call -> new KbPdfPictures.Placed(call.getArgument(1), Map.of()));
        return new DocumentGeneratorService(
                templates,
                newPlaceholderResolver(clock),
                newPlaceholderCatalogue(),
                new LetterRenderer(
                        pictures, mock(MediaLibraryService.class), newStationLogoService(), fonts, newOwnerStores()),
                pdfRenderer,
                stationRepo,
                restrictionService,
                clock);
    }

    @Test
    void anArchivedTemplateGeneratesNothing() {
        int templateId = certificate("Alt");
        templates.setArchived(owner, templateId, true, author);

        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_ARCHIVED,
                () -> generation.generate(as(manager), templateId, lena.id(), null));
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
                    () -> generation.generate(as(manager), templateId, lena.id(), null));
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
                false,
                0,
                RestrictionMode.AND,
                DocumentLanguage.DE,
                null,
                null,
                new LetterContent(
                        List.of(),
                        List.of(),
                        LetterImportService.blocks("{{member.calledName}}"),
                        LetterPage.defaults()));
        var repository = new DocumentTemplateRepository();
        int templateId = repository.create(owner, draft, author).id();
        repository.writeLetter(templateId, (LetterContent) draft.content());

        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_CALLED_NAME_IN_LEGAL,
                () -> generation.generate(as(manager), templateId, lena.id(), null));
    }

    @Test
    void aPreviewIsDrawnForAMemberAndListsWhatIsMissing() {
        int templateId = certificate("Vorschau");

        var preview = generation.preview(as(manager), templateId, max.id(), null);

        assertTrue(preview.pdfBase64().startsWith("JVBER"), "a PDF");
        assertEquals(List.of(new MissingValue("profile." + schoolField, "Schule")), preview.missing());
    }

    /** The editor draws an unsaved template with its placeholders shown by their labels. */
    @Test
    void aDraftIsDrawnWithItsLabelsOrForAMemberByWhoeverMayFileForThem() {
        var draft = letter("Entwurf", "Für {{member.fullName}} am {{today}}")
                .header(HEADER)
                .build();

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
        assertEquals(NOW, offer.lastUsedAt());

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
        generation.generate(as(manager), templateId, lena.id(), null);

        assertNull(selfService.offers(as(lena), lena.id()).stream()
                .filter(candidate -> candidate.templateId() == templateId)
                .findFirst()
                .orElseThrow()
                .lastUsedAt());
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
                letter("Nur Eltern", "{{member.fullName}}")
                        .selfService(true)
                        .cooldown(30)
                        .audience(new RestrictionAudience(
                                List.of(StationUserType.GUARDIAN),
                                List.of(),
                                List.of(),
                                List.of(),
                                RestrictionMode.AND))
                        .build(),
                author);

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
