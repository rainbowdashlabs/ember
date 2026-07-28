/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.quiz.entity.AttemptStatus;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTestAnswer;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttempt;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttemptQuestion;
import dev.chojo.ember.feature.quiz.service.QuizAttemptService;
import dev.chojo.ember.feature.quiz.service.QuizQuestionService;
import dev.chojo.ember.feature.quiz.service.QuizTestAccessService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Endpoints around taking and reviewing a test: starting an attempt, saving and
 * submitting answers, and the reviewer-facing attempt listing and grading.
 */
@Singleton
public class QuizAttemptRoutes implements Routes {

    private final QuizAttemptService attemptService;
    private final QuizTestAccessService accessService;
    private final QuizQuestionService questionService;
    private final QuizRouteGuards guards;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public QuizAttemptRoutes(
            QuizAttemptService attemptService,
            QuizTestAccessService accessService,
            QuizQuestionService questionService,
            QuizRouteGuards guards,
            MemberIdentityFactory memberIdentityFactory) {
        this.attemptService = attemptService;
        this.accessService = accessService;
        this.questionService = questionService;
        this.guards = guards;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/quiz/tests/{id}/start", this::startAttempt, StationPermission.USER);
        routes.get(prefix + "/quiz/tests/{id}/my-attempt", this::getMyAttempt, StationPermission.USER);
        routes.post(prefix + "/quiz/attempts/{id}/answer", this::saveAnswer, StationPermission.USER);
        routes.post(prefix + "/quiz/attempts/{id}/submit", this::submitAttempt, StationPermission.USER);

        routes.get(prefix + "/quiz/tests/{id}/attempts", this::listAttempts, StationPermission.TEST_RESULT_READ);
        routes.get(prefix + "/quiz/attempts/{id}", this::getAttemptDetail, StationPermission.TEST_RESULT_READ);
        routes.post(prefix + "/quiz/answers/{id}/grade", this::gradeAnswer, StationPermission.TEST_REVIEW);
        routes.post(prefix + "/quiz/attempts/{id}/grade", this::gradeAttempt, StationPermission.TEST_REVIEW);
    }

    private void startAttempt(Context ctx) {
        int testId = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        var test = guards.requireOwnedTest(ctx, testId);
        int memberId = session.member().id();
        if (!accessService.isTestAccessible(test, memberId, session.permissions())) {
            throw new ForbiddenResponse("Test is not currently accessible");
        }
        var existing = attemptService.findAttempt(testId, session.member().id());
        if (existing.isPresent()) {
            ctx.json(new AttemptDetail(
                    existing.get(),
                    attemptService.findAttemptQuestions(existing.get().id()),
                    attemptService.findAnswers(existing.get().id()),
                    null,
                    null));
            return;
        }
        var attempt = attemptService.startAttempt(testId, session.member().id());
        ctx.status(HttpStatus.CREATED)
                .json(new AttemptDetail(
                        attempt, attemptService.findAttemptQuestions(attempt.id()), List.of(), null, null));
    }

    private void getMyAttempt(Context ctx) {
        int testId = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        guards.requireOwnedTest(ctx, testId);
        var attempt = attemptService.findAttempt(testId, session.member().id());
        if (attempt.isEmpty()) {
            ctx.json(new EmptyAttemptResponse());
            return;
        }
        ctx.json(new AttemptDetail(
                attempt.get(),
                attemptService.findAttemptQuestions(attempt.get().id()),
                attemptService.findAnswers(attempt.get().id()),
                null,
                null));
    }

    private void saveAnswer(Context ctx) {
        var session = UserSession.from(ctx);
        var attempt = guards.requireMemberAttempt(ctx, session);
        if (attempt.status() != AttemptStatus.IN_PROGRESS) {
            throw new BadRequestResponse("Attempt already submitted");
        }
        var req = ctx.bodyAsClass(AnswerRequest.class);
        attemptService.saveAnswer(attempt.id(), req.questionId(), req.answer());
        ctx.json(new QuizSuccessResponse(true));
    }

    private void submitAttempt(Context ctx) {
        var session = UserSession.from(ctx);
        var attempt = guards.requireMemberAttempt(ctx, session);
        attemptService.submitAttempt(attempt.id());
        attemptService.findAttemptById(attempt.id()).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void listAttempts(Context ctx) {
        int testId = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, testId);
        ctx.json(attemptService.findAttempts(testId));
    }

    private void getAttemptDetail(Context ctx) {
        int attemptId = pathInt(ctx, "id");
        var attempt = guards.requireOwnedAttempt(ctx, attemptId);
        var attemptQuestions = attemptService.findAttemptQuestions(attemptId);
        var answers = attemptService.findAnswers(attemptId);
        var questionIds = attemptQuestions.stream()
                .map(QuizTestAttemptQuestion::questionId)
                .distinct()
                .toList();
        var questions = questionService.findQuestionsByIds(questionIds);
        MemberIdentity memberIdentity = null;
        try {
            memberIdentity = memberIdentityFactory.fromMemberId(attempt.memberId());
        } catch (Exception ignored) {
        }
        ctx.json(new AttemptDetail(attempt, attemptQuestions, answers, questions, memberIdentity));
    }

    private void gradeAnswer(Context ctx) {
        int answerId = pathInt(ctx, "id");
        var answer = attemptService.findAnswerById(answerId).orElseThrow(NotFoundResponse::new);
        guards.requireOwnedAttempt(ctx, answer.attemptId());
        var req = ctx.bodyAsClass(GradeRequest.class);
        if (req.points() == null) throw new BadRequestResponse("points is required");
        attemptService.gradeAnswer(answerId, req.points());
        ctx.json(new QuizSuccessResponse(true));
    }

    private void gradeAttempt(Context ctx) {
        int attemptId = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        guards.requireOwnedAttempt(ctx, attemptId);
        attemptService.gradeAttempt(attemptId, session.member().id());
        attemptService.findAttemptById(attemptId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    public record AnswerRequest(int questionId, String answer) {}

    public record GradeRequest(Double points) {}

    public record AttemptDetail(
            QuizTestAttempt attempt,
            List<QuizTestAttemptQuestion> questions,
            List<QuizTestAnswer> answers,
            List<QuizQuestion> questionDetails,
            MemberIdentity memberIdentity) {}

    private record EmptyAttemptResponse() {}
}
