/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The filename of an export, built from parts in the station's language: {@code Anwesenheit - Jugend -
 * Januar 2026.pdf}, never an id.
 *
 * <p>A part taken from data (a title, a member's name) loses what a filesystem refuses and is cut on a word
 * boundary, since a slash reads as a folder and a long title hides the rest of the name.
 */
public final class DocumentName {

    private static final String SEPARATOR = " - ";

    /** Short enough that a name with a date and an extension stays inside every filesystem's limit. */
    private static final int MAX_BORROWED_LENGTH = 60;

    private static final String FALLBACK = "Export";

    private DocumentName() {}

    /**
     * Joins the non-blank parts and appends the extension; with no part left the name is {@code Export}.
     *
     * @param extension the extension without its dot, for example {@code pdf}
     */
    public static String of(String extension, String... parts) {
        List<String> kept = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part == null) continue;
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) kept.add(trimmed);
        }
        if (kept.isEmpty()) kept.add(FALLBACK);
        return String.join(SEPARATOR, kept) + "." + extension;
    }

    /** Makes a part out of text a person wrote; empty where nothing usable is left, which {@link #of} skips. */
    public static String part(String borrowed) {
        if (borrowed == null) return "";
        String cleaned = strip(Normalizer.normalize(borrowed, Normalizer.Form.NFC));
        return cut(cleaned.trim());
    }

    /**
     * Replaces refused characters and the part separator with a space, since they often stood between two
     * words, and collapses the spaces.
     */
    private static String strip(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c < 0x20 || c == 0x7F || REFUSED.indexOf(c) >= 0) {
                out.append(' ');
                continue;
            }
            out.append(c);
        }
        return collapse(out.toString().replace(SEPARATOR, " "));
    }

    /** Characters a name cannot carry on Windows, on a Mac, or in a path anywhere. */
    private static final String REFUSED = "/\\:*?\"<>|";

    private static String collapse(String input) {
        return String.join(
                " ",
                Arrays.stream(input.split("\\s+")).filter(s -> !s.isEmpty()).toList());
    }

    /** Cuts an over-long part on a word boundary, falling back to a hard cut for a single long word. */
    private static String cut(String input) {
        if (input.length() <= MAX_BORROWED_LENGTH) return input;
        String head = input.substring(0, MAX_BORROWED_LENGTH);
        int lastSpace = head.lastIndexOf(' ');
        return lastSpace > MAX_BORROWED_LENGTH / 2 ? head.substring(0, lastSpace) : head.trim();
    }
}
