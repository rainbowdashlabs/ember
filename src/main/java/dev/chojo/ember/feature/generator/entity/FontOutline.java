/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * How the glyphs of a font are drawn, which decides where it can print.
 *
 * <p>Letters print both kinds. Fields on an uploaded PDF are embedded as a subset by PDFBox, which takes
 * TrueType outlines only, so a family of PostScript outlines is not offered there.
 */
public enum FontOutline {
    /** TrueType outlines: every {@code .ttf} file and some {@code .otf} files. */
    TRUETYPE,
    /** PostScript (CFF) outlines, as most {@code .otf} files have. */
    CFF
}
