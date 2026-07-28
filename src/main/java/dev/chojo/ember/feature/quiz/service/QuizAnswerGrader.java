/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.util.Json;
import jakarta.inject.Singleton;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Scores a submitted answer against the solution stored in its question's config. Question
 * types that have no machine-checkable solution, and answers that do not fit the shape
 * their type prescribes, are handed back for manual grading.
 */
@Singleton
public class QuizAnswerGrader {

    /**
     * Scores one answer.
     *
     * @param question   the answered question
     * @param answerJson the raw submitted payload
     * @return the awarded points, or a negative value when the answer needs manual grading
     */
    public double autoGrade(QuizQuestion question, String answerJson) {
        if (answerJson == null || answerJson.isBlank()) return 0;
        try {
            var config = question.configNode();
            var answer = Json.MAPPER.readTree(answerJson);
            return switch (question.quizQuestionType()) {
                case MULTIPLE_CHOICE -> gradeMultipleChoice(config, answer, question.points());
                case TRUE_FALSE -> gradeTrueFalse(config, answer, question.points());
                case CONNECT -> gradeConnect(config, answer, question.points());
                case ORDERING -> gradeOrdering(config, answer, question.points());
                case FILL_IN_THE_BLANK -> gradeFillBlank(config, answer, question.points());
                case ENUMERATION -> gradeEnumeration(config, answer, question.points());
                case FREE_ANSWER, IMAGE_TEXT -> -1;
            };
        } catch (Exception e) {
            return -1;
        }
    }

    private double gradeMultipleChoice(JsonNode config, JsonNode answer, double maxPoints) {
        var options = config.get("options");
        var selected = answer.get("selected");
        if (options == null || selected == null || !selected.isArray()) return 0;

        var correctSet = new HashSet<Integer>();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).has("correct") && options.get(i).get("correct").asBoolean()) {
                correctSet.add(i);
            }
        }

        double pointsPerCorrect =
                config.has("pointsPerCorrect") ? config.get("pointsPerCorrect").asDouble() : 1;
        int correct = 0;
        int wrong = 0;
        for (var s : selected) {
            if (correctSet.contains(s.asInt())) correct++;
            else wrong++;
        }

        double points = (correct * pointsPerCorrect) - (wrong * pointsPerCorrect);
        return Math.clamp(points, 0, maxPoints);
    }

    private double gradeTrueFalse(JsonNode config, JsonNode answer, double maxPoints) {
        if (!config.has("correctAnswer") || !answer.has("value")) return 0;
        boolean correct = config.get("correctAnswer").asBoolean();
        boolean given = answer.get("value").asBoolean();
        return correct == given ? maxPoints : 0;
    }

    private double gradeConnect(JsonNode config, JsonNode answer, double maxPoints) {
        var pairs = config.get("pairs");
        var givenPairs = answer.get("pairs");
        if (pairs == null || givenPairs == null) return 0;

        double ppc =
                config.has("pointsPerCorrect") ? config.get("pointsPerCorrect").asDouble() : 0;

        int correct = 0;
        for (int i = 0; i < pairs.size(); i++) {
            String expectedRight = pairs.get(i).get("right").asString();
            var given = givenPairs.get(String.valueOf(i));
            if (given != null && given.asString().equals(expectedRight)) {
                correct++;
            }
        }

        if (ppc > 0) return Math.min(correct * ppc, maxPoints);
        int totalPairs = pairs.size();
        return totalPairs == 0 ? 0 : (double) correct / totalPairs * maxPoints;
    }

    private double gradeOrdering(JsonNode config, JsonNode answer, double maxPoints) {
        var items = config.get("items");
        var order = answer.get("order");
        if (items == null || order == null || !order.isArray()) return 0;

        double ppc =
                config.has("pointsPerCorrect") ? config.get("pointsPerCorrect").asDouble() : 0;

        int correct = 0;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).asInt() == i) correct++;
        }

        if (ppc > 0) return Math.min(correct * ppc, maxPoints);
        return correct == items.size() ? maxPoints : 0;
    }

    /**
     * Scores a fill-in-the-blank answer. Answers arrive either as a map of gap index to
     * text, or as a single text for the one-gap questions that predate the map form.
     */
    private double gradeFillBlank(JsonNode config, JsonNode answer, double maxPoints) {
        var correctAnswers = config.get("answers");
        if (correctAnswers == null || !correctAnswers.isArray()) return -1;
        if (correctAnswers.isEmpty()) return -1;

        double ppc =
                config.has("pointsPerCorrect") ? config.get("pointsPerCorrect").asDouble() : 0;

        var gaps = answer.get("gaps");
        if (gaps != null && gaps.isObject()) {
            int correct = 0;
            for (int i = 0; i < correctAnswers.size(); i++) {
                var given = gaps.get(String.valueOf(i));
                if (given != null
                        && given.asString()
                                .trim()
                                .equalsIgnoreCase(
                                        correctAnswers.get(i).asString().trim())) {
                    correct++;
                }
            }
            if (ppc > 0) return Math.min(correct * ppc, maxPoints);
            int total = correctAnswers.size();
            return total == 0 ? 0 : (double) correct / total * maxPoints;
        }

        String given = answer.has("text") ? answer.get("text").asString().trim() : "";
        if (given.isEmpty()) return 0;
        for (var a : correctAnswers) {
            if (a.asString().trim().equalsIgnoreCase(given)) {
                return ppc > 0 ? ppc : maxPoints;
            }
        }
        return 0;
    }

    private double gradeEnumeration(JsonNode config, JsonNode answer, double maxPoints) {
        var correctAnswers = config.get("answers");
        if (correctAnswers == null || !correctAnswers.isArray()) return -1;
        int requiredCount =
                config.has("requiredCount") ? config.get("requiredCount").asInt() : correctAnswers.size();
        boolean ordered =
                config.has("orderedRequired") && config.get("orderedRequired").asBoolean();

        var items = answer.get("items");
        if (items == null || !items.isArray()) return 0;

        var correctSet = new ArrayList<String>();
        for (var a : correctAnswers) correctSet.add(a.asString().trim().toLowerCase());

        int correct = 0;
        for (int i = 0; i < Math.min(items.size(), requiredCount); i++) {
            String given = items.get(i).asString().trim().toLowerCase();
            if (ordered) {
                if (i < correctSet.size() && correctSet.get(i).equals(given)) {
                    correct++;
                }
            } else if (correctSet.contains(given)) {
                correct++;
            }
        }
        return requiredCount == 0 ? 0 : (double) correct / requiredCount * maxPoints;
    }
}
