/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

/**
 * One cell of a letterhead's header or footer.
 *
 * @param kind          what the cell holds
 * @param mediaHash     the content hash of the picture in the station's media library, for
 *                      {@link LetterCellKind#IMAGE} only
 * @param text          the lines of text with their placeholders, for {@link LetterCellKind#TEXT} only
 * @param align         where the content sits within the cell
 * @param imageHeightMm how tall a picture or the logo is drawn, in millimetres
 */
public record LetterCell(
        LetterCellKind kind,
        @Nullable String mediaHash,
        @Nullable String text,
        LetterCellAlign align,
        int imageHeightMm) {

    /** How tall a picture is drawn where nothing else was said. */
    public static final int DEFAULT_IMAGE_HEIGHT_MM = 18;

    /** A cell holding nothing. */
    public static LetterCell empty() {
        return new LetterCell(LetterCellKind.EMPTY, null, null, LetterCellAlign.LEFT, DEFAULT_IMAGE_HEIGHT_MM);
    }
}
