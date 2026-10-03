/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.generator.entity.DocumentGeneration;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.FieldRect;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.FormBinding;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfFieldKind;
import dev.chojo.ember.feature.generator.entity.TextAlign;
import dev.chojo.ember.feature.generator.repository.DocumentFontRepository;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.PdfTemplateRepository;
import dev.chojo.ember.feature.generator.repository.TemplateStationUseRepository;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateResponse;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateSummary;
import dev.chojo.ember.feature.generator.service.TemplateStationUseService.TemplateUseRequest;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService;
import dev.chojo.ember.feature.generator.service.font.TestFonts;
import dev.chojo.ember.feature.generator.service.pdf.TestPdfs;
import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.image;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.row;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.rowsOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Copying templates: everything a template says goes into the copy, a PDF template's PDF as a stored
 * file of its own, nothing of what the template did; a station copies its own and its association's
 * templates, an association its own. And when each template was last used, per station.
 */
class DocumentTemplateCopyServiceTest extends RepositoryTestBase {
    private static final String PICTURE = "c".repeat(64);
    private static final FieldRect NAME = new FieldRect(1, 100, 700, 250, 20);
    private static final RestrictionAudience TRIALS = new RestrictionAudience(
            List.of(StationUserType.TRIAL), List.of(), List.of(), List.of(), RestrictionMode.AND);

    private static DocumentTemplateService templates;
    private static DocumentTemplateCopyService copies;
    private static TemplateStationUseService stationUses;
    private static PdfTemplateService pdfs;
    private static DocumentGenerationRepository log;
    private static StorageService storage;
    private static DocumentFontService fontService;

    private static Owner.Association association;
    private static Owner.Station north;
    private static Owner.Station south;
    private static Owner.Station alone;
    private static int author;

    @BeforeAll
    static void setup() {
        Cluster cluster = clusterService.create("Kopien Verband", null);
        association = new Owner.Association(cluster.id());
        north = new Owner.Station(
                stationInAssociation(cluster, "Kopien Wache Nord").id());
        south = new Owner.Station(
                stationInAssociation(cluster, "Kopien Wache Süd").id());
        alone = new Owner.Station(stationRepo.create("Kopien Einzelwache").id());
        author = Objects.requireNonNull(stationMemberRepo
                .create(
                        north.stationId(),
                        accountRepo
                                .create("copy-author@test.com", "Nora", "Fülling")
                                .id())
                .accountId());

        var backend = localStorage();
        storage = new StorageService(new StorageBackendResolver(backend), backend);
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
        var templateRepository = new DocumentTemplateRepository();
        var pdfTemplates = new PdfTemplateRepository();
        var uses = new TemplateStationUseRepository();
        var catalogue = newPlaceholderCatalogue();
        var fonts = newFontLibrary(storage);
        var letters = new LetterChecks(contentBlocks(), media);
        var issuers = new DocumentIssuerService(stationMemberRepo, uses);
        var checks = new TemplateChecks(
                templateRepository, pdfTemplates, letters, stationRepo, catalogue, fonts, newOwnerStores(), issuers);
        templates = new DocumentTemplateService(
                templateRepository, pdfTemplates, uses, checks, restrictionService, catalogue, newOwnerStores());
        stationUses = new TemplateStationUseService(templates, uses, restrictionService, issuers);
        pdfs = new PdfTemplateService(
                templates, templateRepository, pdfTemplates, newDocumentIntake(), storage, newOwnerStores());
        copies = new DocumentTemplateCopyService(
                templates, pdfs, stationUses, checks, letters, fonts, newOwnerStores(), issuers);
        log = new DocumentGenerationRepository();
        var quota = new StorageQuotaService(storageUsageRepo, new Storage(), new DomainEventBus(Set.of()));
        fontService = new DocumentFontService(new DocumentFontRepository(), fonts, storage, quota);
    }

    private static Station stationInAssociation(Cluster cluster, String name) {
        var station = stationRepo.create(name);
        stationRepo.setCluster(station.id(), cluster.id());
        return station;
    }

