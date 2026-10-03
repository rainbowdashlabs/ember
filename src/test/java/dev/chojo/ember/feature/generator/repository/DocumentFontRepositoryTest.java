/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import dev.chojo.ember.feature.content.entity.ContentRows;
import dev.chojo.ember.feature.generator.entity.DocumentFont;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.FieldRect;
import dev.chojo.ember.feature.generator.entity.FontOrigin;
import dev.chojo.ember.feature.generator.entity.FontOutline;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.FontUse;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.PdfContent;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfFieldKind;
import dev.chojo.ember.feature.generator.entity.PdfLayout;
import dev.chojo.ember.feature.generator.entity.TemplateContent;
import dev.chojo.ember.feature.generator.entity.TextAlign;
import dev.chojo.ember.feature.generator.entity.WebFontFormat;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fonts by owner, the fonts several owners reach together, and the templates that name a family. */
class DocumentFontRepositoryTest extends RepositoryTestBase {
    private static final DocumentFontRepository fonts = new DocumentFontRepository();
    private static final DocumentTemplateRepository templates = new DocumentTemplateRepository();
    private static final PdfTemplateRepository pdfs = new PdfTemplateRepository();

    private static Owner.Station station;
    private static Owner.Station other;
    private static Owner.Association association;
    private static int accountId;
    private static int author;

    @BeforeAll
    static void setup() {
        station = new Owner.Station(stationRepo.create("Schriftablage Wache").id());
        other = new Owner.Station(stationRepo.create("Schriftablage Nachbar").id());
        association = new Owner.Association(
                clusterService.create("Schriftablage Verband", null).id());
        accountId = accountRepo.create("font-repo@test.com", "Rita", "Ablage").id();
        stationMemberRepo.create(station.stationId(), accountId);
        author = accountId;
    }

    private static DocumentFont insert(Owner owner, String family, FontStyle style) {
        return fonts.insert(
                owner,
                new DocumentFontRepository.NewFont(
                        family, style, family + ".ttf", FontOutline.TRUETYPE, family + " Intern", 12, "ab"),
                accountId);
    }

    private static int template(Owner owner, String name, TemplateContent content) {
        var draft = new DocumentTemplateDraft(
                name,
                "t",
                "f",
                List.of(),
                false,
                false,
                false,
                false,
                false,
                30,
                RestrictionMode.AND,
                DocumentLanguage.DE,
                null,
                null,
                content);
        int id = templates.create(owner, draft, author).id();
        switch (content) {
            case LetterContent letter -> templates.writeLetter(id, letter);
            case PdfContent pdf -> pdfs.writeLayout(id, pdf.layout());
        }
        return id;
    }

    private static LetterContent letterIn(String body, String header, String footer) {
        return new LetterContent(
                List.of(), List.of(), ContentRows.read("[]"), new LetterPage(40, 30, 20, 20, 10, body, header, footer));
    }

    @Test
    void aFontIsWrittenAndFoundByItsOwnerOnly() {
        var written = insert(station, "Ablage", FontStyle.BOLD);
        assertEquals(station, written.owner());
        assertEquals(FontOrigin.STATION, written.origin());
        assertEquals("Ablage Intern", written.internalFamily());
        assertEquals(12, written.sizeBytes());

        assertEquals(written, fonts.find(station, written.id()).orElseThrow());
        assertTrue(fonts.find(other, written.id()).isEmpty());
        assertTrue(fonts.find(new Owner.Instance(), written.id()).isEmpty());
        assertTrue(fonts.exists(station, "ABLAGE", FontStyle.BOLD));
        assertFalse(fonts.exists(station, "Ablage", FontStyle.ITALIC));
        assertFalse(fonts.exists(other, "Ablage", FontStyle.BOLD));
        assertTrue(fonts.findOwned(station).contains(written));

        assertTrue(fonts.delete(written.id()));
        assertFalse(fonts.delete(written.id()));
        assertTrue(fonts.findOwned(station).stream().noneMatch(font -> font.id() == written.id()));
    }

