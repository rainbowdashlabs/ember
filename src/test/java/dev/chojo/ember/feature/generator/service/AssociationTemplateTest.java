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
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.Placeholder;
import dev.chojo.ember.feature.generator.repository.DocumentFontRepository;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.PdfTemplateRepository;
import dev.chojo.ember.feature.generator.repository.TemplateStationUseRepository;
import dev.chojo.ember.feature.generator.service.TemplateStationUseService.TemplateUseRequest;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService;
import dev.chojo.ember.feature.generator.service.font.FontLibrary;
import dev.chojo.ember.feature.generator.service.font.TestFonts;
import dev.chojo.ember.feature.generator.service.pdf.PdfStamper;
import dev.chojo.ember.feature.generator.service.pdf.StampFonts;
import dev.chojo.ember.feature.generator.service.pdf.TestPdfs;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfPictures;
import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.media.service.MediaReferenceRegistry;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.PdfText;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import tools.jackson.databind.node.StringNode;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.image;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Templates an association keeps for its stations: who may change them and who may only use them, what
 * they can name and where those values come from, which station offers them for self service, and where
 * their files, fonts and pictures are kept.
 */
class AssociationTemplateTest extends RepositoryTestBase {
    private static final String PICTURE = "e".repeat(64);

    private static DocumentTemplateService templates;
    private static TemplateStationUseService stationUses;
    private static PdfTemplateService pdfs;
    private static DocumentGenerationService generation;
    private static SelfServiceDocumentService selfService;
    private static DocumentFontService fonts;
    private static DocumentService documents;
    private static DocumentGenerationRepository log;
    private static StorageService storage;

    private static Cluster cluster;
    private static Owner.Association association;
    private static Owner.Association otherAssociation;
    private static Station north;
    private static Station south;
    private static Station alone;
    private static StationMember manager;
    private static StationMember lena;
    private static StationMember ben;
    private static StationMember southManager;
    private static int author;
    private static int passField;

    @BeforeAll
    static void setup() {
        cluster = clusterService.create("Vorlagen Verband", null);
        association = new Owner.Association(cluster.id());
        otherAssociation = new Owner.Association(
                clusterService.create("Anderer Vorlagen Verband", null).id());
        stationRepo.updateLocation(cluster.homeStationId(), "Verbandsweg 1", "10115", "Berlin", "DE", null, null);
        north = stationInAssociation("Vorlagen Wache Nord");
        south = stationInAssociation("Vorlagen Wache Süd");
        alone = stationRepo.create("Vorlagen Einzelwache");
        manager = member(north, "assoc-manager@test.com", "Nora", "Fülling");
        author = Objects.requireNonNull(manager.accountId());
        lena = member(north, "assoc-lena@test.com", "Lena", "Nord");
        ben = member(south, "assoc-ben@test.com", "Ben", "Süd");
        southManager = member(south, "assoc-south@test.com", "Sara", "Süd");

        passField = clusterProfileFieldRepo
                .create(
                        cluster.id(),
                        "Verbandsausweis",
                        FieldType.TEXT,
                        ProfileFieldConfig.empty(),
                        false,
                        false,
                        null,
                        false,
                        false,
                        null)
                .id();
        clusterProfileFieldRepo.assignToRole(passField, ProfileFieldScope.MEMBER, 0, null, null, null);
        clusterProfileFieldRepo.assignToRole(passField, ProfileFieldScope.GUARDIAN, 0, null, null, null);
        clusterProfileFieldRepo.setValue(lena.id(), passField, StringNode.valueOf("JF-4711"));

        wire();
    }

    private static Station stationInAssociation(String name) {
        var station = stationRepo.create(name);
        stationRepo.setCluster(station.id(), cluster.id());
        return station;
    }

    private static StationMember member(Station station, String email, String first, String last) {
        return stationMemberRepo.create(
                station.id(), accountRepo.create(email, first, last).id());
    }

