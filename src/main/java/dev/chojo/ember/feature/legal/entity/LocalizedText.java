/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.entity;

/**
 * A piece of text in the locales the generated browser storage disclosure is rendered in.
 * Any locale other than {@code en} falls back to the German wording, matching the fallback
 * the legal document renderer applies to hand-written sections.
 *
 * @param de the German wording
 * @param en the English wording
 */
public record LocalizedText(String de, String en) {

    /**
     * Returns the wording for the given locale.
     *
     * @param locale the desired locale
     * @return the English wording for {@code en}, the German wording otherwise
     */
    public String get(String locale) {
        return "en".equals(locale) && en != null && !en.isBlank() ? en : de;
    }
}
