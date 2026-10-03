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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Where a letter puts its blocks on the paper: every block of a row starts at the top of the row, in the
 * header as in the body, so a short text beside a tall picture lines up with the picture's top edge.
 *
 * <p>Pictures are found as runs of dark pixel rows left of the texts, texts by the top of their first
 * capital letter, read from the drawn page above the baseline PDFBox reports for it.
 */
class LetterPlacementTest extends RepositoryTestBase {
    private static final float A4_HEIGHT = 841.89f;
    private static final float STEP = 0.25f;
    private static final float TOLERANCE = 1.5f;

    private static Owner.Station station;
    private static LetterRenderer renderer;

    @BeforeAll
    static void setup() throws IOException {
        station = new Owner.Station(stationRepo.create("Platzierung Wache").id());
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

    private static ContentRow pictureAndText(String text) {
        return new ContentRow(
                0,
                0,
                0,
                List.of(
                        new ContentCell(0, 0, 0, 50, CellContentType.IMAGE, ContentCell.STATION_LOGO, CellConfig.EMPTY),
                        new ContentCell(0, 0, 0, 50, CellContentType.MARKDOWN, text, CellConfig.EMPTY)));
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
        return positions.stream()
                .filter(position -> position.getUnicode().equals(character))
                .findFirst()
                .orElseThrow();
    }

    /**
     * The top of a capital letter, in points from the top of the page: the highest dark row above its
     * baseline within its width.
     */
    private static float capTop(BufferedImage page, TextPosition letter) {
        float baseline = letter.getYDirAdj();
        for (float y = baseline - 2 * letter.getFontSizeInPt(); y <= baseline; y += STEP) {
            if (darkRow(page, letter.getXDirAdj(), letter.getXDirAdj() + letter.getWidthDirAdj(), y)) return y;
        }
        throw new AssertionError("no letter drawn above " + baseline);
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
        for (float x = Math.max(from, 0); x < to; x += STEP) {
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
                null,
                MemberView.EVERYBODY,
                DocumentLanguage.DE,
                Map.of(),
                Map.of(),
                false,
                LocalDate.of(2026, 10, 3)));
    }
}
