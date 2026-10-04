/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.PronounKey;
import dev.chojo.ember.feature.generator.entity.PronounRole;
import dev.chojo.ember.feature.members.entity.PronounSet;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The word a template writes for a member where it names a pronoun.
 *
 * <p>The words come from the member's answer to the gender field, in the template's language. Where
 * there are none, because the member gave no answer, the answer carries no pronouns, or none for this
 * language, the official first name stands wherever a pronoun would, and its genitive stands for the
 * possessive: "Lenas", and "Max'" for a name ending in a hissing sound. A possessive's ending is added
 * to a pronoun only ("seinen"); the name takes none. A pronoun at the start of a sentence is written
 * with a capital; a name already has one.
 */
public final class Pronouns {

    private Pronouns() {}

    /**
     * The word that stands for a member.
     *
     * @param pronoun   which pronoun the template names
     * @param words     the member's pronouns in the template's language, or null to use the name
     * @param firstName the member's official first name
     * @param language  the template's language
     * @return the word
     */
    public static String of(
            PronounKey pronoun, @Nullable PronounSet words, String firstName, DocumentLanguage language) {
        if (words == null) {
            return switch (pronoun.role()) {
                case POSSESSIVE -> genitive(firstName, language);
                case SUBJECT, OBJECT, DATIVE -> firstName;
            };
        }
        String ending =
                pronoun.role() == PronounRole.POSSESSIVE ? pronoun.ending().suffix() : "";
        String word = pronoun.role().wordIn(words) + ending;
        return pronoun.sentenceStart() ? capitalised(word) : word;
    }

    /**
     * The genitive of a first name, as it stands for a possessive.
     *
     * @param name     the name
     * @param language the template's language
     * @return the name in the genitive
     */
    public static String genitive(String name, DocumentLanguage language) {
        if (name.isEmpty()) return name;
        String lower = name.toLowerCase(Locale.ROOT);
        if (language == DocumentLanguage.EN) return lower.endsWith("s") ? name + "'" : name + "'s";
        boolean hissing = lower.endsWith("s")
                || lower.endsWith("ß")
                || lower.endsWith("x")
                || lower.endsWith("z")
                || lower.endsWith("ce");
        return hissing ? name + "'" : name + "s";
    }

    private static String capitalised(String word) {
        return word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
