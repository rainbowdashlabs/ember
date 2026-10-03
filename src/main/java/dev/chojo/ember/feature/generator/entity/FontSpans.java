/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * How a text of a letter sets some of its words in a font family of their own: the words wrapped in
 * {@code <span data-font="Family">...</span>}, the family written with the characters HTML reserves
 * escaped.
 *
 * <p>The texts are markdown, which has no word for a font, and inline HTML is what passes the editor's round
 * trip and Pandoc's markdown reader unchanged, the way coloured text does. The conversion to Typst turns each
 * pair into a call to the letter's {@code font} function, which looks the family up when the letter is
 * printed.
 */
public final class FontSpans {
    /** The opening tag of a span that names a family; group 1 is the family as written. */
    private static final Pattern OPENING = Pattern.compile("<span\\b[^>]*?\\sdata-font\\s*=\\s*\"([^\"]*)\"");

    private FontSpans() {}

    /**
     * @param text a text of a template
     * @return the family every font span of it names, in order, a family named twice listed twice
     */
    public static Stream<String> familiesIn(String text) {
        return OPENING.matcher(text)
                .results()
                .map(result -> unescape(result.group(1)).strip())
                .filter(family -> !family.isEmpty());
    }

    private static String unescape(String attribute) {
        return attribute
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }
}
