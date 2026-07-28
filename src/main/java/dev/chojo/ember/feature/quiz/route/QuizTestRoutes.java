/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.quiz.entity.AttemptStatus;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTest;
import dev.chojo.ember.feature.quiz.entity.QuizTestSectionSource;
import dev.chojo.ember.feature.quiz.entity.SectionEntry;
import dev.chojo.ember.feature.quiz.entity.SourceEntry;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
import dev.chojo.ember.feature.quiz.service.QuizAttemptService;
import dev.chojo.ember.feature.quiz.service.QuizPdfService;
import dev.chojo.ember.feature.quiz.service.QuizQuestionService;
import dev.chojo.ember.feature.quiz.service.QuizTestAccessService;
import dev.chojo.ember.feature.quiz.service.QuizTestService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

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
    private void listTests(Context ctx) {
        var session = UserSession.from(ctx);
        List<QuizTest> tests;
        if (session.member() != null && !session.permissions().contains(StationPermission.TEST_CONFIGURE)) {
            tests = testService.findTestsForMember(
                    session.stationId(), session.member().id());
        } else {
            tests = testService.findTests(session.stationId());
        }
        var result = tests.stream()
                .map(t -> new TestSummary(t, testService.countAttempts(t.id())))
                .toList();
        ctx.json(result);
    }

    /**
     * Lists the active tests the calling member may take, each with the state of their own
     * attempt. Restriction filtering and the manager bypass are resolved in the database.
     */
    private void listAvailableTests(Context ctx) {
        var session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.json(List.of());
            return;
        }
        int memberId = session.member().id();
        var tests = testService.findTestsForMember(session.stationId(), memberId).stream()
                .filter(t -> t.status() == TestStatus.ACTIVE)
                .toList();
        var result = tests.stream()
                .map(t -> {
                    var attempt = attemptService.findAttempt(t.id(), memberId).orElse(null);
                    AttemptStatus attemptStatus = attempt != null ? attempt.status() : null;
                    Instant startedAt = attempt != null ? attempt.startedAt() : null;
                    Instant submittedAt = attempt != null ? attempt.submittedAt() : null;
                    return new AvailableTest(t, attemptStatus, startedAt, submittedAt);
                })
                .toList();
        ctx.json(result);
    }

    private void getTest(Context ctx) {
        int id = pathInt(ctx, "id");
        var test = guards.requireOwnedTest(ctx, id);
        ctx.json(new TestDetail(
                test, buildSectionDetails(id), attemptService.findAttempts(id).size()));
    }

    private void createTest(Context ctx) {
        var session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        var req = ctx.bodyAsClass(TestRequest.class);
        if (req.title() == null || req.title().isBlank()) throw new BadRequestResponse("title is required");
        var test = testService.createTest(
                session.stationId(),
                req.title(),
                req.description() != null ? req.description() : "",
                req.timeLimit(),
                req.shuffle() != null && req.shuffle(),
                req.forced() != null && req.forced(),
                session.member().id());
        ctx.status(HttpStatus.CREATED).json(test);
    }

    private void updateTest(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, id);
        var req = ctx.bodyAsClass(TestRequest.class);
        if (!testService.updateTest(
                id,
                req.title(),
                req.description() != null ? req.description() : "",
                req.timeLimit(),
                req.shuffle() != null && req.shuffle(),
                req.forced() != null && req.forced(),
                req.startAt(),
                req.endAt())) {
            throw new NotFoundResponse();
        }
        testService.findTest(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void deleteTest(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, id);
        if (testService.deleteTest(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    private void activateTest(Context ctx) {
        int id = pathInt(ctx, "id");
        var test = guards.requireOwnedTest(ctx, id);
        if (test.status() != TestStatus.DRAFT) throw new BadRequestResponse("Test is not in DRAFT status");
        testService.activateTest(id);
        testService.findTest(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void closeTest(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, id);
        if (!testService.closeTest(id)) throw new NotFoundResponse();
        testService.findTest(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void generateFrozenQuestions(Context ctx) {
        int testId = pathInt(ctx, "id");
        var test = guards.requireOwnedTest(ctx, testId);
        if (test.status() == TestStatus.ACTIVE) throw new BadRequestResponse("Cannot regenerate for active test");
        testService.generateFrozenQuestions(testId);
        ctx.json(buildFrozenQuestionResponse(testId));
    }

    private void listFrozenQuestions(Context ctx) {
        int testId = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, testId);
        ctx.json(buildFrozenQuestionResponse(testId));
    }

    private void replaceFrozenQuestion(Context ctx) {
        var test = guards.requireModifiableTest(ctx);
        int position = pathInt(ctx, "position");
        var req = ctx.bodyAsClass(ReplaceQuestionRequest.class);
        testService.replaceFrozenQuestion(test.id(), position, req.questionId());
        ctx.json(buildFrozenQuestionResponse(test.id()));
    }

    private void randomReplaceFrozenQuestion(Context ctx) {
        var test = guards.requireModifiableTest(ctx);
        int position = pathInt(ctx, "position");
        testService.replaceWithRandomQuestion(test.id(), position);
        ctx.json(buildFrozenQuestionResponse(test.id()));
    }

    private void listAvailableReplacements(Context ctx) {
        int testId = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, testId);
        ctx.json(testService.findAvailableReplacements(testId));
    }

    private void listSections(Context ctx) {
        int testId = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, testId);
        ctx.json(buildSectionDetails(testId));
    }

    private void replaceSections(Context ctx) {
        int testId = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, testId);
        var req = ctx.bodyAsClass(SectionRequest[].class);
        var entries = Arrays.stream(req)
                .map(s -> new SectionEntry(
                        s.title() != null ? s.title() : "",
                        s.description() != null ? s.description() : "",
                        s.sources() != null
                                ? s.sources().stream()
                                        .map(src ->
                                                new SourceEntry(src.catalogId(), src.categoryId(), src.questionCount()))
                                        .toList()
                                : List.of()))
                .toList();
        testService.replaceSections(testId, entries);
        ctx.json(testService.findSections(testId));
    }

    private void getRestrictions(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, id);
        var restrictions = accessService.findRestrictions(id);
        ctx.json(new TestRestrictions(
                restrictions.userTypes(),
                restrictions.groupIds(),
                restrictions.tagIds(),
                restrictions.memberIds(),
                restrictions.mode()));
    }

    private void setRestrictions(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, id);
        var req = ctx.bodyAsClass(TestRestrictions.class);
        accessService.setRestrictions(
                id,
                new RestrictionSelection(req.userTypes(), req.groupIds(), req.tagIds(), req.memberIds(), req.mode()));
        if (req.mode() != null) {
            accessService.updateRestrictionMode(id, req.mode());
        }
        ctx.json(req);
    }

    private void grantAccess(Context ctx) {
        int testId = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, testId);
        var req = ctx.bodyAsClass(AccessRequest.class);
        if (req.memberId() == null) throw new BadRequestResponse("memberId is required");
        accessService.grantMemberAccess(testId, req.memberId(), req.closesAt());
        ctx.json(new QuizSuccessResponse(true));
    }

    private void revokeAccess(Context ctx) {
        int testId = pathInt(ctx, "testId");
        int memberId = pathInt(ctx, "memberId");
        guards.requireOwnedTest(ctx, testId);
        accessService.revokeMemberAccess(testId, memberId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void exportQuestionPdf(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, id);
        try {
            byte[] pdf = pdfService.exportQuestionPdf(id);
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", "attachment; filename=\"test-questions.pdf\"");
            ctx.result(pdf);
        } catch (Exception e) {
            log.error("PDF export failed for test {}", id, e);
            throw new InternalServerErrorResponse("Internal server error");
        }
    }

    private void exportSolutionPdf(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedTest(ctx, id);
        try {
            byte[] pdf = pdfService.exportSolutionPdf(id);
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", "attachment; filename=\"test-solutions.pdf\"");
            ctx.result(pdf);
        } catch (Exception e) {
            log.error("PDF solution export failed for test {}", id, e);
            throw new InternalServerErrorResponse("Internal server error");
        }
    }

    /**
     * Builds the section detail responses for a test, loading each section's sources.
     */
    private List<SectionDetail> buildSectionDetails(int testId) {
        return testService.findSections(testId).stream()
                .map(s -> {
                    var sources = testService.findSources(s.id());
                    return new SectionDetail(s.id(), s.testId(), s.title(), s.description(), s.position(), sources);
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

    public record FrozenQuestionDetail(int position, Integer sectionId, QuizQuestion question) {}

    public record TestRequest(
            String title,
            String description,
            Integer timeLimit,
            Boolean shuffle,
            Boolean forced,
            Instant startAt,
            Instant endAt) {}

    public record SectionRequest(String title, String description, List<SourceRequest> sources) {}

    public record SourceRequest(int catalogId, Integer categoryId, int questionCount) {}

    public record AccessRequest(Integer memberId, Instant closesAt) {}

    public record TestRestrictions(
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds,
            RestrictionMode mode) {}

    public record TestSummary(QuizTest test, int attemptCount) {}

    public record TestDetail(QuizTest test, List<SectionDetail> sections, int attemptCount) {}

    public record SectionDetail(
            int id, int testId, String title, String description, int position, List<QuizTestSectionSource> sources) {}

    public record AvailableTest(QuizTest test, AttemptStatus attemptStatus, Instant startedAt, Instant submittedAt) {}
}
