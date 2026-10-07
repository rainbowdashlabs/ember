/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.MemberView;
import dev.chojo.ember.feature.generator.service.pdf.TestPdfs;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfPictures;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.feature.system.service.DemoDocumentTemplateSeeder;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.PdfText;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Where a letter puts its blocks on the paper.
 *
 * <p>Every block of a row starts at the top of the row, in the header as in the body, so a short text
 * beside a tall picture lines up with the picture's top edge. The top margin is the distance from the
 * paper's edge to the header and the bottom margin the distance from the footer to the paper's edge,
 * whatever their height, and the body keeps a gap of {@link #EDGE_GAP_MM} millimetres to both on every
 * page. A letter without them starts and ends its body at the margins.
 *
 * <p>Pictures are found as runs of dark pixel rows, texts by the top of their capital letters, read from
 * the drawn page above the baseline PDFBox reports, and by their baselines, which is where Typst ends a
 * block of text. The header is written in K, the footer in F and the body in B, so each letter says which
 * part it belongs to.
 */
class LetterPlacementTest extends GeneratorTestBase {
    private static final float A4_HEIGHT = 841.89f;
    private static final float POINTS_PER_MM = 72f / 25.4f;
    private static final float EDGE_GAP_MM = 8;
    private static final float STEP = 0.25f;
    private static final float TOLERANCE = 1.5f;

    private static Wiring wiring;
    private static Owner.Station station;
    private static LetterRenderer renderer;

    @BeforeAll
    static void setup() throws IOException {
        wiring = wire("Platzierung Wache");
        station = wiring.owner();
        var backend = localStorage();
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        var pictures = mock(KbPdfPictures.class);
        when(pictures.place(anyInt(), anyString(), anyString()))
                .thenAnswer(call -> new KbPdfPictures.Placed(call.getArgument(1), Map.of()));
        var logos = newStationLogoService();
        logos.store(station.stationId(), blackSquare(), "image/png");
        renderer = new LetterRenderer(
                pictures, mock(MediaLibraryService.class), logos, newFontLibrary(storage), newOwnerStores());
    }

    @Test
    void aTextBesideATallPictureStartsAtThePicturesTop() throws IOException {
        byte[] pdf = render(new LetterContent(
                List.of(pictureAndText("Kopfzeile")),
                List.of(),
                List.of(pictureAndText("Brieftext")),
                LetterPage.defaults()));
        var positions = TestPdfs.positions(pdf, 1);
        var page = TestPdfs.picture(pdf);
        var header = first(positions, "K");
        var body = first(positions, "B");
        var pictures = darkRuns(page, 0, Math.min(header.getXDirAdj(), body.getXDirAdj()) - 4);

        assertEquals(2, pictures.size(), () -> "pictures found at " + pictures);
        float headerText = capTop(page, header);
        float bodyText = capTop(page, body);
        assertEquals(pictures.getFirst(), headerText, TOLERANCE, "the header's text starts at its picture's top");
        assertEquals(pictures.get(1), bodyText, TOLERANCE, "the body's text starts at its picture's top");
    }

    /** Four columns of six lines above and three below, on a page with ten millimetres at each edge. */
    @Test
    void aTallHeaderAndFooterStayAtTheirMarginsAndClearOfTheBodyOnEveryPage() throws IOException {
        var page = new LetterPage(10, 10, 20, 20, 10, null, null, null);
        byte[] pdf =
                render(new LetterContent(List.of(columns("K", 4, 6)), List.of(columns("F", 3, 6)), longBody(), page));

        assertTrue(TestPdfs.pages(pdf) > 1, "the body runs over more than one page");
        assertPlacedOnEveryPage(pdf, page);
    }

    @Test
    void aOneLineHeaderAndFooterStayAtTheirMarginsAndClearOfTheBody() throws IOException {
        var page = new LetterPage(10, 10, 20, 20, 10, null, null, null);
        byte[] pdf =
                render(new LetterContent(List.of(columns("K", 1, 1)), List.of(columns("F", 1, 1)), longBody(), page));

        assertPlacedOnEveryPage(pdf, page);
    }

    @Test
    void aLetterWithoutHeaderAndFooterSetsItsBodyBetweenTheMargins() throws IOException {
        var page = new LetterPage(30, 25, 20, 20, 10, null, null, null);
        byte[] pdf = render(new LetterContent(List.of(), List.of(), longBody(), page));

        for (int number = 1; number <= TestPdfs.pages(pdf); number++) {
            var picture = TestPdfs.picture(pdf, number);
            var body = TestPdfs.positions(pdf, number).stream()
                    .filter(position -> position.getUnicode().equals("B"))
                    .toList();
            assertEquals(mm(30), capTop(picture, highest(body)), TOLERANCE, "the body starts at the top margin");
            assertTrue(
                    lowestBaseline(body) <= A4_HEIGHT - mm(25) + TOLERANCE,
                    () -> "the body ends above the bottom margin at " + lowestBaseline(body));
        }
    }

    /** The demo certificate's three columns of contacts stay whole on the paper, ten millimetres up. */
    @Test
    void theDemoCertificateKeepsItsWholeFooterOnThePaper() throws IOException {
        var manager = wiring.member("platzierung@example.org", "Pia", "Platz");
        var request = DemoDocumentTemplateSeeder.certificate(
                memberGroupRepo.create(station.stationId(), "Anfänger").id(),
                memberGroupRepo.create(station.stationId(), "Fortgeschritten").id(),
                manager.id());
        var preview = wiring.generation()
                .previewDraft(as(manager, StationPermission.DOCUMENT_TEMPLATE_EDIT), request, null, null);
        byte[] pdf = Base64.getDecoder().decode(preview.pdfBase64());
        var page = Objects.requireNonNull(request.page());

        String text = Objects.requireNonNull(PdfText.extract(pdf));
        for (String line :
                List.of("Musterstraße 1", "12345 Musterstadt", "Telefon: 0221 4710 123", "www.example.org")) {
            assertTrue(text.contains(line), () -> "the footer line " + line + " is printed: " + text);
        }
        for (int number = 1; number <= TestPdfs.pages(pdf); number++) {
            var positions = TestPdfs.positions(pdf, number);
            assertEquals(
                    mm(page.marginTopMm()),
                    topmostDark(TestPdfs.picture(pdf, number)),
                    TOLERANCE,
                    "the header starts at the top margin");
            assertEquals(
                    A4_HEIGHT - mm(page.marginBottomMm()),
                    lowestBaseline(positions),
                    TOLERANCE,
                    "the footer's last line ends at the bottom margin");
        }
    }

    private static void assertPlacedOnEveryPage(byte[] pdf, LetterPage page) throws IOException {
        for (int number = 1; number <= TestPdfs.pages(pdf); number++) {
            var picture = TestPdfs.picture(pdf, number);
            var positions = TestPdfs.positions(pdf, number);
            var header = only(positions, "K");
            var footer = only(positions, "F");
            var body = only(positions, "B");
            int onPage = number;

            assertEquals(
                    mm(page.marginTopMm()), capTop(picture, highest(header)), TOLERANCE, "header at the top margin");
            assertEquals(
                    A4_HEIGHT - mm(page.marginBottomMm()),
                    lowestBaseline(footer),
                    TOLERANCE,
                    "footer at the bottom margin");
            float aboveBody = capTop(picture, highest(body)) - lowestBaseline(header);
            float belowBody = capTop(picture, highest(footer)) - lowestBaseline(body);
            assertTrue(
                    aboveBody >= mm(EDGE_GAP_MM) - TOLERANCE,
                    () -> "page " + onPage + ": the body starts " + aboveBody + " points below the header");
            assertTrue(
                    belowBody >= mm(EDGE_GAP_MM) - TOLERANCE,
                    () -> "page " + onPage + ": the body ends " + belowBody + " points above the footer");
        }
    }

    private static float mm(float millimetres) {
        return millimetres * POINTS_PER_MM;
    }

    private static ContentRow pictureAndText(String text) {
        return new ContentRow(
                0,
                0,
                0,
                List.of(
                        new ContentCell(0, 0, 0, 50, CellContentType.IMAGE, ContentCell.STATION_LOGO, CellConfig.EMPTY),
                        new ContentCell(0, 0, 0, 50, CellContentType.MARKDOWN, text, CellConfig.EMPTY)));
    }

    /** A row of columns, each a text of lines written in one letter. */
    private static ContentRow columns(String letter, int count, int lines) {
        String text = String.join("\\\n", Collections.nCopies(lines, letter.repeat(6)));
        var cells = new ArrayList<ContentCell>();
        for (int column = 0; column < count; column++) {
            cells.add(new ContentCell(0, 0, 0, 100.0 / count, CellContentType.MARKDOWN, text, CellConfig.EMPTY));
        }
        return new ContentRow(0, 0, 0, cells);
    }

    private static List<ContentRow> longBody() {
        String paragraph = String.join(" ", Collections.nCopies(12, "BBBB"));
        String text = String.join("\n\n", Collections.nCopies(90, paragraph));
        return List.of(new ContentRow(
                0, 0, 0, List.of(new ContentCell(0, 0, 0, 100, CellContentType.MARKDOWN, text, CellConfig.EMPTY))));
    }

    private static byte[] blackSquare() throws IOException {
        var square = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
        var graphics = square.createGraphics();
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, 400, 400);
        graphics.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(square, "png", out);
        return out.toByteArray();
    }

    private static TextPosition first(List<TextPosition> positions, String character) {
        return only(positions, character).getFirst();
    }

    private static List<TextPosition> only(List<TextPosition> positions, String character) {
        var found = positions.stream()
                .filter(position -> position.getUnicode().equals(character))
                .toList();
        assertTrue(!found.isEmpty(), () -> "no " + character + " on the page");
        return found;
    }

    private static TextPosition highest(List<TextPosition> positions) {
        return positions.stream()
                .min((one, other) -> Float.compare(one.getYDirAdj(), other.getYDirAdj()))
                .orElseThrow();
    }

    private static float lowestBaseline(List<TextPosition> positions) {
        return (float)
                positions.stream().mapToDouble(TextPosition::getYDirAdj).max().orElseThrow();
    }

    /**
     * The top of a capital letter, in points from the top of the page: the highest dark row above its
     * baseline within its width.
     */
    private static float capTop(BufferedImage page, TextPosition letter) {
        float baseline = letter.getYDirAdj();
        for (float y = Math.max(1, baseline - 2 * letter.getFontSizeInPt()); y <= baseline; y += STEP) {
            if (darkRow(page, letter.getXDirAdj(), letter.getXDirAdj() + letter.getWidthDirAdj(), y)) return y;
        }
        throw new AssertionError("no letter drawn above " + baseline);
    }

    /** The highest dark row of a page, in points from its top. */
    private static float topmostDark(BufferedImage page) {
        return darkRuns(page, 0, page.getWidth() / 4f).getFirst();
    }

    /** Where each run of dark rows between two x positions starts, in points from the top of the page. */
    private static List<Float> darkRuns(BufferedImage page, float from, float to) {
        var starts = new ArrayList<Float>();
        boolean inside = false;
        for (float y = 1; y < A4_HEIGHT - 1; y += STEP) {
            boolean dark = darkRow(page, from, to, y);
            if (dark && !inside) starts.add(y);
            inside = dark;
        }
        return starts;
    }

    private static boolean darkRow(BufferedImage page, float from, float to, float y) {
        for (float x = Math.max(from, 0); x < to - 1; x += STEP) {
            if (TestPdfs.darkAt(page, x, A4_HEIGHT - y)) return true;
        }
        return false;
    }

    private static byte[] render(LetterContent letter) {
        return renderer.render(new LetterRenderer.LetterJob(
                station,
                station.stationId(),
                "Brief",
                letter,
                MemberView.EVERYBODY,
                DocumentLanguage.DE,
                Map.of(),
                Map.of(),
                false,
                LocalDate.of(2026, 10, 3)));
    }
}
