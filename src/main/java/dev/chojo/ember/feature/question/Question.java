/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

/**
 * One question, as everything that asks one has to agree on it.
 *
 * <p>Each feature keeps its own row and its own settings as it stores them, and hands one of these
 * over to be checked. What is common to all of them stands here: what it is called, what kind it is,
 * whether it has to be answered and what it starts from. What only some kinds have lives in
 * {@link QuestionRules}.
 *
 * @param name         what the question is called, which is what a refusal names
 * @param kind         what kind of answer it takes
 * @param required     whether it has to be answered
 * @param defaultValue what an unanswered question falls back to, or null where it has none
 * @param rules        what this kind allows beyond being that kind
 */
public record Question(String name, QuestionKind kind, boolean required, String defaultValue, QuestionRules rules) {

    public Question {
        rules = rules == null ? QuestionRules.none() : rules;
    }

    /** A question of a kind that has nothing further to say about what it accepts. */
    public static Question of(String name, QuestionKind kind, boolean required, String defaultValue) {
        return new Question(name, kind, required, defaultValue, QuestionRules.none());
    }
}