    private static void refused(Refusal refusal, Executable action) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, action).refusal());
    }

    private static DocumentTemplateResponse copy(Owner owner, int templateId, String name) {
        return copies.duplicate(owner, templateId, name, author).template();
    }

    private static DocumentTemplateSummary listed(Owner owner, int templateId) {
        return templates.list(owner, false).stream()
                .filter(template -> template.id() == templateId)
                .findFirst()
                .orElseThrow();
    }

    private static void used(Owner.Station station, int templateId, Instant at) {
        log.log(
                new DocumentGeneration(
                        0,
                        station.stationId(),
                        templateId,
                        1,
                        null,
                        null,
                        at,
                        false,
                        null,
                        "a".repeat(64),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false),
                List.of());
    }

    private static PdfField text(String text) {
        return new PdfField(PdfFieldKind.TEXT, NAME, text, 11, TextAlign.LEFT, false, null);
    }

    private static DocumentTemplateResponse pdfTemplate(Owner.Station owner, String name) throws IOException {
        var created = templates.create(owner, TemplateRequestBuilder.pdf(name).build(), author);
        pdfs.upload(owner, created.id(), TestUploads.of("form.pdf", "application/pdf", TestPdfs.withForm()), author);
        return templates.update(
                owner,
                created.id(),
                TemplateRequestBuilder.pdf(name)
                        .legal()
                        .fields(List.of(text("{{member.firstName}}")))
                        .formBindings(List.of(new FormBinding("person.name", "{{member.fullName}}")))
                        .build(),
                author);
    }

    @Test
    void aLetterIsCopiedWithEverythingItSaysAndStartsAtVersionOne() {
        var request = letter("Bescheinigung")
                .title("Bescheinigung {{member.lastName}}")
                .fileName("bescheinigung-{{member.lastName}}")
                .tags(List.of("Ausbildung", "Nachweis"))
                .hidden(true)
                .keepOnArchive(true)
                .legal()
                .selfService(true)
                .cooldown(14)
                .audience(TRIALS)
                .language(DocumentLanguage.EN)
                .header(rowsOf("Kopfzeile"))
                .footer(rowsOf("Fußzeile"))
                .body(rowsOf("Hiermit wird bescheinigt", "{{member.fullName}}"))
                .build();
        var source = templates.create(north, request, author);
        source = templates.update(north, source.id(), request, author);

        var copied = copies.duplicate(north, source.id(), "Kopie von Bescheinigung", author);
        var copy = copied.template();

        assertNotEquals(source.id(), copy.id());
        assertEquals("Kopie von Bescheinigung", copy.name());
        assertEquals(2, source.version());
        assertEquals(1, copy.version());
        assertNull(copy.archivedAt());
        assertEquals(source.kind(), copy.kind());
        assertEquals(source.titlePattern(), copy.titlePattern());
        assertEquals(source.fileNamePattern(), copy.fileNamePattern());
        assertEquals(source.tags(), copy.tags());
        assertEquals(source.hidden(), copy.hidden());
        assertEquals(source.keepOnArchive(), copy.keepOnArchive());
        assertEquals(source.legal(), copy.legal());
        assertEquals(source.forAppointments(), copy.forAppointments());
        assertEquals(source.selfService(), copy.selfService());
        assertEquals(14, copy.cooldownDays());
        assertEquals(TRIALS.userTypes(), copy.audience().userTypes());
        assertEquals(DocumentLanguage.EN, copy.language());
        assertEquals(source.header(), copy.header());
        assertEquals(source.footer(), copy.footer());
        assertEquals(source.body(), copy.body());
        assertEquals(source.page(), copy.page());
        assertTrue(copied.fontsOutOfReach().isEmpty());
        assertEquals(0, copied.picturesOutOfReach());
        assertEquals("Bescheinigung", templates.detail(north, source.id()).name());
    }

    @Test
    void aCopyTakesTheFirstFreeCountAfterItsName() {
        int source = templates
                .create(north, letter("Ausweis", "Text").build(), author)
                .id();

        assertEquals(
                "Kopie von Ausweis", copy(north, source, "Kopie von Ausweis").name());
        assertEquals(
                "Kopie von Ausweis (2)",
                copy(north, source, " Kopie von Ausweis ").name());
        assertEquals(
                "kopie von ausweis (3)",
                copy(north, source, "kopie von ausweis").name());
        assertEquals(
                "Kopie von Ausweis",
                copy(
                                south,
                                templates
                                        .create(south, letter("Ausweis").build(), author)
                                        .id(),
                                "Kopie von Ausweis")
                        .name());

        var longName =
                copy(north, source, "x".repeat(TemplateChecks.MAX_NAME + 10)).name();
        assertEquals(TemplateChecks.MAX_NAME, longName.length());
        var longAgain =
                copy(north, source, "x".repeat(TemplateChecks.MAX_NAME + 10)).name();
        assertEquals(TemplateChecks.MAX_NAME, longAgain.length());
        assertTrue(longAgain.endsWith(" (2)"));

        refused(DocumentRefusal.DOCUMENT_TEMPLATE_NAME_MISSING, () -> copy(north, source, " "));
        refused(DocumentRefusal.DOCUMENT_TEMPLATE_NAME_MISSING, () -> copies.duplicate(north, source, null, author));
    }

    @Test
    void aPdfTemplateIsCopiedWithAStoredPdfOfItsOwn() throws IOException {
        var source = pdfTemplate(north, "Formular");
        var original = Objects.requireNonNull(source.pdf());

        var copy = copy(north, source.id(), "Kopie von Formular");

        var copied = Objects.requireNonNull(copy.pdf());
        assertNotEquals(original.id(), copied.id());
        assertEquals(copy.id(), copied.templateId());
        assertEquals(original.sha256(), copied.sha256());
        assertEquals(original.fileName(), copied.fileName());
        assertEquals(original.inspection(), copied.inspection());
        assertEquals(source.fields(), copy.fields());
        assertEquals(source.formBindings(), copy.formBindings());
        assertTrue(copy.legal());
        assertEquals(1, copy.version());

        var sourceBytes = pdfs.current(north, source.id()).orElseThrow().data();
        storage.delete(stationScope(north), StorageCategory.DOCUMENT_TEMPLATES, original.id() + "/original");
        assertTrue(pdfs.current(north, source.id()).isEmpty());
        assertArrayEquals(
                sourceBytes, pdfs.current(north, copy.id()).orElseThrow().data());
    }

    @Test
    void aPdfTemplateWhosePdfIsGoneIsNotCopied() throws IOException {
        var source = pdfTemplate(north, "Verlorenes Formular");
        var original = Objects.requireNonNull(source.pdf());
        storage.delete(stationScope(north), StorageCategory.DOCUMENT_TEMPLATES, original.id() + "/original");

        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_COPY_PDF_GONE,
                () -> copy(north, source.id(), "Kopie von Verlorenes Formular"));
        assertTrue(templates.list(north, false).stream()
                .noneMatch(template -> template.name().equals("Kopie von Verlorenes Formular")));
    }

    @Test
    void aPdfTemplateWithoutAPdfIsCopiedWithoutOne() {
        var source = templates.create(
                north, TemplateRequestBuilder.pdf("Leeres Formular").build(), author);

        var copy = copy(north, source.id(), "Kopie von Leeres Formular");

        assertNull(copy.pdf());
        assertTrue(copy.fields().isEmpty());
    }

    /**
     * A copy keeps the issuer where it stays at the station that named them: the station's own template,
     * or the association's with the issuer the station chose for it. A station that named nobody, and an
     * association, which names nobody, copy without one.
     */
    @Test
    void aCopyKeepsTheIssuerTheStationNamed() {
        int warden = stationMemberRepo
                .create(
                        north.stationId(),
                        accountRepo
                                .create("copy-warden@test.com", "Erika", "Wehr")
                                .id())
                .id();
        var own = templates.create(
                north,
                letter("Eigene Urkunde", "{{issuer.fullName}}")
                        .issuer(warden, "Jugendwartin")
                        .build(),
                author);
        var ofAssociation = templates.create(
                association, letter("Verbandsurkunde", "{{issuer.fullName}}").build(), author);
        stationUses.setUse(
                north.stationId(), ofAssociation.id(), new TemplateUseRequest(false, null, warden, "Jugendwart"));

        var ownCopy = copy(north, own.id(), "Kopie von Eigene Urkunde");
        var northCopy = copy(north, ofAssociation.id(), "Kopie von Verbandsurkunde");
        var southCopy = copy(south, ofAssociation.id(), "Kopie von Verbandsurkunde");
        var associationCopy = copy(association, ofAssociation.id(), "Kopie von Verbandsurkunde");

        assertEquals(warden, ownCopy.issuerId());
        assertEquals("Jugendwartin", ownCopy.issuerFunction());
        assertEquals(warden, northCopy.issuerId());
        assertEquals("Jugendwart", northCopy.issuerFunction());
        assertNull(southCopy.issuerId());
        assertNull(associationCopy.issuerId());
    }

    @Test
    void aStationCopiesATemplateOfItsAssociationIntoOneOfItsOwn() {
        var source = templates.create(
                association,
                letter("Verbandsbrief")
                        .header(List.of(row(image(PICTURE))))
                        .body(rowsOf("Für {{member.fullName}}"))
                        .selfService(true)
                        .cooldown(7)
                        .build(),
                author);
        stationUses.setUse(north.stationId(), source.id(), new TemplateUseRequest(true, TRIALS, null, null));

        var copied = copies.duplicate(north, source.id(), "Kopie von Verbandsbrief", author);
        var copy = copied.template();

        assertEquals(copy.id(), templates.requireOwned(north, copy.id()).id());
        assertFalse(listed(north, copy.id()).ofAssociation());
        assertTrue(copy.selfService());
        assertEquals(7, copy.cooldownDays());
        assertEquals(TRIALS.userTypes(), copy.audience().userTypes());
        assertEquals(source.header(), copy.header());
        assertEquals(1, copied.picturesOutOfReach());
        var changed =
                templates.update(north, copy.id(), letter("Unser Brief", "Neu").build(), author);
        assertEquals("Unser Brief", changed.name());
        assertEquals("Verbandsbrief", templates.detail(association, source.id()).name());
        refused(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE, () -> templates.requireOwned(association, copy.id()));

        var notChosen = copy(south, source.id(), "Kopie von Verbandsbrief");
        assertFalse(notChosen.selfService());
        assertTrue(notChosen.audience().namesNobody());
    }

    @Test
    void aFontTheOwnerNoLongerReachesIsNamedAndTheCopyIsStillMade() {
        var uploaded = fontService.upload(
                north,
                TestUploads.of("n.ttf", "font/ttf", TestFonts.lisu()),
                "Nordschrift",
                FontStyle.REGULAR,
                true,
                author);
        int source = templates
                .create(
                        north,
                        letter("In Nordschrift", "Text")
                                .page(new LetterPage(40, 30, 20, 20, 10, "Nordschrift", null, null))
                                .build(),
                        author)
                .id();
        templates.setArchived(north, source, true, author);
        fontService.delete(north, uploaded.own().getFirst().id(), author);

        var copied = copies.duplicate(north, source, "Kopie von In Nordschrift", author);

        assertEquals(List.of("Nordschrift"), copied.fontsOutOfReach());
        assertEquals("Nordschrift", copied.template().page().bodyFont());
    }

    @Test
    void anAssociationCopiesItsOwnTemplates() {
        var source =
                templates.create(association, letter("Rundschreiben", "Text").build(), author);

        var copy = copies.duplicate(association, source.id(), "Kopie von Rundschreiben", author);

        assertEquals(
                copy.template().id(),
                templates.requireOwned(association, copy.template().id()).id());
        assertEquals(0, copy.picturesOutOfReach());
    }

    @Test
    void onlyTemplatesTheOwnerSeesAreCopied() {
        int northOwn = templates
                .create(north, letter("Nur Nord", "Text").build(), author)
                .id();
        int ofAssociation = templates
                .create(association, letter("Vom Verband", "Text").build(), author)
                .id();
        int archivedOfAssociation = templates
                .create(association, letter("Alt vom Verband", "Text").build(), author)
                .id();
        templates.setArchived(association, archivedOfAssociation, true, author);
        int archivedOwn = templates
                .create(north, letter("Alt in Nord", "Text").build(), author)
                .id();
        templates.setArchived(north, archivedOwn, true, author);

        refused(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE, () -> copy(south, northOwn, "Kopie"));
        refused(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE, () -> copy(association, northOwn, "Kopie"));
        refused(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE, () -> copy(alone, ofAssociation, "Kopie"));
        refused(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE, () -> copy(north, archivedOfAssociation, "Kopie"));
        refused(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE, () -> copy(north, Integer.MAX_VALUE, "Kopie"));

        var fromArchive = copy(north, archivedOwn, "Kopie von Alt in Nord");
        assertNull(fromArchive.archivedAt());
    }

    @Test
    void theLastUseIsCountedPerStationAndNotCopied() {
        int shared = templates
                .create(association, letter("Gemeinsam genutzt", "Text").build(), author)
                .id();
        int own = templates
                .create(north, letter("Eigene Vorlage", "Text").build(), author)
                .id();
        int unused = templates
                .create(north, letter("Nie genutzt", "Text").build(), author)
                .id();
        var earlier = Instant.parse("2026-09-01T08:00:00Z");
        var later = Instant.parse("2026-09-20T08:00:00Z");
        used(north, shared, earlier);
        used(south, shared, later);
        used(north, own, earlier);
        used(north, own, later);

        assertEquals(earlier, listed(north, shared).lastUsedAt());
        assertEquals(later, listed(south, shared).lastUsedAt());
        assertEquals(later, listed(association, shared).lastUsedAt());
        assertEquals(later, listed(north, own).lastUsedAt());
        var never = listed(north, unused);
        assertNull(never.lastUsedAt());
        assertNotNull(never.createdAt());
        assertEquals(templates.requireOwned(north, unused).createdAt(), never.createdAt());

        var copy = copy(north, own, "Kopie von Eigene Vorlage");
        assertNull(listed(north, copy.id()).lastUsedAt());
    }

    private static StorageScope stationScope(Owner.Station station) {
        return new StorageScope.Station(station.stationId(), stationRepo.requireUid(station.stationId()));
    }
}
