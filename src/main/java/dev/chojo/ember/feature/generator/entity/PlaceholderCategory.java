/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * Whom or what a placeholder is about, which is the first step of the path the editor's picker offers it
 * under. The order here is the order the picker shows the categories in.
 */
public enum PlaceholderCategory {
    /** The member the document is about: the register's values and the profile answers. */
    MEMBER("Mitglied", "Member"),
    /** The pronouns for the member. */
    PRONOUNS("Pronomen", "Pronouns"),
    /** The first guardian of the member. */
    GUARDIAN1("Erziehungsberechtigte 1", "Guardian 1"),
    /** The second guardian of the member. */
    GUARDIAN2("Erziehungsberechtigte 2", "Guardian 2"),
    /** The station that files the document. */
    STATION("Wache", "Station"),
    /** The association the station of the member belongs to. */
    ASSOCIATION("Verband", "Association"),
    /** The appointment a document is generated for. */
    APPOINTMENT("Termin", "Appointment"),
    /** The member who issues the document for the station. */
    ISSUER("Ausstellende Person", "Issuer"),
    /** The document itself. */
    DOCUMENT("Dokument", "Document");

    private final String german;
    private final String english;

    PlaceholderCategory(String german, String english) {
        this.german = german;
        this.english = english;
    }

    /** @return whether the category is one of the member's guardians */
    public boolean guardian() {
        return this == GUARDIAN1 || this == GUARDIAN2;
    }

    /**
     * @param language {@code de} or {@code en}
     * @return what the picker calls this category
     */
    public String word(String language) {
        return "en".equals(language) ? english : german;
    }

    /**
     * @param index the guardian, counted from zero
     * @return the category of that guardian
     */
    public static PlaceholderCategory guardian(int index) {
        return index == 0 ? GUARDIAN1 : GUARDIAN2;
    }
}
