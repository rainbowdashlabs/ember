/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.CreateQuestionCommand;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttempt;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttemptQuestion;
import dev.chojo.ember.feature.system.service.RequirementsService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The quiz feature as the rest of the application sees it. Bundles the handful of
 * operations other features need into one entry point so they do not have to know how the
 * feature is cut up internally; code inside the quiz feature talks to the individual
 * services instead.
 */
@Singleton
public class QuizService {

    private final QuizCatalogService catalogService;
    private final QuizQuestionService questionService;
    private final QuizTestService testService;
    private final QuizAttemptService attemptService;

    @Inject
    public QuizService(
            QuizCatalogService catalogService,
            QuizQuestionService questionService,
            QuizTestService testService,
            QuizAttemptService attemptService) {
        this.catalogService = catalogService;
        this.questionService = questionService;
        this.testService = testService;
        this.attemptService = attemptService;
    }

    public QuizCatalog createCatalog(int stationId, String name, String description, boolean trainingEnabled) {
        return catalogService.createCatalog(stationId, name, description, trainingEnabled);
    }

    public QuizCategory createCategory(int stationId, String name, String description, int position) {
        return catalogService.createCategory(stationId, name, description, position);
    }

    public QuizQuestion createQuestion(CreateQuestionCommand command) {
        return questionService.createQuestion(command);
    }

    public Optional<QuizQuestion> findQuestion(int id) {
        return questionService.findQuestion(id);
    }

    public boolean updateTest(
            int id,
            String title,
            String description,
            Integer timeLimit,
            boolean shuffle,
            boolean forced,
            Instant startAt,
            Instant endAt) {
        return testService.updateTest(id, title, description, timeLimit, shuffle, forced, startAt, endAt);
    }

    public boolean activateTest(int id) {
        return testService.activateTest(id);
    }

    public List<RequirementsService.RequirementItem> findForcedPending(int stationId, int memberId) {
        return testService.findForcedPending(stationId, memberId);
    }

    public QuizTestAttempt startAttempt(int testId, int memberId) {
        return attemptService.startAttempt(testId, memberId);
    }

    public List<QuizTestAttemptQuestion> findAttemptQuestions(int attemptId) {
        return attemptService.findAttemptQuestions(attemptId);
    }

    public void saveAnswer(int attemptId, int questionId, String answer) {
        attemptService.saveAnswer(attemptId, questionId, answer);
    }

    public boolean submitAttempt(int attemptId) {
        return attemptService.submitAttempt(attemptId);
    }
}
