/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The font files tests upload and draw with, both under the SIL Open Font License beside them in
 * {@code src/test/resources/fonts}.
 *
 * <p>Noto Sans Lisu has TrueType outlines and prints the Lisu letters ({@link #LISU_TEXT}), which no
 * other font a test meets has, so a document printing them must have embedded it. Noto Sans Lycian has
 * PostScript outlines.
 */
public final class TestFonts {
    /** A word in Lisu letters, which Noto Sans Lisu prints and Liberation Sans does not. */
    public static final String LISU_TEXT = "ꓡꓲꓢꓴ";

    /** The family name Noto Sans Lisu carries. */
    public static final String LISU_FAMILY = "Noto Sans Lisu";

    /** The PostScript name Noto Sans Lisu is embedded under, after a subset's six letter tag. */
    public static final String LISU_POSTSCRIPT = "NotoSansLisu-Regular";

    private TestFonts() {}

    /** @return Noto Sans Lisu, a TrueType font */
    public static byte[] lisu() {
        return read("fonts/NotoSansLisu-Regular.ttf");
    }

    /** @return Noto Sans Lycian, an OpenType font with PostScript outlines */
    public static byte[] lycian() {
        return read("fonts/NotoSansLycian-Regular.otf");
    }

    /**
     * A copy of a font whose licence bits say something else, written into its OS/2 table.
     *
     * @param font   a TrueType or OpenType font
     * @param fsType the licence bits
     * @return the copy
     */
    public static byte[] withFsType(byte[] font, int fsType) {
        var copy = font.clone();
        var buffer = ByteBuffer.wrap(copy);
        int tables = Short.toUnsignedInt(buffer.getShort(4));
        for (int table = 0; table < tables; table++) {
            int record = 12 + table * 16;
            String tag = new String(copy, record, 4, StandardCharsets.US_ASCII);
            if (tag.equals("OS/2")) {
                buffer.putShort(buffer.getInt(record + 8) + 8, (short) fsType);
                return copy;
            }
        }
        throw new IllegalArgumentException("The font has no OS/2 table");
    }

    private static byte[] read(String resource) {
        try (var in = Objects.requireNonNull(TestFonts.class.getClassLoader().getResourceAsStream(resource))) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
