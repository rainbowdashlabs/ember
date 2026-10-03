/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.Locale;

/**
 * The place a pronoun for the member stands in a sentence, which picks its form.
 */
public enum PronounRole {
    /** Er, she: the one doing something. */
    SUBJECT("er", "sie", "he", "she"),
    /** Ihn, her: the one something is done to. */
    OBJECT("ihn", "sie", "him", "her"),
    /** Ihm, her: the one something is given to. */
    DATIVE("ihm", "ihr", "him", "her"),
    /** Sein, his: the one something belongs to. */
    POSSESSIVE("sein", "ihr", "his", "her");

    private final String germanMale;
    private final String germanFemale;
    private final String englishMale;
    private final String englishFemale;

    PronounRole(String germanMale, String germanFemale, String englishMale, String englishFemale) {
        this.germanMale = germanMale;
        this.germanFemale = germanFemale;
        this.englishMale = englishMale;
        this.englishFemale = englishFemale;
    }

    /** @return how the role is written in a placeholder key */
    public String keyPart() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The masculine and the feminine word of the role, which is how the editor names it.
     *
     * @param language {@code de} or {@code en}
     * @return the two words
     */
    public String[] examples(String language) {
        return "en".equals(language)
                ? new String[] {englishMale, englishFemale}
                : new String[] {germanMale, germanFemale};
    }
}
