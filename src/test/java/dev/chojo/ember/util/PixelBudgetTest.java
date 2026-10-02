/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PixelBudgetTest {

    private static byte[] png(int width, int height) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private static byte[] pdfWithPage(float widthPt, float heightPt) throws IOException {
        try (var document = new PDDocument()) {
            document.addPage(new PDPage(new PDRectangle(widthPt, heightPt)));
            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void aPictureClaimingTooManyPixelsIsRefusedWithoutBeingDecoded() {
        byte[] bomb = OversizedPictures.pngClaiming(30_000, 30_000);
        assertTrue(bomb.length < 100, "the claim is made in a handful of bytes");

        var refusal = assertThrows(RefusalResponse.class, () -> PixelBudget.read(bomb));

        assertEquals(GeneralRefusal.PICTURE_TOO_MANY_PIXELS, refusal.refusal());
        assertEquals(413, refusal.getStatus());
    }

    @Test
    void theClaimIsReadFromTheHeader() throws IOException {
        var size = PixelBudget.measure(OversizedPictures.pngClaiming(30_000, 20_000));

        assertEquals(new PixelBudget.Dimensions(30_000, 20_000), size.orElseThrow());
        assertFalse(size.get().fits());
    }

    @Test
    void requireWithinRefusesTheSamePicture() {
        byte[] bomb = OversizedPictures.pngClaiming(10_000, 10_000);

        assertFalse(PixelBudget.fits(bomb));
        assertThrows(RefusalResponse.class, () -> PixelBudget.requireWithin(bomb));
    }

    @Test
    void aPictureJustInsideTheBudgetFits() {
        assertTrue(PixelBudget.fits(OversizedPictures.pngClaiming(10_000, 5_000)));
        assertFalse(PixelBudget.fits(OversizedPictures.pngClaiming(10_000, 5_001)));
    }

    @Test
    void anOrdinaryPictureIsDecoded() throws IOException {
        BufferedImage image = PixelBudget.read(png(40, 30));

        assertNotNull(image);
        assertEquals(40, image.getWidth());
        assertEquals(30, image.getHeight());
        PixelBudget.requireWithin(png(40, 30));
    }

    @Test
    void bytesNoReaderKnowsAreNotAPicture() throws IOException {
        byte[] text = "not a picture".getBytes();

        assertNull(PixelBudget.read(text));
        assertTrue(PixelBudget.measure(text).isEmpty());
        assertTrue(PixelBudget.fits(text));
    }

    @Test
    void anOrdinaryPageIsDrawnAtTheResolutionAskedFor() throws IOException {
        var page = FilePicture.firstPage(pdfWithPage(PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight()), 96);

        assertNotNull(page);
        assertEquals((int) Math.floor(PDRectangle.A4.getWidth() * 96 / 72f), page.getWidth());
    }

    @Test
    void aHugePageIsDrawnInsideTheBudget() throws IOException {
        var page = FilePicture.firstPage(pdfWithPage(14_400, 14_400), 96);

        assertNotNull(page);
        assertTrue((long) page.getWidth() * page.getHeight() <= PixelBudget.MAX_PIXELS);
    }

    @Test
    void thePageScaleIsLoweredOnlyForLargePages() {
        assertEquals(96 / 72f, PixelBudget.pageScale(PDRectangle.A4, 96));

        var huge = new PDRectangle(200_000, 200_000);
        float scale = PixelBudget.pageScale(huge, 96);
        assertTrue(scale < 96 / 72f);
        assertTrue(PixelBudget.pageSize(huge, scale).fits());
    }
}
