/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * The pronouns one answer of a gender field stands for in one language, by the role they play in a
 * sentence: "er / ihn / ihm / sein", "she / her / her / her".
 *
 * <p>A language that does not tell two roles apart leaves the second empty, and it takes the word of the
 * other: English has no dative of its own, so its dative is the object.
 *
 * @param subject    the one doing something: er, she
 * @param object     the one something is done to: ihn, her
 * @param dative     the one something is given to: ihm, her; empty for the object
 * @param possessive the one something belongs to: sein, her
 */
public record PronounSet(
        @Nullable String subject,
        @Nullable String object,
        @Nullable String dative,
        @Nullable String possessive) {

    /** The longest word a pronoun may be. */
    public static final int MAX_WORD = 40;

    /** Whether no role holds a word, which is the same as using the name. */
    public boolean saysNothing() {
        return words().allMatch(word -> word == null || word.isBlank());
    }

    /** Whether a word is longer than a pronoun may be. */
    public boolean tooLong() {
        return words().anyMatch(word -> word != null && word.length() > MAX_WORD);
    }

    /** The subject, or nothing where it is left empty. */
    public String subjectWord() {
        return word(subject);
    }

    /** The object, or nothing where it is left empty. */
    public String objectWord() {
        return word(object);
    }

    /** The dative, the object where the language does not tell them apart. */
    public String dativeWord() {
        String dative = word(this.dative);
        return dative.isEmpty() ? objectWord() : dative;
    }

    /** The possessive, or nothing where it is left empty. */
    public String possessiveWord() {
        return word(possessive);
    }

    private Stream<@Nullable String> words() {
        return Stream.of(subject, object, dative, possessive);
    }

    private static String word(@Nullable String word) {
        return Objects.requireNonNullElse(word, "").strip();
    }
}
