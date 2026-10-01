/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.feature.form.entity.FormAnswer;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormResponse;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a set of responses answered to each question, counted.
 *
 * <p>Results are counted here rather than in the browser so they can be counted per group of
 * respondents without handing anybody the member data behind the groups. Called once for all
 * responses, it gives the plain results view; called once per group, the grouped one.
 *
 * <p>The numbers are the ones the charts always drew: votes per option, a histogram of ratings,
 * a score per ranked option where first place is worth the most, and the average per Likert
 * statement. Written and date answers are listed as they are.
 *
 * <p>Options and statements are counted by their key, in the order the question lists them now. An
 * answer naming a key the question no longer has is not counted for it.
 */
public final class FormResultTally {
    private static final int DEFAULT_RATING_SCALE = 5;

    private FormResultTally() {}

    /**
     * The counts of one question within one set of responses.
     *
     * <p>Only the fields that belong to the question's kind are set: option counts by option key and
     * the number of "other" answers for a choice, rating counts from one star upward for a rating, a
     * score per option key for a ranking, an average per statement key for a Likert grid (left out
     * where nobody rated that statement), and the answers themselves for text and date questions.
     *
     * <p>{@code reachedCount} is how many of the responses went through the question's page at all.
     * Without it a question behind a branch looks like one most people skipped: "not shown" is not
     * "not answered".
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FormQuestionTally(
            int questionId,
            int answerCount,
            @Nullable Integer reachedCount,
            @Nullable Map<String, Integer> optionCounts,
            @Nullable Integer otherCount,
            @Nullable List<Integer> ratingCounts,
            @Nullable Map<String, Integer> rankingScores,
            @Nullable Map<String, Double> statementAverages,
            @Nullable List<String> values) {}

    /**
     * Counts every question over the answers of the given responses.
     *
     * @param questions   the form's questions, in their order
     * @param answers     every answer of the form
     * @param responseIds the responses to count, or {@code null} for all of them
     * @return one tally per question, in the order of the questions
     */
    public static List<FormQuestionTally> tally(
            List<FormQuestion> questions, Collection<FormAnswer> answers, Set<Integer> responseIds) {
        return tally(questions, answers, null, responseIds);
    }

    /**
     * Counts every question over the answers of the given responses, and how many of them reached it.
     *
     * @param questions   the form's questions, in their order
     * @param answers     every answer of the form
     * @param responses   every response of the form, which say which pages they went through; {@code
     *                    null} where that is not asked for
     * @param responseIds the responses to count, or {@code null} for all of them
     * @return one tally per question, in the order of the questions
     */
    public static List<FormQuestionTally> tally(
            List<FormQuestion> questions,
            Collection<FormAnswer> answers,
            @Nullable Collection<FormResponse> responses,
            Set<Integer> responseIds) {
        return questions.stream()
                .map(question -> tally(
                        question,
                        reached(question, responses, answers, responseIds),
                        answers.stream()
                                .filter(answer -> answer.questionId() == question.id())
                                .filter(answer -> responseIds == null || responseIds.contains(answer.responseId()))
                                .map(answer -> FormAnswerValue.parse(question.formQuestionType(), answer.value()))
                                .filter(Objects::nonNull)
                                .toList()))
                .toList();
    }

    /**
     * How many of the responses reached the question: those whose path holds its page, and those that
     * answered it. A response given before the form had pages holds only the first page as its path,
     * and an answer to a question further down still says it was shown.
     */
    private static Integer reached(
            FormQuestion question,
            Collection<FormResponse> responses,
            Collection<FormAnswer> answers,
            Set<Integer> responseIds) {
        if (responses == null) return null;
        var answered = answers.stream()
                .filter(answer -> answer.questionId() == question.id())
                .map(FormAnswer::responseId)
                .collect(Collectors.toSet());
        return (int) responses.stream()
                .filter(response -> responseIds == null || responseIds.contains(response.id()))
                .filter(response -> response.reached(question.pageKey()) || answered.contains(response.id()))
                .count();
    }

