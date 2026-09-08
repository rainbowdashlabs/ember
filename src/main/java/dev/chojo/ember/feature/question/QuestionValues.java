/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * How an answer that names members is written down, and how it is read back.
 *
 * <p>One member is a bare number, several are a JSON array, and both shapes are tolerated on the way
 * in because both have been stored. Appointments and attendance sheets each had their own copy of
 * this, which is two answers to a question that has one.
 */
public final class QuestionValues {

    private QuestionValues() {}

    /**
     * The members an answer names, in the order it names them.
     *
     * <p>Forgiving on purpose: a stored answer is whatever was once written into it, and a list half
     * of which reads as members is still those members to everybody showing it. Checking an answer
     * on its way in asks the stricter question, which is {@link #namesOnlyMembers(String)}.
     *
     * @param value the answer as it is stored
     * @return the member ids it names, empty where it names none
     */
    public static List<Integer> memberIds(String value) {
        var ids = new ArrayList<Integer>();
        for (String part : parts(value)) {
            try {
                ids.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return ids;
    }

    /**
     * Whether an answer names members and nothing else, which is the stricter question checking one
     * asks: reading is forgiving so that an old answer still shows, and writing is not.
     *
     * @param value the answer being given
     * @return true where every part of it is a member
     */
    public static boolean namesOnlyMembers(String value) {
        var parts = parts(value);
        if (parts.isEmpty()) return false;
        return parts.size() == memberIds(value).size();
    }

    /** The pieces an answer is made of, whichever of the two shapes it was written in. */
    private static List<String> parts(String value) {
        if (value == null || value.isBlank()) return List.of();
        String cleaned = value.trim();
        if (cleaned.startsWith("[")) {
            cleaned = cleaned.replaceAll("[\\[\\]\"\\s]", "");
            if (cleaned.isBlank()) return List.of();
            return Arrays.stream(cleaned.split(","))
                    .map(String::trim)
                    .filter(part -> !part.isBlank())
                    .toList();
        }
        cleaned = cleaned.replace("\"", "").trim();
        return cleaned.isBlank() ? List.of() : List.of(cleaned);
    }

    /**
     * An answer as plain text, whichever way the feature that holds it writes it down.
     *
     * <p>Three of them keep their answers as JSON, so a line of text arrives wrapped in quotes and a
     * date reads as {@code "2011-09-01"} rather than as a date. Checking one has to see what
     * somebody actually typed.
     *
     * @param stored the answer as the feature holds it
     * @return the same answer with nothing around it
     */
    public static String text(String stored) {
        if (stored == null) return "";
        String trimmed = stored.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /** Several members as an answer stores them. */
    public static String formatMembers(List<Integer> ids) {
        var written = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) written.append(",");
            written.append(ids.get(i));
        }
        return written.append("]").toString();
    }

    /** One member as an answer stores them, empty where there is none. */
    public static String formatMember(Integer id) {
        return id == null ? "" : String.valueOf(id);
    }
}
