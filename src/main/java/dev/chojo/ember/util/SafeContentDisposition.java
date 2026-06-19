/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.nio.charset.StandardCharsets;

/**
 * Builds an RFC 6266 {@code Content-Disposition} header for a download whose
 * filename is partially user-controlled.
 *
 * <p>The output combines a sanitised ASCII {@code filename="..."} fallback
 * with a {@code filename*=UTF-8''...} percent-encoded form for non-ASCII
 * characters. The two forms are required for legacy browsers and modern
 * browsers respectively; together they cover every UA without leaving the
 * server vulnerable to header injection (CR / LF / quote / backslash are
 * stripped from both forms before the header value is composed).
 *
 * <p>Disposition selection (inline vs attachment) is the caller's
 * responsibility — see {@link SafeInlineMime#isInlineSafe(String)}.
 */
public final class SafeContentDisposition {

    private static final String FALLBACK_NAME = "download";

    private SafeContentDisposition() {}

    /**
     * Disposition type.
     */
    public enum Disposition {
        /** Render the file in the browser viewport (PDF / image preview). */
        INLINE("inline"),
        /** Force the browser's save dialog. */
        ATTACHMENT("attachment");

        private final String token;

        Disposition(String token) {
            this.token = token;
        }
    }

    /**
     * Returns a {@code Content-Disposition} header value of the form
     * {@code <inline|attachment>; filename="<ascii>"; filename*=UTF-8''<encoded>}.
     * When the filename is blank or scrubs down to nothing, a generic
     * {@value #FALLBACK_NAME} is used.
     *
     * @param disposition whether the response should render inline or download.
     * @param filename    the user-facing filename; may contain non-ASCII.
     */
    public static String build(Disposition disposition, String filename) {
        String safe = scrub(filename);
        String ascii = asciiFallback(safe);
        if (ascii.isEmpty()) ascii = FALLBACK_NAME;
        String encoded = percentEncode(safe);
        return disposition.token + "; filename=\"" + ascii + "\"" + "; filename*=UTF-8''" + encoded;
    }

    private static String scrub(String input) {
        if (input == null) return "";
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\r' || c == '\n' || c == '"' || c == '\\' || c == '\0') continue;
            if (c < 0x20 || c == 0x7F) continue;
            out.append(c);
        }
        return out.toString().trim();
    }

    private static String asciiFallback(String safe) {
        StringBuilder out = new StringBuilder(safe.length());
        for (int i = 0; i < safe.length(); i++) {
            char c = safe.charAt(i);
            if (c < 0x80) {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        return out.toString().trim();
    }

    private static String percentEncode(String safe) {
        StringBuilder out = new StringBuilder(safe.length() * 2);
        byte[] bytes = safe.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            int u = b & 0xFF;
            if (isUnreservedFilenameByte(u)) {
                out.append((char) u);
            } else {
                out.append('%');
                out.append(hex((u >> 4) & 0xF));
                out.append(hex(u & 0xF));
            }
        }
        return out.toString();
    }

    private static boolean isUnreservedFilenameByte(int u) {
        if (u >= 'A' && u <= 'Z') return true;
        if (u >= 'a' && u <= 'z') return true;
        if (u >= '0' && u <= '9') return true;
        return u == '!' || u == '#' || u == '$' || u == '&' || u == '+' || u == '-' || u == '.' || u == '^' || u == '_'
                || u == '`' || u == '|' || u == '~';
    }

    private static char hex(int nibble) {
        return (char) (nibble < 10 ? '0' + nibble : 'A' + (nibble - 10));
    }
}
