/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import dev.chojo.ember.feature.question.QuestionConfigs;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * The page a letter is set on. The paper is always A4.
 *
 * <p>The top margin reaches from the paper's edge to the header and the bottom margin from the footer to
 * the paper's edge. The body starts a fixed gap below the header and ends the same gap above the footer,
 * however tall the two are, so neither leaves the paper nor runs into the text. The body, the header and
 * the footer each print in the default font, Liberation Sans, or in a family of uploaded fonts the
 * station reaches.
 *
 * @param marginTopMm    the distance from the paper's top edge to the header, in millimetres
 * @param marginBottomMm the distance from the footer to the paper's bottom edge, in millimetres
 * @param marginLeftMm   the left margin in millimetres
 * @param marginRightMm  the right margin in millimetres
 * @param fontSizePt     the size of the body text in points
 * @param bodyFont       the family the body prints in, or null for the default font
 * @param headerFont     the family the header prints in, or null for the default font
 * @param footerFont     the family the footer prints in, or null for the default font
 */
public record LetterPage(
        int marginTopMm,
        int marginBottomMm,
        int marginLeftMm,
        int marginRightMm,
        int fontSizePt,
        @Nullable String bodyFont,
        @Nullable String headerFont,
        @Nullable String footerFont) {

    /** The smallest margin a letter may have, in millimetres. */
    public static final int MIN_MARGIN_MM = 5;

    /** The largest margin a letter may have, in millimetres. */
    public static final int MAX_MARGIN_MM = 80;

    /** The smallest body text, in points. */
    public static final int MIN_FONT_SIZE_PT = 8;

    /** The largest body text, in points. */
    public static final int MAX_FONT_SIZE_PT = 16;

    private static final LetterPage DEFAULT = new LetterPage(15, 12, 20, 20, 10, null, null, null);

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

    /** @return the families the letter names, the default font left out */
    public Stream<String> fontFamilies() {
        return Stream.of(bodyFont, headerFont, footerFont)
                .filter(Objects::nonNull)
                .filter(family -> !family.isBlank());
    }

    /** @return the page with its family names stripped, a blank one taken as the default font */
    public LetterPage tidied() {
        return new LetterPage(
                marginTopMm,
                marginBottomMm,
                marginLeftMm,
                marginRightMm,
                fontSizePt,
                tidy(bodyFont),
                tidy(headerFont),
                tidy(footerFont));
    }

    private static @Nullable String tidy(@Nullable String family) {
        return family == null || family.isBlank() ? null : family.strip();
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
