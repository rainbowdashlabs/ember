/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.QuestionKind;
import org.jspecify.annotations.Nullable;

/**
 * What a placeholder that holds a date holds: a day, such as a birth date, or a day with a time of day,
 * such as the start of an appointment. A day takes the date formats; a day with a time also those that
 * print hours and minutes.
 */
public enum DateKind {
    DATE,
    DATE_TIME;

    /**
     * @param type the type of a profile question
     * @return the kind of date its answer holds, a day for a date, a birth date and an expiry date, or
     *         null for any other type
     */
    public static @Nullable DateKind of(FieldType type) {
        return type.kind()
                .filter(kind -> kind == QuestionKind.DATE)
                .map(kind -> DATE)
                .orElse(null);
    }

    /** @return the format a placeholder of this kind prints in when its key names none */
    public DatePreset standard() {
        return this == DATE ? DatePreset.SHORT : DatePreset.DATE_TIME;
    }

    /**
     * @param format a format written after the key of a placeholder of this kind
     * @return whether the format can print a value of this kind, which a time of day on a plain day
     *         cannot
     */
    public boolean takes(DateFormat format) {
        return this == DATE_TIME || !format.readsClock();
    }
}
