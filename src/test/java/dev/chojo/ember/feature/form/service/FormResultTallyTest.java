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
import dev.chojo.ember.feature.form.entity.FormQuestionConfig.Option;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.FormResponse;
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
                new FormQuestionConfig.Choice(
                        Option.numbered("Zeltlager", "Kreisjugendtag"), true, false, true, null, null));
        var answers = answers(
                1,
                new FormAnswerValue.ChoiceAnswer(List.of("o0"), null),
                new FormAnswerValue.ChoiceAnswer(List.of("o0", "o1"), null),
                new FormAnswerValue.ChoiceAnswer(List.of("o1"), "Übungsdienst"),
                new FormAnswerValue.ChoiceAnswer(List.of("gone"), null));

        var tally = only(question, answers, null);

        assertEquals(4, tally.answerCount());
        assertEquals(Map.of("o0", 2, "o1", 2), tally.optionCounts(), "a key the options lack is not counted");
        assertEquals(1, tally.otherCount());
    }

    @Test
    void optionsAreCountedByKeyInTheOrderTheQuestionListsThem() {
        var question = question(
                9,
                FormQuestionType.CHOICE,
                new FormQuestionConfig.Choice(
                        List.of(new Option("b", "Zweite"), new Option("a", "Erste")), false, false, false, null, null));
        var answers = answers(9, new FormAnswerValue.ChoiceAnswer(List.of("a"), null));

        var tally = only(question, answers, null);

        assertEquals(List.of("b", "a"), List.copyOf(tally.optionCounts().keySet()));
        assertEquals(1, tally.optionCounts().get("a"));
    }

    @Test
    void aRatingIsAHistogramFromOneUp() {
        var question = question(2, FormQuestionType.RATING, new FormQuestionConfig.Rating(null, null));
        var answers = answers(
                2,
                new FormAnswerValue.RatingAnswer(5),
                new FormAnswerValue.RatingAnswer(4),
                new FormAnswerValue.RatingAnswer(5),
                new FormAnswerValue.RatingAnswer(9));

        var tally = only(question, answers, null);

        assertEquals(List.of(0, 0, 0, 1, 2), tally.ratingCounts(), "no scale means five, and 9 is off it");
    }

    @Test
    void aRankingScoresFirstPlaceHighest() {
        var question =
                question(3, FormQuestionType.RANKING, new FormQuestionConfig.Ranking(Option.numbered("A", "B", "C")));
        var answers = answers(
                3,
                new FormAnswerValue.RankingAnswer(List.of("o2", "o0", "o1")),
                new FormAnswerValue.RankingAnswer(List.of("o0", "o2", "o1")));

        var tally = only(question, answers, null);

        assertEquals(Map.of("o0", 5, "o1", 2, "o2", 5), tally.rankingScores());
    }

    @Test
    void aLikertGridAveragesEachStatementToOneDecimal() {
        var question = question(
                4,
                FormQuestionType.LIKERT,
                new FormQuestionConfig.Likert(Option.numbered("Essen", "Programm", "Unterkunft"), 1, 5, null));
        var answers = answers(
                4,
                new FormAnswerValue.LikertAnswer(Map.of("o0", 4, "o1", 5)),
                new FormAnswerValue.LikertAnswer(Map.of("o0", 5, "o1", 2, "gone", 1)),
                new FormAnswerValue.LikertAnswer(Map.of("o0", 5)));

        var tally = only(question, answers, null);

        assertEquals(
                List.of("o0", "o1", "o2"), List.copyOf(tally.statementAverages().keySet()));
        assertEquals(4.7, tally.statementAverages().get("o0"));
        assertEquals(3.5, tally.statementAverages().get("o1"));
        assertNull(tally.statementAverages().get("o2"), "a statement nobody rated has no average rather than zero");
    }

    @Test
    void writtenAndDateAnswersAreListedAsGiven() {
        var text = question(5, FormQuestionType.TEXT, new FormQuestionConfig.Text(false));
        var date = question(6, FormQuestionType.DATE, new FormQuestionConfig.Date());
        var answers = new ArrayList<FormAnswer>();
        answers.addAll(answers(5, new FormAnswerValue.TextAnswer("Mehr Zeit"), new FormAnswerValue.TextAnswer("  ")));
        answers.addAll(answers(6, new FormAnswerValue.DateAnswer("2026-07-01")));

        var tallies = FormResultTally.tally(List.of(text, date), answers, null);

        assertEquals(List.of("Mehr Zeit"), tallies.get(0).values(), "a blank answer lists nothing");
        assertEquals(2, tallies.get(0).answerCount());
        assertEquals(List.of("2026-07-01"), tallies.get(1).values());
    }

    @Test
    void onlyTheChosenResponsesAreCounted() {
        var question = question(7, FormQuestionType.RATING, new FormQuestionConfig.Rating(3, null));
        var answers = answers(
                7,
                new FormAnswerValue.RatingAnswer(1),
                new FormAnswerValue.RatingAnswer(3),
                new FormAnswerValue.RatingAnswer(3));
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
        answers.addAll(answers(8, new FormAnswerValue.RatingAnswer(2)));

        assertEquals(1, only(question, answers, null).answerCount());
    }

    @Test
    void countsHowManyOfTheChosenResponsesReachedTheQuestionsPage() {
        var question = new FormQuestion(
                9,
                1,
                9,
                "later",
                FormQuestionType.TEXT,
                "Frage 9",
                "",
                false,
                false,
                new FormQuestionConfig.Text(false),
                null);
        var responses =
                List.of(response(1, List.of("p0", "later")), response(2, List.of("p0")), response(3, List.of()));

        var all = FormResultTally.tally(List.of(question), List.of(), responses, null)
                .getFirst();
        var some = FormResultTally.tally(List.of(question), List.of(), responses, Set.of(2))
                .getFirst();

        assertEquals(2, all.reachedCount(), "a response written without a walk saw every page");
        assertEquals(0, some.reachedCount());
        assertNull(FormResultTally.tally(List.of(question), List.of(), null)
                .getFirst()
                .reachedCount());
    }

    /** A response from before the form was split holds only the first page, and still reached what it answered. */
    @Test
    void aResponseThatAnsweredTheQuestionReachedIt() {
        var question = new FormQuestion(
                10,
                1,
                10,
                "later",
                FormQuestionType.TEXT,
                "Frage 10",
                "",
                false,
                false,
                new FormQuestionConfig.Text(false),
                null);
        var responses = List.of(response(1, List.of("p0")), response(2, List.of("p0")));
        var answers = List.of(new FormAnswer(nextAnswerId++, 1, 10, new FormAnswerValue.TextAnswer("früher").toJson()));

        var tally = FormResultTally.tally(List.of(question), answers, responses, null)
                .getFirst();

        assertEquals(1, tally.reachedCount());
    }

    private static FormResponse response(int id, List<String> path) {
        return new FormResponse(id, 1, id, id, null, null, null, null, null, path);
    }

    private static FormResultTally.QuestionTally only(
            FormQuestion question, List<FormAnswer> answers, Set<Integer> responses) {
        return FormResultTally.tally(List.of(question), answers, responses).getFirst();
    }

    private static FormQuestion question(int id, FormQuestionType type, FormQuestionConfig config) {
        return new FormQuestion(id, 1, id, "p0", type, "Frage " + id, "", false, false, config, null);
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
