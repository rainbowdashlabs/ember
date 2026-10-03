/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a placeholder is written in a template: {@code {{key}}}, or {@code {{key|format}}} for a date in a
 * format of its own ({@link PlaceholderKey}), with spaces inside the braces allowed.
 *
 * <p>Keys are letters, digits, dots and underscores. A format takes anything but braces, bars, quotes,
 * backslashes and line breaks, up to twice the length an own format may have, so a mistyped one is read
 * as a placeholder and refused by name rather than printed as text. Neither ever holds a quote or a
 * backslash, which is what lets a key travel into Typst inside a string without escaping.
 *
 * <p>The key a text names is the key and, where there is one, a bar and the format without the spaces
 * around it; that is the key every value and label is looked up by.
 */
public final class PlaceholderTokens {
    /** One placeholder; group 1 is its key and group 2 its format, where it has one. */
    public static final Pattern TOKEN = Pattern.compile(
            "\\{\\{\\s*([A-Za-z0-9_.]+)\\s*(?:\\|([^{}|\"\\\\\\r\\n]{0," + DateFormat.MAX_LENGTH * 2 + "}))?}}");

    private PlaceholderTokens() {}

    /**
     * @param text a text of a template
     * @return the keys it names, in the order they first appear
     */
    public static Set<String> keysIn(String text) {
        return new LinkedHashSet<>(occurrences(text));
    }

    /**
     * @param text a text of a template
     * @return the key of every placeholder it holds, in order, a key named twice listed twice
     */
    public static List<String> occurrences(String text) {
        var keys = new ArrayList<String>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            keys.add(keyOf(matcher));
        }
        return keys;
    }

    private static String keyOf(Matcher matcher) {
        String format = matcher.group(2);
        return PlaceholderKey.written(matcher.group(1), format == null ? null : format.strip());
    }

    /**
     * Replaces every placeholder of a text.
     *
     * @param text        a text of a template
     * @param replacement what each key is replaced by
     * @return the text with every placeholder replaced
     */
    public static String replace(String text, Function<String, String> replacement) {
        Matcher matcher = TOKEN.matcher(text);
        var out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement.apply(keyOf(matcher))));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Fills the placeholders of a text with their values, leaving a placeholder without one empty.
     *
     * @param text   a text of a template
     * @param values the values by key
     * @return the text as it is printed
     */
    public static String fill(String text, Map<String, String> values) {
        return replace(text, key -> values.getOrDefault(key, ""));
    }
}
