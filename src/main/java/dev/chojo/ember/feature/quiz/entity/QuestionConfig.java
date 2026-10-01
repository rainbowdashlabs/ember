/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Typed question config records for Jackson deserialization.
 * Each record maps directly to the JSON config structure of its question type.
 * The config class is stored on {@link QuizQuestionType} for direct parsing.
 *
 * <p>A stored config is read leniently, so a list a config of an older version never carried
 * comes back as null rather than empty.
 */
public sealed interface QuestionConfig {

    default double pointsPerCorrect() {
        return 1;
    }

    default int gradableItemCount() {
        return 1;
    }

    default double autoPoints() {
        return gradableItemCount() * pointsPerCorrect();
    }

    record MultipleChoice(@Nullable List<ChoiceOption> options, double pointsPerCorrect) implements QuestionConfig {
        @Override
        public double pointsPerCorrect() {
            return pointsPerCorrect > 0 ? pointsPerCorrect : 1;
        }

        @Override
        public int gradableItemCount() {
            return options == null
                    ? 0
                    : (int) options.stream().filter(ChoiceOption::correct).count();
        }

        public record ChoiceOption(String text, boolean correct) {}
    }

    record Connect(@Nullable List<Pair> pairs, double pointsPerCorrect) implements QuestionConfig {
        @Override
        public double pointsPerCorrect() {
            return pointsPerCorrect > 0 ? pointsPerCorrect : 1;
        }

        @Override
        public int gradableItemCount() {
            return pairs == null ? 0 : pairs.size();
        }

        public record Pair(String left, String right) {}
    }

    record Ordering(@Nullable List<String> items, double pointsPerCorrect) implements QuestionConfig {
        @Override
        public double pointsPerCorrect() {
            return pointsPerCorrect > 0 ? pointsPerCorrect : 1;
        }

        @Override
        public int gradableItemCount() {
            return items == null ? 0 : items.size();
        }
    }

    record FillInTheBlank(
            @Nullable String text,
            @Nullable List<String> answers,
            @Nullable List<String> distractors,
            boolean useDropdown,
            double pointsPerCorrect)
            implements QuestionConfig {
        @Override
        public double pointsPerCorrect() {
            return pointsPerCorrect > 0 ? pointsPerCorrect : 1;
        }

        @Override
        public int gradableItemCount() {
            return answers == null ? 0 : answers.size();
        }
    }

    record Enumeration(
            @Nullable List<String> answers, int requiredCount, boolean orderedRequired, double pointsPerCorrect)
            implements QuestionConfig {
        @Override
        public double pointsPerCorrect() {
            return pointsPerCorrect > 0 ? pointsPerCorrect : 1;
        }

        @Override
        public int gradableItemCount() {
            return answers == null ? 0 : answers.size();
        }
    }

    record FreeAnswer(@Nullable List<String> answers, int lines, double pointsPerCorrect) implements QuestionConfig {
        @Override
        public double pointsPerCorrect() {
            return pointsPerCorrect > 0 ? pointsPerCorrect : 1;
        }

        @Override
        public int gradableItemCount() {
            return answers == null || answers.isEmpty() ? 1 : answers.size();
        }
    }

    record TrueFalse(boolean correctAnswer) implements QuestionConfig {}

    record ImageText(@Nullable String imageUrl, @Nullable String answer) implements QuestionConfig {}

    record Unknown() implements QuestionConfig {}
}
