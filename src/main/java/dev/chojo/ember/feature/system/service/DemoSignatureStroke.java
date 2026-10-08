/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;

import javax.imageio.ImageIO;

/**
 * A signature as the demo's signers draw it on the screen: a dark, looping stroke on a transparent canvas,
 * sent as a PNG the way the browser sends one.
 *
 * <p>Each signer gets a stroke of their own, the same one on every seed run, so two signatures on one
 * document do not look like copies of each other and a reset draws them as before.
 */
final class DemoSignatureStroke {
    private static final int WIDTH = 600;
    private static final int HEIGHT = 200;
    private static final int LOOPS = 7;

    private DemoSignatureStroke() {}

    /**
     * @param signer what tells the signer's stroke apart, such as their name
     * @return their stroke as a PNG
     */
    static byte[] of(String signer) {
        var random = new Random(signer.hashCode());
        var canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        var graphics = canvas.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.draw(stroke(random));
        graphics.dispose();
        return png(canvas);
    }

    private static Path2D stroke(Random random) {
        var path = new Path2D.Double();
        double step = (WIDTH - 120.0) / LOOPS;
        double x = 60;
        double middle = HEIGHT / 2.0;
        path.moveTo(x, middle + random.nextInt(30));
        for (int loop = 0; loop < LOOPS; loop++) {
            double rise = 30 + random.nextInt(50);
            path.curveTo(x + step * 0.2, middle - rise, x + step * 0.9, middle - rise, x + step * 0.5, middle);
            path.curveTo(x + step * 0.2, middle + rise / 2, x + step * 0.8, middle + rise / 2, x + step, middle);
            x += step;
        }
        return path;
    }

    private static byte[] png(BufferedImage image) {
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
