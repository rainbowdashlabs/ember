/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import dev.chojo.ember.feature.question.QuestionConfigs;

/**
 * The page a letter is set on. The paper is always A4.
 *
 * <p>The header and the footer are drawn inside the top and bottom margins, so those two have to
 * leave room for them.
 *
 * @param marginTopMm    the top margin in millimetres, which holds the header
 * @param marginBottomMm the bottom margin in millimetres, which holds the footer
 * @param marginLeftMm   the left margin in millimetres
 * @param marginRightMm  the right margin in millimetres
 * @param fontSizePt     the size of the body text in points
 */
public record LetterPage(int marginTopMm, int marginBottomMm, int marginLeftMm, int marginRightMm, int fontSizePt) {

    /** The smallest margin a letter may have, in millimetres. */
    public static final int MIN_MARGIN_MM = 5;

    /** The largest margin a letter may have, in millimetres. */
    public static final int MAX_MARGIN_MM = 80;

    /** The smallest body text, in points. */
    public static final int MIN_FONT_SIZE_PT = 8;

    /** The largest body text, in points. */
    public static final int MAX_FONT_SIZE_PT = 16;

    private static final LetterPage DEFAULT = new LetterPage(40, 30, 20, 20, 10);

    /** The page a new template starts with. */
    public static LetterPage defaults() {
        return DEFAULT;
    }

    /**
     * Whether every margin and the font size lie within what a letter may have.
     */
    public boolean withinBounds() {
        return margin(marginTopMm)
                && margin(marginBottomMm)
                && margin(marginLeftMm)
                && margin(marginRightMm)
                && fontSizePt >= MIN_FONT_SIZE_PT
                && fontSizePt <= MAX_FONT_SIZE_PT;
    }

    private static boolean margin(int millimetres) {
        return millimetres >= MIN_MARGIN_MM && millimetres <= MAX_MARGIN_MM;
    }

    /**
     * Reads the stored column, answering the defaults where it says nothing readable.
     *
     * @param json the column
     * @return the page
     */
    public static LetterPage parse(String json) {
        return QuestionConfigs.parse(json, LetterPage.class, DEFAULT);
    }

    /**
     * @return the page as it is stored
     */
    public String toJson() {
        return QuestionConfigs.toJson(this);
    }
}
