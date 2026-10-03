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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A letter prints its aligned paragraphs where the editor showed them: centred ones in the middle of the
 * text, right-aligned ones against its right edge, and justified ones filling their lines to it.
 */
class LetterAlignmentTest extends RepositoryTestBase {
    private static final String LONG_WORD = "Donaudampfschifffahrtsgesellschaftskapitän";
    private static final String RAGGED = "Ein Satz aus kurzen Wörtern, der vor einem langen Wort umbricht, "
            + "nämlich vor dem Wort " + LONG_WORD + " und " + LONG_WORD + " und " + LONG_WORD + ".";

    private static Owner.Station station;
    private static LetterRenderer renderer;

    @BeforeAll
    static void setup() {
        station = new Owner.Station(stationRepo.create("Ausrichtung Wache").id());
        var backend = localStorage();
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        var pictures = mock(KbPdfPictures.class);
        when(pictures.place(anyInt(), anyString(), anyString()))
                .thenAnswer(call -> new KbPdfPictures.Placed(call.getArgument(1), Map.of()));
        renderer = new LetterRenderer(
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
        var letter = new LetterContent(List.of(), List.of(), List.of(rows), LetterPage.defaults());
        return renderer.render(new LetterRenderer.LetterJob(
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
                LocalDate.of(2026, 10, 3)));
    }

    private static ContentRow row(String text) {
        return new ContentRow(
                0, 0, 0, List.of(new ContentCell(0, 0, 0, 100, CellContentType.MARKDOWN, text, CellConfig.EMPTY)));
    }
}
