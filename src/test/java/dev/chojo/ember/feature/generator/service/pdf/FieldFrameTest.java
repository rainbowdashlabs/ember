/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import dev.chojo.ember.feature.generator.entity.FieldRect;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The frame of a box on a turned page maps its own corners onto the stored box, so whatever is drawn
 * inside the frame stays inside the box, and its bottom left is the bottom left the reader sees.
 */
class FieldFrameTest {
    private static final FieldRect RECT = new FieldRect(1, 100, 200, 40, 150);

    @ParameterizedTest
    @ValueSource(ints = {0, 90, 180, 270})
    void theFrameCoversTheBoxExactly(int rotation) {
        var frame = FieldFrame.of(RECT, rotation);
        boolean sideways = rotation == 90 || rotation == 270;

        assertEquals(sideways ? 150 : 40, frame.width(), 0.001);
        assertEquals(sideways ? 40 : 150, frame.height(), 0.001);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (float[] corner :
                new float[][] {{0, 0}, {frame.width(), 0}, {0, frame.height()}, {frame.width(), frame.height()}}) {
            Point2D.Float point = frame.toPage().transformPoint(corner[0], corner[1]);
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        assertEquals(RECT.x(), minX, 0.001);
        assertEquals(RECT.y(), minY, 0.001);
        assertEquals(RECT.right(), maxX, 0.001);
        assertEquals(RECT.top(), maxY, 0.001);
    }

    /**
     * The reader's bottom left: on a page turned a quarter clockwise, the box's bottom right corner in
     * the page's own coordinates is what shows at the bottom left.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, 90, 180, 270})
    void theOriginIsTheCornerTheReaderSeesAtTheBottomLeft(int rotation) {
        var origin = FieldFrame.of(RECT, rotation).toPage().transformPoint(0, 0);

        var expected =
                switch (rotation) {
                    case 90 -> new Point2D.Float(140, 200);
                    case 180 -> new Point2D.Float(140, 350);
                    case 270 -> new Point2D.Float(100, 350);
                    default -> new Point2D.Float(100, 200);
                };
        assertEquals(expected.x, origin.x, 0.001);
        assertEquals(expected.y, origin.y, 0.001);
    }
}
