/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.entity.BuiltInPlaceholder;
import dev.chojo.ember.feature.generator.entity.DatePreset;
import dev.chojo.ember.feature.generator.entity.Placeholder;
import dev.chojo.ember.feature.generator.entity.PlaceholderKey;
import dev.chojo.ember.feature.generator.entity.PossessiveEnding;
import dev.chojo.ember.feature.generator.entity.PronounKey;
import dev.chojo.ember.feature.generator.entity.PronounRole;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The placeholders the words of a gap in an imported document stand for: a gap in brackets in a letter,
 * the label of a field in a form.
 *
 * <p>The words are the common German names of member data, of the station and of whoever generates the
 * document, the pronouns, and the names of the profile questions the owner's templates can name. A
 * pronoun written with a capital starts its sentence. The words of an id number (Ausweisnummer,
 * Personalnummer, Mitgliedsnummer) name the profile question called by any of them, since a form and a
 * station rarely call the number alike.
 */
public final class GapWords {
    /** The placeholder keys the words of a gap stand for, the words written in lower case. */
    private static final Map<String, String> WORDS = words();

    /** The names an id number goes by, in lower case. */
    private static final Set<String> ID_NUMBER = Set.of(
            "ausweisnummer",
            "ausweis-nr",
            "ausweis nr",
            "dienstausweisnummer",
            "personalnummer",
            "personal-nr",
            "mitgliedsnummer",
            "mitglieds-nr",
            "mitgliedsnr");

    private GapWords() {}

    private static Map<String, String> words() {
        var words = new LinkedHashMap<String, String>();
        String fullName = BuiltInPlaceholder.MEMBER_FULL_NAME.key();
        String birthDate = BuiltInPlaceholder.MEMBER_BIRTH_DATE.key();
        String joinDate = BuiltInPlaceholder.MEMBER_JOIN_DATE.key();
        String station = BuiltInPlaceholder.STATION_NAME.key();
        String generatedBy = BuiltInPlaceholder.GENERATED_BY.key();
        List.of("vorname nachname", "vor- und nachname", "name").forEach(word -> words.put(word, fullName));
        words.put("vorname", BuiltInPlaceholder.MEMBER_FIRST_NAME.key());
        words.put("nachname", BuiltInPlaceholder.MEMBER_LAST_NAME.key());
        List.of("geburtsdatum", "geb.-datum", "geb. datum", "geb.datum", "geboren am")
                .forEach(word -> words.put(word, birthDate));
        words.put("alter", BuiltInPlaceholder.MEMBER_AGE.key());
        List.of(
                        "eintrittsdatum",
                        "eintritt",
                        "eintritt am",
                        "eintritt jf",
                        "eintritt ff",
                        "eintritt verein",
                        "beitritt",
                        "beigetreten am")
                .forEach(word -> words.put(word, joinDate));
        words.put("monat/jahr", PlaceholderKey.written(joinDate, DatePreset.MONTH_YEAR.written()));
        words.put("datum", BuiltInPlaceholder.TODAY.key());
        List.of("verein", "wache", "feuerwehr", "jugendfeuerwehr", "ortswehr")
                .forEach(word -> words.put(word, station));
        List.of("bearbeiter", "bearbeiterin", "bearbeitet von", "sachbearbeiter", "sachbearbeiterin")
                .forEach(word -> words.put(word, generatedBy));
        words.put("er/sie", pronoun(PronounRole.SUBJECT, PossessiveEnding.NONE));
        words.put("ihn/sie", pronoun(PronounRole.OBJECT, PossessiveEnding.NONE));
        words.put("ihm/ihr", pronoun(PronounRole.DATIVE, PossessiveEnding.NONE));
        words.put("sein/ihr", pronoun(PronounRole.POSSESSIVE, PossessiveEnding.NONE));
        words.put("seine/ihre", pronoun(PronounRole.POSSESSIVE, PossessiveEnding.E));
        words.put("seinen/ihren", pronoun(PronounRole.POSSESSIVE, PossessiveEnding.EN));
        words.put("seinem/ihrem", pronoun(PronounRole.POSSESSIVE, PossessiveEnding.EM));
        words.put("seiner/ihrer", pronoun(PronounRole.POSSESSIVE, PossessiveEnding.ER));
        words.put("seines/ihres", pronoun(PronounRole.POSSESSIVE, PossessiveEnding.ES));
        return Map.copyOf(words);
    }

    private static String pronoun(PronounRole role, PossessiveEnding ending) {
        return new PronounKey(role, false, ending).key();
    }

    /**
     * @param answerable the answers to profile questions a template of the owner can name
     * @return their keys by the name of their question in lower case, the first of a name kept
     */
    public static Map<String, String> fieldsOf(List<Placeholder> answerable) {
        var fields = new LinkedHashMap<String, String>();
        answerable.forEach(field -> fields.putIfAbsent(field.label().toLowerCase(Locale.ROOT), field.key()));
        return fields;
    }

    /**
     * @param gap    the words of a gap as written
     * @param fields the keys of the owner's profile questions by their names in lower case
     * @return the key the words stand for, or null where they stand for none
     */
    public static @Nullable String keyOf(String gap, Map<String, String> fields) {
        String words = gap.strip().replaceAll("\\s+", " ");
        if (words.isEmpty()) return null;
        String lower = words.toLowerCase(Locale.ROOT);
        var key = WORDS.get(lower);
        if (key == null) return fields.getOrDefault(lower, idNumber(lower, fields));
        var pronoun = PronounKey.parse(key);
        boolean capital = Character.isUpperCase(words.charAt(0));
        if (pronoun.isEmpty() || !capital) return key;
        return new PronounKey(pronoun.get().role(), true, pronoun.get().ending()).key();
    }

    private static @Nullable String idNumber(String lower, Map<String, String> fields) {
        if (!ID_NUMBER.contains(lower)) return null;
        return fields.entrySet().stream()
                .filter(field -> ID_NUMBER.contains(field.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
