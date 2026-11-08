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
 * @param pdfOnly        whether only PDF fields print in it, as for a light cut, which Typst knows by the name
 *                       of its family and not by its own, so a letter naming it would not find it
 */
public record BuiltInFace(
        String internalFamily,
        FontStyle style,
        FontOutline outline,
        @Nullable String bundledFile,
        boolean pdfOnly) implements FontFace {

    /**
     * A face letters and PDF fields alike print in, where it can.
     *
     * @param internalFamily the family name the face carries, which Typst finds it by
     * @param style          which style of its family it is
     * @param outline        how its glyphs are drawn
     * @param bundledFile    the name of the file under {@code fonts/}, or null for a face Typst carries itself
     */
    public BuiltInFace(String internalFamily, FontStyle style, FontOutline outline, @Nullable String bundledFile) {
        this(internalFamily, style, outline, bundledFile, false);
    }

    @Override
    public boolean printsInLetters() {
        return !pdfOnly;
    }

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

    /**
     * {@inheritDoc}
     *
     * <p>A face only PDF fields print in carries the name of its family, the same as a face of the family
     * itself, so it is told apart by its file.
     */
    @Override
    public String identity() {
        return pdfOnly ? "built-in:" + bundledFile : "built-in:" + internalFamily + ":" + style;
    }
}
