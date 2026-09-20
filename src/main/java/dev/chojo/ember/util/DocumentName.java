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
 * The name a reader finds in their downloads folder.
 *
 * <p>A name is built from parts and reads {@code Was - Kontext - Zeitraum.ext}, so an attendance
 * report for one group in January is {@code Anwesenheit - Jugend - Januar 2026.pdf}. The parts are the
 * words the interface already uses, in the station's own language, because a file named in a second
 * vocabulary is a file nobody recognises.
 *
 * <p>Parts that come from data rather than from the product, an appointment's title or a member's
 * name, cannot be trusted to be a name. A slash would read as a folder, a colon is refused outright on
 * some systems, and a title long enough fills the whole line and hides the rest. Such a part is
 * therefore trimmed of what a filesystem refuses and cut on a word boundary, and where nothing usable
 * survives it is left out rather than replaced with something meaningless.
 *
 * <p>A name never carries an id. A reader has no use for the number a row happens to have, and two
 * exports of the same thing are told apart by what is in them, not by what the database calls them.
 */
public final class DocumentName {

    /** What separates the parts of a name, in the product and in a filename alike. */
    private static final String SEPARATOR = " - ";

    /**
     * How much of a borrowed title survives.
     *
     * <p>Long enough for a title a reader would recognise, short enough that the whole name stays
     * inside the limit every common filesystem agrees on once an extension and a date are added.
     */
    private static final int MAX_BORROWED_LENGTH = 60;

    private static final String FALLBACK = "Export";

    private DocumentName() {}

    /**
     * Joins the parts of a name and appends the extension.
     *
     * <p>Empty and blank parts are left out, so a caller may pass a part it does not always have
     * without asking whether it has one. Where nothing at all survives, the name still says something
     * rather than being only an extension.
     *
     * @param extension the extension without its dot, for example {@code pdf}
     * @param parts     the parts of the name, in reading order
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

    /**
     * Makes a part out of something a person wrote, which may be anything at all.
     *
     * <p>Returns an empty string where nothing usable is left, which {@link #of} then leaves out.
     *
     * @param borrowed a title, a name, or whatever the data holds
     */
    public static String part(String borrowed) {
        if (borrowed == null) return "";
        String cleaned = strip(Normalizer.normalize(borrowed, Normalizer.Form.NFC));
        return cut(cleaned.trim());
    }

    /**
     * Replaces what a filesystem or a path refuses with a space, and collapses what is left.
     *
     * <p>A space rather than nothing, because what is removed often stood between two words: a title
     * broken over two lines would otherwise come back with those lines run together into one word.
     *
     * <p>The separator is stripped from a borrowed part as well: a title that already contains one
     * would otherwise read as two parts of the name and say something the export does not mean.
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
