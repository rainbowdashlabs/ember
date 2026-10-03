/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.FieldRect;
import dev.chojo.ember.feature.generator.entity.FontOrigin;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.MemberView;
import dev.chojo.ember.feature.generator.entity.PdfContent;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfFieldKind;
import dev.chojo.ember.feature.generator.entity.PdfLayout;
import dev.chojo.ember.feature.generator.entity.PdfOriginal;
import dev.chojo.ember.feature.generator.entity.TextAlign;
import dev.chojo.ember.feature.generator.repository.DocumentFontRepository;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.PdfTemplateRepository;
import dev.chojo.ember.feature.generator.repository.TemplateStationUseRepository;
import dev.chojo.ember.feature.generator.service.font.BundledFont;
import dev.chojo.ember.feature.generator.service.font.DefaultFont;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService.DocumentFontsResponse;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService.FontFamilyOption;
import dev.chojo.ember.feature.generator.service.font.FontLibrary;
import dev.chojo.ember.feature.generator.service.font.TestFonts;
import dev.chojo.ember.feature.generator.service.pdf.PdfFonts;
import dev.chojo.ember.feature.generator.service.pdf.PdfStamper;
import dev.chojo.ember.feature.generator.service.pdf.StampFonts;
import dev.chojo.ember.feature.generator.service.pdf.TestPdfs;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfPictures;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.UploadedFile;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fonts at the instance, an association and a station: how they add up for a template, which uploads
 * are refused, the room they take, and when one may be deleted.
 */
class DocumentFontServiceTest extends RepositoryTestBase {
    /** The built-in families by name, sorted. */
    private static final List<String> BUILT_IN = List.of(
            "DejaVu Sans Mono",
            "Liberation Mono",
            "Liberation Sans",
            "Liberation Serif",
            "Libertinus Serif",
            "New Computer Modern");

    private static DocumentFontService fonts;
    private static FontLibrary library;
    private static StorageService storage;
    private static StorageQuotaService quota;
    private static DocumentTemplateService templates;
    private static Cluster cluster;
    private static Owner.Station station;
    private static Owner.Station outsider;
    private static Owner.Association association;
    private static int accountId;
    private static int authorId;

    @BeforeAll
    static void setup() {
        var backend = localStorage();
        storage = new StorageService(new StorageBackendResolver(backend), backend);
        quota = new StorageQuotaService(storageUsageRepo, new Storage(), new DomainEventBus(Set.of()));
        library = newFontLibrary(storage);
        fonts = new DocumentFontService(new DocumentFontRepository(), library, storage, quota);

        cluster = clusterService.create("Schriften Verband", null);
        association = new Owner.Association(cluster.id());
        var member = stationRepo.create("Schriften Wache");
        stationRepo.setCluster(member.id(), cluster.id());
        station = new Owner.Station(member.id());
        outsider = new Owner.Station(stationRepo.create("Schriften Einzelwache").id());
        accountId = accountRepo.create("fonts@test.com", "Fiona", "Font").id();
        stationMemberRepo.create(member.id(), accountId);
        authorId = accountId;

        var catalogue = newPlaceholderCatalogue();
        var templateRepository = new DocumentTemplateRepository();
        var pdfTemplates = new PdfTemplateRepository();
        var checks = new TemplateChecks(
                templateRepository,
                pdfTemplates,
                new LetterChecks(contentBlocks(), mock(MediaLibraryService.class)),
                stationRepo,
                catalogue,
                library,
                newOwnerStores());
        templates = new DocumentTemplateService(
                templateRepository,
                pdfTemplates,
                new TemplateStationUseRepository(),
                checks,
                restrictionService,
                catalogue,
                newOwnerStores());
    }

    private static UploadedFile file(byte[] data) {
        return TestUploads.of("schrift.ttf", "font/ttf", data);
    }

    private static DocumentFontsResponse upload(Owner owner, String family, FontStyle style) {
        return fonts.upload(owner, file(TestFonts.lisu()), family, style, true, accountId);
    }

