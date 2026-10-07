/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import dev.chojo.ember.feature.members.entity.PronounPreset;
import dev.chojo.ember.feature.members.entity.PronounSet;

import java.util.Locale;

/**
 * The place a pronoun for the member stands in a sentence, which picks its form.
 */
public enum PronounRole {
    /** Er, she: the one doing something. */
    SUBJECT("Wer", "Who"),
    /** Ihn, her: the one something is done to. */
    OBJECT("Wen", "Whom"),
    /** Ihm, her: the one something is given to. */
    DATIVE("Wem", "To whom"),
    /** Sein, his: the one something belongs to. */
    POSSESSIVE("Wessen", "Whose");

    private final String germanQuestion;
    private final String englishQuestion;

    PronounRole(String germanQuestion, String englishQuestion) {
        this.germanQuestion = germanQuestion;
        this.englishQuestion = englishQuestion;
    }

    /**
     * How the picker names the role: the question the pronoun answers and its two words, such as
     * "Wem (ihm / ihr)". The question tells apart the English roles whose words are the same.
     *
     * @param language the language the picker speaks
     * @return the name of the role
     */
    public String title(DocumentLanguage language) {
        var words = examples(language);
        String question = language.pick(germanQuestion, englishQuestion);
        return question + " (" + words[0] + " / " + words[1] + ")";
    }

    /** @return how the role is written in a placeholder key */
    public String keyPart() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The masculine and the feminine word of the role, which is how the editor names it.
     *
     * @param language the language the picker speaks
     * @return the two words
     */
    public String[] examples(DocumentLanguage language) {
        return new String[] {wordIn(PronounPreset.MALE.in(language)), wordIn(PronounPreset.FEMALE.in(language))};
    }

    /**
     * @param words the pronouns of an answer in one language
     * @return the word they have for this role, the object for a dative the language does not tell apart
     */
    public String wordIn(PronounSet words) {
        return switch (this) {
            case SUBJECT -> words.subjectWord();
            case OBJECT -> words.objectWord();
            case DATIVE -> words.dativeWord();
            case POSSESSIVE -> words.possessiveWord();
        };
    }

    /**
     * @param language a language a document is written in
     * @return whether the language has a word of its own for this role; English has no dative of its own
     */
    public boolean toldApartIn(DocumentLanguage language) {
        return this != DATIVE || language == DocumentLanguage.DE;
    }
}
