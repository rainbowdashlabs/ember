/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * One page of an uploaded PDF as far as fields on it care: the part of it that is shown, and how it
 * is turned.
 *
 * @param x        the left edge of the crop box, in points
 * @param y        the bottom edge of the crop box, in points
 * @param width    how wide the crop box is
 * @param height   how tall the crop box is
 * @param rotation how far the page is turned clockwise when shown: 0, 90, 180 or 270
 */
public record PdfPage(double x, double y, double width, double height, int rotation) {
    /** How far a field may reach past the edge, for the rounding of a box drawn to the very edge. */
    private static final double SLACK = 0.5;

    /**
     * @param rect a field on this page
     * @return whether the field lies within the part of the page that is shown
     */
    public boolean holds(FieldRect rect) {
        return rect.x() >= x - SLACK
                && rect.y() >= y - SLACK
                && rect.right() <= x + width + SLACK
                && rect.top() <= y + height + SLACK;
    }
}