    private static void refused(Refusal refusal, Executable action) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, action).refusal());
    }

    private static FontFamilyOption reached(Owner owner, String family) {
        return fonts.list(owner).reachable().stream()
                .filter(option -> option.family().equalsIgnoreCase(family))
                .findFirst()
                .orElseThrow();
    }

    private static int fontId(Owner owner, String family, FontStyle style) {
        return fonts.list(owner).own().stream()
                .filter(font -> font.family().equals(family) && font.style() == style)
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static LetterPage bodyIn(@Nullable String family) {
        return new LetterPage(40, 30, 20, 20, 10, family, null, null);
    }

    private static int letterIn(Owner.Station owner, String name, String family) {
        return templates
                .create(owner, letter(name, "Text").page(bodyIn(family)).build(), authorId)
                .id();
    }

    /** A station sees the instance's, its association's and its own fonts; a station elsewhere only the instance's. */
    @Test
    void reachAddsUpAndTheNearestOwnerWins() {
        upload(new Owner.Instance(), "Leitschrift", FontStyle.REGULAR);
        upload(new Owner.Instance(), "Nur Instanz", FontStyle.REGULAR);
        upload(association, "leitschrift", FontStyle.BOLD);
        upload(association, "Nur Verband", FontStyle.REGULAR);

        var house = reached(station, "Leitschrift");
        assertEquals(FontOrigin.ASSOCIATION, house.origin());
        assertEquals(List.of(FontStyle.BOLD), house.styles());
        assertTrue(house.printsOnPdf());
        assertEquals(FontOrigin.INSTANCE, reached(station, "Nur Instanz").origin());
        assertEquals(FontOrigin.ASSOCIATION, reached(station, "Nur Verband").origin());
        assertEquals(FontOrigin.INSTANCE, reached(outsider, "Leitschrift").origin());
        assertTrue(fonts.list(outsider).reachable().stream()
                .noneMatch(option -> option.family().equals("Nur Verband")));
        assertEquals(FontOrigin.ASSOCIATION, reached(association, "Leitschrift").origin());
        assertEquals(
                FontOrigin.INSTANCE,
                reached(new Owner.Instance(), "Leitschrift").origin());

        upload(station, "LEITSCHRIFT", FontStyle.ITALIC);
        assertEquals(FontOrigin.STATION, reached(station, "Leitschrift").origin());
        assertEquals(List.of(FontStyle.ITALIC), reached(station, "Leitschrift").styles());
        assertTrue(fonts.list(station).own().stream()
                .allMatch(font -> font.family().equals("LEITSCHRIFT")));
    }

    @Test
    void aFontIsKeptWhereItsOwnerKeepsFiles() {
        int instanceFont = fontId(upload(new Owner.Instance(), "Ablage Instanz", FontStyle.REGULAR));
        int associationFont = fontId(upload(association, "Ablage Verband", FontStyle.REGULAR));
        int stationFont = fontId(upload(station, "Ablage Wache", FontStyle.REGULAR));

        assertTrue(storage.exists(
                new StorageScope.Instance(), StorageCategory.INSTANCE_FONTS, String.valueOf(instanceFont)));
        var home =
                new StorageScope.Association(cluster.homeStationId(), stationRepo.requireUid(cluster.homeStationId()));
        assertEquals(home, library.scopeOf(association));
        assertTrue(storage.exists(home, StorageCategory.ASSOCIATION_FONTS, String.valueOf(associationFont)));
        assertTrue(storage.exists(library.scopeOf(station), StorageCategory.FONTS, String.valueOf(stationFont)));
    }

    private static int fontId(DocumentFontsResponse response) {
        return response.own().getLast().id();
    }

    @Test
    void anUploadIsRefusedWithAPlainReason() {
        refused(
                DocumentRefusal.DOCUMENT_FONT_LICENCE_NOT_CONFIRMED,
                () -> fonts.upload(
                        station, file(TestFonts.lisu()), "Ohne Zusage", FontStyle.REGULAR, false, accountId));
        refused(DocumentRefusal.DOCUMENT_FONT_FAMILY_INVALID, () -> upload(station, " ", FontStyle.REGULAR));
        refused(DocumentRefusal.DOCUMENT_FONT_FAMILY_INVALID, () -> upload(station, "x".repeat(61), FontStyle.REGULAR));
        refused(
                DocumentRefusal.DOCUMENT_FONT_MISSING_FILE,
                () -> fonts.upload(station, null, "Ohne Datei", FontStyle.REGULAR, true, accountId));
        refused(
                DocumentRefusal.DOCUMENT_FONT_TOO_LARGE,
                () -> fonts.upload(
                        station,
                        TestUploads.unreadable("riesig.ttf", 11L * 1024 * 1024),
                        "Riesig",
                        FontStyle.REGULAR,
                        true,
                        accountId));
        refused(
                DocumentRefusal.DOCUMENT_FONT_NOT_A_FONT,
                () -> fonts.upload(
                        station,
                        TestUploads.unreadable("kaputt.ttf", 100),
                        "Kaputt",
                        FontStyle.REGULAR,
                        true,
                        accountId));
        refused(
                DocumentRefusal.DOCUMENT_FONT_NOT_A_FONT,
                () -> fonts.upload(
                        station, file("kein Font".getBytes()), "Kein Font", FontStyle.REGULAR, true, accountId));
        refused(
                DocumentRefusal.DOCUMENT_FONT_EMBEDDING_FORBIDDEN,
                () -> fonts.upload(
                        station,
                        file(TestFonts.withFsType(TestFonts.lisu(), 0x0002)),
                        "Gesperrt",
                        FontStyle.REGULAR,
                        true,
                        accountId));
        assertTrue(fonts.list(station).own().stream()
                .noneMatch(font -> font.family().equals("Gesperrt")));
    }

    /** A family has one file per style at one owner; another owner, or another style, is free. */
    @Test
    void aStyleOfAFamilyIsUploadedOnceAtAnOwner() {
        upload(station, "Doppelt", FontStyle.REGULAR);
        refused(DocumentRefusal.DOCUMENT_FONT_TAKEN, () -> upload(station, "doppelt", FontStyle.REGULAR));
        upload(station, "Doppelt", FontStyle.BOLD_ITALIC);
        upload(association, "Doppelt", FontStyle.REGULAR);
        var otf = fonts.upload(station, file(TestFonts.lycian()), "Lykisch", FontStyle.REGULAR, true, accountId);
        assertFalse(otf.reachable().stream()
                .filter(option -> option.family().equals("Lykisch"))
                .findFirst()
                .orElseThrow()
                .printsOnPdf());
    }

    @Test
    void aStationFontTakesRoomInTheStation() {
        var full = new Owner.Station(stationRepo.create("Volle Wache").id());
        long before = quota.getTotalUsedBytes(full.stationId());
        upload(full, "Erste", FontStyle.REGULAR);
        assertEquals(before + TestFonts.lisu().length, quota.getTotalUsedBytes(full.stationId()));

        quota.updateStationQuotas(
                full.stationId(), before + TestFonts.lisu().length + 10, null, null, null, null, null, null);
        refused(DocumentRefusal.DOCUMENT_FONT_NO_ROOM, () -> upload(full, "Zweite", FontStyle.REGULAR));
        assertTrue(
                fonts.list(full).own().stream().noneMatch(font -> font.family().equals("Zweite")));

        fonts.delete(full, fontId(full, "Erste", FontStyle.REGULAR), accountId);
        assertEquals(before, quota.getTotalUsedBytes(full.stationId()));
    }

    /** An association's fonts count against the room of its home station, which is the room it was given. */
    @Test
    void anAssociationFontTakesRoomInItsHomeStation() {
        var other = clusterService.create("Voller Verband", null);
        var owner = new Owner.Association(other.id());
        int home = other.homeStationId();
        upload(owner, "Verbandsschrift", FontStyle.REGULAR);
        assertEquals(
                TestFonts.lisu().length,
                quota.getUsage(home).stream()
                        .filter(usage -> usage.category() == StorageCategory.ASSOCIATION_FONTS)
                        .findFirst()
                        .orElseThrow()
                        .totalBytes());

        quota.trackDelta(home, StorageCategory.MEMBER_DOCUMENTS, quota.getEffectiveTotalQuota(home), 1);
        refused(DocumentRefusal.DOCUMENT_FONT_NO_ROOM, () -> upload(owner, "Noch eine", FontStyle.REGULAR));
    }

    @Test
    void aTemplateNamesOnlyAFamilyItReaches() {
        upload(association, "Erreichbar", FontStyle.REGULAR);
        letterIn(station, "Erreichbarer Brief", "erreichbar");
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_FONT_UNKNOWN,
                () -> letterIn(outsider, "Fremder Brief", "Erreichbar"));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_FONT_UNKNOWN,
                () -> letterIn(station, "Leerer Brief", "Gibt es nicht"));
        var plain = templates.create(
                station, letter("Standardbrief", "Text").page(bodyIn(" ")).build(), authorId);
        assertNull(plain.page().bodyFont());
        assertEquals(
                "erreichbar",
                templates
                        .detail(station, letterIn(station, "Zweiter Brief", " erreichbar "))
                        .page()
                        .bodyFont());
    }

    /** Words a text sets in a family of their own are held to the same reach as the families of the page. */
    @Test
    void aTextSetsWordsOnlyInAFamilyItReaches() {
        upload(association, "Wortschrift", FontStyle.REGULAR);
        templates.create(
                station, letter("Wortbrief", inFamily("wortschrift", "Wort")).build(), authorId);
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_FONT_UNKNOWN,
                () -> templates.create(
                        outsider,
                        letter("Fremder Wortbrief", inFamily("Wortschrift", "Wort"))
                                .build(),
                        authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_FONT_UNKNOWN,
                () -> templates.create(
                        station,
                        letter("Leerer Wortbrief", inFamily("Gibt es nicht", "Wort"))
                                .build(),
                        authorId));
    }

    private static String inFamily(String family, String words) {
        return "Ein <span data-font=\"" + family + "\">" + words + "</span> im Brief.";
    }

    /** A field on an uploaded PDF embeds TrueType outlines only, so a family of PostScript outlines is refused there. */
    @Test
    void aPdfFieldNamesOnlyAFamilyItCanEmbed() {
        fonts.upload(station, file(TestFonts.lycian()), "Nur Brief", FontStyle.REGULAR, true, accountId);
        upload(station, "Auch Formular", FontStyle.REGULAR);
        var rect = new FieldRect(1, 10, 10, 100, 20);
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_FONT_UNKNOWN,
                () -> library.requireReachable(
                        station,
                        pdfWith(new PdfField(
                                PdfFieldKind.TEXT,
                                rect,
                                "x",
                                10,
                                TextAlign.LEFT,
                                false,
                                null,
                                "Nur Brief",
                                FontStyle.REGULAR))));
        library.requireReachable(
                station,
                pdfWith(new PdfField(
                        PdfFieldKind.TEXT,
                        rect,
                        "x",
                        10,
                        TextAlign.LEFT,
                        false,
                        null,
                        "Auch Formular",
                        FontStyle.BOLD)));
        library.requireReachable(
                station, pdfWith(new PdfField(PdfFieldKind.TEXT, rect, "x", 10, TextAlign.LEFT, false, null)));
    }

    private static PdfContent pdfWith(PdfField field) {
        return new PdfContent(null, new PdfLayout(List.of(field), List.of()));
    }

    private static PdfField fieldIn(String family, FontStyle style) {
        return new PdfField(
                PdfFieldKind.TEXT,
                new FieldRect(1, 100, 700, 300, 20),
                "Lena Muster",
                10,
                TextAlign.LEFT,
                false,
                null,
                family,
                style);
    }

    /**
     * Every owner reaches the built-in families without uploading anything; they are nobody's own, and
     * only those that ship as a file are offered to fields on an uploaded PDF.
     */
    @Test
    void everyOwnerReachesTheBuiltInFamilies() {
        for (var owner : List.<Owner>of(outsider, association, new Owner.Instance())) {
            var builtIn = fonts.list(owner).reachable().stream()
                    .filter(option -> option.origin() == FontOrigin.BUILT_IN)
                    .map(FontFamilyOption::family)
                    .toList();
            assertEquals(BUILT_IN, builtIn.stream().sorted().toList());
            assertTrue(fonts.list(owner).own().stream().noneMatch(font -> BUILT_IN.contains(font.family())));
        }
        assertTrue(reached(outsider, "Liberation Serif").printsOnPdf());
        assertEquals(
                FontStyle.values().length,
                reached(outsider, "Liberation Mono").styles().size());
        assertFalse(reached(outsider, "Libertinus Serif").printsOnPdf());
        assertFalse(reached(outsider, "DejaVu Sans Mono").printsOnPdf());

        letterIn(outsider, "Brief in Libertinus", "libertinus serif");
        library.requireReachable(outsider, pdfWith(fieldIn("Liberation Mono", FontStyle.BOLD)));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_FONT_UNKNOWN,
                () -> library.requireReachable(outsider, pdfWith(fieldIn("New Computer Modern", FontStyle.REGULAR))));
    }

    /** A letter prints in each built-in family: the bundled ones from their files, the rest from Typst itself. */
    @Test
    void aLetterPrintsInTheBuiltInFamilies() throws IOException {
        var page = new LetterPage(40, 30, 20, 20, 10, "Liberation Serif", "Libertinus Serif", "DejaVu Sans Mono");
        var letter = new LetterContent(
                List.of(row("Kopf")),
                List.of(row("Fuß")),
                List.of(
                        row("Brief"),
                        row(inFamily("Liberation Mono", "Nummer 12345")),
                        row(inFamily("New Computer Modern", "Wort"))),
                page);
        byte[] pdf = renderer().render(job(letter));

        var names = PdfFonts.namesIn(pdf);
        for (var embedded :
                List.of("LiberationSerif", "LibertinusSerif", "DejaVuSansMono", "LiberationMono", "NewCM")) {
            assertTrue(names.stream().anyMatch(name -> name.contains(embedded)), embedded + " in " + names);
        }
        assertTrue(PdfFonts.isPdfA(pdf));
    }

    /** A field on an uploaded PDF draws in a bundled built-in family, in the style it asks for. */
    @Test
    void aPdfFieldPrintsInABuiltInFamily() throws IOException {
        var pdfs = mock(PdfTemplateService.class);
        when(pdfs.read(any(), any())).thenReturn(Optional.of(TestPdfs.plain(1)));
        var renderer = new PdfTemplateRenderer(pdfs, new PdfStamper(new StampFonts(DefaultFont.absent())), library);
        var content = new PdfContent(
                mock(PdfOriginal.class), new PdfLayout(List.of(fieldIn("Liberation Mono", FontStyle.BOLD)), List.of()));

        var stamped = renderer.render(outsider, content, 0, UnaryOperator.identity());

        assertTrue(stamped.unprintable().isEmpty(), stamped.unprintable()::toString);
        var names = PdfFonts.namesIn(stamped.pdf());
        assertTrue(names.stream().anyMatch(name -> name.endsWith("+LiberationMono-Bold")), names::toString);
    }

    /** A font a template in use prints with stays; once the template is archived, it may go. */
    @Test
    void aFontInUseIsNotDeleted() {
        upload(station, "Belegt", FontStyle.REGULAR);
        upload(station, "Belegt", FontStyle.BOLD);
        int template = letterIn(station, "Belegter Brief", "Belegt");
        int bold = fontId(station, "Belegt", FontStyle.BOLD);

        var refusal = assertThrows(RefusalResponse.class, () -> fonts.delete(station, bold, accountId));
        assertEquals(DocumentRefusal.DOCUMENT_FONT_IN_USE, refusal.refusal());
        assertEquals("Belegter Brief", Objects.requireNonNull(refusal.detail()).inEnglish());

        templates.setArchived(station, template, true, authorId);
        fonts.delete(station, bold, accountId);
        assertTrue(fonts.list(station).own().stream().noneMatch(font -> font.id() == bold));
        assertTrue(storage.readAllBytes(library.scopeOf(station), StorageCategory.FONTS, String.valueOf(bold))
                .isEmpty());
    }

    /**
     * An association's font is in use by a member station's template only while that station finds the
     * association's family under the name; once the station has a family of its own by that name, the
     * association's is free.
     */
    @Test
    void anAssociationFontIsInUseOnlyWhereItIsTheOneFound() {
        upload(association, "Geteilt", FontStyle.REGULAR);
        letterIn(station, "Geteilter Brief", "Geteilt");
        int shared = fontId(association, "Geteilt", FontStyle.REGULAR);
        refused(DocumentRefusal.DOCUMENT_FONT_IN_USE, () -> fonts.delete(association, shared, accountId));

        upload(station, "Geteilt", FontStyle.REGULAR);
        fonts.delete(association, shared, accountId);
        refused(DocumentRefusal.DOCUMENT_FONT_NOT_HERE, () -> fonts.delete(association, shared, accountId));
        refused(
                DocumentRefusal.DOCUMENT_FONT_NOT_HERE,
                () -> fonts.delete(outsider, fontId(station, "Geteilt", FontStyle.REGULAR), accountId));
    }

    /**
     * Typst is handed the family's files and the bundled fallback, embeds them in a PDF/A-3b, and prints
     * the characters the family lacks in Liberation Sans.
     */
    @Test
    void aLetterPrintsInTheFamiliesItNames() throws IOException {
        upload(station, "Lisu", FontStyle.REGULAR);
        var page = new LetterPage(40, 30, 20, 20, 10, "lisu", "Lisu", "Gibt es nicht mehr");
        var letter = new LetterContent(
                List.of(row(TestFonts.LISU_TEXT)),
                List.of(row("Fuß")),
                List.of(row(TestFonts.LISU_TEXT + " Brief")),
                page);
        byte[] pdf = renderer().render(job(letter));

        var names = PdfFonts.namesIn(pdf);
        assertTrue(names.stream().anyMatch(name -> name.endsWith(TestFonts.LISU_POSTSCRIPT)), names::toString);
        assertTrue(names.stream().anyMatch(name -> name.contains("LiberationSans")), names::toString);
        assertTrue(PdfFonts.isPdfA(pdf));
    }

    /** Words set in a family of their own print in it, bold among them, while the rest keeps the default font. */
    @Test
    void wordsPrintInTheFamilyTheyAreSetIn() throws IOException {
        upload(station, "Lisu Worte", FontStyle.REGULAR);
        var letter = new LetterContent(
                List.of(),
                List.of(),
                List.of(row(inFamily("lisu worte", "**" + TestFonts.LISU_TEXT + "**"))),
                LetterPage.defaults());

        var names = PdfFonts.namesIn(renderer().render(job(letter)));
        assertTrue(names.stream().anyMatch(name -> name.endsWith(TestFonts.LISU_POSTSCRIPT)), names::toString);
        assertTrue(names.stream().anyMatch(name -> name.contains("LiberationSans")), names::toString);
    }

    /**
     * Words set in a family the letter no longer reaches print in the font around them, the template's
     * body font here, rather than failing the letter.
     */
    @Test
    void wordsInAFamilyOutOfReachPrintInTheFontAroundThem() throws IOException {
        upload(station, "Lisu Umgebung", FontStyle.REGULAR);
        var letter = new LetterContent(
                List.of(),
                List.of(),
                List.of(row(inFamily("Längst gelöscht", TestFonts.LISU_TEXT))),
                bodyIn("Lisu Umgebung"));

        var names = PdfFonts.namesIn(renderer().render(job(letter)));
        assertTrue(names.stream().anyMatch(name -> name.endsWith(TestFonts.LISU_POSTSCRIPT)), names::toString);

        var plain = new LetterContent(
                List.of(), List.of(), List.of(row(inFamily("Längst gelöscht", "Wort"))), LetterPage.defaults());
        var defaults = PdfFonts.namesIn(renderer().render(job(plain)));
        assertTrue(defaults.stream().noneMatch(name -> name.contains("Noto")), defaults::toString);
    }

    @Test
    void aLetterInTheDefaultFontEmbedsNoUploadedFont() throws IOException {
        var letter = new LetterContent(List.of(), List.of(), List.of(row("Nur Text")), LetterPage.defaults());
        var names = PdfFonts.namesIn(renderer().render(job(letter)));
        assertTrue(names.stream().noneMatch(name -> name.contains("Noto")), names::toString);
        assertTrue(names.stream().anyMatch(name -> name.contains("LiberationSans")), names::toString);
    }

    /**
     * A letter naming no font prints in the default font from its directory, still a PDF/A-3b, and its
     * italic text goes on to the fonts behind it rather than failing.
     */
    @Test
    void aLetterNamingNoFontPrintsInTheDefaultFont(@TempDir Path directory) throws IOException {
        var withDefault = newFontLibrary(storage, TestFonts.defaultFontIn(directory));
        var letter = new LetterContent(
                List.of(row("Kopf")),
                List.of(row("_" + TestFonts.LISU_TEXT + "_")),
                List.of(row(TestFonts.LISU_TEXT + " Brief")),
                LetterPage.defaults());
        byte[] pdf = renderer(withDefault).render(job(letter));

        var names = PdfFonts.namesIn(pdf);
        assertTrue(names.stream().anyMatch(name -> name.endsWith(TestFonts.LISU_POSTSCRIPT)), names::toString);
        assertTrue(PdfFonts.isPdfA(pdf));
    }

    /** The screens name the default font: Liberation Sans without one, the family read from the file with one. */
    @Test
    void theListNamesTheDefaultFont(@TempDir Path directory) {
        assertEquals(BundledFont.FAMILY, fonts.list(station).defaultFamily());

        var withDefault = new DocumentFontService(
                new DocumentFontRepository(),
                newFontLibrary(storage, TestFonts.defaultFontIn(directory)),
                storage,
                quota);
        assertEquals(TestFonts.LISU_FAMILY, withDefault.list(station).defaultFamily());
    }

    private static LetterRenderer renderer() {
        return renderer(library);
    }

    private static LetterRenderer renderer(FontLibrary fonts) {
        var pictures = mock(KbPdfPictures.class);
        when(pictures.place(anyInt(), anyString(), anyString()))
                .thenAnswer(call -> new KbPdfPictures.Placed(call.getArgument(1), Map.of()));
        return new LetterRenderer(
                pictures, mock(MediaLibraryService.class), newStationLogoService(), fonts, newOwnerStores());
    }

    private static LetterRenderer.LetterJob job(LetterContent letter) {
        return new LetterRenderer.LetterJob(
                station,
                station.stationId(),
                "Brief",
                letter,
                null,
                MemberView.EVERYBODY,
                DocumentLanguage.DE,
                Map.of(),
                Map.of(),
                false,
                LocalDate.of(2026, 10, 3));
    }

    private static ContentRow row(String text) {
        return new ContentRow(
                0, 0, 0, List.of(new ContentCell(0, 0, 0, 100, CellContentType.MARKDOWN, text, CellConfig.EMPTY)));
    }

    @Test
    void anInstanceFontIsInUseByAnyStationThatFindsIt() {
        upload(new Owner.Instance(), "Überall", FontStyle.REGULAR);
        letterIn(outsider, "Brief überall", "Überall");
        int everywhere = fontId(new Owner.Instance(), "Überall", FontStyle.REGULAR);
        refused(DocumentRefusal.DOCUMENT_FONT_IN_USE, () -> fonts.delete(new Owner.Instance(), everywhere, accountId));
    }
}
