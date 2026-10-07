/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import dev.chojo.ember.util.DocumentWord;

import java.util.Locale;
import java.util.Set;

/**
 * When a filled-in text counts as yes, which is what ticks a check field or a check box.
 *
 * <p>A text says yes when it holds anything but the word for no. A yes or no question is filled in as
 * its word ("Ja", "Nein"), so a check field bound to it is ticked exactly where the answer is yes; a
 * field bound to anything else is ticked where the member has that value at all, for example an
 * allergy written in the profile.
 */
public final class YesWords {
    private static final Set<String> NO = Set.of(
            DocumentWord.NO.in("de").toLowerCase(Locale.ROOT),
            DocumentWord.NO.in("en").toLowerCase(Locale.ROOT),
            "false",
            "0");

    private YesWords() {}

    /**
     * @param filled a text with its placeholders filled in
     * @return whether it says yes
     */
    public static boolean saysYes(String filled) {
        String text = filled.strip();
        return !text.isEmpty() && !NO.contains(text.toLowerCase(Locale.ROOT));
    }
}
