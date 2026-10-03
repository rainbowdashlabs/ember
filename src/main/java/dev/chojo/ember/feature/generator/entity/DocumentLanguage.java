/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.Locale;

/**
 * The language a template writes its documents in. It picks the pronouns a gender field gives for the
 * member, how dates and yes or no are written, and the words the letter's frame prints.
 */
public enum DocumentLanguage {
    DE,
    EN;

    /** @return the language code, which names the template folder and the pronouns of a gender field */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return the locale the names of months and weekdays are written in */
    public Locale locale() {
        return this == EN ? Locale.ENGLISH : Locale.GERMAN;
    }

    /**
     * @param code a language code such as a station's ({@code de}, {@code en})
     * @return the language of that code, German for anything else
     */
    public static DocumentLanguage of(String code) {
        return "en".equals(code) ? EN : DE;
    }
}
