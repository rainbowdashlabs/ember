/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

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
 */
public final class BundledFont {
    /** The family name Liberation Sans carries, which a letter asks for. */
    public static final String FAMILY = "Liberation Sans";

    /** The name the file is written under next to a document. */
    public static final String FILE_NAME = "LiberationSans-Regular.ttf";

    private BundledFont() {}

    /** @return the file, a copy the caller may keep */
    public static byte[] data() {
        return BuiltInFonts.bundledFile(FILE_NAME);
    }

    /** @return font files by the name they are written under, holding the fallback's, for the caller to add to */
    public static Map<String, byte[]> files() {
        var files = new LinkedHashMap<String, byte[]>();
        files.put(FILE_NAME, data());
        return files;
    }

    /**
     * Ends a list of family names in the fallback, unless it is in the list already.
     *
     * @param families the names, in the order they are asked for
     */
    public static void appendTo(List<String> families) {
        if (!families.contains(FAMILY)) families.add(FAMILY);
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