    private static FormQuestionTally tally(FormQuestion question, Integer reached, List<FormAnswerValue> values) {
        var tally =
                switch (question.config()) {
                    case FormQuestionConfig.Choice choice -> choices(question.id(), choice, values);
                    case FormQuestionConfig.Rating rating -> ratings(question.id(), rating, values);
                    case FormQuestionConfig.Ranking ranking -> rankings(question.id(), ranking, values);
                    case FormQuestionConfig.Likert likert -> likert(question.id(), likert, values);
                    case null, default -> listed(question.id(), values);
                };
        return new FormQuestionTally(
                tally.questionId(),
                tally.answerCount(),
                reached,
                tally.optionCounts(),
                tally.otherCount(),
                tally.ratingCounts(),
                tally.rankingScores(),
                tally.statementAverages(),
                tally.values());
    }

    private static FormQuestionTally choices(int id, FormQuestionConfig.Choice config, List<FormAnswerValue> values) {
        var counts = zeroPerOption(config);
        int other = 0;
        for (var value : values) {
            if (!(value instanceof FormAnswerValue.ChoiceAnswer(List<String> selected, String otherText))) continue;
            if (selected != null) {
                for (var key : selected) counts.computeIfPresent(key, (k, count) -> count + 1);
            }
            if (otherText != null && !otherText.isBlank()) other++;
        }
        return new FormQuestionTally(id, values.size(), null, counts, other, null, null, null, null);
    }

    private static FormQuestionTally ratings(int id, FormQuestionConfig.Rating config, List<FormAnswerValue> values) {
        Integer configured = config.scale();
        int scale = configured != null && configured > 0 ? configured : DEFAULT_RATING_SCALE;
        int[] counts = new int[scale];
        for (var value : values) {
            if (value instanceof FormAnswerValue.RatingAnswer(int rating) && rating >= 1 && rating <= scale) {
                counts[rating - 1]++;
            }
        }
        return new FormQuestionTally(
                id,
                values.size(),
                null,
                null,
                null,
                Arrays.stream(counts).boxed().toList(),
                null,
                null,
                null);
    }

    private static FormQuestionTally rankings(int id, FormQuestionConfig.Ranking config, List<FormAnswerValue> values) {
        var scores = zeroPerOption(config);
        for (var value : values) {
            if (!(value instanceof FormAnswerValue.RankingAnswer(List<String> order)) || order == null) continue;
            for (int rank = 0; rank < order.size(); rank++) {
                int points = order.size() - rank;
                scores.computeIfPresent(order.get(rank), (k, score) -> score + points);
            }
        }
        return new FormQuestionTally(id, values.size(), null, null, null, null, scores, null, null);
    }

    private static FormQuestionTally likert(int id, FormQuestionConfig.Likert config, List<FormAnswerValue> values) {
        var sums = new LinkedHashMap<String, Double>();
        var counts = zeroPerOption(config);
        for (var value : values) {
            if (!(value instanceof FormAnswerValue.LikertAnswer(var ratings)) || ratings == null) continue;
            ratings.forEach((key, rating) -> {
                if (rating == null || !counts.containsKey(key)) return;
                sums.merge(key, rating.doubleValue(), Double::sum);
                counts.merge(key, 1, Integer::sum);
            });
        }
        var averages = new LinkedHashMap<String, Double>();
        counts.forEach((key, count) -> {
            if (count > 0) averages.put(key, Math.round(sums.get(key) / count * 10) / 10.0);
        });
        return new FormQuestionTally(id, values.size(), null, null, null, null, null, averages, null);
    }

    private static FormQuestionTally listed(int id, List<FormAnswerValue> values) {
        var listed = values.stream()
                .map(value -> switch (value) {
                    case FormAnswerValue.TextAnswer(String text) -> text;
                    case FormAnswerValue.DateAnswer(String date) -> date;
                    default -> null;
                })
                .filter(text -> text != null && !text.isBlank())
                .toList();
        return new FormQuestionTally(id, values.size(), null, null, null, null, null, null, listed);
    }

    private static Map<String, Integer> zeroPerOption(FormQuestionConfig config) {
        var counts = new LinkedHashMap<String, Integer>();
        for (var key : config.optionKeys()) counts.put(key, 0);
        return counts;
    }
}
