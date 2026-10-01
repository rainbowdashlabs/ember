/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.quiz.entity.CreateQuestionCommand;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionRead;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionReport;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.service.QuizQuestionImageService;
import dev.chojo.ember.feature.quiz.service.QuizQuestionReportService;
import dev.chojo.ember.feature.quiz.service.QuizQuestionSanitizer;
import dev.chojo.ember.feature.quiz.service.QuizQuestionService;
import dev.chojo.ember.feature.quiz.service.QuizRouteGuards;
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
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Question endpoints: the catalog-scoped question CRUD and the per-question image.
 * Members without catalog access receive the answer-free question projection.
 */
@Singleton
public class QuizQuestionRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(QuizQuestionRoutes.class);
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final QuizQuestionService questionService;
    private final QuizQuestionSanitizer sanitizer;
    private final QuizQuestionImageService imageService;
    private final QuizQuestionReportService reportService;
    private final QuizRouteGuards guards;
    private final Api apiConfig;

    @Inject
    public QuizQuestionRoutes(
            QuizQuestionService questionService,
            QuizQuestionSanitizer sanitizer,
            QuizQuestionImageService imageService,
            QuizQuestionReportService reportService,
            QuizRouteGuards guards,
            Api apiConfig) {
        this.questionService = questionService;
        this.sanitizer = sanitizer;
        this.imageService = imageService;
        this.reportService = reportService;
        this.guards = guards;
        this.apiConfig = apiConfig;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/quiz/catalogs/{id}/questions", this::listQuestions, StationPermission.TEST_CATALOG_VIEW);
        routes.post(
                prefix + "/quiz/catalogs/{id}/questions", this::createQuestion, StationPermission.TEST_CATALOG_EDIT);
        routes.get(prefix + "/quiz/questions/{id}", this::getQuestion, StationPermission.USER);
        routes.put(prefix + "/quiz/questions/{id}", this::updateQuestion, StationPermission.TEST_CATALOG_EDIT);
        routes.delete(prefix + "/quiz/questions/{id}", this::deleteQuestion, StationPermission.TEST_CATALOG_EDIT);

        routes.get(prefix + "/quiz/questions/{id}/image", this::getQuestionImage, StationPermission.USER);
        routes.post(
                prefix + "/quiz/questions/{id}/image", this::uploadQuestionImage, StationPermission.TEST_CATALOG_EDIT);
        routes.delete(
                prefix + "/quiz/questions/{id}/image", this::deleteQuestionImage, StationPermission.TEST_CATALOG_EDIT);

        routes.post(prefix + "/quiz/questions/{id}/reports", this::reportQuestion, StationPermission.USER);
        routes.get(
                prefix + "/quiz/catalogs/{id}/reports", this::listCatalogReports, StationPermission.TEST_CATALOG_VIEW);
        routes.delete(prefix + "/quiz/reports/{id}", this::acknowledgeReport, StationPermission.TEST_CATALOG_EDIT);
    }

    /**
     * Records what a member says is wrong with a question. Open to anyone who may train, because
     * the person who trains against a catalog is the one who notices that an answer has gone stale.
     */
    @OpenApi(
            path = "/api/v1/quiz/questions/{id}/reports",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuizReportRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = QuizQuestionReport.class)))
    private void reportQuestion(Context ctx) {
        var question = guards.requireOwnedQuestion(ctx, pathInt(ctx, "id"));
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(QuizReportRequest.class);
        var member = session.member();
        ctx.status(HttpStatus.CREATED)
                .json(reportService.report(question.id(), member != null ? member.id() : null, req.note()));
    }

    @OpenApi(
            path = "/api/v1/quiz/catalogs/{id}/reports",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizQuestionReport[].class)))
    private void listCatalogReports(Context ctx) {
        var catalog = guards.requireOwnedCatalog(ctx, pathInt(ctx, "id"));
        ctx.json(reportService.findByCatalog(catalog.id()));
    }

    /**
     * Acknowledges a note, which deletes it. Whoever maintains the catalog says with this that the
     * question has been dealt with, so what remains on a catalog is only what is still open.
     */
    @OpenApi(
            path = "/api/v1/quiz/reports/{id}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void acknowledgeReport(Context ctx) {
        int reportId = pathInt(ctx, "id");
        int catalogId =
                reportService.findCatalogOfReport(reportId).orElseThrow(Refusal.QUIZ_QUESTION_NOTE_NOT_HERE::raise);
        guards.requireOwnedCatalog(ctx, catalogId);
        if (!reportService.acknowledge(reportId)) throw Refusal.QUIZ_QUESTION_NOTE_NOT_CLEARED.raise();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/quiz/catalogs/{id}/questions",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizQuestion[].class)))
    private void listQuestions(Context ctx) {
        int catalogId = pathInt(ctx, "id");
        guards.requireOwnedCatalog(ctx, catalogId);
        ctx.json(questionService.findQuestions(catalogId));
    }

    @OpenApi(
            path = "/api/v1/quiz/questions/{id}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizQuestionRead.class)))
    private void getQuestion(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        var question = guards.requireOwnedQuestion(ctx, id);
        if (session.permissions().contains(StationPermission.TEST_CATALOG_VIEW)) {
            ctx.json(question);
        } else {
            ctx.json(sanitizer.sanitize(question));
        }
    }

    @OpenApi(
            path = "/api/v1/quiz/catalogs/{id}/questions",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuizQuestionRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = QuizQuestion.class)))
    private void createQuestion(Context ctx) {
        int catalogId = pathInt(ctx, "id");
        guards.requireOwnedCatalog(ctx, catalogId);
        var req = ctx.bodyAsClass(QuizQuestionRequest.class);
        if (req.title() == null || req.title().isBlank()) throw Refusal.QUIZ_QUESTION_NEEDS_A_TITLE.raise();
        if (req.quizQuestionType() == null) throw Refusal.QUIZ_QUESTION_NEEDS_A_KIND.raise();
        var question = questionService.createQuestion(
                CreateQuestionCommand.builder(catalogId, req.quizQuestionType(), req.title())
                        .category(req.categoryId())
                        .description(req.description())
                        .imageUrl(req.imageUrl())
                        .points(req.points())
                        .autoPoints(req.autoPoints())
                        .configJson(req.configString())
                        .position(req.position())
                        .build());
        ctx.status(HttpStatus.CREATED).json(question);
    }

    @OpenApi(
            path = "/api/v1/quiz/questions/{id}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuizQuestionRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizQuestion.class)))
    private void updateQuestion(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedQuestion(ctx, id);
        var req = ctx.bodyAsClass(QuizQuestionRequest.class);
        if (!questionService.updateQuestion(
                id,
                req.categoryId(),
                req.title(),
                Objects.requireNonNullElse(req.description(), ""),
                req.imageUrl(),
                Objects.requireNonNullElse(req.points(), 1.0),
                !Boolean.FALSE.equals(req.autoPoints()),
                req.configString(),
                Objects.requireNonNullElse(req.position(), 0))) {
            throw Refusal.QUIZ_QUESTION_NOT_CHANGED.raise();
        }
        questionService.findQuestion(id).ifPresentOrElse(ctx::json, () -> {
            throw Refusal.QUIZ_QUESTION_NOT_HERE_AFTER_CHANGE.raise();
        });
    }

    @OpenApi(
            path = "/api/v1/quiz/questions/{id}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteQuestion(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedQuestion(ctx, id);
        if (questionService.deleteQuestion(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw Refusal.QUIZ_QUESTION_NOT_DELETED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/quiz/questions/{id}/image",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void getQuestionImage(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
        imageService
                .read(session.stationId(), id, size)
                .ifPresentOrElse(
                        img -> {
                            ctx.contentType(img.contentType());
                            ctx.header("Cache-Control", "public, max-age=3600");
                            ctx.result(img.data());
                        },
                        () -> {
                            throw Refusal.QUIZ_QUESTION_PICTURE_NOT_HERE.raise();
                        });
    }

    @OpenApi(
            path = "/api/v1/quiz/questions/{id}/image",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuizSuccessResponse.class)))
    private void uploadQuestionImage(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        guards.requireOwnedQuestion(ctx, id);
        var file = ctx.uploadedFile("image");
        if (file == null) {
            throw Refusal.QUIZ_PICTURE_UPLOAD_WITHOUT_FILE.raise();
        }
        String contentType = file.contentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw Refusal.QUIZ_PICTURE_KIND_NOT_TAKEN.raise();
        }
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            imageService.store(session.stationId(), id, data, contentType, apiConfig.maxImageSizeBytes());
            ctx.json(new QuizSuccessResponse(true));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument storing question image for question {}", id, e);
            throw Refusal.QUIZ_PICTURE_NOT_SAVED.raise();
        } catch (IOException e) {
            log.error("Failed to process question image for question {}", id, e);
            throw Refusal.QUIZ_PICTURE_NOT_PROCESSED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/quiz/questions/{id}/image",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteQuestionImage(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        imageService.delete(session.stationId(), id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record QuizQuestionRequest(
            QuizQuestionType quizQuestionType,
            @Nullable Integer categoryId,
            String title,
            @Nullable String description,
            @Nullable String imageUrl,
            @Nullable Double points,
            @Nullable Boolean autoPoints,
            @Nullable JsonNode config,
            @Nullable Integer position) {

        public String configString() {
            return config != null ? config.toString() : "{}";
        }
    }

    /**
     * @param note what the member says is wrong with the question, in their own words
     */
    public record QuizReportRequest(String note) {}
}
