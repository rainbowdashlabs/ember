/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.question.QuestionConfigs;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What a check of a member rule compares with. Each operator reads only the parts its {@link RuleNeed}
 * names and leaves the rest empty.
 *
 * @param answers   answers of a choice field
 * @param userTypes user types
 * @param states    expiry states
 * @param number    a number, or the lower end of a range
 * @param numberTo  the upper end of a range of numbers
 * @param text      a text
 * @param date      a date
 * @param amount    the amount of a duration, or a number of years, or the lower end of a range of years
 * @param amountTo  the upper end of a range of years
 * @param unit      the unit of a duration
 */
public record RuleValue(
        @Nullable List<String> answers,
        @Nullable List<StationUserType> userTypes,
        @Nullable List<ExpiryState> states,
        @Nullable BigDecimal number,
        @Nullable BigDecimal numberTo,
        @Nullable String text,
        @Nullable LocalDate date,
        @Nullable Integer amount,
        @Nullable Integer amountTo,
        @Nullable RuleUnit unit) {
    private static final RuleValue EMPTY = new RuleValue(null, null, null, null, null, null, null, null, null, null);

    /** A value carrying nothing, for operators that need nothing. */
    public static RuleValue empty() {
        return EMPTY;
    }

    /** A value of answers of a choice field. */
    public static RuleValue ofAnswers(List<String> answers) {
        return new RuleValue(List.copyOf(answers), null, null, null, null, null, null, null, null, null);
    }

    /** A value of user types. */
    public static RuleValue ofUserTypes(List<StationUserType> userTypes) {
        return new RuleValue(null, List.copyOf(userTypes), null, null, null, null, null, null, null, null);
    }

    /** A value of expiry states. */
    public static RuleValue ofStates(List<ExpiryState> states) {
        return new RuleValue(null, null, List.copyOf(states), null, null, null, null, null, null, null);
    }

    /** A single number. */
    public static RuleValue ofNumber(BigDecimal number) {
        return new RuleValue(null, null, null, number, null, null, null, null, null, null);
    }

    /** A range of numbers, both ends included. */
    public static RuleValue ofNumbers(BigDecimal from, BigDecimal to) {
        return new RuleValue(null, null, null, from, to, null, null, null, null, null);
    }

    /** A text. */
    public static RuleValue ofText(String text) {
        return new RuleValue(null, null, null, null, null, text, null, null, null, null);
    }

    /** A date. */
    public static RuleValue ofDate(LocalDate date) {
        return new RuleValue(null, null, null, null, null, null, date, null, null, null);
    }

    /** A duration. */
    public static RuleValue ofDuration(int amount, RuleUnit unit) {
        return new RuleValue(null, null, null, null, null, null, null, amount, null, unit);
    }

    /** A number of whole years. */
    public static RuleValue ofYears(int years) {
        return new RuleValue(null, null, null, null, null, null, null, years, null, RuleUnit.YEARS);
    }

    /** A range of whole years, both ends included. */
    public static RuleValue ofYears(int from, int to) {
        return new RuleValue(null, null, null, null, null, null, null, from, to, RuleUnit.YEARS);
    }

    /**
     * Whether this value carries what the need asks for, in a shape that can be compared with.
     *
     * @param need what the check's operator needs
     */
    public boolean satisfies(RuleNeed need) {
        return switch (need) {
            case NOTHING -> true;
            case ANSWERS -> answers != null && answers.stream().anyMatch(a -> a != null && !a.isBlank());
            case USER_TYPES -> userTypes != null && !userTypes.isEmpty();
            case STATES -> states != null && !states.isEmpty();
            case NUMBER -> number != null;
            case NUMBER_RANGE -> number != null && numberTo != null && number.compareTo(numberTo) <= 0;
            case TEXT -> text != null && !text.isBlank();
            case DATE -> date != null;
            case DURATION -> amount != null && amount >= 0 && unit != null;
            case YEARS -> amount != null && amount >= 0;
            case YEARS_RANGE -> amount != null && amountTo != null && amount >= 0 && amount <= amountTo;
        };
    }

    /**
     * Reads a stored value, returning an empty one where nothing or nothing readable is stored.
     *
     * @param json the stored JSON, may be null
     */
    public static RuleValue parse(@Nullable String json) {
        if (json == null) return EMPTY;
        return QuestionConfigs.parse(json, RuleValue.class, EMPTY);
    }

    /** The JSON this value is stored as. */
    public String toJson() {
        return QuestionConfigs.toJson(this);
    }
}
