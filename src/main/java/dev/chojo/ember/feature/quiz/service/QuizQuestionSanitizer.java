/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionConfigView;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionView;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Strips the solution out of a question so it can be handed to the member taking the
 * test. The projection is driven by the question type, not by the stored config variant:
 * a question whose config failed to parse still renders, it just falls back to the empty
 * defaults for its type.
 */
@Singleton
public class QuizQuestionSanitizer {

    private static final int DEFAULT_FREE_ANSWER_LINES = 3;
    private static final int DEFAULT_ENUMERATION_REQUIRED_COUNT = 3;

    /**
     * Projects a question onto its answer-free view.
     */
    public QuizQuestionView sanitize(QuizQuestion question) {
        return new QuizQuestionView(
                question.id(),
                question.catalogId(),
                question.categoryId(),
                question.quizQuestionType(),
                question.title(),
                question.description(),
                question.imageUrl(),
                question.points(),
                question.position(),
                sanitizeConfig(question.quizQuestionType(), question.config()));
    }

    /**
     * Projects a question config onto the answer-free view its type prescribes.
     */
    public QuizQuestionConfigView sanitizeConfig(QuizQuestionType type, QuestionConfig config) {
        return switch (type) {
            case MULTIPLE_CHOICE -> multipleChoice(config);
            case FREE_ANSWER -> freeAnswer(config);
            case FILL_IN_THE_BLANK -> fillInTheBlank(config);
            case CONNECT -> connect(config);
            case ORDERING -> ordering(config);
            case ENUMERATION -> enumeration(config);
            case TRUE_FALSE, IMAGE_TEXT -> new QuizQuestionConfigView.Empty();
        };
    }

    private QuizQuestionConfigView multipleChoice(QuestionConfig config) {
        if (!(config instanceof QuestionConfig.MultipleChoice choice) || choice.options() == null) {
            return new QuizQuestionConfigView.MultipleChoice(List.of(), false);
        }
        var options = choice.options().stream()
                .map(option -> new QuizQuestionConfigView.MultipleChoice.Option(option.text()))
                .toList();
        long correct = choice.options().stream()
                .filter(QuestionConfig.MultipleChoice.Option::correct)
                .count();
        return new QuizQuestionConfigView.MultipleChoice(options, correct > 1);
    }

    private QuizQuestionConfigView freeAnswer(QuestionConfig config) {
        if (!(config instanceof QuestionConfig.FreeAnswer free)) {
            return new QuizQuestionConfigView.FreeAnswer(DEFAULT_FREE_ANSWER_LINES);
        }
        return new QuizQuestionConfigView.FreeAnswer(free.lines());
    }

    private QuizQuestionConfigView fillInTheBlank(QuestionConfig config) {
        if (!(config instanceof QuestionConfig.FillInTheBlank fill)) {
            return new QuizQuestionConfigView.FillInTheBlank("", List.of(), 0, false);
        }
        var answers = fill.answers() != null ? fill.answers() : List.<String>of();
        var wordBank = new ArrayList<>(answers);
        if (fill.distractors() != null) wordBank.addAll(fill.distractors());
        return new QuizQuestionConfigView.FillInTheBlank(
                fill.text() != null ? fill.text() : "", wordBank, answers.size(), fill.useDropdown());
    }

    private QuizQuestionConfigView connect(QuestionConfig config) {
        if (!(config instanceof QuestionConfig.Connect connect) || connect.pairs() == null) {
            return new QuizQuestionConfigView.Connect(List.of(), List.of());
        }
        var leftItems =
                connect.pairs().stream().map(QuestionConfig.Connect.Pair::left).toList();
        var rightItems =
                connect.pairs().stream().map(QuestionConfig.Connect.Pair::right).toList();
        return new QuizQuestionConfigView.Connect(leftItems, rightItems);
    }

    private QuizQuestionConfigView ordering(QuestionConfig config) {
        if (!(config instanceof QuestionConfig.Ordering order) || order.items() == null) {
            return new QuizQuestionConfigView.Ordering(List.of());
        }
        return new QuizQuestionConfigView.Ordering(order.items());
    }

    private QuizQuestionConfigView enumeration(QuestionConfig config) {
        if (!(config instanceof QuestionConfig.Enumeration enumeration)) {
            return new QuizQuestionConfigView.Enumeration(DEFAULT_ENUMERATION_REQUIRED_COUNT);
        }
        return new QuizQuestionConfigView.Enumeration(enumeration.requiredCount());
    }
}
