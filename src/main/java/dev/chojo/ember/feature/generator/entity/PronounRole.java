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
    SUBJECT("er", "sie", "he", "she", "Wer", "Who"),
    /** Ihn, her: the one something is done to. */
    OBJECT("ihn", "sie", "him", "her", "Wen", "Whom"),
    /** Ihm, her: the one something is given to. */
    DATIVE("ihm", "ihr", "him", "her", "Wem", "To whom"),
    /** Sein, his: the one something belongs to. */
    POSSESSIVE("sein", "ihr", "his", "her", "Wessen", "Whose");

    private final String germanMale;
    private final String germanFemale;
    private final String englishMale;
    private final String englishFemale;
    private final String germanQuestion;
    private final String englishQuestion;

    PronounRole(
            String germanMale,
            String germanFemale,
            String englishMale,
            String englishFemale,
            String germanQuestion,
            String englishQuestion) {
        this.germanMale = germanMale;
        this.germanFemale = germanFemale;
        this.englishMale = englishMale;
        this.englishFemale = englishFemale;
        this.germanQuestion = germanQuestion;
        this.englishQuestion = englishQuestion;
    }

    /**
     * How the picker names the role: the question the pronoun answers and its two words, such as
     * "Wem (ihm / ihr)". The question tells apart the English roles whose words are the same.
     *
     * @param language {@code de} or {@code en}
     * @return the name of the role
     */
    public String title(String language) {
        var words = examples(language);
        String question = "en".equals(language) ? englishQuestion : germanQuestion;
        return question + " (" + words[0] + " / " + words[1] + ")";
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
