/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.generator.entity.QuestionLimits;
import dev.chojo.ember.feature.generator.entity.QuestionLimits.DayBound;
import dev.chojo.ember.feature.generator.entity.TemplateQuestion;
import dev.chojo.ember.feature.generator.entity.TemplateQuestionKind;
import dev.chojo.ember.feature.generator.entity.TextFormat;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerLimitsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    private static final QuestionLimits EVERYTHING = new QuestionLimits(
            new DayBound(LocalDate.of(2026, 1, 1), null),
            new DayBound(null, 7),
            20,
            TextFormat.DIGITS,
            BigDecimal.ZERO,
            BigDecimal.TEN,
            true,
            List.of("A", "B"));

    @Test
    void aQuestionKeepsOnlyTheLimitsOfItsKind() {
        assertEquals(
                QuestionLimits.days(new DayBound(LocalDate.of(2026, 1, 1), null), new DayBound(null, 7)),
                AnswerLimits.checked(TemplateQuestionKind.DATE, EVERYTHING, "Tag"));
        assertEquals(
                QuestionLimits.text(20, TextFormat.DIGITS),
                AnswerLimits.checked(TemplateQuestionKind.TEXT, EVERYTHING, "Text"));
        assertEquals(
                QuestionLimits.number(BigDecimal.ZERO, BigDecimal.TEN, true),
                AnswerLimits.checked(TemplateQuestionKind.NUMBER, EVERYTHING, "Zahl"));
        assertEquals(
                QuestionLimits.choice(List.of("A", "B")),
                AnswerLimits.checked(TemplateQuestionKind.CHOICE, EVERYTHING, "Wahl"));
        assertEquals(QuestionLimits.NONE, AnswerLimits.checked(TemplateQuestionKind.CHECK, EVERYTHING, "Haken"));
    }

    @Test
    void limitsThatContradictEachOtherAreRefused() {
        refusedAsInvalid(TemplateQuestionKind.DATE, QuestionLimits.days(new DayBound(null, 5), new DayBound(null, 1)));
        refusedAsInvalid(
                TemplateQuestionKind.DATE, QuestionLimits.days(new DayBound(LocalDate.of(2026, 5, 1), 3), null));
        refusedAsInvalid(TemplateQuestionKind.DATE, QuestionLimits.days(new DayBound(null, 40_000), null));
        refusedAsInvalid(TemplateQuestionKind.TEXT, QuestionLimits.text(0, null));
        refusedAsInvalid(TemplateQuestionKind.TEXT, QuestionLimits.text(601, null));
        refusedAsInvalid(TemplateQuestionKind.NUMBER, QuestionLimits.number(BigDecimal.TEN, BigDecimal.ONE, false));
    }

    @Test
    void boundsOfDifferentSortsAreNotCompared() {
        var mixed = QuestionLimits.days(new DayBound(LocalDate.of(2030, 1, 1), null), new DayBound(null, 0));
        assertEquals(mixed, AnswerLimits.checked(TemplateQuestionKind.DATE, mixed, "Tag"));
    }

    @Test
    void aChoiceOffersAFewDifferentEntries() {
        refusedAsChoices(List.of());
        refusedAsChoices(List.of("A", "a"));
        refusedAsChoices(List.of(" "));
        refusedAsChoices(List.of("x".repeat(121)));
        refusedAsChoices(IntStream.range(0, 51).mapToObj(index -> "x" + index).toList());
        assertEquals(
                QuestionLimits.choice(List.of("Ja", "Nein")),
                AnswerLimits.checked(
                        TemplateQuestionKind.CHOICE, QuestionLimits.choice(List.of(" Ja ", "Nein")), "Wahl"));
    }

    @Test
    void aNumberIsWrittenWithAPointOrAComma() {
        assertEquals(Optional.of("2.5"), AnswerLimits.number("2,50").map(AnswerLimits::kept));
        assertEquals(Optional.of("-3"), AnswerLimits.number("-3.0").map(AnswerLimits::kept));
        assertEquals(Optional.of("100"), AnswerLimits.number("100").map(AnswerLimits::kept));
        assertTrue(AnswerLimits.number("1.000,5").isEmpty());
        assertTrue(AnswerLimits.number("zwei").isEmpty());
    }

    @Test
    void aChoiceIsTakenInAnyCase() {
        var question = question(TemplateQuestionKind.CHOICE, QuestionLimits.choice(List.of("Leitung")));
        assertEquals(Optional.of("Leitung"), AnswerLimits.choice(question, "LEITUNG"));
        assertTrue(AnswerLimits.choice(question, "Gast").isEmpty());
    }

    @Test
    void anAnswerBreaksTheLimitItIsOutsideOf() {
        var days =
                question(TemplateQuestionKind.DATE, QuestionLimits.days(new DayBound(null, 0), new DayBound(null, 14)));
        assertBroken(DocumentRefusal.DOCUMENT_ANSWER_DAY_OUT_OF_RANGE, days, "2026-10-01");
        assertBroken(DocumentRefusal.DOCUMENT_ANSWER_DAY_OUT_OF_RANGE, days, "2026-10-17");
        assertFits(days, "2026-10-02");
        assertFits(days, "2026-10-16");

        var text = question(TemplateQuestionKind.TEXT, QuestionLimits.text(5, TextFormat.DIGITS));
        assertBroken(DocumentRefusal.DOCUMENT_ANSWER_TOO_LONG, text, "123456");
        assertBroken(DocumentRefusal.DOCUMENT_ANSWER_WRONG_FORMAT, text, "12a");
        assertFits(text, "12345");

        var number = question(TemplateQuestionKind.NUMBER, QuestionLimits.number(BigDecimal.ONE, BigDecimal.TEN, true));
        assertBroken(DocumentRefusal.DOCUMENT_ANSWER_NUMBER_OUT_OF_RANGE, number, "0");
        assertBroken(DocumentRefusal.DOCUMENT_ANSWER_NUMBER_OUT_OF_RANGE, number, "11");
        assertBroken(DocumentRefusal.DOCUMENT_ANSWER_NUMBER_OUT_OF_RANGE, number, "2.5");
        assertFits(number, "10");
    }

    @Test
    void eachFormatTakesWhatItNames() {
        assertTrue(TextFormat.LETTERS.matches("Anna-Lena O'Neil"));
        assertFalse(TextFormat.LETTERS.matches("Anna1"));
        assertTrue(TextFormat.POSTAL_CODE.matches("01067"));
        assertFalse(TextFormat.POSTAL_CODE.matches("1067"));
        assertTrue(TextFormat.EMAIL.matches("a.b@example.org"));
        assertFalse(TextFormat.EMAIL.matches("a.b@example"));
        assertTrue(TextFormat.PHONE.matches("+49 30 / 123-45"));
        assertFalse(TextFormat.PHONE.matches("030 abc"));
    }

    private static TemplateQuestion question(TemplateQuestionKind kind, QuestionLimits limits) {
        return new TemplateQuestion("frage", "Frage", kind, "", false, limits);
    }

    private static void assertBroken(DocumentRefusal expected, TemplateQuestion question, String answer) {
        assertEquals(Optional.of(expected), AnswerLimits.broken(question, answer, TODAY), answer);
    }

    private static void assertFits(TemplateQuestion question, String answer) {
        assertEquals(Optional.empty(), AnswerLimits.broken(question, answer, TODAY), answer);
    }

    private static void refusedAsInvalid(TemplateQuestionKind kind, QuestionLimits limits) {
        var refusal = assertThrows(RefusalResponse.class, () -> AnswerLimits.checked(kind, limits, "Frage"));
        assertEquals(DocumentRefusal.DOCUMENT_TEMPLATE_QUESTION_LIMITS_INVALID, refusal.refusal());
    }

    private static void refusedAsChoices(List<String> choices) {
        var refusal = assertThrows(
                RefusalResponse.class,
                () -> AnswerLimits.checked(TemplateQuestionKind.CHOICE, QuestionLimits.choice(choices), "Wahl"));
        assertEquals(DocumentRefusal.DOCUMENT_TEMPLATE_QUESTION_CHOICES_INVALID, refusal.refusal());
    }
}
