/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

/**
 * One style of a font every installation has built in. Nobody uploaded it, so nobody can delete it and
 * it takes no room.
 *
 * <p>A face either ships with the application as a file ({@code bundledFile}), which a letter writes
 * next to itself and a field on an uploaded PDF embeds, or is one Typst carries inside itself, which
 * letters print in without a file and PDF fields cannot.
 *
 * @param internalFamily the family name the face carries, which Typst finds it by
 * @param style          which style of its family it is
 * @param outline        how its glyphs are drawn
 * @param bundledFile    the name of the file under {@code fonts/} in the application's resources, or
 *                       null for a face Typst carries itself
 */
public record BuiltInFace(
        String internalFamily,
        FontStyle style,
        FontOutline outline,
        @Nullable String bundledFile) implements FontFace {

    /** @return whether the face ships with the application as a file */
    public boolean bundled() {
        return bundledFile != null;
    }

    @Override
    public boolean hasFile() {
        return bundled();
    }

    @Override
    public boolean printsOnPdf() {
        return bundled() && outline == FontOutline.TRUETYPE;
    }

    @Override
    public String identity() {
        return "built-in:" + internalFamily + ":" + style;
    }
}