    /** A style gets a web version, keeps the next one in its place and loses it again, its file untouched. */
    @Test
    void aWebVersionIsSetReplacedAndCleared() {
        var font = insert(station, "Webablage", FontStyle.REGULAR);
        assertNull(font.web());

        var first = fonts.setWeb(
                font.id(), new DocumentFontRepository.NewWebFont("a.woff2", WebFontFormat.WOFF2, 30, "cd"));
        var web = first.web();
        assertNotNull(web);
        assertEquals("a.woff2", web.fileName());
        assertEquals(WebFontFormat.WOFF2, web.format());
        assertEquals(30, web.sizeBytes());
        assertEquals("cd", web.sha256());
        assertEquals(42, first.storedBytes());

        var second =
                fonts.setWeb(font.id(), new DocumentFontRepository.NewWebFont("b.woff", WebFontFormat.WOFF, 8, "ef"));
        assertEquals(WebFontFormat.WOFF, second.web().format());
        assertEquals(second, fonts.find(station, font.id()).orElseThrow());

        var cleared = fonts.clearWeb(font.id());
        assertNull(cleared.web());
        assertEquals(font.fileName(), cleared.fileName());
        assertEquals(12, cleared.storedBytes());
    }

    @Test
    void aStationReachesItsOwnItsAssociationsAndTheInstancesFonts() {
        var own = insert(station, "Eigen", FontStyle.REGULAR);
        var shared = insert(association, "Verbund", FontStyle.REGULAR);
        var everyone = insert(new Owner.Instance(), "Allgemein", FontStyle.REGULAR);
        var neighbour = insert(other, "Nachbar", FontStyle.REGULAR);

        var reached = fonts.findReachable(station.stationId(), association.clusterId());
        assertTrue(reached.containsAll(List.of(own, shared, everyone)));
        assertFalse(reached.contains(neighbour));
        assertEquals(FontOrigin.ASSOCIATION, shared.origin());
        assertEquals(FontOrigin.INSTANCE, everyone.origin());

        var instanceOnly = fonts.findReachable(null, null);
        assertTrue(instanceOnly.contains(everyone));
        assertFalse(instanceOnly.contains(own));
        assertTrue(fonts.findOwned(new Owner.Instance()).contains(everyone));
        assertTrue(fonts.findOwned(association).contains(shared));
    }

    @Test
    void theTemplatesInUseNamingAFamilyAreFound() {
        int body = template(station, "Rumpf", letterIn("Gesucht", null, null));
        int header = template(station, "Kopf", letterIn(null, "gesucht", null));
        int footer = template(other, "Fuss", letterIn(null, null, "GESUCHT"));
        int field = template(
                station,
                "Feld",
                new PdfContent(
                        null,
                        new PdfLayout(
                                List.of(new PdfField(
                                        PdfFieldKind.TEXT,
                                        new FieldRect(1, 1, 1, 10, 10),
                                        "x",
                                        10,
                                        TextAlign.LEFT,
                                        false,
                                        null,
                                        "Gesucht",
                                        FontStyle.BOLD)),
                                List.of())));
        int archived = template(station, "Archiv", letterIn("Gesucht", null, null));
        templates.setArchived(archived, true, author);
        template(station, "Anders", letterIn("Anders", null, null));
        int ofAssociation = template(association, "Verbandsbrief", letterIn("Gesucht", null, null));

        assertEquals(
                List.of(field, header, body).stream().sorted().toList(),
                idsOf(fonts.templatesNaming("gesucht", station)));
        var everywhere = fonts.templatesNaming("Gesucht", new Owner.Instance());
        assertEquals(5, everywhere.size());
        assertTrue(everywhere.contains(new FontUse(footer, other, "Fuss")));
        assertTrue(everywhere.contains(new FontUse(ofAssociation, association, "Verbandsbrief")));
        assertEquals(List.of(ofAssociation), idsOf(fonts.templatesNaming("Gesucht", association)));
    }

    private static List<Integer> idsOf(List<FontUse> uses) {
        return uses.stream().map(FontUse::templateId).sorted().toList();
    }
}
