/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import dev.chojo.ember.feature.question.QuestionConfigs;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What counts as an answer to a question of a template. Each kind of question reads only its own part:
 * a date its earliest and latest day, a text its length and format, a number its bounds, a choice its
 * entries. A check has no limits. Every part left empty limits nothing.
 *
 * @param earliest  the earliest day a date question takes, or null for any
 * @param latest    the latest day a date question takes, or null for any
 * @param maxLength the longest text a text question takes, or null for any
 * @param format    the format a text question takes, or null for any
 * @param min       the lowest number a number question takes, or null for any
 * @param max       the highest number a number question takes, or null for any
 * @param whole     whether a number question takes whole numbers only
 * @param choices   the answers a choice question offers, in the order it offers them
 */
public record QuestionLimits(
        @Nullable DayBound earliest,
        @Nullable DayBound latest,
        @Nullable Integer maxLength,
        @Nullable TextFormat format,
        @Nullable BigDecimal min,
        @Nullable BigDecimal max,
        boolean whole,
        List<String> choices) {

    /** The longest text a text question can limit its answers to. */
    public static final int MAX_LENGTH = 600;

    /** The most entries one choice question offers. */
    public static final int MAX_CHOICES = 50;

    /** The longest entry of a choice question. */
    public static final int MAX_CHOICE = 120;

    /** Limits that limit nothing. */
    public static final QuestionLimits NONE = new QuestionLimits(null, null, null, null, null, null, false, List.of());

    public QuestionLimits {
        choices = choices == null ? List.of() : List.copyOf(choices);
    }

    /**
     * @param earliest the earliest day, or null for any
     * @param latest   the latest day, or null for any
     * @return the limits of a date question
     */
    public static QuestionLimits days(@Nullable DayBound earliest, @Nullable DayBound latest) {
        return new QuestionLimits(earliest, latest, null, null, null, null, false, List.of());
    }

    /**
     * @param maxLength the longest text, or null for any
     * @param format    the format, or null for any
     * @return the limits of a text question
     */
    public static QuestionLimits text(@Nullable Integer maxLength, @Nullable TextFormat format) {
        return new QuestionLimits(null, null, maxLength, format, null, null, false, List.of());
    }

    /**
     * @param min   the lowest number, or null for any
     * @param max   the highest number, or null for any
     * @param whole whether only whole numbers are taken
     * @return the limits of a number question
     */
    public static QuestionLimits number(@Nullable BigDecimal min, @Nullable BigDecimal max, boolean whole) {
        return new QuestionLimits(null, null, null, null, min, max, whole, List.of());
    }

    /**
     * @param choices the answers offered
     * @return the limits of a choice question
     */
    public static QuestionLimits choice(List<String> choices) {
        return new QuestionLimits(null, null, null, null, null, null, false, choices);
    }

    /**
     * Reads the stored column.
     *
     * @param json the column
     * @return the limits, none where the column says nothing readable
     */
    public static QuestionLimits parse(String json) {
        return QuestionConfigs.parse(json, QuestionLimits.class, NONE);
    }

    /** @return the limits as they are stored */
    public String toJson() {
        return QuestionConfigs.toJson(this);
    }

    /**
     * A bound of a date question: a fixed day, or a number of days from the day a document is generated.
     * Exactly one of the two is set.
     *
     * @param day       the fixed day, or null
     * @param fromToday the days from the day of generation, negative for the past, or null
     */
    public record DayBound(
            @Nullable LocalDate day, @Nullable Integer fromToday) {

        /**
         * @param today the day a document is generated
         * @return the day this bound names on that day, or null where it names none
         */
        public @Nullable LocalDate on(LocalDate today) {
            if (day != null) return day;
            return fromToday == null ? null : today.plusDays(fromToday);
        }
    }
}
