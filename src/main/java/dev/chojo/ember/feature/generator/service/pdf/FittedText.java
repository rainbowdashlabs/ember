/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A text laid out to fit a box: cut into lines and drawn as large as the box allows, up to the size
 * the field asks for.
 *
 * <p>A field that does not wrap keeps its text on one line and shrinks it until it fits across. A
 * field that wraps breaks it at spaces, and inside a word only where the word alone is wider than the
 * box, and shrinks it until every line fits down the box. Below {@link #MIN_SIZE} nothing shrinks
 * further; what still does not fit is cut off at the edge of the box.
 *
 * @param lines       the lines, top first
 * @param size        the size the text is drawn at, in points
 * @param unprintable the characters no font could print
 */
public record FittedText(List<FontChain.Shaped> lines, float size, Set<String> unprintable) {
    /** The smallest size a text shrinks to. */
    public static final float MIN_SIZE = 4f;

    /** The room between the edge of the box and the text, in points. */
    public static final float PADDING = 1.5f;

    /** The distance between two baselines, as a share of the size. */
    public static final float LEADING = 1.15f;

    private static final float STEP = 0.5f;
    private static final Pattern BREAK = Pattern.compile("\\r\\n|\\r|\\n");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /**
     * @param fonts    the fonts to draw in
     * @param text     the text, line breaks included
     * @param width    the width of the box
     * @param height   the height of the box
     * @param maxSize  the size the field asks for
     * @param wrap     whether the text may run onto further lines
     * @return the text laid out
     */
    public static FittedText fit(FontChain fonts, String text, float width, float height, float maxSize, boolean wrap) {
        float room = Math.max(width - 2 * PADDING, 0);
        var unprintable = new LinkedHashSet<String>();
        List<FontChain.Shaped> lines = List.of();
        float size = Math.max(maxSize, MIN_SIZE);
        for (; size >= MIN_SIZE; size -= STEP) {
            lines = wrap ? wrapped(fonts, text, room, size) : List.of(fonts.shape(oneLine(text)));
            if (fits(lines, room, height, size)) break;
        }
        size = Math.max(size, MIN_SIZE);
        lines.forEach(line -> unprintable.addAll(line.unprintable()));
        return new FittedText(lines, size, unprintable);
    }

    private static boolean fits(List<FontChain.Shaped> lines, float room, float height, float size) {
        float needed = size + (lines.size() - 1) * size * LEADING;
        if (needed > height) return false;
        return lines.stream().allMatch(line -> line.width(size) <= room);
    }

    private static String oneLine(String text) {
        return SPACES.matcher(text).replaceAll(" ").strip();
    }

    private static List<FontChain.Shaped> wrapped(FontChain fonts, String text, float room, float size) {
        var lines = new ArrayList<FontChain.Shaped>();
        for (String paragraph : BREAK.split(text, -1)) {
            var line = new StringBuilder();
            for (String word : SPACES.split(paragraph.strip())) {
                if (word.isEmpty()) continue;
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (fonts.shape(candidate).width(size) <= room) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) lines.add(fonts.shape(line.toString()));
                line.setLength(0);
                line.append(breakWord(fonts, word, room, size, lines));
            }
            lines.add(fonts.shape(line.toString()));
        }
        return lines;
    }

    /**
     * Breaks a word wider than the box into lines of their own, answering the part that is left over.
     */
    private static String breakWord(
            FontChain fonts, String word, float room, float size, List<FontChain.Shaped> lines) {
        String rest = word;
        while (fonts.shape(rest).width(size) > room && rest.codePointCount(0, rest.length()) > 1) {
            int cut = rest.length();
            while (rest.codePointCount(0, cut) > 1
                    && fonts.shape(rest.substring(0, cut)).width(size) > room) {
                cut = rest.offsetByCodePoints(cut, -1);
            }
            lines.add(fonts.shape(rest.substring(0, cut)));
            rest = rest.substring(cut);
        }
        return rest;
    }
}
