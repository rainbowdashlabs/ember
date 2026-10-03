/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

/**
 * A field drawn on a page of a PDF template.
 *
 * <p>A text field prints its text with the placeholders filled in, in the default font or in a family
 * of uploaded fonts. A check field prints a cross where its text, filled in, says yes: anything but
 * nothing and the word for no. A signature field becomes an empty PDF signature field for its role and
 * prints as a line.
 *
 * @param kind       what the field does
 * @param rect       where it sits
 * @param text       the text with placeholders, for a text or a check field; null for a signature field
 * @param fontSize   the size of the text in points, for a text field
 * @param align      where the text sits across the box, for a text field
 * @param wrap       whether a text runs onto further lines rather than shrinking to fit one
 * @param role       who signs, for a signature field only
 * @param fontFamily the family of uploaded fonts a text field prints in, or null for the default font
 * @param fontStyle  the style of that family; regular where left out
 */
public record PdfField(
        PdfFieldKind kind,
        FieldRect rect,
        @Nullable String text,
        double fontSize,
        TextAlign align,
        boolean wrap,
        @Nullable SignatureRole role,
        @Nullable String fontFamily,
        @Nullable FontStyle fontStyle) {

    /** The columns {@link #map()} reads, in a form a query can splice in. */
    public static final String COLUMNS =
            "kind, page, x, y, width, height, text, font_size, align, wrap, role, font_family, font_style";

    /** A field printing in the default font. */
    public PdfField(
            PdfFieldKind kind,
            FieldRect rect,
            @Nullable String text,
            double fontSize,
            TextAlign align,
            boolean wrap,
            @Nullable SignatureRole role) {
        this(kind, rect, text, fontSize, align, wrap, role, null, FontStyle.REGULAR);
    }

    public static RowMapping<PdfField> map() {
        return row -> new PdfField(
                row.getEnum("kind", PdfFieldKind.class),
                new FieldRect(
                        row.getInt("page"),
                        row.getDouble("x"),
                        row.getDouble("y"),
                        row.getDouble("width"),
                        row.getDouble("height")),
                row.getString("text"),
                row.getDouble("font_size"),
                row.getEnum("align", TextAlign.class),
                row.getBoolean("wrap"),
                row.getString("role") == null ? null : row.getEnum("role", SignatureRole.class),
                row.getString("font_family"),
                row.getEnum("font_style", FontStyle.class));
    }
}
