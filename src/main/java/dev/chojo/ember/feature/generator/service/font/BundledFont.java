/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * The font every document falls back to: Liberation Sans, shipped with the application
 * ({@code fonts/LiberationSans-Regular.ttf}, under the SIL Open Font License in
 * {@code fonts/Liberation-LICENSE.txt}), so a text reads
 * the same on every installation, whatever fonts the system has.
 */
public final class BundledFont {
    /** The family name Liberation Sans carries, which a letter asks for. */
    public static final String FAMILY = "Liberation Sans";

    /** The name the file is written under next to a document. */
    public static final String FILE_NAME = "LiberationSans-Regular.ttf";

    private static final byte[] DATA = resource(FILE_NAME);

    private BundledFont() {}

    /** @return the file, a copy the caller may keep */
    public static byte[] data() {
        return DATA.clone();
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
