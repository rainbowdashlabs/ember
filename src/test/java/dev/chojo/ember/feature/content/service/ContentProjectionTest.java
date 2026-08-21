/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.service;

import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class ContentProjectionTest {

    private static final Function<String, String> FILE_URL = hash -> "/media/" + hash;

    private static ContentCell cell(CellContentType type, String content, CellConfig config) {
        return new ContentCell(0, 0, 0, 100.0, type, content, config);
    }

    private static String project(ContentCell... cells) {
        return ContentProjection.toMarkdown(List.of(new ContentRow(0, 0, 0, List.of(cells))), FILE_URL);
    }

    @Test
    void markdownIsInlinedVerbatim() {
        assertEquals("# Hallo\n\nText", project(cell(CellContentType.MARKDOWN, "# Hallo\n\nText", CellConfig.EMPTY)));
    }

    @Test
    void anImageBecomesAnImageWithItsCaptionUnderIt() {
        var config = new CellConfig.ImageConfig(
                null, "Das Fahrzeug", null, "Bei der Übung", null, null, null, null, null, null, null);
        assertEquals(
                "![Das Fahrzeug](/media/abc)\n\nBei der Übung", project(cell(CellContentType.IMAGE, "abc", config)));
        assertEquals("", project(cell(CellContentType.IMAGE, "  ", config)), "an image cell with no file is dropped");
    }

    @Test
    void aGalleryBecomesOneImagePerItem() {
        var config = new CellConfig.ImageGalleryConfig(
                List.of(
                        new CellConfig.GalleryItem("one", "Erstes", "Untertitel"),
                        new CellConfig.GalleryItem("two", null, null),
                        new CellConfig.GalleryItem(null, "ohne Bild", null)),
                null,
                null,
                null);
        String out = project(cell(CellContentType.IMAGE_GALLERY, "", config));
        assertTrue(out.contains("![Erstes](/media/one)"));
        assertTrue(out.contains("Untertitel"));
        assertTrue(out.contains("![](/media/two)"));
        assertFalse(out.contains("ohne Bild"), "an item without an image has nothing to show");
    }

    @Test
    void aHeroBannerKeepsItsImageHeadlineAndCall() {
        var config = new CellConfig.HeroBannerConfig("hero", "Willkommen", "Bei uns", "Mehr", "https://example.org");
        String out = project(cell(CellContentType.HERO_BANNER, "", config));
        assertTrue(out.contains("![Willkommen](/media/hero)"));
        assertTrue(out.contains("## Willkommen"));
        assertTrue(out.contains("Bei uns"));
        assertTrue(out.contains("[Mehr](https://example.org)"));
    }

    @Test
    void aCalloutBecomesABlockquoteWithItsLeadInBold() {
        var titled = new CellConfig.CalloutConfig(CellConfig.CalloutVariant.WARNING, "Achtung");
        assertEquals(
                "> **Achtung**\n> \n> Nicht vergessen",
                project(cell(CellContentType.CALLOUT, "Nicht vergessen", titled)));

        var untitled = new CellConfig.CalloutConfig(CellConfig.CalloutVariant.INFO, null);
        assertEquals("> **INFO**\n> \n> Hinweis", project(cell(CellContentType.CALLOUT, "Hinweis", untitled)));
    }

    @Test
    void aQuoteKeepsItsAuthor() {
        var config = new CellConfig.QuoteConfig("Anna", null);
        assertEquals("> Gut gemacht\n> \n> - Anna", project(cell(CellContentType.QUOTE, "Gut gemacht", config)));
    }

    @Test
    void aCodeBlockIsFencedWithItsLanguage() {
        var config = new CellConfig.CodeBlockConfig("java");
        assertEquals("```java\nint a = 1;\n```", project(cell(CellContentType.CODE_BLOCK, "int a = 1;", config)));
        assertEquals("", project(cell(CellContentType.CODE_BLOCK, "", config)));
    }

    @Test
    void accordionAndTabsBecomeHeadingsWithBodies() {
        var accordion = new CellConfig.AccordionConfig("Ablauf", null);
        assertEquals("### Ablauf\n\nErst dies", project(cell(CellContentType.ACCORDION, "Erst dies", accordion)));

        var tabs = new CellConfig.TabsConfig(List.of(
                new CellConfig.TabItem("Erster", "Inhalt eins"), new CellConfig.TabItem("Zweiter", "Inhalt zwei")));
        String out = project(cell(CellContentType.TABS, "", tabs));
        assertTrue(out.contains("### Erster\n\nInhalt eins"));
        assertTrue(out.contains("### Zweiter\n\nInhalt zwei"));
    }

    @Test
    void everyKindOfLinkBecomesALinkLine() {
        assertEquals(
                "[Protokoll](https://example.org/p.pdf)\n\nDas Protokoll",
                project(cell(
                        CellContentType.FILE_DOWNLOAD,
                        "",
                        new CellConfig.FileDownloadConfig("https://example.org/p.pdf", "Protokoll", "Das Protokoll"))));
        assertEquals(
                "[Die Seite](/seite)",
                project(cell(CellContentType.PAGE_LINK, "/seite", new CellConfig.PageLinkConfig(1, "Die Seite"))));
        assertEquals(
                "[Der Artikel](/artikel)",
                project(cell(
                        CellContentType.KB_ARTICLE, "/artikel", new CellConfig.KbArticleConfig(1, "Der Artikel"))));
        assertTrue(project(cell(
                        CellContentType.NEWS_TEASER,
                        "",
                        new CellConfig.NewsTeaserConfig("Neu", null, "Kurzfassung", "/news/1", null)))
                .contains("[Neu](/news/1)"));
        assertTrue(project(cell(
                        CellContentType.EXTERNAL_LINK_CARD,
                        "",
                        new CellConfig.ExternalLinkCardConfig(
                                "https://example.org", "Partner", "Beschreibung", null, null)))
                .contains("[Partner](https://example.org)"));
    }

    @Test
    void mediaCellsBecomeALinkToTheirSource() {
        assertEquals(
                "[https://youtu.be/x](https://youtu.be/x)",
                project(cell(CellContentType.VIDEO, "https://youtu.be/x", CellConfig.EMPTY)));
        assertEquals(
                "[Folge 1](https://example.org/a.mp3)",
                project(cell(
                        CellContentType.AUDIO_EMBED,
                        "",
                        new CellConfig.AudioEmbedConfig("https://example.org/a.mp3", "Folge 1"))));
        assertEquals(
                "[https://example.org/a.pdf](https://example.org/a.pdf)",
                project(cell(CellContentType.PDF, "", new CellConfig.PdfConfig("https://example.org/a.pdf", null))));
        assertTrue(project(cell(CellContentType.MAP, "", new CellConfig.MapConfig(50.1, 8.7, null, null, "Wache")))
                .startsWith("[Wache](https://www.openstreetmap.org/?mlat=50.1"));
        assertEquals(
                "Ohne Koordinaten",
                project(cell(
                        CellContentType.MAP,
                        "",
                        new CellConfig.MapConfig(null, null, null, null, "Ohne Koordinaten"))));
    }

    @Test
    void textOnlyCellsBecomeOneLineEach() {
        assertEquals(
                "Wache\nHauptstraße 1\n12345 Musterstadt\nDeutschland",
                project(cell(
                        CellContentType.ADDRESS_CARD,
                        "",
                        new CellConfig.AddressCardConfig(
                                "Hauptstraße 1", "12345", "Musterstadt", "Deutschland", null, "Wache"))));
        assertEquals(
                "Einsätze 120\nMitglieder 45",
                project(cell(
                        CellContentType.STATS_COUNTER,
                        "",
                        new CellConfig.StatsCounterConfig(List.of(
                                new CellConfig.StatItem("Einsätze", "120", null),
                                new CellConfig.StatItem("Mitglieder", "45", null))))));
        assertEquals(
                "Tag der offenen Tür\nnoch\n2026-09-01",
                project(cell(
                        CellContentType.COUNTDOWN,
                        "",
                        new CellConfig.CountdownConfig("2026-09-01", "Tag der offenen Tür", "noch"))));
    }

    @Test
    void aDividerIsARuleAndASpacerIsNothing() {
        assertEquals("---", project(cell(CellContentType.DIVIDER, "", new CellConfig.DividerConfig(null))));
        assertEquals("", project(cell(CellContentType.SPACER, "", new CellConfig.SpacerConfig(40))));
    }

    @Test
    void aPageOnlyCellProjectsToNothing() {
        assertEquals("", project(cell(CellContentType.BLOG_SIGNUP, "", new CellConfig.BlogSignupConfig("A", "B"))));
    }

    @Test
    void nestedRowsAreRecursedInReadingOrder() {
        var nested = CellConfig.parse(CellContentType.NESTED_ROWS, CellConfig.MAPPER.readTree("""
                        {"rows":[{"cells":[
                            {"contentType":"MARKDOWN","content":"links"},
                            {"contentType":"MARKDOWN","content":"rechts"},
                            {"contentType":"UNKNOWN_KIND","content":"weg"},
                            {"content":"ohne Typ"}
                        ]}]}"""));
        assertEquals("links\n\nrechts", project(cell(CellContentType.NESTED_ROWS, "", nested)));
    }

    @Test
    void columnsFlattenLeftToRightThenDown() {
        var rows = List.of(
                new ContentRow(
                        0,
                        0,
                        0,
                        List.of(
                                cell(CellContentType.MARKDOWN, "links", CellConfig.EMPTY),
                                cell(CellContentType.MARKDOWN, "rechts", CellConfig.EMPTY))),
                new ContentRow(0, 0, 1, List.of(cell(CellContentType.MARKDOWN, "darunter", CellConfig.EMPTY))));
        assertEquals("links\n\nrechts\n\ndarunter", ContentProjection.toMarkdown(rows, FILE_URL));
    }

    @Test
    void plainTextKeepsTheWordsAndDropsTheMarkup() {
        var rows = List.of(new ContentRow(
                0,
                0,
                0,
                List.of(
                        cell(
                                CellContentType.MARKDOWN,
                                "# Überschrift\n\n**fett** und [ein Link](/ziel)",
                                CellConfig.EMPTY),
                        cell(
                                CellContentType.IMAGE,
                                "abc",
                                new CellConfig.ImageConfig(
                                        null, "alt", null, null, null, null, null, null, null, null, null)))));
        String text = ContentProjection.toPlainText(rows, FILE_URL);
        assertTrue(text.contains("Überschrift"));
        assertTrue(text.contains("fett und ein Link"));
        assertFalse(text.contains("!["), "an image is not a word");
        assertFalse(text.contains("#"));
    }

    @Test
    void strippingNothingGivesNothing() {
        assertEquals("", ContentProjection.stripMarkup(null));
        assertEquals("", ContentProjection.stripMarkup("   "));
    }
}
