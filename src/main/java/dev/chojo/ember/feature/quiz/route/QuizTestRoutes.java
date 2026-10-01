/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.quiz.entity.AttemptStatus;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTest;
import dev.chojo.ember.feature.quiz.entity.QuizTestSection;
import dev.chojo.ember.feature.quiz.entity.QuizTestSectionSource;
import dev.chojo.ember.feature.quiz.entity.SectionEntry;
import dev.chojo.ember.feature.quiz.entity.SourceEntry;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
import dev.chojo.ember.feature.quiz.service.QuizAttemptService;
import dev.chojo.ember.feature.quiz.service.QuizPdfService;
import dev.chojo.ember.feature.quiz.service.QuizQuestionService;
import dev.chojo.ember.feature.quiz.service.QuizRouteGuards;
import dev.chojo.ember.feature.quiz.service.QuizTestAccessService;
import dev.chojo.ember.feature.quiz.service.QuizTestService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionType;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Test configuration endpoints: the test CRUD and lifecycle, the frozen question set,
 * sections and their sources, access restrictions, per-member access grants and the
 * printable question and solution sheets.
 */
@Singleton
public class QuizTestRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(QuizTestRoutes.class);

    private final QuizTestService testService;
    private final QuizTestAccessService accessService;
    private final QuizQuestionService questionService;
    private final QuizAttemptService attemptService;
    private final QuizPdfService pdfService;
    private final QuizRouteGuards guards;

    @Inject
    public QuizTestRoutes(
            QuizTestService testService,
            QuizTestAccessService accessService,
            QuizQuestionService questionService,
            QuizAttemptService attemptService,
            QuizPdfService pdfService,
            QuizRouteGuards guards) {
        this.testService = testService;
        this.accessService = accessService;
        this.questionService = questionService;
        this.attemptService = attemptService;
        this.pdfService = pdfService;
        this.guards = guards;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/quiz/tests", this::listTests, StationPermission.TEST_RESULT_READ);
        routes.get(prefix + "/quiz/tests/available", this::listAvailableTests, StationPermission.USER);
        routes.post(prefix + "/quiz/tests", this::createTest, StationPermission.TEST_CONFIGURE);
        routes.get(prefix + "/quiz/tests/{id}", this::getTest, StationPermission.USER);
        routes.put(prefix + "/quiz/tests/{id}", this::updateTest, StationPermission.TEST_CONFIGURE);
        routes.delete(prefix + "/quiz/tests/{id}", this::deleteTest, StationPermission.TEST_CONFIGURE);
        routes.post(prefix + "/quiz/tests/{id}/activate", this::activateTest, StationPermission.TEST_CONFIGURE);
        routes.post(prefix + "/quiz/tests/{id}/close", this::closeTest, StationPermission.TEST_CONFIGURE);
        routes.post(
                prefix + "/quiz/tests/{id}/generate-questions",
                this::generateFrozenQuestions,
                StationPermission.TEST_CONFIGURE);
        routes.get(
                prefix + "/quiz/tests/{id}/frozen-questions",
                this::listFrozenQuestions,
                StationPermission.TEST_RESULT_READ);
        routes.put(
                prefix + "/quiz/tests/{id}/frozen-questions/{position}",
                this::replaceFrozenQuestion,
                StationPermission.TEST_CONFIGURE);
        routes.post(
                prefix + "/quiz/tests/{id}/frozen-questions/{position}/random",
                this::randomReplaceFrozenQuestion,
                StationPermission.TEST_CONFIGURE);
        routes.get(
                prefix + "/quiz/tests/{id}/available-questions",
                this::listAvailableReplacements,
                StationPermission.TEST_CONFIGURE);

        routes.get(prefix + "/quiz/tests/{id}/sections", this::listSections, StationPermission.TEST_CONFIGURE);
        routes.put(prefix + "/quiz/tests/{id}/sections", this::replaceSections, StationPermission.TEST_CONFIGURE);

        routes.get(prefix + "/quiz/tests/{id}/restrictions", this::getRestrictions, StationPermission.TEST_RESULT_READ);
        routes.put(prefix + "/quiz/tests/{id}/restrictions", this::setRestrictions, StationPermission.TEST_CONFIGURE);

        routes.post(prefix + "/quiz/tests/{id}/access", this::grantAccess, StationPermission.TEST_CONFIGURE);
        routes.delete(
                prefix + "/quiz/tests/{testId}/access/{memberId}",
                this::revokeAccess,
                StationPermission.TEST_CONFIGURE);

        routes.get(
                prefix + "/quiz/tests/{id}/export/questions",
                this::exportQuestionPdf,
                StationPermission.TEST_CONFIGURE);
        routes.get(
                prefix + "/quiz/tests/{id}/export/solutions",
                this::exportSolutionPdf,
                StationPermission.TEST_CONFIGURE);
    }

    /**
     * Lists the station's tests with their attempt counts. A member who may configure
     * tests sees all of them; everyone else sees only the tests their restrictions admit.
     */
    @OpenApi(
            path = "/api/v1/quiz/tests",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizTestSummary[].class)))
    private void listTests(Context ctx) {
        var session = UserSession.from(ctx);
        List<QuizTest> tests;
        if (session.member() != null && !session.permissions().contains(StationPermission.TEST_CONFIGURE)) {
            tests = testService.findTestsForMember(
                    session.stationId(),
                    session.member().id(),
                    session.hasPermission(RestrictionType.QUIZ_TEST.managerPermission()));
        } else {
            tests = testService.findTests(session.stationId());
        }
        var result = tests.stream()
                .map(t -> new QuizTestSummary(t, testService.countAttempts(t.id())))
                .toList();
        ctx.json(result);
    }

    /**
     * Lists the active tests the calling member may take, each with the state of their own
     * attempt. A test manager sees every test; everyone else the tests their restrictions admit.
     */
    @OpenApi(
            path = "/api/v1/quiz/tests/available",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizAvailableTest[].class)))
    private void listAvailableTests(Context ctx) {
        var session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.json(List.of());
            return;
        }
        int memberId = session.member().id();
        boolean manager = session.hasPermission(RestrictionType.QUIZ_TEST.managerPermission());
        var tests = testService.findTestsForMember(session.stationId(), memberId, manager).stream()
                .filter(t -> t.status() == TestStatus.ACTIVE)
                .toList();
        var result = tests.stream()
                .map(t -> {
                    var attempt = attemptService.findAttempt(t.id(), memberId).orElse(null);
                    AttemptStatus attemptStatus = attempt != null ? attempt.status() : null;
                    Instant startedAt = attempt != null ? attempt.startedAt() : null;
                    Instant submittedAt = attempt != null ? attempt.submittedAt() : null;
                    return new QuizAvailableTest(t, attemptStatus, startedAt, submittedAt);
                })
                .toList();
        ctx.json(result);
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizTestDetail.class)))
    private void getTest(Context ctx) {
        int id = pathInt(ctx, "id");
        var test = guards.requireOwnedTest(ctx, id);
        ctx.json(new QuizTestDetail(
                test, buildSectionDetails(id), attemptService.findAttempts(id).size()));
    }

    @OpenApi(
            path = "/api/v1/quiz/tests",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuizTestRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = QuizTest.class)))
    private void createTest(Context ctx) {
        var session = StationSession.from(ctx);
        var req = ctx.bodyAsClass(QuizTestRequest.class);
        if (req.title() == null || req.title().isBlank()) throw Refusal.QUIZ_TEST_NEEDS_A_TITLE.raise();
        var test = testService.createTest(
                session.stationId(),
                req.title(),
                Objects.requireNonNullElse(req.description(), ""),
                req.timeLimit(),
                Boolean.TRUE.equals(req.shuffle()),
                Boolean.TRUE.equals(req.forced()),
                session.member().id());
        ctx.status(HttpStatus.CREATED).json(test);
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuizTestRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizTest.class)))
    private void updateTest(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, id);
        var req = ctx.bodyAsClass(QuizTestRequest.class);
        if (!testService.updateTest(
                id,
                req.title(),
                Objects.requireNonNullElse(req.description(), ""),
                req.timeLimit(),
                Boolean.TRUE.equals(req.shuffle()),
                Boolean.TRUE.equals(req.forced()),
                req.startAt(),
                req.endAt())) {
            throw Refusal.QUIZ_TEST_NOT_CHANGED.raise();
        }
        testService.findTest(id).ifPresentOrElse(ctx::json, () -> {
            throw Refusal.QUIZ_TEST_NOT_HERE_AFTER_CHANGE.raise();
        });
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteTest(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, id);
        if (testService.deleteTest(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw Refusal.QUIZ_TEST_NOT_DELETED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/activate",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizTest.class)))
    private void activateTest(Context ctx) {
        int id = pathInt(ctx, "id");
        var test = guards.requireOwnedTest(ctx, id);
        if (test.status() != TestStatus.DRAFT) throw Refusal.QUIZ_TEST_NOT_A_DRAFT.raise();
        testService.activateTest(id);
        testService.findTest(id).ifPresentOrElse(ctx::json, () -> {
            throw Refusal.QUIZ_TEST_NOT_HERE_AFTER_ACTIVATION.raise();
        });
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/close",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizTest.class)))
    private void closeTest(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, id);
        if (!testService.closeTest(id)) throw Refusal.QUIZ_TEST_NOT_CLOSED.raise();
        testService.findTest(id).ifPresentOrElse(ctx::json, () -> {
            throw Refusal.QUIZ_TEST_NOT_HERE_AFTER_CLOSING.raise();
        });
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/generate-questions",
            methods = HttpMethod.POST,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = FrozenQuestionDetail[].class)))
    private void generateFrozenQuestions(Context ctx) {
        int testId = pathInt(ctx, "id");
        var test = guards.requireOwnedTest(ctx, testId);
        if (test.status() == TestStatus.ACTIVE) throw Refusal.QUIZ_TEST_RUNNING_CANNOT_REDRAW.raise();
        testService.generateFrozenQuestions(testId);
        ctx.json(buildFrozenQuestionResponse(testId));
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/frozen-questions",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = FrozenQuestionDetail[].class)))
    private void listFrozenQuestions(Context ctx) {
        int testId = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, testId);
        ctx.json(buildFrozenQuestionResponse(testId));
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/frozen-questions/{position}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ReplaceQuestionRequest.class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = FrozenQuestionDetail[].class)))
    private void replaceFrozenQuestion(Context ctx) {
        var test = guards.requireModifiableTest(ctx);
        int position = pathInt(ctx, "position");
        var req = ctx.bodyAsClass(ReplaceQuestionRequest.class);
        guards.requireOwnedQuestion(ctx, req.questionId());
        testService.replaceFrozenQuestion(test.id(), position, req.questionId());
        ctx.json(buildFrozenQuestionResponse(test.id()));
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/frozen-questions/{position}/random",
            methods = HttpMethod.POST,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = FrozenQuestionDetail[].class)))
    private void randomReplaceFrozenQuestion(Context ctx) {
        var test = guards.requireModifiableTest(ctx);
        int position = pathInt(ctx, "position");
        testService.replaceWithRandomQuestion(test.id(), position);
        ctx.json(buildFrozenQuestionResponse(test.id()));
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/available-questions",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizQuestion[].class)))
    private void listAvailableReplacements(Context ctx) {
        int testId = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, testId);
        ctx.json(testService.findAvailableReplacements(testId));
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/sections",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizSectionDetail[].class)))
    private void listSections(Context ctx) {
        int testId = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, testId);
        ctx.json(buildSectionDetails(testId));
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/sections",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuizSectionRequest[].class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizTestSection[].class)))
    private void replaceSections(Context ctx) {
        int testId = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, testId);
        var req = ctx.bodyAsClass(QuizSectionRequest[].class);
        var entries = Arrays.stream(req)
                .map(s -> new SectionEntry(
                        Objects.requireNonNullElse(s.title(), ""),
                        Objects.requireNonNullElse(s.description(), ""),
                        Objects.requireNonNullElse(s.sources(), List.<QuizSourceRequest>of()).stream()
                                .map(src -> new SourceEntry(src.catalogId(), src.categoryId(), src.questionCount()))
                                .toList()))
                .toList();
        testService.replaceSections(testId, entries);
        ctx.json(testService.findSections(testId));
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/restrictions",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizTestRestrictions.class)))
    private void getRestrictions(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, id);
        ctx.json(storedRestrictions(id));
    }

    /**
     * Replaces who may take a test and answers with the restrictions as stored, so a list or a mode
     * the request left out reads back as what the test now holds rather than as nothing.
     */
    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/restrictions",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuizTestRestrictionsRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizTestRestrictions.class)))
    private void setRestrictions(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, id);
        var req = ctx.bodyAsClass(QuizTestRestrictionsRequest.class);
        accessService.setRestrictions(
                id,
                new RestrictionSelection(req.userTypes(), req.groupIds(), req.tagIds(), req.memberIds(), req.mode()));
        var mode = req.mode();
        if (mode != null) {
            accessService.updateRestrictionMode(id, mode);
        }
        ctx.json(storedRestrictions(id));
    }

    private QuizTestRestrictions storedRestrictions(int testId) {
        var restrictions = accessService.findRestrictions(testId);
        return new QuizTestRestrictions(
                restrictions.userTypes(),
                restrictions.groupIds(),
                restrictions.tagIds(),
                restrictions.memberIds(),
                restrictions.mode());
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/access",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuizAccessRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizSuccessResponse.class)))
    private void grantAccess(Context ctx) {
        int testId = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, testId);
        var req = ctx.bodyAsClass(QuizAccessRequest.class);
        if (req.memberId() == null) throw Refusal.QUIZ_TEST_ACCESS_NEEDS_A_MEMBER.raise();
        accessService.grantMemberAccess(testId, req.memberId(), req.closesAt());
        ctx.json(new QuizSuccessResponse(true));
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{testId}/access/{memberId}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void revokeAccess(Context ctx) {
        int testId = pathInt(ctx, "testId");
        int memberId = pathInt(ctx, "memberId");
        guards.requireOwnedTest(ctx, testId);
        accessService.revokeMemberAccess(testId, memberId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/export/questions",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void exportQuestionPdf(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, id);
        try {
            var pdf = pdfService.exportQuestionPdf(id);
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", pdf.contentDisposition());
            ctx.result(pdf.bytes());
        } catch (Exception e) {
            log.error("PDF export failed for test {}", id, e);
            throw Refusal.QUIZ_TEST_PDF_NOT_MADE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/quiz/tests/{id}/export/solutions",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void exportSolutionPdf(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, id);
        try {
            var pdf = pdfService.exportSolutionPdf(id);
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", pdf.contentDisposition());
            ctx.result(pdf.bytes());
        } catch (Exception e) {
            log.error("PDF solution export failed for test {}", id, e);
            throw Refusal.QUIZ_TEST_SOLUTION_PDF_NOT_MADE.raise();
        }
    }

    /**
     * Builds the section detail responses for a test, loading each section's sources.
     */
    private List<QuizSectionDetail> buildSectionDetails(int testId) {
        return testService.findSections(testId).stream()
                .map(s -> {
                    var sources = testService.findSources(s.id());
                    return new QuizSectionDetail(s.id(), s.testId(), s.title(), s.description(), s.position(), sources);
                })
                .toList();
    }

    private List<FrozenQuestionDetail> buildFrozenQuestionResponse(int testId) {
        var frozen = testService.findFrozenQuestions(testId);
        return frozen.stream()
                .map(fq -> {
                    var question = questionService.findQuestion(fq.questionId()).orElse(null);
                    return new FrozenQuestionDetail(fq.position(), fq.sectionId(), question);
                })
                .toList();
    }

    public record ReplaceQuestionRequest(int questionId) {}

    /**
     * @param question the question drawn for the place, or {@code null} where it was deleted since
     */
    public record FrozenQuestionDetail(
            int position,
            @Nullable Integer sectionId,
            @Nullable QuizQuestion question) {}

    public record QuizTestRequest(
            String title,
            @Nullable String description,
            @Nullable Integer timeLimit,
            @Nullable Boolean shuffle,
            @Nullable Boolean forced,
            @Nullable Instant startAt,
            @Nullable Instant endAt) {}

    public record QuizSectionRequest(
            @Nullable String title,
            @Nullable String description,
            @Nullable List<QuizSourceRequest> sources) {}

    public record QuizSourceRequest(int catalogId, @Nullable Integer categoryId, int questionCount) {}

    public record QuizAccessRequest(
            Integer memberId, @Nullable Instant closesAt) {}

    /**
     * Who may take a test, as it is stored.
     */
    public record QuizTestRestrictions(
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds,
            RestrictionMode mode) {}

    /**
     * Who may take a test, as an editor sends it. A list left out counts as empty; a mode left out
     * keeps the one the test has.
     */
    public record QuizTestRestrictionsRequest(
            @Nullable List<StationUserType> userTypes,
            @Nullable List<Integer> groupIds,
            @Nullable List<Integer> tagIds,
            @Nullable List<Integer> memberIds,
            @Nullable RestrictionMode mode) {}

    public record QuizTestSummary(QuizTest test, int attemptCount) {}

    public record QuizTestDetail(QuizTest test, List<QuizSectionDetail> sections, int attemptCount) {}

    public record QuizSectionDetail(
            int id, int testId, String title, String description, int position, List<QuizTestSectionSource> sources) {}

    public record QuizAvailableTest(
            QuizTest test,
            @Nullable AttemptStatus attemptStatus,
            @Nullable Instant startedAt,
            @Nullable Instant submittedAt) {}
}
