/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.PronounRole;

import java.util.Arrays;
import java.util.List;

/**
 * A language a document is written in, as the editor of a gender field offers it: the words it asks
 * for and the two predefined answers in it.
 *
 * @param language the language
 * @param code     how a gender field keys its pronouns in this language
 * @param roles    the roles the language tells apart, each of which takes a word of its own
 * @param male     the pronouns of {@link PronounPreset#MALE}
 * @param female   the pronouns of {@link PronounPreset#FEMALE}
 */
public record PronounLanguage(
        DocumentLanguage language, String code, List<PronounRole> roles, PronounSet male, PronounSet female) {

    /** @return every language a document is written in */
    public static List<PronounLanguage> all() {
        return Arrays.stream(DocumentLanguage.values()).map(PronounLanguage::of).toList();
    }

    private static PronounLanguage of(DocumentLanguage language) {
        return new PronounLanguage(
                language,
                language.code(),
                Arrays.stream(PronounRole.values())
                        .filter(role -> role.toldApartIn(language))
                        .toList(),
                PronounPreset.MALE.in(language),
                PronounPreset.FEMALE.in(language));
    }
}
