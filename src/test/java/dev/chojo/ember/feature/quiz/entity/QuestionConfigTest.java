/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestionConfigTest {

    @Test
    void multipleChoiceAutoPoints() {
        var config = QuizQuestionType.MULTIPLE_CHOICE.parseConfig("""
                {"options":[{"text":"A","correct":true},{"text":"B","correct":false},{"text":"C","correct":true}],"pointsPerCorrect":0.5}
                """);
        assertInstanceOf(QuestionConfig.MultipleChoice.class, config);
        var mc = (QuestionConfig.MultipleChoice) config;
        assertEquals(2, mc.gradableItemCount());
        assertEquals(0.5, mc.pointsPerCorrect());
        assertEquals(1.0, mc.autoPoints());
    }

    @Test
    void multipleChoiceDefaultPointsPerCorrect() {
        var config = QuizQuestionType.MULTIPLE_CHOICE.parseConfig("""
                {"options":[{"text":"A","correct":true},{"text":"B","correct":false}]}
                """);
        assertInstanceOf(QuestionConfig.MultipleChoice.class, config);
        assertEquals(1.0, config.pointsPerCorrect());
        assertEquals(1.0, config.autoPoints());
    }

    @Test
    void connectAutoPoints() {
        var config = QuizQuestionType.CONNECT.parseConfig("""
                {"pairs":[{"left":"A","right":"1"},{"left":"B","right":"2"},{"left":"C","right":"3"}],"pointsPerCorrect":2}
                """);
        assertInstanceOf(QuestionConfig.Connect.class, config);
        assertEquals(3, config.gradableItemCount());
        assertEquals(2.0, config.pointsPerCorrect());
        assertEquals(6.0, config.autoPoints());
    }

    @Test
    void orderingAutoPoints() {
        var config = QuizQuestionType.ORDERING.parseConfig("""
                {"items":["first","second","third","fourth"],"pointsPerCorrect":0.5}
                """);
        assertInstanceOf(QuestionConfig.Ordering.class, config);
        assertEquals(4, config.gradableItemCount());
        assertEquals(2.0, config.autoPoints());
    }

    @Test
    void fillInTheBlankAutoPoints() {
        var config = QuizQuestionType.FILL_IN_THE_BLANK.parseConfig("""
                {"text":"The ___ is ___","answers":["sky","blue"],"pointsPerCorrect":1.5}
                """);
        assertInstanceOf(QuestionConfig.FillInTheBlank.class, config);
        assertEquals(2, config.gradableItemCount());
        assertEquals(3.0, config.autoPoints());
    }

    @Test
    void enumerationAutoPoints() {
        var config = QuizQuestionType.ENUMERATION.parseConfig("""
                {"answers":["red","green","blue"],"requiredCount":2,"orderedRequired":false,"pointsPerCorrect":1}
                """);
        assertInstanceOf(QuestionConfig.Enumeration.class, config);
        assertEquals(3, config.gradableItemCount());
        assertEquals(3.0, config.autoPoints());
    }

    @Test
    void trueFalseAutoPoints() {
        var config = QuizQuestionType.TRUE_FALSE.parseConfig("""
                {"correctAnswer":true}
                """);
        assertInstanceOf(QuestionConfig.TrueFalse.class, config);
        assertEquals(1, config.gradableItemCount());
        assertEquals(1.0, config.autoPoints());
    }

    @Test
    void freeAnswerAutoPoints() {
        var config = QuizQuestionType.FREE_ANSWER.parseConfig("""
                {"answers":["answer1","answer2"],"lines":3,"pointsPerCorrect":2}
                """);
        assertInstanceOf(QuestionConfig.FreeAnswer.class, config);
        assertEquals(2, config.gradableItemCount());
        assertEquals(4.0, config.autoPoints());
    }

    @Test
    void freeAnswerNoAnswersFallsBackToOne() {
        var config = QuizQuestionType.FREE_ANSWER.parseConfig("""
                {"answers":[],"lines":3}
                """);
        assertInstanceOf(QuestionConfig.FreeAnswer.class, config);
        assertEquals(1, config.gradableItemCount());
    }

    @Test
    void invalidConfigReturnsUnknown() {
        var config = QuizQuestionType.MULTIPLE_CHOICE.parseConfig("not json");
        assertInstanceOf(QuestionConfig.Unknown.class, config);
        assertEquals(1.0, config.autoPoints());
    }

    @Test
    void nullConfigReturnsUnknown() {
        var config = QuizQuestionType.MULTIPLE_CHOICE.parseConfig(null);
        assertInstanceOf(QuestionConfig.Unknown.class, config);
    }

    @Test
    void emptyConfigReturnsUnknown() {
        var config = QuizQuestionType.ORDERING.parseConfig("");
        assertInstanceOf(QuestionConfig.Unknown.class, config);
    }
}
