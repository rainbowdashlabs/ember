/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.entity.PronounForm;

import java.util.Locale;

/**
 * The pronouns a template writes for a member, by form and by grammatical case.
 *
 * <p>Where the form is {@link PronounForm#NAME}, the official first name stands wherever a pronoun
 * would, and its genitive stands for the possessive: "Lenas", and "Max'" for a name ending in a hissing
 * sound. A pronoun at the start of a sentence is written with a capital; a name already has one.
 */
public final class Pronouns {

    private Pronouns() {}

    /** The four places a pronoun stands in a sentence. */
    public enum Case {
        /** Er, sie: the one doing something. */
        SUBJECT,
        /** Ihn, sie: the one something is done to. */
        OBJECT,
        /** Ihm, ihr: the one something is given to. */
        DATIVE,
        /** Sein, ihr: the one something belongs to. */
        POSSESSIVE
    }

    /**
     * The word that stands for a member.
     *
     * @param form          which column of pronouns the member is written with
     * @param grammar       where in the sentence it stands
     * @param firstName     the member's official first name
     * @param language      {@code de} or {@code en}
     * @param sentenceStart whether it opens a sentence
     * @return the word
     */
    public static String of(PronounForm form, Case grammar, String firstName, String language, boolean sentenceStart) {
        if (form == PronounForm.NAME) {
            return grammar == Case.POSSESSIVE ? genitive(firstName, language) : firstName;
        }
        String word = "en".equals(language) ? english(form, grammar) : german(form, grammar);
        return sentenceStart ? capitalised(word) : word;
    }

    /**
     * The genitive of a first name, as it stands for a possessive.
     *
     * @param name     the name
     * @param language {@code de} or {@code en}
     * @return the name in the genitive
     */
    public static String genitive(String name, String language) {
        if (name.isEmpty()) return name;
        String lower = name.toLowerCase(Locale.ROOT);
        if ("en".equals(language)) return lower.endsWith("s") ? name + "'" : name + "'s";
        boolean hissing = lower.endsWith("s")
                || lower.endsWith("ß")
                || lower.endsWith("x")
                || lower.endsWith("z")
                || lower.endsWith("ce");
        return hissing ? name + "'" : name + "s";
    }

    private static String german(PronounForm form, Case grammar) {
        boolean masculine = form == PronounForm.ER;
        return switch (grammar) {
            case SUBJECT -> masculine ? "er" : "sie";
            case OBJECT -> masculine ? "ihn" : "sie";
            case DATIVE -> masculine ? "ihm" : "ihr";
            case POSSESSIVE -> masculine ? "sein" : "ihr";
        };
    }

    private static String english(PronounForm form, Case grammar) {
        boolean masculine = form == PronounForm.ER;
        return switch (grammar) {
            case SUBJECT -> masculine ? "he" : "she";
            case OBJECT, DATIVE -> masculine ? "him" : "her";
            case POSSESSIVE -> masculine ? "his" : "her";
        };
    }

    private static String capitalised(String word) {
        return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
