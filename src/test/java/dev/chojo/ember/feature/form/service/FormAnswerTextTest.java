/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPage;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig.Option;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.PageTarget;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** An export writes answers as a person reads them: labels, never the keys they are stored by. */
class FormAnswerTextTest {
    private static final List<Option> OPTIONS = List.of(new Option("k-a", "Zelte"), new Option("k-b", "Essen"));

    @Test
    void writesTheLabelsOfTheOptionsPicked() {
        var question = question(
                FormQuestionType.CHOICE, new FormQuestionConfig.Choice(OPTIONS, true, false, true, null, null));

        assertEquals(
                "Essen, gone, Sonstige: Musik",
                FormAnswerText.of(question, new FormAnswerValue.ChoiceAnswer(List.of("k-b", "gone"), "Musik"), "de"));
        assertEquals(
                "Other: Musik", FormAnswerText.of(question, new FormAnswerValue.ChoiceAnswer(null, "Musik"), "en"));
    }

    @Test
    void writesARankingInItsOrderAndALikertGridPerStatement() {
        var ranking = question(FormQuestionType.RANKING, new FormQuestionConfig.Ranking(OPTIONS));
        var likert = question(FormQuestionType.LIKERT, new FormQuestionConfig.Likert(OPTIONS, 1, 5, List.of()));
        var ratings = new LinkedHashMap<String, Integer>();
        ratings.put("k-b", 2);
        ratings.put("k-a", 4);

        assertEquals(
                "1. Essen, 2. Zelte",
                FormAnswerText.of(ranking, new FormAnswerValue.RankingAnswer(List.of("k-b", "k-a")), "de"));
        assertEquals("Zelte: 4, Essen: 2", FormAnswerText.of(likert, new FormAnswerValue.LikertAnswer(ratings), "de"));
        assertEquals("", FormAnswerText.of(ranking, new FormAnswerValue.RankingAnswer(null), "de"));
        assertEquals("", FormAnswerText.of(likert, new FormAnswerValue.LikertAnswer(null), "de"));
    }

    @Test
    void writesPlainAnswersAsGivenAndNothingForNoAnswer() {
        var text = question(FormQuestionType.TEXT, new FormQuestionConfig.Text(false));

        assertEquals("Ja", FormAnswerText.of(text, new FormAnswerValue.TextAnswer("Ja"), "de"));
        assertEquals("", FormAnswerText.of(text, new FormAnswerValue.TextAnswer(null), "de"));
        assertEquals("2026-10-01", FormAnswerText.of(text, new FormAnswerValue.DateAnswer("2026-10-01"), "de"));
        assertEquals("", FormAnswerText.of(text, new FormAnswerValue.DateAnswer(null), "de"));
        assertEquals("4", FormAnswerText.of(text, new FormAnswerValue.RatingAnswer(4), "de"));
        assertEquals("", FormAnswerText.of(text, new FormAnswerValue.RatingAnswer(0), "de"));
        assertEquals("", FormAnswerText.of(text, null, "de"));
    }

    @Test
    void namesThePagesOfAPathByTitleOrNumber() {
        var pages = List.of(page("p0", ""), page("kids", "Für Kinder"), page("end", " "));

        assertEquals(
                "Seite 1, Für Kinder, Seite 3, gone",
                FormAnswerText.path(List.of("p0", "kids", "end", "gone"), pages, "de"));
        assertEquals("Page 1", FormAnswerText.path(List.of("p0"), pages, "en"));
    }

    private static FormQuestion question(FormQuestionType type, FormQuestionConfig config) {
        return new FormQuestion(1, 1, 0, "p0", type, "Frage", "", false, false, config, null);
    }

    private static FormPage page(String key, String title) {
        return new FormPage(0, 1, key, 0, title, "", PageTarget.NEXT);
    }
}
