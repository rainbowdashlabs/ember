/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.question;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one check every question goes through, kind by kind.
 */
class QuestionCheckTest {

    private static Question choice(boolean required, String defaultValue, String... options) {
        return new Question(
                "Shirtgröße", QuestionKind.CHOICE, required, defaultValue, QuestionRules.choice(List.of(options)));
    }

    private static QuestionProblem.Code codeOf(Question question, String answer) {
        return QuestionCheck.answer(question, answer).orElseThrow().code();
    }

    @Test
    void anAnswerOutsideTheOptionsIsRefused() {
        var question = choice(false, null, "S", "M", "L");
        assertTrue(QuestionCheck.answer(question, "M").isEmpty());
        assertEquals(QuestionProblem.Code.NOT_AN_OPTION, codeOf(question, "XXL"));
    }

    /** A choice nobody wrote options for takes anything, which is what it has always done. */
    @Test
    void aChoiceWithoutOptionsTakesAnything() {
        assertTrue(QuestionCheck.answer(choice(false, null), "was auch immer").isEmpty());
    }

    @Test
    void aQuestionThatHasToBeAnsweredIsRefusedEmpty() {
        assertEquals(QuestionProblem.Code.REQUIRED, codeOf(choice(true, null, "S", "M"), ""));
        assertEquals(QuestionProblem.Code.REQUIRED, codeOf(choice(true, null, "S", "M"), null));
    }

    /** A default is an answer: a required question that has one is answered by having it. */
    @Test
    void aDefaultAnswersARequiredQuestion() {
        assertTrue(QuestionCheck.answer(choice(true, "M", "S", "M"), null).isEmpty());
    }

    /**
     * The default is measured by the same rules, where the question is written rather than where it
     * is answered. A sheet set up to start at a value the question refuses is a mistake nobody can
     * put right afterwards.
     */
    @Test
    void aDefaultOutsideTheOptionsIsRefusedWhereItIsSet() {
        assertTrue(QuestionCheck.defaultValue(choice(false, "M", "S", "M", "L")).isEmpty());
        assertEquals(
                QuestionProblem.Code.NOT_AN_OPTION,
                QuestionCheck.defaultValue(choice(false, "XXL", "S", "M", "L"))
                        .orElseThrow()
                        .code());
        assertTrue(QuestionCheck.defaultValue(choice(false, null, "S", "M")).isEmpty());
    }

    @Test
    void aWholeNumberRefusesAFractionAndItsBounds() {
        var question = new Question("Gäste", QuestionKind.NUMBER, false, null, QuestionRules.bounds(0, 5));
        assertTrue(QuestionCheck.answer(question, "3").isEmpty());
        assertEquals(QuestionProblem.Code.NOT_A_NUMBER, codeOf(question, "drei"));
        assertEquals(QuestionProblem.Code.NOT_A_NUMBER, codeOf(question, "1.5"));
        assertEquals(QuestionProblem.Code.OUT_OF_RANGE, codeOf(question, "9"));
        assertEquals(QuestionProblem.Code.OUT_OF_RANGE, codeOf(question, "-1"));
    }

    /** Gear is measured, so a fraction is an answer there and the bounds still hold. */
    @Test
    void aDecimalTakesAFraction() {
        var question = new Question("Länge", QuestionKind.DECIMAL, false, null, QuestionRules.bounds(0, 10));
        assertTrue(QuestionCheck.answer(question, "1.5").isEmpty());
        assertTrue(QuestionCheck.answer(question, "1,5").isEmpty());
        assertEquals(QuestionProblem.Code.OUT_OF_RANGE, codeOf(question, "11"));
    }

    @Test
    void aDateAndATimeHaveToBeOne() {
        var date = Question.of("Geburtstag", QuestionKind.DATE, false, null);
        assertTrue(QuestionCheck.answer(date, "2011-09-01").isEmpty());
        assertEquals(QuestionProblem.Code.NOT_A_DATE, codeOf(date, "01.09.2011"));

        var time = Question.of("Beginn", QuestionKind.TIME, false, null);
        assertTrue(QuestionCheck.answer(time, "18:30").isEmpty());
        assertEquals(QuestionProblem.Code.NOT_A_TIME, codeOf(time, "halb sieben"));
    }

    @Test
    void yesOrNoIsOneOfFourSpellings() {
        var question = Question.of("Dabei", QuestionKind.BOOLEAN, false, null);
        for (String answer : List.of("true", "FALSE", "1", "0")) {
            assertTrue(QuestionCheck.answer(question, answer).isEmpty(), answer + " reads as yes or no");
        }
        assertEquals(QuestionProblem.Code.NOT_A_BOOLEAN, codeOf(question, "vielleicht"));
    }

    /** A web address nobody can follow looks answered and is not. */
    @Test
    void aWebAddressHasToBeFollowable() {
        var question = Question.of("Seite", QuestionKind.URL, false, null);
        assertTrue(QuestionCheck.answer(question, "https://example.org").isEmpty());
        assertEquals(QuestionProblem.Code.NOT_A_URL, codeOf(question, "example.org"));
    }

    @Test
    void namingMembersMeansNamingMembers() {
        var one = Question.of("Leitung", QuestionKind.MEMBER, false, null);
        assertTrue(QuestionCheck.answer(one, "42").isEmpty());
        assertEquals(QuestionProblem.Code.NOT_A_MEMBER, codeOf(one, "Anna"));
        assertEquals(QuestionProblem.Code.NOT_A_MEMBER, codeOf(one, "[42,17]"));

        var several = Question.of("Betreuung", QuestionKind.MEMBER_LIST, false, null);
        assertTrue(QuestionCheck.answer(several, "[42,17]").isEmpty());
        assertTrue(QuestionCheck.answer(several, "42").isEmpty());
        assertEquals(QuestionProblem.Code.NOT_A_MEMBER, codeOf(several, "[42,Anna]"));
    }

    /** Three features keep their answers as JSON, so what somebody typed arrives wrapped in quotes. */
    @Test
    void anAnswerStoredAsJsonIsReadAsWhatSomebodyTyped() {
        assertTrue(QuestionCheck.answer(Question.of("Geburtstag", QuestionKind.DATE, false, null), "\"2011-09-01\"")
                .isEmpty());
        assertTrue(QuestionCheck.answer(choice(false, null, "S", "M"), "\"M\"").isEmpty());
    }

    /**
     * The features that keep answers as JSON write an unanswered question in three spellings, and a
     * choice left alone must not read as an answer outside its options.
     */
    @Test
    void nothingIsNothingHoweverItIsWrittenDown() {
        var question = choice(false, null, "S", "M");
        for (String nothing : List.of("", "   ", "null", "\"\"")) {
            assertTrue(QuestionCheck.answerIfGiven(question, nothing).isEmpty(), "[" + nothing + "] says nothing");
        }
        assertEquals(QuestionProblem.Code.REQUIRED, codeOf(choice(true, null, "S", "M"), "null"));
    }

    @Test
    void textTakesWhateverItIsGiven() {
        assertTrue(QuestionCheck.answer(Question.of("Notiz", QuestionKind.TEXT, false, null), "irgendwas")
                .isEmpty());
        assertTrue(QuestionCheck.answer(Question.of("Notiz", QuestionKind.LONG_TEXT, false, null), "zwei\nZeilen")
                .isEmpty());
    }
}
