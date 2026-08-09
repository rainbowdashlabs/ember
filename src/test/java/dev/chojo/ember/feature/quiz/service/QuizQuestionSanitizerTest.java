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
import dev.chojo.ember.util.Json;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizQuestionSanitizerTest {

    private final QuizQuestionSanitizer sanitizer = new QuizQuestionSanitizer();

    private QuizQuestion question(QuizQuestionType type, QuestionConfig config) {
        return new QuizQuestion(
                7,
                3,
                9,
                type,
                "Question title",
                "Question description",
                "image.png",
                2.5,
                true,
                config,
                4,
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private String json(QuizQuestionType type, QuestionConfig config) {
        return Json.MAPPER.writeValueAsString(sanitizer.sanitizeConfig(type, config));
    }

    @Test
    void sanitizeCopiesPresentationFieldsAndDropsGradingFields() {
        var view = sanitizer.sanitize(question(QuizQuestionType.TRUE_FALSE, new QuestionConfig.TrueFalse(true)));

        assertEquals(7, view.id());
        assertEquals(3, view.catalogId());
        assertEquals(9, view.categoryId().intValue());
        assertEquals(QuizQuestionType.TRUE_FALSE, view.quizQuestionType());
        assertEquals("Question title", view.title());
        assertEquals("Question description", view.description());
        assertEquals("image.png", view.imageUrl());
        assertEquals(2.5, view.points());
        assertEquals(4, view.position());
        assertEquals(
                "{\"id\":7,\"catalogId\":3,\"categoryId\":9,\"quizQuestionType\":\"TRUE_FALSE\","
                        + "\"title\":\"Question title\",\"description\":\"Question description\","
                        + "\"imageUrl\":\"image.png\",\"points\":2.5,\"position\":4,\"config\":{}}",
                Json.MAPPER.writeValueAsString(view));
    }

    @Test
    void sanitizeKeepsNullableFieldsAsNull() {
        var question = new QuizQuestion(
                1,
                1,
                null,
                QuizQuestionType.IMAGE_TEXT,
                "T",
                null,
                null,
                1,
                false,
                new QuestionConfig.ImageText("img", "answer"),
                0,
                Instant.EPOCH,
                Instant.EPOCH);

        var view = sanitizer.sanitize(question);

        assertEquals(
                "{\"id\":1,\"catalogId\":1,\"categoryId\":null,\"quizQuestionType\":\"IMAGE_TEXT\","
                        + "\"title\":\"T\",\"description\":null,\"imageUrl\":null,\"points\":1.0,"
                        + "\"position\":0,\"config\":{}}",
                Json.MAPPER.writeValueAsString(view));
    }

    @Test
    void multipleChoiceDropsCorrectMarkerAndReportsSingleSelect() {
        var config = new QuestionConfig.MultipleChoice(
                List.of(
                        new QuestionConfig.MultipleChoice.Option("A", true),
                        new QuestionConfig.MultipleChoice.Option("B", false)),
                1);

        var view = assertInstanceOf(
                QuizQuestionConfigView.MultipleChoice.class,
                sanitizer.sanitizeConfig(QuizQuestionType.MULTIPLE_CHOICE, config));

        assertEquals(2, view.options().size());
        assertEquals("A", view.options().getFirst().text());
        assertFalse(view.multiSelect());
        assertEquals(
                "{\"options\":[{\"text\":\"A\"},{\"text\":\"B\"}],\"multiSelect\":false}",
                json(QuizQuestionType.MULTIPLE_CHOICE, config));
    }

    @Test
    void multipleChoiceReportsMultiSelectWhenMoreThanOneOptionIsCorrect() {
        var config = new QuestionConfig.MultipleChoice(
                List.of(
                        new QuestionConfig.MultipleChoice.Option("A", true),
                        new QuestionConfig.MultipleChoice.Option("B", true)),
                1);

        assertEquals(
                "{\"options\":[{\"text\":\"A\"},{\"text\":\"B\"}],\"multiSelect\":true}",
                json(QuizQuestionType.MULTIPLE_CHOICE, config));
    }

    @Test
    void multipleChoiceFallsBackToEmptyOptions() {
        assertEquals(
                "{\"options\":[],\"multiSelect\":false}",
                json(QuizQuestionType.MULTIPLE_CHOICE, new QuestionConfig.MultipleChoice(null, 1)));
        assertEquals(
                "{\"options\":[],\"multiSelect\":false}",
                json(QuizQuestionType.MULTIPLE_CHOICE, new QuestionConfig.Unknown()));
    }

    @Test
    void trueFalseAndImageTextRevealNothing() {
        assertEquals("{}", json(QuizQuestionType.TRUE_FALSE, new QuestionConfig.TrueFalse(true)));
        assertEquals("{}", json(QuizQuestionType.IMAGE_TEXT, new QuestionConfig.ImageText("img", "secret")));
        assertEquals("{}", json(QuizQuestionType.TRUE_FALSE, new QuestionConfig.Unknown()));
    }

    @Test
    void freeAnswerKeepsLineCountAndDropsAnswers() {
        assertEquals(
                "{\"lines\":6}",
                json(QuizQuestionType.FREE_ANSWER, new QuestionConfig.FreeAnswer(List.of("secret"), 6, 1)));
    }

    @Test
    void freeAnswerFallsBackToThreeLines() {
        assertEquals("{\"lines\":3}", json(QuizQuestionType.FREE_ANSWER, new QuestionConfig.Unknown()));
    }

    @Test
    void fillInTheBlankMixesAnswersAndDistractorsIntoOneWordBank() {
        var config = new QuestionConfig.FillInTheBlank(
                "The capital is _ and _", List.of("Paris", "Berlin"), List.of("Rome"), true, 1);

        var view = assertInstanceOf(
                QuizQuestionConfigView.FillInTheBlank.class,
                sanitizer.sanitizeConfig(QuizQuestionType.FILL_IN_THE_BLANK, config));

        assertEquals(List.of("Paris", "Berlin", "Rome"), view.wordBank());
        assertEquals(2, view.gapCount());
        assertTrue(view.useDropdown());
        assertEquals(
                "{\"text\":\"The capital is _ and _\",\"wordBank\":[\"Paris\",\"Berlin\",\"Rome\"],"
                        + "\"gapCount\":2,\"useDropdown\":true}",
                json(QuizQuestionType.FILL_IN_THE_BLANK, config));
    }

    @Test
    void fillInTheBlankToleratesMissingParts() {
        assertEquals(
                "{\"text\":\"\",\"wordBank\":[],\"gapCount\":0,\"useDropdown\":false}",
                json(
                        QuizQuestionType.FILL_IN_THE_BLANK,
                        new QuestionConfig.FillInTheBlank(null, null, null, false, 0)));
        assertEquals(
                "{\"text\":\"\",\"wordBank\":[],\"gapCount\":0,\"useDropdown\":false}",
                json(QuizQuestionType.FILL_IN_THE_BLANK, new QuestionConfig.Unknown()));
    }

    @Test
    void connectSplitsPairsIntoTwoColumns() {
        var config = new QuestionConfig.Connect(
                List.of(
                        new QuestionConfig.Connect.Pair("left1", "right1"),
                        new QuestionConfig.Connect.Pair("left2", "right2")),
                1);

        assertEquals(
                "{\"leftItems\":[\"left1\",\"left2\"],\"rightItems\":[\"right1\",\"right2\"]}",
                json(QuizQuestionType.CONNECT, config));
    }

    @Test
    void connectFallsBackToEmptyColumns() {
        assertEquals(
                "{\"leftItems\":[],\"rightItems\":[]}",
                json(QuizQuestionType.CONNECT, new QuestionConfig.Connect(null, 1)));
        assertEquals(
                "{\"leftItems\":[],\"rightItems\":[]}", json(QuizQuestionType.CONNECT, new QuestionConfig.Unknown()));
    }

    @Test
    void orderingKeepsItems() {
        assertEquals(
                "{\"items\":[\"A\",\"B\"]}",
                json(QuizQuestionType.ORDERING, new QuestionConfig.Ordering(List.of("A", "B"), 1)));
    }

    @Test
    void orderingFallsBackToEmptyItems() {
        assertEquals("{\"items\":[]}", json(QuizQuestionType.ORDERING, new QuestionConfig.Ordering(null, 1)));
        assertEquals("{\"items\":[]}", json(QuizQuestionType.ORDERING, new QuestionConfig.Unknown()));
    }

    @Test
    void enumerationKeepsOnlyTheRequiredCount() {
        assertEquals(
                "{\"requiredCount\":2}",
                json(
                        QuizQuestionType.ENUMERATION,
                        new QuestionConfig.Enumeration(List.of("red", "blue"), 2, false, 1)));
    }

    @Test
    void enumerationFallsBackToThree() {
        assertEquals("{\"requiredCount\":3}", json(QuizQuestionType.ENUMERATION, new QuestionConfig.Unknown()));
    }

    @Test
    void everyQuestionTypeIsProjected() {
        for (var type : QuizQuestionType.values()) {
            assertFalse(json(type, new QuestionConfig.Unknown()).isEmpty(), "no projection for " + type);
        }
    }
}
