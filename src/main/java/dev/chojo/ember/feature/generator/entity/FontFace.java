/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * One style of a font family a template can print in: a file an owner uploaded, or one of the fonts
 * every installation has built in.
 */
public sealed interface FontFace permits DocumentFont, BuiltInFace {
    /** @return which style of its family it is */
    FontStyle style();

    /** @return how its glyphs are drawn */
    FontOutline outline();

    /** @return the family name it carries itself, which Typst finds it by */
    String internalFamily();

    /** @return whether a field on an uploaded PDF can be drawn in it */
    boolean printsOnPdf();

    /** @return whether the server holds a file of it, rather than Typst carrying it inside itself */
    boolean hasFile();

    /**
     * @return what the face is, the same for the same file wherever it is kept, so a sample drawn in
     *         it can be kept and found again
     */
    String identity();
}
