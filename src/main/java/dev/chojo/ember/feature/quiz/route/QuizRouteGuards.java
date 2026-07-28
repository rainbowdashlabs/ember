/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTest;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttempt;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
import dev.chojo.ember.feature.quiz.service.QuizService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * Ownership and state preconditions shared by the quiz route classes. Keeps the
 * blanket-404 policy for cross-station lookups in one place instead of repeating it in
 * every handler.
 */
@Singleton
public class QuizRouteGuards {

    private final QuizService quizService;

    @Inject
    public QuizRouteGuards(QuizService quizService) {
        this.quizService = quizService;
    }

    /**
     * Loads a quiz catalog and asserts it belongs to the caller's station, returning it. Answers
     * 404 when absent or owned by another station.
     */
    public QuizCatalog requireOwnedCatalog(Context ctx, int catalogId) {
        return requireOwnedOrNotFound(ctx, catalogId, quizService::findCatalog, QuizCatalog::stationId);
    }

    /**
     * Loads a quiz category and asserts it belongs to the caller's station, returning it. Answers
     * 404 when absent or owned by another station.
     */
    public QuizCategory requireOwnedCategory(Context ctx, int categoryId) {
        return requireOwnedOrNotFound(ctx, categoryId, quizService::findCategory, QuizCategory::stationId);
    }

    /**
     * Loads a question and asserts its catalog belongs to the caller's station, returning it.
     */
    public QuizQuestion requireOwnedQuestion(Context ctx, int questionId) {
        var question = quizService.findQuestion(questionId).orElseThrow(NotFoundResponse::new);
        requireOwnedCatalog(ctx, question.catalogId());
        return question;
    }

    /**
     * Loads a test and asserts it belongs to the caller's station, returning it. Answers 404
     * when absent or owned by another station.
     */
    public QuizTest requireOwnedTest(Context ctx, int testId) {
        return requireOwnedOrNotFound(ctx, testId, quizService::findTest, QuizTest::stationId);
    }

    /**
     * Loads an attempt and asserts its test belongs to the caller's station, returning it.
     */
    public QuizTestAttempt requireOwnedAttempt(Context ctx, int attemptId) {
        var attempt = quizService.findAttemptById(attemptId).orElseThrow(NotFoundResponse::new);
        requireOwnedTest(ctx, attempt.testId());
        return attempt;
    }

    /**
     * Loads the test named by the {@code id} path parameter, asserts it belongs to the caller's
     * station and is not active, returning it. Answers 404 when absent or owned by another
     * station and 400 when the test is active.
     */
    public QuizTest requireModifiableTest(Context ctx) {
        var test = requireOwnedTest(ctx, pathInt(ctx, "id"));
        if (test.status() == TestStatus.ACTIVE) throw new BadRequestResponse("Cannot modify active test");
        return test;
    }

    /**
     * Loads the attempt named by the {@code id} path parameter and asserts it belongs to the
     * calling member, returning it. Answers 400 when the caller is not a station member, 404 when
     * absent and 403 when the attempt belongs to another member.
     */
    public QuizTestAttempt requireMemberAttempt(Context ctx, UserSession session) {
        int attemptId = pathInt(ctx, "id");
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        var attempt = quizService.findAttemptById(attemptId).orElseThrow(NotFoundResponse::new);
        if (attempt.memberId() != session.member().id()) throw new ForbiddenResponse();
        return attempt;
    }
}
