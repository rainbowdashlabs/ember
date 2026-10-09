/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A picture of a signature comes out as ink on a transparent background, cut to the signature, no larger
 * than a field needs, whether it was drawn on a canvas or photographed on paper; what is no picture, too
 * large, or shows no signature is refused.
 */
class SignatureImagesTest {

    @Test
    void aDrawnSignatureIsCutToItsInkOnATransparentBackground() throws IOException {
        var picture = SignatureImages.clean(TestSignatures.drawn());

        var image = read(picture.png());
        assertEquals(picture.width(), image.getWidth());
        assertEquals(picture.height(), image.getHeight());
        assertTrue(image.getWidth() < 600 && image.getHeight() < 200, "cut to the stroke");
        assertEquals(0, image.getRGB(0, 0) >>> 24, "the corner is transparent");
        assertTrue(inkPixels(image) > 0);
    }

    @Test
    void aPhotographedSignatureLosesItsPaper() throws IOException {
        var image = read(SignatureImages.clean(TestSignatures.photographed()).png());

        assertEquals(0, image.getRGB(0, 0) >>> 24, "the paper is gone");
        assertTrue(inkPixels(image) > 0, "the stroke stays");
    }

    @Test
    void aLargePictureIsScaledDownToWhatAFieldNeeds() throws IOException {
        var large = new BufferedImage(4000, 1200, BufferedImage.TYPE_INT_RGB);
        var graphics = large.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 4000, 1200);
        graphics.setColor(Color.BLACK);
        graphics.fillRect(100, 100, 3800, 1000);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(200, 200, 3600, 800);
        graphics.dispose();

        var picture = SignatureImages.clean(encode(large, "png"));

        assertTrue(picture.width() <= SignatureImages.MAX_WIDTH);
        assertTrue(picture.height() <= SignatureImages.MAX_HEIGHT);
    }

    @Test
    void whatIsNoSignaturePictureIsRefused() {
        refused(DocumentRefusal.SIGNATURE_IMAGE_EMPTY, () -> SignatureImages.clean(TestSignatures.empty()));
        refused(
                DocumentRefusal.SIGNATURE_IMAGE_NOT_A_PICTURE,
                () -> SignatureImages.clean("nichts".getBytes(StandardCharsets.UTF_8)));
        byte[] brokenPng = TestSignatures.drawn();
        byte[] truncated = new byte[64];
        System.arraycopy(brokenPng, 0, truncated, 0, truncated.length);
        refused(DocumentRefusal.SIGNATURE_IMAGE_NOT_A_PICTURE, () -> SignatureImages.clean(truncated));
        refused(
                DocumentRefusal.SIGNATURE_IMAGE_TOO_LARGE,
                () -> SignatureImages.clean(new byte[SignatureImages.MAX_BYTES + 1]));
    }

    private static int inkPixels(BufferedImage image) {
        int ink = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) > 200) ink++;
            }
        }
        return ink;
    }

    private static BufferedImage read(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static void refused(DocumentRefusal refusal, Executable action) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, action).refusal());
    }
}
