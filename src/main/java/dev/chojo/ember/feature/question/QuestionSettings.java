/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import dev.chojo.ember.api.auth.StationUserType;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * The settings a question carries whoever asks it.
 *
 * <p>Six features store these, and each declared its own: five of them declare the options of a
 * choice, four whether an answer is required, three what it starts from, three how wide the box is,
 * and two the group, user type or tag a member field is narrowed to. A seventh feature added tomorrow
 * would declare them a seventh time, and one of the six would spell one of them differently, which is
 * how the six drifted apart in the first place.
 *
 * <p>What is stored is untouched: a feature keeps its own row and its own JSON, and hands over one
 * of these. That is deliberate. Making the stored shape itself a set of typed records per kind would
 * either repeat the settings above in every kind of every feature, or nest them and rewrite the
 * settings of every question in every station's database for no answer that reads differently
 * afterwards.
 *
 * @param required     whether the question has to be answered
 * @param defaultValue what an unanswered question falls back to, or null where it has none
 * @param options      the answers a choice offers, empty where the kind offers none
 * @param min          the smallest answer a number takes, or null
 * @param max          the largest answer a number takes, or null
 * @param step         the step a number moves in, or null for whole numbers; a step below one lets a
 *                     number carry a fraction
 * @param width        how wide the box is on the form, or null for the feature's default
 * @param groupId      the group a member field is narrowed to, or null
 * @param userType     the user type a member field is narrowed to, or null
 * @param tagId        the tag a member field is narrowed to, or null
 */
public record QuestionSettings(
        boolean required,
        @Nullable String defaultValue,
        List<String> options,
        @Nullable BigDecimal min,
        @Nullable BigDecimal max,
        @Nullable BigDecimal step,
        @Nullable String width,
        @Nullable Integer groupId,
        @Nullable StationUserType userType,
        @Nullable Integer tagId) {

    public QuestionSettings {
        options = options == null ? List.of() : List.copyOf(options);
    }

    /** The settings of a question that carries none of them. */
    public static QuestionSettings none() {
        return required(false);
    }

    /** The settings of a question that only says whether it has to be answered. */
    public static QuestionSettings required(boolean required) {
        return new QuestionSettings(required, null, List.of(), null, null, null, null, null, null, null);
    }

    /** The same settings with whether an answer is expected, where the feature keeps that apart. */
    public QuestionSettings withRequired(boolean expected) {
        return new QuestionSettings(expected, defaultValue, options, min, max, step, width, groupId, userType, tagId);
    }

    /** The same settings with a starting value, however the feature stores one. */
    public QuestionSettings withDefault(@Nullable Object value) {
        return new QuestionSettings(
                required,
                value == null ? null : String.valueOf(value),
                options,
                min,
                max,
                step,
                width,
                groupId,
                userType,
                tagId);
    }

    /** The same settings with the answers a choice offers. */
    public QuestionSettings withOptions(@Nullable List<String> offered) {
        List<String> written = offered == null ? List.of() : offered;
        return new QuestionSettings(required, defaultValue, written, min, max, step, width, groupId, userType, tagId);
    }

    /** The same settings with what a number has to sit between, as whole numbers. */
    public QuestionSettings withBounds(@Nullable Integer smallest, @Nullable Integer largest) {
        return withBounds(
                smallest == null ? null : BigDecimal.valueOf(smallest),
                largest == null ? null : BigDecimal.valueOf(largest));
    }

    /** The same settings with what a number has to sit between. */
    public QuestionSettings withBounds(@Nullable BigDecimal smallest, @Nullable BigDecimal largest) {
        return new QuestionSettings(
                required, defaultValue, options, smallest, largest, step, width, groupId, userType, tagId);
    }

    /** The same settings with the step a number moves in. */
    public QuestionSettings withStep(@Nullable BigDecimal moves) {
        return new QuestionSettings(required, defaultValue, options, min, max, moves, width, groupId, userType, tagId);
    }

    /** The same settings with how wide the box is. */
    public QuestionSettings withWidth(@Nullable String wide) {
        return new QuestionSettings(required, defaultValue, options, min, max, step, wide, groupId, userType, tagId);
    }

    /** The same settings with the group, user type and tag a member field is narrowed to. */
    public QuestionSettings withMembers(
            @Nullable Integer group, @Nullable StationUserType type, @Nullable Integer tag) {
        return new QuestionSettings(required, defaultValue, options, min, max, step, width, group, type, tag);
    }

    /** Whether a number under these settings may carry a fraction. */
    public boolean takesFractions() {
        return step != null && step.compareTo(BigDecimal.ONE) < 0;
    }

    /**
     * The question a field of this kind asks, which is what the one check measures an answer
     * against.
     *
     * <p>Which of these settings mean anything follows the kind and is decided here rather than in
     * each feature: the options of a choice, the bounds of a number, and nothing else.
     *
     * @param name what the question is called, which is what a refusal names
     * @param kind what kind of answer it takes
     */
    public Question asQuestion(String name, QuestionKind kind) {
        return new Question(name, kind, required, defaultValue, rulesFor(kind));
    }

    /**
     * The question a field of this type asks, or nothing for a type that holds no value.
     *
     * <p>A number whose step is below one asks for a decimal, every other number for a whole one. A
     * member field narrowed to a group, a user type or a tag carries that narrowing, so the check can
     * refuse a member outside it.
     *
     * @param name what the question is called, which is what a refusal names
     * @param type the field type
     */
    public Optional<Question> asQuestion(String name, FieldType type) {
        return type.kind().map(kind -> {
            QuestionKind asked = kind == QuestionKind.NUMBER && takesFractions() ? QuestionKind.DECIMAL : kind;
            QuestionRules rules = kind.namesMembers() ? membersOf(type.constraint()) : rulesFor(asked);
            return new Question(name, asked, required, defaultValue, rules);
        });
    }

    private QuestionRules membersOf(MemberConstraint constraint) {
        return switch (constraint) {
            case NONE -> QuestionRules.none();
            case GROUP -> new QuestionRules.Members(constraint, groupId, null);
            case USER_TYPE -> new QuestionRules.Members(constraint, null, userType);
            case TAG -> new QuestionRules.Members(constraint, tagId, null);
        };
    }

    private QuestionRules rulesFor(QuestionKind kind) {
        return switch (kind) {
            case CHOICE -> QuestionRules.choice(options);
            case NUMBER, DECIMAL -> QuestionRules.bounds(min, max);
            default -> QuestionRules.none();
        };
    }
}
