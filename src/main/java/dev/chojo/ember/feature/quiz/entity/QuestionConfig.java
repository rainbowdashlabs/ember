/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import java.util.List;

/**
 * Typed configuration models for each question type.
 * Used for JSON schema generation in AI prompts.
 */
public sealed interface QuestionConfig {

    record MultipleChoiceConfig(List<Option> options, double pointsPerCorrect) implements QuestionConfig {
        public record Option(String text, boolean correct) {}
    }

    record TrueFalseConfig(boolean correctAnswer) implements QuestionConfig {}

    record FreeAnswerConfig(int lines, List<String> answers) implements QuestionConfig {}

    record FillBlankConfig(String text, List<String> answers, List<String> distractors, boolean useDropdown)
            implements QuestionConfig {}

    record ConnectConfig(List<Pair> pairs) implements QuestionConfig {
        public record Pair(String left, String right) {}
    }

    record OrderingConfig(List<String> items) implements QuestionConfig {}

    record GeneratedQuestion(String title, Object config) {}
}
