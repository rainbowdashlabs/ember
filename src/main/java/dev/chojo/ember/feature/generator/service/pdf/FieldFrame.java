/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import dev.chojo.ember.feature.generator.entity.FieldRect;
import org.apache.pdfbox.util.Matrix;

/**
 * A field box as the reader sees it on a turned page: upright, with its own origin at the bottom left
 * of what is shown.
 *
 * <p>A box is stored in the page's unturned coordinates. On a page shown a quarter turn clockwise, the
 * box's left edge is what the reader sees as its bottom, and a text drawn straight into it would run
 * down the page. Drawing through {@link #toPage()} turns the text with the page, so it reads upright
 * wherever the page is turned.
 *
 * @param width  how wide the box looks to the reader
 * @param height how tall the box looks to the reader
 * @param toPage maps the frame's coordinates onto the page's
 */
public record FieldFrame(float width, float height, Matrix toPage) {

    /**
     * @param rect     the box as stored
     * @param rotation how far its page is turned clockwise when shown: 0, 90, 180 or 270
     * @return the box as the reader sees it
     */
    public static FieldFrame of(FieldRect rect, int rotation) {
        float x = (float) rect.x();
        float y = (float) rect.y();
        float w = (float) rect.width();
        float h = (float) rect.height();
        double turn = Math.toRadians(rotation);
        return switch (rotation) {
            case 90 -> new FieldFrame(h, w, Matrix.getRotateInstance(turn, x + w, y));
            case 180 -> new FieldFrame(w, h, Matrix.getRotateInstance(turn, x + w, y + h));
            case 270 -> new FieldFrame(h, w, Matrix.getRotateInstance(turn, x, y + h));
            default -> new FieldFrame(w, h, Matrix.getTranslateInstance(x, y));
        };
    }
}
