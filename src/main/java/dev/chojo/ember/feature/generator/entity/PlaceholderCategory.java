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
    MEMBER(PlaceholderGroup.MEMBER, "Mitglied", "Member"),
    /** The pronouns for the member. */
    PRONOUNS(PlaceholderGroup.PRONOUN, "Pronomen", "Pronouns"),
    /** The first guardian of the member. */
    GUARDIAN1(PlaceholderGroup.GUARDIAN, "Erziehungsberechtigte 1", "Guardian 1"),
    /** The second guardian of the member. */
    GUARDIAN2(PlaceholderGroup.GUARDIAN, "Erziehungsberechtigte 2", "Guardian 2"),
    /** The station that files the document. */
    STATION(PlaceholderGroup.STATION, "Wache", "Station"),
    /** The association the station of the member belongs to. */
    ASSOCIATION(PlaceholderGroup.ASSOCIATION, "Verband", "Association"),
    /** The appointment a document is generated for. */
    APPOINTMENT(PlaceholderGroup.EVENT, "Termin", "Appointment"),
    /** The document itself. */
    DOCUMENT(PlaceholderGroup.DOCUMENT, "Dokument", "Document");

    private final PlaceholderGroup group;
    private final String german;
    private final String english;

    PlaceholderCategory(PlaceholderGroup group, String german, String english) {
        this.group = group;
        this.german = german;
        this.english = english;
    }

    /** @return where the values of this category come from, where they are no profile answers */
    public PlaceholderGroup group() {
        return group;
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
