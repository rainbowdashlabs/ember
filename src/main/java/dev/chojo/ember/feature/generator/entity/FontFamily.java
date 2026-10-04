/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The files of one family at one owner, or one of the built-in families, which a template sees as one
 * font.
 *
 * <p>A family is a unit: where several owners have a family of the same name, the nearest one's files
 * are taken, all of them, and the others' are not mixed in. A built-in family is the farthest of all.
 *
 * @param name   the family name, as the owner spelled it
 * @param origin who uploaded it, or that it is built in
 * @param files  the face of every style the family has
 */
public record FontFamily(String name, FontOrigin origin, Map<FontStyle, FontFace> files) {

    public FontFamily {
        var copy = new EnumMap<FontStyle, FontFace>(FontStyle.class);
        copy.putAll(files);
        files = Collections.unmodifiableMap(copy);
    }

    /**
     * The face a style is drawn from: that style where the family has it, else its regular one, else
     * whichever it has.
     *
     * @param style the style asked for
     * @return the face
     */
    public FontFace file(FontStyle style) {
        var exact = files.get(style);
        if (exact != null) return exact;
        var regular = files.get(FontStyle.REGULAR);
        if (regular != null) return regular;
        return files.values().iterator().next();
    }

    /**
     * The face a field on an uploaded PDF is drawn from, which has to be a file with TrueType outlines.
     *
     * @param style the style asked for
     * @return the face, or empty where the family cannot print there
     */
    public Optional<FontFace> pdfFile(FontStyle style) {
        var file = file(style);
        return file.printsOnPdf() ? Optional.of(file) : Optional.empty();
    }

    /** @return whether fields on an uploaded PDF can print in the family */
    public boolean printsOnPdf() {
        return files.values().stream().allMatch(FontFace::printsOnPdf);
    }

    /** @return whether the server holds a file of every style, which the template editor can load */
    public boolean hasFiles() {
        return files.values().stream().allMatch(FontFace::hasFile);
    }

    /** @return the styles the family has, in their natural order */
    public List<FontStyle> styles() {
        return List.copyOf(files.keySet());
    }
}
