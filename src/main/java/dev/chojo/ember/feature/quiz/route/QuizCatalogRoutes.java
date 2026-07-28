/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.quiz.entity.CreateQuestionCommand;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.service.QuizCatalogService;
import dev.chojo.ember.feature.quiz.service.QuizFederationService;
import dev.chojo.ember.feature.quiz.service.QuizFederationService.SharedQuizCatalog;
import dev.chojo.ember.feature.quiz.service.QuizImportService;
import dev.chojo.ember.feature.quiz.service.QuizImportService.CsvMappings;
import dev.chojo.ember.feature.quiz.service.QuizQuestionService;
import dev.chojo.ember.util.Json;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Local catalog-scoped quiz endpoints: catalogs and their station-wide categories, the
 * training view, catalog import/export and the CSV import.
 */
@Singleton
public class QuizCatalogRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(QuizCatalogRoutes.class);

    private final QuizCatalogService catalogService;
    private final QuizQuestionService questionService;
    private final QuizFederationService federationService;
    private final QuizImportService importService;
    private final QuizRouteGuards guards;

    @Inject
    public QuizCatalogRoutes(
            QuizCatalogService catalogService,
            QuizQuestionService questionService,
            QuizFederationService federationService,
            QuizImportService importService,
            QuizRouteGuards guards) {
        this.catalogService = catalogService;
        this.questionService = questionService;
        this.federationService = federationService;
        this.importService = importService;
        this.guards = guards;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/quiz/catalogs",
                this::listCatalogs,
                StationPermission.TEST_CATALOG_VIEW,
                StationPermission.TEST_RESULT_READ);
        routes.post(prefix + "/quiz/catalogs", this::createCatalog, StationPermission.TEST_CATALOG_EDIT);
        routes.get(
                prefix + "/quiz/catalogs/{id}",
                this::getCatalog,
                StationPermission.TEST_CATALOG_VIEW,
                StationPermission.TEST_RESULT_READ);
        routes.put(prefix + "/quiz/catalogs/{id}", this::updateCatalog, StationPermission.TEST_CATALOG_EDIT);
        routes.delete(prefix + "/quiz/catalogs/{id}", this::deleteCatalog, StationPermission.TEST_CATALOG_EDIT);

        routes.get(prefix + "/quiz/categories", this::listCategories, StationPermission.TEST_CATALOG_VIEW);
        routes.post(prefix + "/quiz/categories", this::createCategory, StationPermission.TEST_CATALOG_EDIT);
        routes.put(prefix + "/quiz/categories/{id}", this::updateCategory, StationPermission.TEST_CATALOG_EDIT);
        routes.delete(prefix + "/quiz/categories/{id}", this::deleteCategory, StationPermission.TEST_CATALOG_EDIT);

        routes.get(prefix + "/quiz/training/catalogs", this::listTrainingCatalogs, StationPermission.USER);
        routes.get(
                prefix + "/quiz/training/catalogs/{id}/questions", this::getTrainingQuestions, StationPermission.USER);

        routes.get(prefix + "/quiz/catalogs/{id}/export", this::exportCatalog, StationPermission.TEST_CATALOG_EDIT);
        routes.post(prefix + "/quiz/catalogs/import", this::importCatalog, StationPermission.TEST_CATALOG_EDIT);

        routes.post(prefix + "/quiz/catalogs/{id}/import-csv", this::importCsv, StationPermission.TEST_CATALOG_EDIT);
    }

    /**
     * Lists the station's own catalogs plus the ones federated partners share. Reachable
     * with either catalog-view or result-read permission: the test detail page resolves
     * catalog names by id, and a reviewer evaluating attempts holds only the latter.
     */
    private void listCatalogs(Context ctx) {
        var session = UserSession.from(ctx);
        var catalogs = catalogService.findCatalogs(session.stationId());
        ctx.json(new CatalogListResponse(catalogs, federationService.browseSharedCatalogs(session.stationId())));
    }

    private void getCatalog(Context ctx) {
        int id = pathInt(ctx, "id");
        catalogService
                .findCatalog(id)
                .ifPresentOrElse(
                        catalog -> {
                            var questions = questionService.findQuestions(id);
                            var categories = catalogService.findCategories(catalog.stationId());
                            var typeCounts = new LinkedHashMap<String, Integer>();
                            for (var q : questions) {
                                typeCounts.merge(q.quizQuestionType().name(), 1, Integer::sum);
                            }
                            ctx.json(new CatalogDetail(
                                    catalog.id(),
                                    catalog.stationId(),
                                    catalog.name(),
                                    catalog.description(),
                                    catalog.trainingEnabled(),
                                    questions.size(),
                                    typeCounts,
                                    categories,
                                    catalog.createdAt(),
                                    catalog.updatedAt()));
                        },
                        () -> {
                            throw new NotFoundResponse();
                        });
    }

    private void createCatalog(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CatalogRequest.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        var catalog = catalogService.createCatalog(
                session.stationId(),
                req.name(),
                req.description() != null ? req.description() : "",
                req.trainingEnabled() != null && req.trainingEnabled());
        ctx.status(HttpStatus.CREATED).json(catalog);
    }

    private void updateCatalog(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedCatalog(ctx, id);
        var req = ctx.bodyAsClass(CatalogRequest.class);
        if (!catalogService.updateCatalog(
                id,
                req.name(),
                req.description() != null ? req.description() : "",
                req.trainingEnabled() != null && req.trainingEnabled())) {
            throw new NotFoundResponse();
        }
        catalogService.findCatalog(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void deleteCatalog(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedCatalog(ctx, id);
        if (catalogService.deleteCatalog(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    private void listCategories(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(catalogService.findCategories(session.stationId()));
    }

    private void createCategory(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CategoryRequest.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        ctx.status(HttpStatus.CREATED)
                .json(catalogService.createCategory(
                        session.stationId(),
                        req.name(),
                        req.description() != null ? req.description() : "",
                        req.position() != null ? req.position() : 0));
    }

    private void updateCategory(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedCategory(ctx, id);
        var req = ctx.bodyAsClass(CategoryRequest.class);
        if (!catalogService.updateCategory(
                id,
                req.name(),
                req.description() != null ? req.description() : "",
                req.position() != null ? req.position() : 0)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.OK).json(new QuizSuccessResponse(true));
    }

    private void deleteCategory(Context ctx) {
        int id = pathInt(ctx, "id");
        guards.requireOwnedCategory(ctx, id);
        if (catalogService.deleteCategory(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    private void listTrainingCatalogs(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(catalogService.findTrainingCatalogs(session.stationId()));
    }

    private void getTrainingQuestions(Context ctx) {
        int catalogId = pathInt(ctx, "id");
        var catalog = guards.requireOwnedCatalog(ctx, catalogId);
        if (!catalog.trainingEnabled()) throw new ForbiddenResponse("Training not enabled for this catalog");
        ctx.json(questionService.findQuestions(catalogId));
    }

    private void exportCatalog(Context ctx) {
        int catalogId = pathInt(ctx, "id");
        var catalog = guards.requireOwnedCatalog(ctx, catalogId);
        var categories = catalogService.findCategories(catalog.stationId());
        var questions = questionService.findQuestions(catalogId);
        ctx.json(new CatalogExport(
                catalog.name(), catalog.description(), catalog.trainingEnabled(), categories, questions));
    }

    private void importCatalog(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CatalogExport.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        var catalog = catalogService.createCatalog(
                session.stationId(),
                req.name(),
                req.description() != null ? req.description() : "",
                req.trainingEnabled());

        var categoryIdMap = new HashMap<Integer, Integer>();
        if (req.categories() != null) {
            for (var cat : req.categories()) {
                var created =
                        catalogService.createCategory(catalog.id(), cat.name(), cat.description(), cat.position());
                categoryIdMap.put(cat.id(), created.id());
            }
        }

        if (req.questions() != null) {
            for (var q : req.questions()) {
                Integer newCategoryId = q.categoryId() != null ? categoryIdMap.get(q.categoryId()) : null;
                questionService.createQuestion(
                        CreateQuestionCommand.builder(catalog.id(), q.quizQuestionType(), q.title())
                                .category(newCategoryId)
                                .description(q.description())
                                .imageUrl(q.imageUrl())
                                .points(q.points())
                                .autoPoints(q.autoPoints())
                                .config(q.config())
                                .position(q.position())
                                .build());
            }
        }
        ctx.status(HttpStatus.CREATED).json(catalog);
    }

    private void importCsv(Context ctx) {
        int catalogId = pathInt(ctx, "id");
        var catalog = guards.requireOwnedCatalog(ctx, catalogId);

        var csvFile = ctx.uploadedFile("file");
        if (csvFile == null) throw new BadRequestResponse("No CSV file uploaded");

        String mappingsJson = ctx.formParam("mappings");
        if (mappingsJson == null || mappingsJson.isBlank()) throw new BadRequestResponse("mappings is required");

        CsvMappings mappings;
        try {
            mappings = Json.MAPPER.readValue(mappingsJson, CsvMappings.class);
        } catch (Exception e) {
            log.warn("Invalid mappings JSON for CSV import", e);
            throw new BadRequestResponse("Invalid mappings JSON");
        }

        String csvContent;
        try (var content = csvFile.content()) {
            csvContent = new String(content.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read CSV file for catalog {}", catalogId, e);
            throw new InternalServerErrorResponse("Failed to read CSV file");
        }

        ctx.json(importService.importCsv(catalog, csvContent, mappings));
    }

    public record CatalogRequest(String name, String description, Boolean trainingEnabled) {}

    public record CategoryRequest(String name, String description, Integer position) {}

    public record CatalogDetail(
            int id,
            int stationId,
            String name,
            String description,
            boolean trainingEnabled,
            int questionCount,
            Map<String, Integer> questionTypeCounts,
            List<QuizCategory> categories,
            Instant createdAt,
            Instant updatedAt) {}

    public record CatalogExport(
            String name,
            String description,
            boolean trainingEnabled,
            List<QuizCategory> categories,
            List<QuizQuestion> questions) {}

    private record CatalogListResponse(List<QuizCatalog> catalogs, List<SharedQuizCatalog> sharedCatalogs) {}
}
