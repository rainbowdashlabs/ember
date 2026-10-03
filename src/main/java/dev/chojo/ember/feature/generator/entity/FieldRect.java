/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * Where a field sits on a page of a PDF, in the page's own coordinates.
 *
 * <p>The numbers are PDF points in the page's user space as it is before any rotation: the origin at
 * the bottom left, x to the right, y upwards. A crop box that does not start at zero is part of the
 * numbers, so a box is drawn exactly where it is stored without asking the page anything. Turning
 * what the reader sees into these numbers, rotation and crop box included, is the editor's job.
 *
 * @param page   the page, counted from one
 * @param x      the left edge
 * @param y      the bottom edge
 * @param width  how wide it is
 * @param height how tall it is
 */
public record FieldRect(int page, double x, double y, double width, double height) {

    /** @return the right edge */
    public double right() {
        return x + width;
    }

    /** @return the top edge */
    public double top() {
        return y + height;
    }
}
