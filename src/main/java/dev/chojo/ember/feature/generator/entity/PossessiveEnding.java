/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * The ending a German possessive takes after the word it belongs to: "sein" becomes "seine",
 * "seinem", "seinen", "seiner", "seines". It is added to a pronoun only; a name in the genitive
 * ("Lenas") takes none.
 */
public enum PossessiveEnding {
    NONE(""),
    E("e"),
    EM("em"),
    EN("en"),
    ER("er"),
    ES("es");

    private final String suffix;

    PossessiveEnding(String suffix) {
        this.suffix = suffix;
    }

    /** @return the letters added to the pronoun, nothing for {@link #NONE} */
    public String suffix() {
        return suffix;
    }

    /**
     * @param suffix the letters as a placeholder key writes them
     * @return the ending of those letters, or empty where there is none
     */
    public static Optional<PossessiveEnding> ofSuffix(String suffix) {
        if (suffix.isEmpty()) return Optional.empty();
        return Arrays.stream(values())
                .filter(ending -> ending.suffix.equals(suffix))
                .findFirst();
    }
}
