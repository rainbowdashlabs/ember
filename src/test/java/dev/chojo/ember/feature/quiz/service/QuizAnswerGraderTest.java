/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuizAnswerGraderTest {
    private static final String EMPTY_CONFIG = "{}";

    private final QuizAnswerGrader grader = new QuizAnswerGrader();

    private static QuizQuestion question(QuizQuestionType type, String configJson, double points) {
        return new QuizQuestion(
                1, 1, null, type, "Question", "", null, points, false, type.parseConfig(configJson), 0, null, null);
    }

    @Test
    void blankAnswerScoresZero() {
        assertEquals(0, grader.autoGrade(question(QuizQuestionType.TRUE_FALSE, "{\"correctAnswer\":true}", 2), "  "));
        assertEquals(0, grader.autoGrade(question(QuizQuestionType.TRUE_FALSE, "{\"correctAnswer\":true}", 2), null));
    }

    @Test
    void unparsableAnswerNeedsManualGrading() {
        assertTrue(
                grader.autoGrade(question(QuizQuestionType.TRUE_FALSE, "{\"correctAnswer\":true}", 2), "nonsense") < 0);
    }

    @Test
    void manualTypesAreNotGraded() {
        assertTrue(
                grader.autoGrade(question(QuizQuestionType.FREE_ANSWER, EMPTY_CONFIG, 5), "{\"text\":\"answer\"}") < 0);
        assertTrue(
                grader.autoGrade(question(QuizQuestionType.IMAGE_TEXT, EMPTY_CONFIG, 5), "{\"text\":\"answer\"}") < 0);
    }

    @Test
    void multipleChoiceAwardsPerCorrectAndPenalisesWrong() {
        var q = question(
                QuizQuestionType.MULTIPLE_CHOICE,
                "{\"options\":[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false},"
                        + "{\"text\":\"C\",\"correct\":true}],\"pointsPerCorrect\":1.0}",
                3);
        assertEquals(2, grader.autoGrade(q, "{\"selected\":[0,2]}"));
        assertEquals(0, grader.autoGrade(q, "{\"selected\":[1]}"));
        assertEquals(0, grader.autoGrade(q, "{\"selected\":[0,1]}"));
        assertEquals(1, grader.autoGrade(q, "{\"selected\":[0]}"));
    }

    @Test
    void multipleChoiceWithoutOptionsOrSelectionScoresZero() {
        assertEquals(0, grader.autoGrade(question(QuizQuestionType.MULTIPLE_CHOICE, "[]", 3), "{\"selected\":[0]}"));
        assertEquals(
                0,
                grader.autoGrade(
                        question(
                                QuizQuestionType.MULTIPLE_CHOICE,
                                "{\"options\":[{\"text\":\"A\",\"correct\":true}]}",
                                3),
                        EMPTY_CONFIG));
    }

    @Test
    void trueFalseComparesTheGivenValue() {
        var q = question(QuizQuestionType.TRUE_FALSE, "{\"correctAnswer\":true}", 2);
        assertEquals(2, grader.autoGrade(q, "{\"value\":true}"));
        assertEquals(0, grader.autoGrade(q, "{\"value\":false}"));
        assertEquals(0, grader.autoGrade(q, "{\"other\":true}"));
        assertEquals(0, grader.autoGrade(question(QuizQuestionType.TRUE_FALSE, "[]", 2), "{\"value\":true}"));
    }

    @Test
    void connectScoresMatchedPairs() {
        var q = question(
                QuizQuestionType.CONNECT,
                "{\"pairs\":[{\"left\":\"A\",\"right\":\"1\"},{\"left\":\"B\",\"right\":\"2\"}]}",
                2);
        assertEquals(2, grader.autoGrade(q, "{\"pairs\":{\"0\":\"1\",\"1\":\"2\"}}"));
        assertEquals(1, grader.autoGrade(q, "{\"pairs\":{\"0\":\"1\",\"1\":\"9\"}}"));
        assertEquals(0, grader.autoGrade(q, EMPTY_CONFIG));
        assertEquals(0, grader.autoGrade(question(QuizQuestionType.CONNECT, "[]", 2), "{\"pairs\":{}}"));
    }

    @Test
    void orderingScoresPositionsInPlace() {
        var q = question(QuizQuestionType.ORDERING, "{\"items\":[\"A\",\"B\",\"C\"]}", 3);
        assertEquals(3, grader.autoGrade(q, "{\"order\":[0,1,2]}"));
        assertEquals(1, grader.autoGrade(q, "{\"order\":[0,2,1]}"));
        assertEquals(0, grader.autoGrade(q, EMPTY_CONFIG));
        assertEquals(0, grader.autoGrade(question(QuizQuestionType.ORDERING, "[]", 3), "{\"order\":[0]}"));
    }

    @Test
    void fillInTheBlankScoresGapsAndLegacyText() {
        var q = question(QuizQuestionType.FILL_IN_THE_BLANK, "{\"answers\":[\"Paris\",\"Berlin\"]}", 4);
        assertEquals(2, grader.autoGrade(q, "{\"gaps\":{\"0\":\"paris \",\"1\":\"Berlin\"}}"));
        assertEquals(1, grader.autoGrade(q, "{\"gaps\":{\"0\":\"Paris\",\"1\":\"Rome\"}}"));
        assertEquals(1, grader.autoGrade(q, "{\"text\":\"Berlin\"}"));
        assertEquals(0, grader.autoGrade(q, "{\"text\":\"Rome\"}"));
        assertEquals(0, grader.autoGrade(q, "{\"text\":\"  \"}"));
    }

    @Test
    void fillInTheBlankWithoutAnswersNeedsManualGrading() {
        assertTrue(grader.autoGrade(question(QuizQuestionType.FILL_IN_THE_BLANK, "[]", 4), "{\"text\":\"x\"}") < 0);
        assertTrue(grader.autoGrade(
                        question(QuizQuestionType.FILL_IN_THE_BLANK, "{\"answers\":[]}", 4), "{\"text\":\"x\"}")
                < 0);
    }

    @Test
    void enumerationScoresAnyOrderByDefault() {
        var q = question(
                QuizQuestionType.ENUMERATION, "{\"answers\":[\"red\",\"blue\",\"green\"],\"requiredCount\":3}", 3);
        assertEquals(3, grader.autoGrade(q, "{\"items\":[\"green\",\"red\",\"blue\"]}"));
        assertEquals(1, grader.autoGrade(q, "{\"items\":[\"red\",\"pink\",\"grey\"]}"));
        assertEquals(0, grader.autoGrade(q, EMPTY_CONFIG));
    }

    @Test
    void enumerationCanRequireTheGivenOrder() {
        var q = question(
                QuizQuestionType.ENUMERATION,
                "{\"answers\":[\"red\",\"blue\",\"green\"],\"requiredCount\":3,\"orderedRequired\":true}",
                3);
        assertEquals(3, grader.autoGrade(q, "{\"items\":[\"red\",\"blue\",\"green\"]}"));
        assertEquals(1, grader.autoGrade(q, "{\"items\":[\"red\",\"green\",\"blue\"]}"));
    }

    @Test
    void enumerationWithoutAnswersNeedsManualGrading() {
        assertTrue(grader.autoGrade(question(QuizQuestionType.ENUMERATION, "[]", 3), "{\"items\":[\"red\"]}") < 0);
    }
}
