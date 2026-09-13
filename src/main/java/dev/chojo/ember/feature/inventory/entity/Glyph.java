/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import io.javalin.http.BadRequestResponse;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The picture a row of gear is drawn with: a FontAwesome name and the colour it is drawn in.
 *
 * <p>Both halves are optional and absent is the ordinary state. A reader falls back from the kind to
 * the inventory to a plain shape, so the pair travels together rather than as two fields that can be
 * half answered.
 *
 * <p>The name is not checked against anything. The catalogue of offerable pictures belongs to the
 * frontend, and a name it has retired must not make a save fail years later. The colour is checked,
 * because an unparseable colour would be painted.
 *
 * @param icon  the FontAwesome name, or {@code null} when nobody chose one
 * @param color the colour as {@code #rrggbb} in lower case, or {@code null} for the muted neutral
 */
public record Glyph(String icon, String color) {
    /**
     * Nothing chosen, which is what every row looks like before anybody picks a picture.
     */
    public static final Glyph NONE = new Glyph(null, null);

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    /**
     * A glyph from what somebody sent, with blanks read as absent and the colour lowered.
     *
     * <p>A blank string and a missing field mean the same thing to a reader, and leaving both forms in
     * the column would make every comparison ask about whitespace.
     *
     * @param icon  the FontAwesome name, or {@code null}
     * @param color the colour, or {@code null}
     * @return the pair, with empty halves as {@code null}
     */
    public static Glyph of(String icon, String color) {
        return new Glyph(trimmedOrNull(icon), lowered(trimmedOrNull(color)));
    }

    /**
     * Whether the colour is one that can be painted, an absent colour included.
     *
     * @return {@code false} only for a colour that is present and not {@code #rrggbb}
     */
    public boolean validColor() {
        return color == null || HEX_COLOR.matcher(color).matches();
    }

    /**
     * Refuses a colour nothing could paint, which is the one half of a glyph worth checking.
     *
     * @throws BadRequestResponse when a colour is present and is not {@code #rrggbb}
     */
    public void requirePaintable() {
        if (!validColor()) {
            throw new BadRequestResponse("A colour is written as #rrggbb, and '%s' is not".formatted(color));
        }
    }

    /**
     * The same glyph in the one spelling a column keeps, refusing a colour nothing could paint.
     *
     * <p>What reaches a service has been built by a caller rather than by {@link #of}, so it is
     * normalised here as well. Two rows holding {@code #2563EB} and {@code #2563eb} would be two
     * colours to everything that compares them and one colour to everybody looking at them.
     *
     * @return the normalised pair
     * @throws BadRequestResponse when a colour is present and is not {@code #rrggbb}
     */
    public Glyph paintable() {
        Glyph normalised = of(icon, color);
        normalised.requirePaintable();
        return normalised;
    }

    private static String trimmedOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String lowered(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
