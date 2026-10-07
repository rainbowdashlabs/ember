/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.generator.entity.WebFontFormat;
import dev.chojo.ember.util.ByteSignature;

import java.nio.ByteBuffer;

/**
 * Reads what an uploaded web version of a font style is, refusing one that is no font.
 *
 * <p>A WOFF2 or WOFF file is told by its signature and taken where its header is complete and names the
 * length the file has; nothing else of it is parsed, since the browser that loads it reads it. A
 * TrueType or OpenType file is read in full, as an uploaded font is ({@link FontFiles}).
 */
public final class WebFontFiles {
    /** The size of a WOFF2 header, the least a WOFF2 file holds. */
    private static final int WOFF2_HEADER = 48;

    /** The size of a WOFF header, the least a WOFF file holds. */
    private static final int WOFF_HEADER = 44;

    /** Where both headers keep the length of the whole file. */
    private static final int LENGTH_OFFSET = 8;

    private WebFontFiles() {}

    /**
     * Reads a web version.
     *
     * @param data the file
     * @return what it is
     * @throws RefusalResponse {@code DOCUMENT_WEB_FONT_NOT_A_FONT} for a file that is none of the formats,
     *                         {@code DOCUMENT_FONT_EMBEDDING_FORBIDDEN} for a font whose licence forbids embedding
     */
    public static WebFontFormat inspect(byte[] data) {
        if (ByteSignature.startsWith(data, "wOF2")) return complete(data, WOFF2_HEADER, WebFontFormat.WOFF2);
        if (ByteSignature.startsWith(data, "wOFF")) return complete(data, WOFF_HEADER, WebFontFormat.WOFF);
        try {
            return WebFontFormat.of(FontFiles.inspect(data).outline());
        } catch (RefusalResponse refused) {
            if (refused.refusal() == DocumentRefusal.DOCUMENT_FONT_NOT_A_FONT) {
                throw DocumentRefusal.DOCUMENT_WEB_FONT_NOT_A_FONT.raise();
            }
            throw refused;
        }
    }

    /**
     * @param data a file
     * @return whether it is a WOFF2 file with a complete header that names the length it has
     */
    static boolean woff2(byte[] data) {
        return ByteSignature.startsWith(data, "wOF2") && complete(data, WOFF2_HEADER);
    }

    private static boolean complete(byte[] data, int header) {
        return data.length >= header && ByteBuffer.wrap(data, LENGTH_OFFSET, 4).getInt() == data.length;
    }

    private static WebFontFormat complete(byte[] data, int header, WebFontFormat format) {
        if (!complete(data, header)) throw DocumentRefusal.DOCUMENT_WEB_FONT_NOT_A_FONT.raise();
        return format;
    }
}
