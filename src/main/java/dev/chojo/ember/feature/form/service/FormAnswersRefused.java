/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import io.javalin.openapi.OpenApiName;

import java.util.List;

/**
 * Answers to a form refused for what they said, with one refusal per question it was about.
 *
 * <p>A form that could not be sent used to say so in one joined English sentence naming the
 * questions by their titles, attached to nothing. Each problem now names its question and the page
 * it stands on, with a code of its own, so the fill screen can open that page and mark the question.
 */
public class FormAnswersRefused extends RefusalResponse {
    private final transient List<Problem> problems;

    /**
     * Refuses answers for the given problems.
     *
     * @param refusal  what refused the answers as a whole
     * @param problems what was wrong, one entry per question
     */
    public FormAnswersRefused(Refusal refusal, List<Problem> problems) {
        super(refusal, refusal.message());
        this.problems = List.copyOf(problems);
    }

    /**
     * The same problems, refused under the given refusal. Every place answers are taken from says it
     * in its own code, while what was wrong with them is found in one place.
     *
     * @param refusal what refuses the answers as a whole where they were sent
     * @return the refusal to throw
     */
    public FormAnswersRefused as(Refusal refusal) {
        return new FormAnswersRefused(refusal, problems);
    }

    /**
     * What was wrong, one entry per question.
     *
     * @return the problems
     */
    public List<Problem> problems() {
        return problems;
    }

    @Override
    public Object body() {
        var refusal = refusal();
        return new Body(refusal.status().getMessage(), getMessage(), refusal.code(), problems);
    }

    /**
     * One question an answer was refused at.
     *
     * @param questionId the question
     * @param pageKey    the page it stands on, or {@code null} for a question the form does not have
     * @param code       the code of what was wrong
     * @param message    what was wrong, in one sentence
     */
    @OpenApiName("FormAnswerProblem")
    public record Problem(int questionId, String pageKey, String code, String message) {
        /**
         * A problem named by a refusal.
         *
         * @param questionId the question
         * @param pageKey    the page it stands on
         * @param refusal    what was wrong
         * @return the problem
         */
        public static Problem of(int questionId, String pageKey, Refusal refusal) {
            return new Problem(questionId, pageKey, refusal.code(), refusal.message());
        }
    }

    /**
     * The error body of refused answers: the usual error, and the problems one by one.
     *
     * @param error    the error category
     * @param message  what was refused as a whole
     * @param code     the code of the refusal as a whole
     * @param problems what was wrong, one entry per question
     */
    @OpenApiName("FormAnswersRefusedBody")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String error, String message, String code, List<Problem> problems) {}
}
