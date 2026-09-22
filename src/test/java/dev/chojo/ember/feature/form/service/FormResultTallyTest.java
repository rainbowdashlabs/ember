/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.feature.form.entity.FormAnswer;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The counting behind every results chart.
 *
 * <p>These numbers used to be counted in the browser; the tests hold them to what the charts drew
 * then, so moving the counting to the server changed nothing a reader could see.
 */
class FormResultTallyTest {
    private static int nextAnswerId = 1;

    @Test
    void aChoiceCountsVotesPerOptionAndOtherAnswersApart() {
        var question = question(
                1,
                FormQuestionType.CHOICE,
                new FormQuestionConfig.Choice(List.of("Zeltlager", "Kreisjugendtag"), true, false, true, null, null));
        var answers = answers(
                1,
                new FormAnswerValue.Choice(List.of(0), null),
                new FormAnswerValue.Choice(List.of(0, 1), null),
                new FormAnswerValue.Choice(List.of(1), "Übungsdienst"),
                new FormAnswerValue.Choice(List.of(7), null));

        var tally = only(question, answers, null);

        assertEquals(4, tally.answerCount());
        assertEquals(List.of(2, 2), tally.optionCounts(), "an index outside the options is not counted");
        assertEquals(1, tally.otherCount());
    }

    @Test
    void aRatingIsAHistogramFromOneUp() {
        var question = question(2, FormQuestionType.RATING, new FormQuestionConfig.Rating(null, null));
        var answers = answers(
                2,
                new FormAnswerValue.Rating(5),
                new FormAnswerValue.Rating(4),
                new FormAnswerValue.Rating(5),
                new FormAnswerValue.Rating(9));

        var tally = only(question, answers, null);

        assertEquals(List.of(0, 0, 0, 1, 2), tally.ratingCounts(), "no scale means five, and 9 is off it");
    }

    @Test
    void aRankingScoresFirstPlaceHighest() {
        var question = question(3, FormQuestionType.RANKING, new FormQuestionConfig.Ranking(List.of("A", "B", "C")));
        var answers = answers(
                3, new FormAnswerValue.Ranking(List.of(2, 0, 1)), new FormAnswerValue.Ranking(List.of(0, 2, 1)));

        var tally = only(question, answers, null);

        assertEquals(List.of(5, 2, 5), tally.rankingScores());
    }

    @Test
    void aLikertGridAveragesEachStatementToOneDecimal() {
        var question = question(
                4,
                FormQuestionType.LIKERT,
                new FormQuestionConfig.Likert(List.of("Essen", "Programm", "Unterkunft"), 1, 5, null));
        var answers = answers(
                4,
                new FormAnswerValue.Likert(Map.of("0", 4, "1", 5)),
                new FormAnswerValue.Likert(Map.of("0", 5, "1", 2)),
                new FormAnswerValue.Likert(Map.of("0", 5)));

        var tally = only(question, answers, null);

        assertEquals(4.7, tally.statementAverages().get(0));
        assertEquals(3.5, tally.statementAverages().get(1));
        assertNull(tally.statementAverages().get(2), "a statement nobody rated has no average rather than zero");
    }

    @Test
    void writtenAndDateAnswersAreListedAsGiven() {
        var text = question(5, FormQuestionType.TEXT, new FormQuestionConfig.Text(false));
        var date = question(6, FormQuestionType.DATE, new FormQuestionConfig.Date());
        var answers = new ArrayList<FormAnswer>();
        answers.addAll(answers(5, new FormAnswerValue.Text("Mehr Zeit"), new FormAnswerValue.Text("  ")));
        answers.addAll(answers(6, new FormAnswerValue.DateValue("2026-07-01")));

        var tallies = FormResultTally.tally(List.of(text, date), answers, null);

        assertEquals(List.of("Mehr Zeit"), tallies.get(0).values(), "a blank answer lists nothing");
        assertEquals(2, tallies.get(0).answerCount());
        assertEquals(List.of("2026-07-01"), tallies.get(1).values());
    }

    @Test
    void onlyTheChosenResponsesAreCounted() {
        var question = question(7, FormQuestionType.RATING, new FormQuestionConfig.Rating(3, null));
        var answers =
                answers(7, new FormAnswerValue.Rating(1), new FormAnswerValue.Rating(3), new FormAnswerValue.Rating(3));
        int firstResponse = answers.getFirst().responseId();

        var tally = only(question, answers, Set.of(firstResponse));

        assertEquals(1, tally.answerCount());
        assertEquals(List.of(1, 0, 0), tally.ratingCounts());
    }

    @Test
    void anUnreadableAnswerIsSkipped() {
        var question = question(8, FormQuestionType.RATING, new FormQuestionConfig.Rating(5, null));
        var answers = new ArrayList<FormAnswer>();
        answers.add(new FormAnswer(nextAnswerId++, 999, 8, "not json"));
        answers.addAll(answers(8, new FormAnswerValue.Rating(2)));

        assertEquals(1, only(question, answers, null).answerCount());
    }

    private static FormResultTally.QuestionTally only(
            FormQuestion question, List<FormAnswer> answers, Set<Integer> responses) {
        return FormResultTally.tally(List.of(question), answers, responses).getFirst();
    }

    private static FormQuestion question(int id, FormQuestionType type, FormQuestionConfig config) {
        return new FormQuestion(id, 1, id, type, "Frage " + id, "", false, false, config);
    }

    /** One stored answer per value, each from a response of its own, stored the way the app stores it. */
    private static List<FormAnswer> answers(int questionId, FormAnswerValue... values) {
        return Arrays.stream(values)
                .map(value -> {
                    int id = nextAnswerId++;
                    return new FormAnswer(id, 1000 + id, questionId, value.toJson());
                })
                .toList();
    }
}
