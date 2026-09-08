/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import java.math.BigDecimal;
import java.util.List;

/**
 * The settings a question carries whoever asks it.
 *
 * <p>Six features store these, and each declared its own: five of them declare the options of a
 * choice, four whether an answer is required, three what it starts from, three how wide the box is.
 * A seventh feature added tomorrow would declare them a seventh time, and one of the six would spell
 * one of them differently, which is how the six drifted apart in the first place.
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
 */
public record QuestionSettings(
        boolean required, String defaultValue, List<String> options, BigDecimal min, BigDecimal max) {

    public QuestionSettings {
        options = options == null ? List.of() : List.copyOf(options);
    }

    /** The settings of a question that carries none of them. */
    public static QuestionSettings none() {
        return new QuestionSettings(false, null, List.of(), null, null);
    }

    /** The settings of a question that only says whether it has to be answered. */
    public static QuestionSettings required(boolean required) {
        return new QuestionSettings(required, null, List.of(), null, null);
    }

    /** The same settings with whether an answer is expected, where the feature keeps that apart. */
    public QuestionSettings withRequired(boolean expected) {
        return new QuestionSettings(expected, defaultValue, options, min, max);
    }

    /** The same settings with a starting value, however the feature stores one. */
    public QuestionSettings withDefault(Object value) {
        return new QuestionSettings(required, value == null ? null : String.valueOf(value), options, min, max);
    }

    /** The same settings with the answers a choice offers. */
    public QuestionSettings withOptions(List<String> offered) {
        return new QuestionSettings(required, defaultValue, offered, min, max);
    }

    /** The same settings with what a number has to sit between, as whole numbers. */
    public QuestionSettings withBounds(Integer smallest, Integer largest) {
        return withBounds(
                smallest == null ? null : BigDecimal.valueOf(smallest),
                largest == null ? null : BigDecimal.valueOf(largest));
    }

    /** The same settings with what a number has to sit between. */
    public QuestionSettings withBounds(BigDecimal smallest, BigDecimal largest) {
        return new QuestionSettings(required, defaultValue, options, smallest, largest);
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

    private QuestionRules rulesFor(QuestionKind kind) {
        return switch (kind) {
            case CHOICE -> QuestionRules.choice(options);
            case NUMBER, DECIMAL -> QuestionRules.bounds(min, max);
            default -> QuestionRules.none();
        };
    }
}
