/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Refusal;
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
import dev.chojo.ember.feature.quiz.service.QuizRouteGuards;
import dev.chojo.ember.feature.quiz.service.QuizTestAccessService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

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

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/start",
            methods = HttpMethod.POST,
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizAttemptDetail.class)),
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = QuizAttemptDetail.class))
            })
    private void startAttempt(Context ctx) {
        int testId = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        if (session.member() == null) throw Refusal.QUIZ_ATTEMPT_NEEDS_MEMBERSHIP_TO_START.raise();
        var test = guards.requireOwnedTest(ctx, testId);
        int memberId = session.member().id();
        if (!accessService.isTestAccessible(test, memberId, session.permissions())) {
            throw Refusal.QUIZ_TEST_NOT_OPEN_TO_YOU.raise();
        }
        var existing = attemptService.findAttempt(testId, session.member().id());
        if (existing.isPresent()) {
            ctx.json(new QuizAttemptDetail(
                    existing.get(),
                    attemptService.findAttemptQuestions(existing.get().id()),
                    attemptService.findAnswers(existing.get().id()),
                    null,
                    null));
            return;
        }
        var attempt = attemptService.startAttempt(testId, session.member().id());
        ctx.status(HttpStatus.CREATED)
                .json(new QuizAttemptDetail(
                        attempt, attemptService.findAttemptQuestions(attempt.id()), List.of(), null, null));
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/my-attempt",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MyQuizAttempt.class)))
    private void getMyAttempt(Context ctx) {
        int testId = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        if (session.member() == null) throw Refusal.QUIZ_ATTEMPT_NEEDS_MEMBERSHIP_TO_READ.raise();
        guards.requireOwnedTest(ctx, testId);
        var attempt = attemptService.findAttempt(testId, session.member().id());
        if (attempt.isEmpty()) {
            ctx.json(new NoQuizAttempt());
            return;
        }
        ctx.json(new QuizAttemptDetail(
                attempt.get(),
                attemptService.findAttemptQuestions(attempt.get().id()),
                attemptService.findAnswers(attempt.get().id()),
                null,
                null));
    }

    @OpenApi(
            path = "/api/v1/quiz/attempts/{id}/answer",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuizAnswerRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizSuccessResponse.class)))
    private void saveAnswer(Context ctx) {
        var session = UserSession.from(ctx);
        var attempt = guards.requireMemberAttempt(ctx, session);
        if (attempt.status() != AttemptStatus.IN_PROGRESS) {
            throw Refusal.QUIZ_ALREADY_HANDED_IN.raise();
        }
        var req = ctx.bodyAsClass(QuizAnswerRequest.class);
        attemptService.saveAnswer(attempt.id(), req.questionId(), req.answer());
        ctx.json(new QuizSuccessResponse(true));
    }

    /**
     * Hands a paper in.
     *
     * <p>A paper that was not still being written is refused rather than answered as though it had
     * been taken. It used to answer with the attempt whatever happened, so somebody whose time had
     * run out, or whose paper was already in, was told it had gone through and had no reason to
     * look again.
     */
    @OpenApi(
            path = "/api/v1/quiz/attempts/{id}/submit",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizTestAttempt.class)))
    private void submitAttempt(Context ctx) {
        var session = UserSession.from(ctx);
        var attempt = guards.requireMemberAttempt(ctx, session);
        if (!attemptService.submitAttempt(attempt.id())) {
            throw Refusal.QUIZ_NOT_HANDED_IN.raise();
        }
        ctx.json(attemptService
                .findAttemptById(attempt.id())
                .orElseThrow(Refusal.QUIZ_ATTEMPT_NOT_HERE_AFTER_HANDING_IN::raise));
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/attempts",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizTestAttempt[].class)))
    private void listAttempts(Context ctx) {
        int testId = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, testId);
        ctx.json(attemptService.findAttempts(testId));
    }

    @OpenApi(
            path = "/api/v1/quiz/attempts/{id}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizAttemptDetail.class)))
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
        ctx.json(new QuizAttemptDetail(attempt, attemptQuestions, answers, questions, memberIdentity));
    }

    @OpenApi(
            path = "/api/v1/quiz/answers/{id}/grade",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuizGradeRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizSuccessResponse.class)))
    private void gradeAnswer(Context ctx) {
        int answerId = pathInt(ctx, "id");
        var answer = attemptService.findAnswerById(answerId).orElseThrow(Refusal.QUIZ_ANSWER_NOT_HERE::raise);
        guards.requireOwnedAttempt(ctx, answer.attemptId());
        var req = ctx.bodyAsClass(QuizGradeRequest.class);
        if (req.points() == null) throw Refusal.QUIZ_GRADE_NEEDS_POINTS.raise();
        attemptService.gradeAnswer(answerId, req.points());
        ctx.json(new QuizSuccessResponse(true));
    }

    @OpenApi(
            path = "/api/v1/quiz/attempts/{id}/grade",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizTestAttempt.class)))
    private void gradeAttempt(Context ctx) {
        int attemptId = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        if (session.member() == null) throw Refusal.QUIZ_GRADING_NEEDS_MEMBERSHIP.raise();
        guards.requireOwnedAttempt(ctx, attemptId);
        attemptService.gradeAttempt(attemptId, session.member().id());
        attemptService.findAttemptById(attemptId).ifPresentOrElse(ctx::json, () -> {
            throw Refusal.QUIZ_ATTEMPT_NOT_HERE_AFTER_GRADING.raise();
        });
    }

    public record QuizAnswerRequest(int questionId, String answer) {}

    public record QuizGradeRequest(Double points) {}

    /**
     * What a member finds of their own paper: the paper, or nothing where they have not started one.
     */
    public sealed interface MyQuizAttempt permits QuizAttemptDetail, NoQuizAttempt {}

    /**
     * An attempt with its questions and answers.
     *
     * @param questionDetails the questions in full, sent only to a reviewer
     * @param memberIdentity  who wrote the paper, sent only to a reviewer and only where it resolves
     */
    public record QuizAttemptDetail(
            QuizTestAttempt attempt,
            List<QuizTestAttemptQuestion> questions,
            List<QuizTestAnswer> answers,
            @Nullable List<QuizQuestion> questionDetails,
            @Nullable MemberIdentity memberIdentity)
            implements MyQuizAttempt {}

    /** The answer for a member who has not started the paper yet. */
    public record NoQuizAttempt() implements MyQuizAttempt {}
}
