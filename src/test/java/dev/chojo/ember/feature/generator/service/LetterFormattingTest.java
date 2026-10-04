/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

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
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A letter prints its paragraphs and words the way the editor showed them: centred paragraphs in the middle
 * of the text, right-aligned ones against its right edge, justified ones filling their lines to it, and
 * words set in a size of their own in that size, a pixel printed as three quarters of a point.
 */
class LetterFormattingTest extends RepositoryTestBase {
    private static final String LONG_WORD = "Donaudampfschifffahrtsgesellschaftskapitän";
    private static final String RAGGED = "Ein Satz aus kurzen Wörtern, der vor einem langen Wort umbricht, "
            + "nämlich vor dem Wort " + LONG_WORD + " und " + LONG_WORD + " und " + LONG_WORD + ".";

    private static Owner.Station station;
    private static LetterRenderer renderer;

    @BeforeAll
    static void setup() {
        station = new Owner.Station(stationRepo.create("Ausrichtung Wache").id());
        renderer = newRenderer();
    }

    private static LetterRenderer newRenderer() {
        var backend = localStorage();
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        var pictures = mock(KbPdfPictures.class);
        when(pictures.place(anyInt(), anyString(), anyString()))
                .thenAnswer(call -> new KbPdfPictures.Placed(call.getArgument(1), Map.of()));
        return new LetterRenderer(
                pictures,
                mock(MediaLibraryService.class),
                newStationLogoService(),
                newFontLibrary(storage),
                newOwnerStores());
    }

    @Test
    void centredAndRightAlignedParagraphsMoveTowardsTheRightEdge() throws IOException {
        var positions = TestPdfs.positions(
                render(row("Links"), row(aligned("center", "Mitte")), row(aligned("right", "Rechts"))), 1);

        float left = startOf(positions, "L");
        float centre = startOf(positions, "M");
        float right = startOf(positions, "R");
        assertTrue(centre > left + 100, () -> "centred at " + centre + ", left at " + left);
        assertTrue(right > centre + 100, () -> "right at " + right + ", centred at " + centre);
    }

    @Test
    void aJustifiedParagraphFillsItsFirstLine() throws IOException {
        float ragged = firstLineEnd(TestPdfs.positions(render(row(RAGGED)), 1));
        float justified = firstLineEnd(TestPdfs.positions(render(row(aligned("justify", RAGGED))), 1));

        assertTrue(justified > ragged + 20, () -> "justified to " + justified + ", ragged to " + ragged);
    }

    @Test
    void sizedWordsPrintInTheirSize() throws IOException {
        var positions = TestPdfs.positions(render(row("normal <span style=\"font-size: 48px\">G</span>")), 1);

        float normal = first(positions, "n").getFontSizeInPt();
        float sized = first(positions, "G").getFontSizeInPt();
        assertEquals(36, sized, 0.5, () -> "sized at " + sized + ", normal at " + normal);
        assertTrue(sized > normal * 2, () -> "sized at " + sized + ", normal at " + normal);
    }

    @Test
    void sizedWordsPrintInTheirSizeInTheHeaderAndTheFooterToo() throws IOException {
        var sized = row("klein <span style=\"font-size: 32px\">G</span>");
        var letter = new LetterContent(List.of(sized), List.of(sized), List.of(sized), LetterPage.defaults());

        var words = TestPdfs.positions(render(letter), 1).stream()
                .filter(position -> position.getUnicode().equals("G"))
                .toList();

        assertEquals(3, words.size(), "one in the header, the body and the footer");
        words.forEach(
                word -> assertEquals(24, word.getFontSizeInPt(), 0.5, () -> "printed at " + word.getFontSizeInPt()));
    }

    /**
     * A text is converted once, whichever letter and block it stands in, so a new version or a draft
     * converts only the texts that changed.
     */
    @Test
    void anUnchangedTextIsConvertedOnce() {
        var fresh = newRenderer();
        var same = row("Gleicher Text für {{member.fullName}}");
        fresh.render(job(new LetterContent(List.of(same), List.of(same), List.of(same), LetterPage.defaults())));
        fresh.render(job(new LetterContent(List.of(), List.of(), List.of(same, row("Neu")), LetterPage.defaults())));

        assertEquals(2, fresh.convertedTexts());
    }

    private static String aligned(String alignment, String markdown) {
        return "<div data-align=\"" + alignment + "\">\n\n" + markdown + "\n\n</div>";
    }

    private static TextPosition first(List<TextPosition> positions, String character) {
        return positions.stream()
                .filter(position -> position.getUnicode().equals(character))
                .findFirst()
                .orElseThrow();
    }

    private static float startOf(List<TextPosition> positions, String character) {
        return first(positions, character).getXDirAdj();
    }

    private static float firstLineEnd(List<TextPosition> positions) {
        float firstLine = first(positions, "E").getYDirAdj();
        return (float) positions.stream()
                .filter(position -> Math.abs(position.getYDirAdj() - firstLine) < 1)
                .mapToDouble(position -> position.getXDirAdj() + position.getWidthDirAdj())
                .max()
                .orElseThrow();
    }

    private static byte[] render(ContentRow... rows) {
        return render(new LetterContent(List.of(), List.of(), List.of(rows), LetterPage.defaults()));
    }

    private static byte[] render(LetterContent letter) {
        return renderer.render(job(letter));
    }

    private static LetterRenderer.LetterJob job(LetterContent letter) {
        return new LetterRenderer.LetterJob(
                station,
                station.stationId(),
                "Brief",
                letter,
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
}
