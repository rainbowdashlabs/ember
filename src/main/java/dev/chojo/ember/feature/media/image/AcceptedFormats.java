/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.image;

import java.util.Locale;

/**
 * Which of the stored formats a reader can be handed.
 *
 * <p>PNG, JPEG and GIF are read by everything. WebP is the one format a size may be written in that
 * an older reader cannot show, so it is the only one that is ever held back.
 */
public enum AcceptedFormats {
    /** Every stored format, WebP included: every browser, and the PDF exports. */
    EVERY_FORMAT,
    /** Everything but WebP. */
    WITHOUT_WEBP;

    /**
     * What a request's {@code Accept} header says it takes. Only an explicit {@code image/webp}
     * counts, since a bare wildcard is also what a client sends that has never heard of WebP.
     *
     * @param acceptHeader the header, possibly {@code null}
     * @return the formats the reader can be handed
     */
    public static AcceptedFormats fromAcceptHeader(String acceptHeader) {
        if (acceptHeader == null) return WITHOUT_WEBP;
        return acceptHeader.toLowerCase(Locale.ROOT).contains(ImageFormat.WEBP.mimeType())
                ? EVERY_FORMAT
                : WITHOUT_WEBP;
    }

    /**
     * Whether a stored file may be handed to the reader.
     *
     * @param file the stored file
     * @return false only for a WebP file and a reader that does not take WebP
     */
    public boolean accepts(VariantFile file) {
        return this == EVERY_FORMAT
                || file.format().filter(ImageFormat.WEBP::equals).isEmpty();
    }
}
