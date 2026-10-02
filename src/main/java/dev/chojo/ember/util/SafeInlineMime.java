/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * The content type user-uploaded bytes are served under: raster images and PDF, which a browser cannot run
 * as script, keep theirs; everything else (HTML, SVG, JavaScript, unknown) becomes
 * {@code application/octet-stream} and downloads. Routes serving uploads pair {@link #safeContentType} with
 * {@link #isInlineSafe} to choose the disposition.
 */
public final class SafeInlineMime {

    /** The types safe to serve inline, lower case, matched exactly. */
    public static final Set<String> INLINE_ALLOWED =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif", "application/pdf");

    public static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    private SafeInlineMime() {}

    /** The stored type, trimmed and lower-cased, when it is allowed inline, else the fallback. */
    public static String safeContentType(@Nullable String storedMime) {
        String normalised = normalise(storedMime);
        return INLINE_ALLOWED.contains(normalised) ? normalised : FALLBACK_CONTENT_TYPE;
    }

    /** Whether the stored type may be served inline. */
    public static boolean isInlineSafe(@Nullable String storedMime) {
        return INLINE_ALLOWED.contains(normalise(storedMime));
    }

    private static String normalise(@Nullable String mime) {
        if (mime == null) return "";
        String trimmed = mime.trim().toLowerCase();
        int semicolon = trimmed.indexOf(';');
        return semicolon < 0 ? trimmed : trimmed.substring(0, semicolon).trim();
    }
}
