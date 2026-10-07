/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * The ready-made formats a date placeholder offers, each written after the bar of its key by its name,
 * such as {@code {{member.birthDate|long}}}, and each spelled in the tokens of an own format
 * ({@link DateToken}) once per language of a template.
 *
 * <p>{@link #SHORT} is how a date prints when its key names no format, and {@link #DATE_TIME} how a day
 * with a time does; both read the same in either language, as dates always did. The last two print a time
 * of day and are offered only where a placeholder has one.
 */
public enum DatePreset {
    SHORT("short", "TT.MM.JJJJ", "TT.MM.JJJJ"),
    LONG("long", "T. MMMM JJJJ", "MMMM T, JJJJ"),
    MEDIUM("medium", "T. MMM JJJJ", "MMM T, JJJJ"),
    MONTH_YEAR("monthYear", "MMMM JJJJ", "MMMM JJJJ"),
    YEAR("year", "JJJJ", "JJJJ"),
    WEEKDAY("weekday", "TTTT, T. MMMM JJJJ", "TTTT, MMMM T, JJJJ"),
    DATE_TIME("dateTime", "TT.MM.JJJJ hh:mm", "TT.MM.JJJJ hh:mm"),
    TIME("time", "hh:mm", "hh:mm");

    private final String name;
    private final String german;
    private final String english;

    DatePreset(String name, String german, String english) {
        this.name = name;
        this.german = german;
        this.english = english;
    }

    /** @return how a key names it after its bar */
    public String written() {
        return name;
    }

    /**
     * @param language the language of the template
     * @return the format in the tokens of an own format
     */
    public String pattern(DocumentLanguage language) {
        return language == DocumentLanguage.EN ? english : german;
    }

    /**
     * @param written what a key names after its bar
     * @return the preset of that name, or empty where it names an own format
     */
    public static Optional<DatePreset> of(String written) {
        return Arrays.stream(values())
                .filter(preset -> preset.name.equals(written))
                .findFirst();
    }
}
