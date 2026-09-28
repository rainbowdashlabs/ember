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

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
     * score per option key for a ranking, an average per statement key for a Likert grid (null where
     * nobody rated that statement), and the answers themselves for text and date questions.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionTally(
            int questionId,
            int answerCount,
            Map<String, Integer> optionCounts,
            Integer otherCount,
            List<Integer> ratingCounts,
            Map<String, Integer> rankingScores,
            Map<String, Double> statementAverages,
            List<String> values) {}

    /**
     * Counts every question over the answers of the given responses.
     *
     * @param questions   the form's questions, in their order
     * @param answers     every answer of the form
     * @param responseIds the responses to count, or {@code null} for all of them
     * @return one tally per question, in the order of the questions
     */
    public static List<QuestionTally> tally(
            List<FormQuestion> questions, Collection<FormAnswer> answers, Set<Integer> responseIds) {
        return questions.stream()
                .map(question -> tally(
                        question,
                        answers.stream()
                                .filter(answer -> answer.questionId() == question.id())
                                .filter(answer -> responseIds == null || responseIds.contains(answer.responseId()))
                                .map(answer -> FormAnswerValue.parse(question.formQuestionType(), answer.value()))
                                .filter(Objects::nonNull)
                                .toList()))
                .toList();
    }

    private static QuestionTally tally(FormQuestion question, List<FormAnswerValue> values) {
        int id = question.id();
        return switch (question.config()) {
            case FormQuestionConfig.Choice choice -> choices(id, choice, values);
            case FormQuestionConfig.Rating rating -> ratings(id, rating, values);
            case FormQuestionConfig.Ranking ranking -> rankings(id, ranking, values);
            case FormQuestionConfig.Likert likert -> likert(id, likert, values);
            case null, default -> listed(id, values);
        };
    }

    private static QuestionTally choices(int id, FormQuestionConfig.Choice config, List<FormAnswerValue> values) {
        var counts = zeroPerOption(config);
        int other = 0;
        for (var value : values) {
            if (!(value instanceof FormAnswerValue.Choice(List<String> selected, String otherText))) continue;
            if (selected != null) {
                for (var key : selected) counts.computeIfPresent(key, (k, count) -> count + 1);
            }
            if (otherText != null && !otherText.isBlank()) other++;
        }
        return new QuestionTally(id, values.size(), counts, other, null, null, null, null);
    }

    private static QuestionTally ratings(int id, FormQuestionConfig.Rating config, List<FormAnswerValue> values) {
        int scale = config.scale() != null && config.scale() > 0 ? config.scale() : DEFAULT_RATING_SCALE;
        int[] counts = new int[scale];
        for (var value : values) {
            if (value instanceof FormAnswerValue.Rating(int rating) && rating >= 1 && rating <= scale) {
                counts[rating - 1]++;
            }
        }
        return new QuestionTally(
                id, values.size(), null, null, Arrays.stream(counts).boxed().toList(), null, null, null);
    }

    private static QuestionTally rankings(int id, FormQuestionConfig.Ranking config, List<FormAnswerValue> values) {
        var scores = zeroPerOption(config);
        for (var value : values) {
            if (!(value instanceof FormAnswerValue.Ranking(List<String> order)) || order == null) continue;
            for (int rank = 0; rank < order.size(); rank++) {
                int points = order.size() - rank;
                scores.computeIfPresent(order.get(rank), (k, score) -> score + points);
            }
        }
        return new QuestionTally(id, values.size(), null, null, null, scores, null, null);
    }

    private static QuestionTally likert(int id, FormQuestionConfig.Likert config, List<FormAnswerValue> values) {
        var sums = new LinkedHashMap<String, Double>();
        var counts = zeroPerOption(config);
        for (var value : values) {
            if (!(value instanceof FormAnswerValue.Likert(var ratings)) || ratings == null) continue;
            ratings.forEach((key, rating) -> {
                if (rating == null || !counts.containsKey(key)) return;
                sums.merge(key, rating.doubleValue(), Double::sum);
                counts.merge(key, 1, Integer::sum);
            });
        }
        var averages = new LinkedHashMap<String, Double>();
        counts.forEach(
                (key, count) -> averages.put(key, count == 0 ? null : Math.round(sums.get(key) / count * 10) / 10.0));
        return new QuestionTally(id, values.size(), null, null, null, null, averages, null);
    }

    private static QuestionTally listed(int id, List<FormAnswerValue> values) {
        var listed = values.stream()
                .map(value -> switch (value) {
                    case FormAnswerValue.Text(String text) -> text;
                    case FormAnswerValue.DateValue(String date) -> date;
                    default -> null;
                })
                .filter(text -> text != null && !text.isBlank())
                .toList();
        return new QuestionTally(id, values.size(), null, null, null, null, null, listed);
    }

    private static Map<String, Integer> zeroPerOption(FormQuestionConfig config) {
        var counts = new LinkedHashMap<String, Integer>();
        for (var key : config.optionKeys()) counts.put(key, 0);
        return counts;
    }
}
