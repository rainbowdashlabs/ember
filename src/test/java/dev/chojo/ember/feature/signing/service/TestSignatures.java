/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/**
 * Pictures of signatures for tests: a stroke drawn on a transparent canvas as a browser sends it, and a
 * dark stroke on light grey paper as a photo of one would be.
 */
public final class TestSignatures {
    private TestSignatures() {}

    /** @return a wavy stroke on a transparent canvas of 600 by 200 pixels, as a PNG */
    public static byte[] drawn() {
        var canvas = new BufferedImage(600, 200, BufferedImage.TYPE_INT_ARGB);
        stroke(canvas, Color.BLACK);
        return png(canvas);
    }

    /** @return a blue stroke on light grey paper of 800 by 300 pixels, as a JPEG */
    public static byte[] photographed() {
        var paper = new BufferedImage(800, 300, BufferedImage.TYPE_INT_RGB);
        var graphics = paper.createGraphics();
        graphics.setColor(new Color(0xDD, 0xDA, 0xD2));
        graphics.fillRect(0, 0, 800, 300);
        graphics.dispose();
        stroke(paper, new Color(0x20, 0x30, 0x90));
        return encode(paper, "jpg");
    }

    /** @return a transparent canvas with nothing drawn on it, as a PNG */
    public static byte[] empty() {
        return png(new BufferedImage(600, 200, BufferedImage.TYPE_INT_ARGB));
    }

    private static void stroke(BufferedImage image, Color ink) {
        var graphics = image.createGraphics();
        graphics.setColor(ink);
        graphics.setStroke(new BasicStroke(6f));
        int left = image.getWidth() / 6;
        int middle = image.getHeight() / 2;
        int step = image.getWidth() / 12;
        for (int i = 0; i < 8; i++) {
            int x = left + i * step;
            graphics.drawLine(x, middle - 30 + (i % 2) * 60, x + step, middle + 30 - (i % 2) * 60);
        }
        graphics.dispose();
    }

    private static byte[] png(BufferedImage image) {
        return encode(image, "png");
    }

    private static byte[] encode(BufferedImage image, String format) {
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
