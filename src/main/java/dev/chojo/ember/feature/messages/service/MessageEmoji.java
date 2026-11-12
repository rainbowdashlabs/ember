/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Whether a reaction is a standard Unicode emoji. There are no custom emoji, so anything else is
 * refused rather than stored as text somebody typed.
 *
 * <p>An emoji is one character as a reader sees it, written with a short run of code points that
 * Unicode marks as emoji or as the parts emoji are
 * built from: joiners, variation selectors, skin tones, keycaps, flags and tags. At least one of them
 * has to be a picture, a flag letter or a keycap, which is what keeps a plain digit or a lone joiner
 * out, since Unicode counts those as emoji parts too.
 */
public final class MessageEmoji {
    /** The most code points one emoji is built from; the longest family and flag sequences stay below it. */
    private static final int MOST_CODE_POINTS = 16;

    /** One character as a reader sees it, however many code points it is written with. */
    private static final Pattern ONE_CHARACTER = Pattern.compile("\\X");

    private static final int KEYCAP = 0x20E3;
    private static final int FIRST_FLAG_LETTER = 0x1F1E6;
    private static final int LAST_FLAG_LETTER = 0x1F1FF;

    private MessageEmoji() {}

    /**
     * Whether the text is one standard emoji.
     *
     * @param text the reaction as sent
     * @return true for an emoji
     */
    public static boolean isStandard(@Nullable String text) {
        if (text == null || text.isEmpty() || text.length() > 32) return false;
        if (!ONE_CHARACTER.matcher(text).matches()) return false;
        int[] codePoints = text.codePoints().toArray();
        if (codePoints.length > MOST_CODE_POINTS) return false;
        boolean pictured = false;
        for (int codePoint : codePoints) {
            if (!Character.isEmoji(codePoint) && !Character.isEmojiComponent(codePoint)) return false;
            pictured |= Character.isExtendedPictographic(codePoint) || isFlagLetter(codePoint) || codePoint == KEYCAP;
        }
        return pictured;
    }

    private static boolean isFlagLetter(int codePoint) {
        return codePoint >= FIRST_FLAG_LETTER && codePoint <= LAST_FLAG_LETTER;
    }
}
