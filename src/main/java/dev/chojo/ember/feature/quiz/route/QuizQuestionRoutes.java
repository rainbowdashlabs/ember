/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.quiz.entity.CreateQuestionCommand;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.service.QuizQuestionImageService;
import dev.chojo.ember.feature.quiz.service.QuizQuestionReportService;
import dev.chojo.ember.feature.quiz.service.QuizQuestionSanitizer;
import dev.chojo.ember.feature.quiz.service.QuizQuestionService;
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
import tools.jackson.databind.JsonNode;

import java.io.IOException;
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
    private void reportQuestion(Context ctx) {
        var question = guards.requireOwnedQuestion(ctx, pathInt(ctx, "id"));
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(ReportRequest.class);
        var member = session.member();
        ctx.status(HttpStatus.CREATED)
                .json(reportService.report(question.id(), member != null ? member.id() : null, req.note()));
    }

    private void listCatalogReports(Context ctx) {
        var catalog = guards.requireOwnedCatalog(ctx, pathInt(ctx, "id"));
        ctx.json(reportService.findByCatalog(catalog.id()));
    }

    /**
     * Acknowledges a note, which deletes it. Whoever maintains the catalog says with this that the
     * question has been dealt with, so what remains on a catalog is only what is still open.
     */
    private void acknowledgeReport(Context ctx) {
        int reportId = pathInt(ctx, "id");
        int catalogId = reportService.findCatalogOfReport(reportId).orElseThrow(NotFoundResponse::new);
        guards.requireOwnedCatalog(ctx, catalogId);
        if (!reportService.acknowledge(reportId)) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listQuestions(Context ctx) {
        int catalogId = pathInt(ctx, "id");
        guards.requireOwnedCatalog(ctx, catalogId);
        ctx.json(questionService.findQuestions(catalogId));
    }

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

    private void createQuestion(Context ctx) {
        int catalogId = pathInt(ctx, "id");
        guards.requireOwnedCatalog(ctx, catalogId);
        var req = ctx.bodyAsClass(QuestionRequest.class);
        if (req.title() == null || req.title().isBlank()) throw new BadRequestResponse("title is required");
        if (req.quizQuestionType() == null) throw new BadRequestResponse("questionType is required");
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

    private void updateQuestion(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedQuestion(ctx, id);
        var req = ctx.bodyAsClass(QuestionRequest.class);
        if (!questionService.updateQuestion(
                id,
                req.categoryId(),
                req.title(),
                req.description() != null ? req.description() : "",
                req.imageUrl(),
                req.points() != null ? req.points() : 1.0,
                req.autoPoints() == null || req.autoPoints(),
                req.configString(),
                req.position() != null ? req.position() : 0)) {
            throw new NotFoundResponse();
        }
        questionService.findQuestion(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void deleteQuestion(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedQuestion(ctx, id);
        if (questionService.deleteQuestion(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

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
                            throw new NotFoundResponse("No image");
                        });
    }

    private void uploadQuestionImage(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        guards.requireOwnedQuestion(ctx, id);
        var file = ctx.uploadedFile("image");
        if (file == null) {
            throw new BadRequestResponse("No file uploaded");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(file.contentType())) {
            throw new BadRequestResponse("Invalid file type. Allowed: PNG, JPEG, WebP");
        }
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            imageService.store(session.stationId(), id, data, file.contentType(), apiConfig.maxImageSizeBytes());
            ctx.json(new QuizSuccessResponse(true));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument storing question image for question {}", id, e);
            throw new BadRequestResponse(e.getMessage());
        } catch (IOException e) {
            log.error("Failed to process question image for question {}", id, e);
            throw new InternalServerErrorResponse("Failed to process image");
        }
    }

    private void deleteQuestionImage(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        imageService.delete(session.stationId(), id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record QuestionRequest(
            QuizQuestionType quizQuestionType,
            Integer categoryId,
            String title,
            String description,
            String imageUrl,
            Double points,
            Boolean autoPoints,
            JsonNode config,
            Integer position) {

        public String configString() {
            return config != null ? config.toString() : "{}";
        }
    }

    /**
     * @param note what the member says is wrong with the question, in their own words
     */
    public record ReportRequest(String note) {}
}
