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
 * The files of one family at one owner, which a template sees as one font.
 *
 * <p>A family is a unit: where several owners have a family of the same name, the nearest one's files
 * are taken, all of them, and the others' are not mixed in.
 *
 * @param name   the family name, as the owner spelled it
 * @param origin who uploaded it
 * @param files  the file of every style the family has
 */
public record FontFamily(String name, FontOrigin origin, Map<FontStyle, DocumentFont> files) {

    public FontFamily {
        var copy = new EnumMap<FontStyle, DocumentFont>(FontStyle.class);
        copy.putAll(files);
        files = Collections.unmodifiableMap(copy);
    }

    /**
     * The file a style is drawn from: that style where the family has it, else its regular one, else
     * whichever it has.
     *
     * @param style the style asked for
     * @return the file
     */
    public DocumentFont file(FontStyle style) {
        var exact = files.get(style);
        if (exact != null) return exact;
        var regular = files.get(FontStyle.REGULAR);
        if (regular != null) return regular;
        return files.values().iterator().next();
    }

    /**
     * The file a field on an uploaded PDF is drawn from, which has to have TrueType outlines.
     *
     * @param style the style asked for
     * @return the file, or empty where the family cannot print there
     */
    public Optional<DocumentFont> pdfFile(FontStyle style) {
        var file = file(style);
        return file.outline() == FontOutline.TRUETYPE ? Optional.of(file) : Optional.empty();
    }

    /** @return whether fields on an uploaded PDF can print in the family */
    public boolean printsOnPdf() {
        return files.values().stream().allMatch(file -> file.outline() == FontOutline.TRUETYPE);
    }

    /** @return the styles the family has, in their natural order */
    public List<FontStyle> styles() {
        return List.copyOf(files.keySet());
    }

    /** @return the family names its files carry themselves, regular first, each once */
    public List<String> internalFamilies() {
        return files.values().stream()
                .map(DocumentFont::internalFamily)
                .distinct()
                .toList();
    }
}
