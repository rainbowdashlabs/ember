/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.util.Set;

/**
 * Maps a stored (user-controlled) MIME type to a content type that is safe to
 * serve back with {@code Content-Disposition: inline}.
 *
 * <p>The allow-list contains only types the browser cannot execute as script
 * - raster images and PDF. Anything else (HTML, XHTML, SVG, JavaScript, JSON,
 * unknown) is rewritten to {@code application/octet-stream} so the browser
 * downloads instead of rendering it.
 *
 * <p>Routes that serve user-uploaded bytes must call
 * {@link #safeContentType(String)} before setting the response content type,
 * and pair it with {@link #isInlineSafe(String)} to decide between
 * {@code inline} and {@code attachment} disposition.
 */
public final class SafeInlineMime {

    /**
     * MIME types that are safe to serve with {@code Content-Disposition: inline}
     * in the user's browser. Lowercase, exact-match.
     */
    public static final Set<String> INLINE_ALLOWED =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif", "application/pdf");

    /**
     * Fallback content type for anything not on {@link #INLINE_ALLOWED}.
     */
    public static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    private SafeInlineMime() {}

    /**
     * Returns the input {@code storedMime} (trimmed, lowercased) when it is on
     * the inline allow-list. Otherwise returns {@link #FALLBACK_CONTENT_TYPE}.
     *
     * @param storedMime the MIME the upload claimed, as persisted in the
     *                   database; may be {@code null} or blank.
     */
    public static String safeContentType(String storedMime) {
        String normalised = normalise(storedMime);
        return INLINE_ALLOWED.contains(normalised) ? normalised : FALLBACK_CONTENT_TYPE;
    }

    /**
     * Returns {@code true} when {@code storedMime} is on the inline allow-list.
     */
    public static boolean isInlineSafe(String storedMime) {
        return INLINE_ALLOWED.contains(normalise(storedMime));
    }

    private static String normalise(String mime) {
        if (mime == null) return "";
        String trimmed = mime.trim().toLowerCase();
        int semicolon = trimmed.indexOf(';');
        return semicolon < 0 ? trimmed : trimmed.substring(0, semicolon).trim();
    }
}
