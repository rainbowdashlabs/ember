/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * The second step of the path to a placeholder about a person: the values every member has, or the
 * answers to the station's profile questions.
 */
public enum PlaceholderSubgroup {
    /** Names, dates and the kind of member, which every member has. */
    DETAILS("Stammdaten", "Basic details"),
    /** The answers to the station's profile questions, split by the headings of the form. */
    PROFILE("Profil", "Profile");

    private final String german;
    private final String english;

    PlaceholderSubgroup(String german, String english) {
        this.german = german;
        this.english = english;
    }

    /**
     * @param language {@code de} or {@code en}
     * @return what the picker calls this step
     */
    public String word(String language) {
        return "en".equals(language) ? english : german;
    }
}
