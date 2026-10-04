/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.feature.generator.entity.DocumentLanguage;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The two predefined answers of a gender field and the pronouns they stand for in every language a
 * document is written in. A new gender field starts with them, the field editor offers them as a choice
 * for any answer, and the picker of a template names the roles of a pronoun by their words.
 */
public enum PronounPreset {
    /** Er, he. */
    MALE(new PronounSet("er", "ihn", "ihm", "sein"), new PronounSet("he", "him", null, "his")),
    /** Sie, she. */
    FEMALE(new PronounSet("sie", "sie", "ihr", "ihr"), new PronounSet("she", "her", null, "her"));

    private final PronounSet german;
    private final PronounSet english;

    PronounPreset(PronounSet german, PronounSet english) {
        this.german = german;
        this.english = english;
    }

    /**
     * @param language a language a document is written in
     * @return the pronouns in it
     */
    public PronounSet in(DocumentLanguage language) {
        return language.pick(german, english);
    }

    /** @return the pronouns by language code, as a gender field keeps them for an answer */
    public Map<String, PronounSet> byLanguage() {
        var byLanguage = new LinkedHashMap<String, PronounSet>();
        Arrays.stream(DocumentLanguage.values()).forEach(language -> byLanguage.put(language.code(), in(language)));
        return byLanguage;
    }
}