    private static void wire() {
        var backend = localStorage();
        storage = new StorageService(new StorageBackendResolver(backend), backend);
        documents = newDocumentService(storage);
        var media = mock(MediaLibraryService.class);
        when(media.findByHash(any(), anyString())).thenReturn(Optional.empty());
        when(media.findByHash(eq(cluster.homeStationId()), eq(PICTURE)))
                .thenReturn(Optional.of(new StationFile(
                        1,
                        0,
                        cluster.homeStationId(),
                        PICTURE,
                        "wappen.png",
                        "image/png",
                        1,
                        Instant.EPOCH,
                        null,
                        null,
                        null)));
        var pictures = mock(KbPdfPictures.class);
        when(pictures.place(anyInt(), anyString(), anyString()))
                .thenAnswer(call -> new KbPdfPictures.Placed(call.getArgument(1), Map.of()));
        var clock = new MovableClock(Instant.parse("2026-10-03T10:00:00Z"));
        var catalogue = newPlaceholderCatalogue();
        var templateRepository = new DocumentTemplateRepository();
        var pdfTemplates = new PdfTemplateRepository();
        var uses = new TemplateStationUseRepository();
        FontLibrary library = newFontLibrary(storage);
        var checks = new TemplateChecks(
                templateRepository,
                pdfTemplates,
                new LetterChecks(contentBlocks(), media),
                stationRepo,
                catalogue,
                library,
                newOwnerStores());
        templates = new DocumentTemplateService(
                templateRepository, pdfTemplates, uses, checks, restrictionService, catalogue, newOwnerStores());
        stationUses = new TemplateStationUseService(templates, uses, restrictionService);
        pdfs = new PdfTemplateService(
                templates, templateRepository, pdfTemplates, newDocumentIntake(), storage, newOwnerStores());
        var generator = new DocumentGeneratorService(
                templates,
                newPlaceholderResolver(clock),
                catalogue,
                new LetterRenderer(pictures, media, newStationLogoService(), library, newOwnerStores()),
                new PdfTemplateRenderer(pdfs, new PdfStamper(new StampFonts()), library),
                stationRepo,
                restrictionService,
                clock);
        log = new DocumentGenerationRepository();
        generation =
                new DocumentGenerationService(templates, generator, documents, newDocumentIntake(), log, checks, clock);
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
                clock);
        var quota = new StorageQuotaService(storageUsageRepo, new Storage(), new DomainEventBus(Set.of()));
        fonts = new DocumentFontService(new DocumentFontRepository(), library, storage, quota);
    }

    private static int associationLetter(String name, String... texts) {
        return templates
                .create(association, letter(name, texts).build(), author)
                .id();
    }

    private static StationSession as(StationMember member, StationPermission... permissions) {
        return stationSession(member, permissions);
    }

    private static void refused(Refusal refusal, Executable action) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, action).refusal());
    }

    private static String previewText(StationMember viewer, StationMember member, int templateId) throws IOException {
        var preview = generation.preview(as(viewer), templateId, member.id());
        return PdfText.extract(Base64.getDecoder().decode(preview.pdfBase64()));
    }

    @Test
    void theAssociationWritesATemplateItsStationsUseButDoNotChange() {
        int templateId = associationLetter("Verbandsbescheinigung", "Für {{member.fullName}}");
        var northOwner = new Owner.Station(north.id());

        var listed = templates.list(northOwner, false).stream()
                .filter(template -> template.id() == templateId)
                .findFirst()
                .orElseThrow();
        assertTrue(listed.ofAssociation());
        assertEquals(templateId, templates.requireUsable(south.id(), templateId).id());

        refused(DocumentRefusal.DOCUMENT_TEMPLATE_KEPT_BY_ASSOCIATION, () -> templates.detail(northOwner, templateId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_KEPT_BY_ASSOCIATION,
                () -> templates.update(
                        northOwner, templateId, letter("Umbenannt").build(), author));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_KEPT_BY_ASSOCIATION,
                () -> templates.setArchived(northOwner, templateId, true, author));
        refused(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE, () -> templates.requireUsable(alone.id(), templateId));
        refused(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE, () -> templates.detail(otherAssociation, templateId));
        assertTrue(templates.list(new Owner.Station(alone.id()), false).stream()
                .noneMatch(template -> template.id() == templateId));
        assertTrue(templates.list(otherAssociation, false).stream().noneMatch(template -> template.id() == templateId));
    }

    @Test
    void anAssociationOffersSelfServiceButLeavesTheAudienceToItsStations() {
        var audience = new RestrictionAudience(
                List.of(StationUserType.TRIAL), List.of(), List.of(), List.of(), RestrictionMode.AND);
        refused(
                DocumentRefusal.DOCUMENT_ASSOCIATION_TEMPLATE_AUDIENCE,
                () -> templates.create(
                        association,
                        letter("Mit Zielgruppe")
                                .selfService(true)
                                .audience(audience)
                                .build(),
                        author));
    }

    @Test
    void anAssociationsTemplateNamesTheAssociationAndItsQuestions() {
        var ofAssociation = keys(templates.catalogue(association).placeholders());
        assertTrue(ofAssociation.contains("associationProfile." + passField));
        assertTrue(ofAssociation.contains("guardian1.associationProfile." + passField));
        assertTrue(ofAssociation.contains("association.name"));
        assertTrue(ofAssociation.contains("station.name"));

        var atStation = keys(templates.catalogue(new Owner.Station(north.id())).placeholders());
        assertTrue(atStation.contains("associationProfile." + passField));
        assertTrue(atStation.contains("association.address"));

        var alone = keys(templates
                .catalogue(new Owner.Station(AssociationTemplateTest.alone.id()))
                .placeholders());
        assertFalse(alone.contains("association.name"));
        assertTrue(alone.stream().noneMatch(key -> key.contains("associationProfile.")));
    }

    private static List<String> keys(List<Placeholder> placeholders) {
        return placeholders.stream().map(Placeholder::key).toList();
    }

    /** One template names the right station for every member, with the association and its answers. */
    @Test
    void aDocumentOfTheAssociationIsFilledAtTheMembersOwnStation() throws IOException {
        int templateId = associationLetter(
                "Verbandsausweis",
                "{{member.fullName}} von {{station.name}}",
                "{{association.name}}, {{association.address}}",
                "Ausweis {{associationProfile.%d}}".formatted(passField));

        String forLena = previewText(manager, lena, templateId);
        assertTrue(forLena.contains("Vorlagen Wache Nord"), forLena);
        assertTrue(forLena.contains("Vorlagen Verband, Verbandsweg 1, 10115 Berlin"), forLena);
        assertTrue(forLena.contains("Ausweis JF-4711"), forLena);
        assertTrue(previewText(southManager, ben, templateId).contains("Vorlagen Wache Süd"));

        var filed = generation.generate(as(manager, StationPermission.DOCUMENT_EDIT_MEMBER), templateId, lena.id());
        var entry = log.findById(filed.generationId()).orElseThrow();
        assertEquals(north.id(), entry.stationId());
        assertEquals(templateId, entry.templateId());
        assertTrue(memberDocumentRepo.isBoundTo(filed.documentId(), lena.id()));
        assertEquals(
                north.id(),
                memberDocumentRepo.findById(filed.documentId()).orElseThrow().stationId());

        int elsewhere = templates
                .create(otherAssociation, letter("Anderer Verband", "x").build(), author)
                .id();
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE,
                () -> generation.generate(as(manager), elsewhere, lena.id()));
    }

    /** The association offers a template; each station switches it on for an audience of its own. */
    @Test
    void eachStationDecidesWhetherItsMembersGenerateAnAssociationsTemplate() {
        int offered = templates
                .create(
                        association,
                        letter("Selbst erstellen", "{{member.fullName}}")
                                .selfService(true)
                                .build(),
                        author)
                .id();
        int notOffered = associationLetter("Nur für Verwalter", "{{member.fullName}}");

        assertFalse(offeredTo(lena, offered), "nothing until the station decides");
        var everybody = new TemplateUseRequest(true, null);
        assertTrue(stationUses.setUse(north.id(), offered, everybody).selfService());
        assertTrue(offeredTo(lena, offered));
        assertFalse(offeredTo(ben, offered), "another station has not switched it on");

        stationUses.setUse(north.id(), notOffered, everybody);
        assertFalse(offeredTo(lena, notOffered), "the association does not offer it");

        var guardiansOnly = new RestrictionAudience(
                List.of(StationUserType.GUARDIAN), List.of(), List.of(), List.of(), RestrictionMode.AND);
        var narrowed = stationUses.setUse(north.id(), offered, new TemplateUseRequest(true, guardiansOnly));
        assertEquals(List.of(StationUserType.GUARDIAN), narrowed.audience().userTypes());
        assertFalse(offeredTo(lena, offered));
        refused(
                DocumentRefusal.DOCUMENT_SELF_SERVICE_NOT_OFFERED,
                () -> selfService.generate(as(lena), offered, lena.id()));

        stationUses.setUse(north.id(), offered, everybody);
        var filed = selfService.generate(as(lena), offered, lena.id());
        assertEquals(
                north.id(), log.findById(filed.generationId()).orElseThrow().stationId());
        refused(
                DocumentRefusal.DOCUMENT_SELF_SERVICE_COOLING_DOWN,
                () -> selfService.generate(as(lena), offered, lena.id()));
    }

    private static boolean offeredTo(StationMember member, int templateId) {
        return selfService.offers(as(member), member.id()).stream().anyMatch(offer -> offer.templateId() == templateId);
    }

    @Test
    void aStationsOwnTemplateHasNoUseToSet() {
        int own = templates
                .create(new Owner.Station(north.id()), letter("Eigene Vorlage").build(), author)
                .id();
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_USE_NOT_ASSOCIATION,
                () -> stationUses.setUse(north.id(), own, new TemplateUseRequest(true, null)));
        refused(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE, () -> stationUses.useOf(alone.id(), own));
    }

    /** The PDF of an association's template is kept in the association's own scope, and read from there at any station. */
    @Test
    void anAssociationsPdfIsKeptInItsOwnScope() throws IOException {
        var created = templates.create(
                association, TemplateRequestBuilder.pdf("Verbandsformular").build(), author);
        var uploaded = pdfs.upload(
                association, created.id(), TestUploads.of("form.pdf", "application/pdf", TestPdfs.plain(1)), author);
        var original = Objects.requireNonNull(uploaded.pdf());

        var scope =
                new StorageScope.Association(cluster.homeStationId(), stationRepo.requireUid(cluster.homeStationId()));
        assertTrue(storage.exists(scope, StorageCategory.ASSOCIATION_DOCUMENT_TEMPLATES, original.id() + "/original"));
        assertTrue(pdfs.current(association, created.id()).isPresent());
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_KEPT_BY_ASSOCIATION,
                () -> pdfs.current(new Owner.Station(north.id()), created.id()));

        var filed = generation.generate(as(manager, StationPermission.DOCUMENT_EDIT_MEMBER), created.id(), lena.id());
        assertNotNull(memberDocumentRepo.findById(filed.documentId()).orElseThrow());
    }

    /** An association's template prints in the fonts of the instance and the association, never in a station's. */
    @Test
    void anAssociationsTemplateReachesItsOwnFontsButNoStations() {
        fonts.upload(
                new Owner.Station(north.id()),
                TestUploads.of("w.ttf", "font/ttf", TestFonts.lisu()),
                "Wachenschrift",
                FontStyle.REGULAR,
                true,
                author);
        fonts.upload(
                association,
                TestUploads.of("v.ttf", "font/ttf", TestFonts.lisu()),
                "Verbandsschrift",
                FontStyle.REGULAR,
                true,
                author);

        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_FONT_UNKNOWN,
                () -> templates.create(
                        association,
                        letter("Wachenfont").page(bodyIn("Wachenschrift")).build(),
                        author));
        assertNotNull(templates.create(
                association,
                letter("Verbandsfont").page(bodyIn("Verbandsschrift")).build(),
                author));
        assertNotNull(templates.create(
                new Owner.Station(north.id()),
                letter("Beide")
                        .page(new LetterPage(40, 30, 20, 20, 10, "Wachenschrift", "Verbandsschrift", null))
                        .build(),
                author));
    }

    private static LetterPage bodyIn(String family) {
        return new LetterPage(40, 30, 20, 20, 10, family, null, null);
    }

    /** Pictures of an association's letters come from its home station's library, which keeps them. */
    @Test
    void thePicturesOfAnAssociationsLettersAreKeptInItsHomeStation() {
        templates.create(
                association,
                letter("Mit Wappen").header(List.of(row(image(PICTURE)))).build(),
                author);

        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PICTURE_NOT_HERE,
                () -> templates.create(
                        new Owner.Station(north.id()),
                        letter("Wappen der Wache")
                                .header(List.of(row(image(PICTURE))))
                                .build(),
                        author));
        var registry = new MediaReferenceRegistry(contentContainerRepo);
        assertTrue(registry.collect(cluster.homeStationId()).contains(PICTURE));
        assertFalse(registry.collect(north.id()).contains(PICTURE));
    }

    /** The association has no members of its own, so it looks at its template without one. */
    @Test
    void anAssociationsTemplateIsLookedAtWithoutAMember() {
        var request = letter("Ansicht", "{{member.fullName}} bei {{station.name}}, {{association.name}}")
                .build();

        var preview = generation.previewAssociationDraft(association, request, null, null);
        assertTrue(preview.pdfBase64().length() > 0);
        refused(
                DocumentRefusal.DOCUMENT_ASSOCIATION_PREVIEW_WITH_MEMBER,
                () -> generation.previewAssociationDraft(association, request, null, lena.id()));
    }
}
