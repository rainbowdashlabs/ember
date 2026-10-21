/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.feature.generator.entity.FontFace;
import dev.chojo.ember.feature.generator.entity.FontStyle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The font every document falls back to: Liberation Sans, shipped with the application
 * ({@code fonts/LiberationSans-Regular.ttf}, under the SIL Open Font License in
 * {@code fonts/Liberation-LICENSE.txt}), so a text reads
 * the same on every installation, whatever fonts the system has. Its file is the one
 * {@link BuiltInFonts} holds for the regular style of the family.
 *
 * <p>Behind it stands DejaVu Sans ({@code fonts/DejaVuSans.ttf}, under the licence in
 * {@code fonts/DejaVu-LICENSE.txt}) for the symbols Liberation Sans lacks, such as the boxes a check answer
 * prints. A letter is a PDF/A, which refuses a character no font can draw, and a server need not have
 * any font of its own that holds them.
 */
public final class BundledFont {
    /** The family name Liberation Sans carries, which a letter asks for. */
    public static final String FAMILY = "Liberation Sans";

    /** The name the file is written under next to a document. */
    public static final String FILE_NAME = "LiberationSans-Regular.ttf";

    /** The family name of the font for the symbols Liberation Sans lacks. */
    public static final String SYMBOL_FAMILY = "DejaVu Sans";

    /** The name the symbol font's file is written under next to a document. */
    public static final String SYMBOL_FILE_NAME = "DejaVuSans.ttf";

    private BundledFont() {}

    /** @return the file, a copy the caller may keep */
    public static byte[] data() {
        return BuiltInFonts.bundledFile(FILE_NAME);
    }

    /** @return the face of the fallback's file */
    public static FontFace face() {
        return BuiltInFonts.fallback().file(FontStyle.REGULAR);
    }

    /** @return the face of the symbol font */
    public static FontFace symbolFace() {
        return BuiltInFonts.symbolFallback();
    }

    /**
     * @return font files by the name they are written under, holding the fallback's and the symbol font's, for
     *     the caller to add to
     */
    public static Map<String, byte[]> files() {
        var files = new LinkedHashMap<String, byte[]>();
        files.put(FILE_NAME, data());
        files.put(SYMBOL_FILE_NAME, BuiltInFonts.bundledFile(SYMBOL_FILE_NAME));
        return files;
    }

    /**
     * Ends a list of family names in the fallback and the symbol font, unless they are in the list already.
     *
     * @param families the names, in the order they are asked for
     */
    public static void appendTo(List<String> families) {
        if (!families.contains(FAMILY)) families.add(FAMILY);
        if (!families.contains(SYMBOL_FAMILY)) families.add(SYMBOL_FAMILY);
    }

    /**
     * Reads a font file the application ships.
     *
     * @param fileName its name under {@code fonts/} in the application's resources
     * @return the file
     */
    static byte[] resource(String fileName) {
        String resource = "fonts/" + fileName;
        try (var in = Objects.requireNonNull(
                BundledFont.class.getClassLoader().getResourceAsStream(resource),
                "The application ships " + resource)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("The font " + resource + " could not be read", e);
        }
    }
}
