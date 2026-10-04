/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A pronoun for the member a template names: {@code pronoun.<role>}, followed by {@code .start} where it
 * opens a sentence, and for a possessive by the ending it takes ({@code pronoun.possessive.en} writes
 * "seinen", "ihren" or "Lenas").
 *
 * <p>Which words stand there follows the member's answer to the station's gender field, in the language
 * of the template; without an answer, or for an answer without pronouns, the official first name stands.
 *
 * @param role          where in the sentence it stands
 * @param sentenceStart whether it opens a sentence and starts with a capital
 * @param ending        the ending a possessive takes, {@link PossessiveEnding#NONE} for every other role
 */
public record PronounKey(PronounRole role, boolean sentenceStart, PossessiveEnding ending) {
    /** How a pronoun key starts. */
    public static final String PREFIX = "pronoun.";

    private static final String START = "start";

    /** @return the key a template writes */
    public String key() {
        var key = new StringBuilder(PREFIX).append(role.keyPart());
        if (sentenceStart) key.append('.').append(START);
        if (ending != PossessiveEnding.NONE) key.append('.').append(ending.suffix());
        return key.toString();
    }

    /**
     * @param key a key a template wrote
     * @return the pronoun it stands for, or empty where it is no pronoun key
     */
    public static Optional<PronounKey> parse(String key) {
        if (!key.startsWith(PREFIX)) return Optional.empty();
        var parts = List.of(key.substring(PREFIX.length()).split("\\.", -1));
        var role = roleOf(parts.getFirst());
        if (role.isEmpty()) return Optional.empty();
        int next = 1;
        boolean start = parts.size() > next && START.equals(parts.get(next));
        if (start) next++;
        var ending = PossessiveEnding.NONE;
        if (parts.size() > next) {
            var read = PossessiveEnding.ofSuffix(parts.get(next));
            if (read.isEmpty() || role.get() != PronounRole.POSSESSIVE) return Optional.empty();
            ending = read.get();
            next++;
        }
        if (parts.size() > next) return Optional.empty();
        return Optional.of(new PronounKey(role.get(), start, ending));
    }

    private static Optional<PronounRole> roleOf(String part) {
        for (var role : PronounRole.values()) {
            if (role.keyPart().equals(part)) return Optional.of(role);
        }
        return Optional.empty();
    }

    /**
     * Every pronoun a template can name, in the order the picker offers them.
     *
     * @return the pronouns
     */
    public static List<PronounKey> all() {
        var out = new ArrayList<PronounKey>();
        for (var role : PronounRole.values()) {
            for (boolean start : new boolean[] {false, true}) {
                if (role != PronounRole.POSSESSIVE) {
                    out.add(new PronounKey(role, start, PossessiveEnding.NONE));
                    continue;
                }
                for (var ending : PossessiveEnding.values()) out.add(new PronounKey(role, start, ending));
            }
        }
        return out;
    }

    /**
     * The pronoun as the catalogue offers it.
     *
     * <p>The picker reaches it through its role. A possessive, which has six endings in each place, is
     * then split by whether it opens a sentence, and named by its words alone; every other role offers its
     * two forms right below the role.
     *
     * @param language {@code de} or {@code en}, the language the editor speaks
     * @return the pronoun as the catalogue offers it
     */
    public Placeholder in(String language) {
        boolean english = "en".equals(language);
        var words = role.examples(language);
        boolean declined = !english && ending != PossessiveEnding.NONE;
        String male = shaped(words[0] + (declined ? ending.suffix() : ""));
        String female = shaped(words[1] + (declined ? ending.suffix() : ""));
        String name = role == PronounRole.POSSESSIVE
                ? (english ? "first name's" : "Vornamens")
                : (english ? "first name" : "Vorname");
        var forms = new StringBuilder(male + " / " + female + " / " + name);
        if (english && ending != PossessiveEnding.NONE)
            forms.append(" (+").append(ending.suffix()).append(')');
        String label = sentenceStart ? forms + (english ? " (sentence start)" : " (Satzanfang)") : forms.toString();
        String category = PlaceholderCategory.PRONOUNS.word(language);
        var path = role == PronounRole.POSSESSIVE
                ? List.of(category, role.title(language), place(english), forms.toString())
                : List.of(category, role.title(language), label);
        return new Placeholder(key(), label, PlaceholderCategory.PRONOUNS, path, false, false, null);
    }

    private String place(boolean english) {
        if (sentenceStart) return english ? "At the start of a sentence" : "Am Satzanfang";
        return english ? "Within a sentence" : "Im Satz";
    }

    private String shaped(String word) {
        if (!sentenceStart) return word;
        return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
    }
}
