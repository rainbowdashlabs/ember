/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.quiz.entity.CatalogMetadata;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCatalogTemplate;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.service.QuizCatalogService;
import dev.chojo.ember.feature.quiz.service.QuizCatalogTransferService;
import dev.chojo.ember.feature.quiz.service.QuizCatalogTransferService.TransferProblem;
import dev.chojo.ember.feature.quiz.service.QuizFederationService;
import dev.chojo.ember.feature.quiz.service.QuizFederationService.SharedQuizCatalog;
import dev.chojo.ember.feature.quiz.service.QuizImportService;
import dev.chojo.ember.feature.quiz.service.QuizImportService.CsvMappings;
import dev.chojo.ember.feature.quiz.service.QuizQuestionService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Local catalog-scoped quiz endpoints: catalogs and their station-wide categories, the training
 * view, the export, the two ways a file is imported, the reading of a sheet into a draft, and the
 * example files that document both formats.
 */
@Singleton
public class QuizCatalogRoutes implements Routes {

    private final QuizCatalogService catalogService;
    private final QuizQuestionService questionService;
    private final QuizFederationService federationService;
    private final QuizImportService importService;
    private final QuizCatalogTransferService transferService;
    private final QuizRouteGuards guards;

    @Inject
    public QuizCatalogRoutes(
            QuizCatalogService catalogService,
            QuizQuestionService questionService,
            QuizFederationService federationService,
            QuizImportService importService,
            QuizCatalogTransferService transferService,
            QuizRouteGuards guards) {
        this.catalogService = catalogService;
        this.questionService = questionService;
        this.federationService = federationService;
        this.importService = importService;
        this.transferService = transferService;
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
        routes.post(prefix + "/quiz/catalogs/{id}/import", this::appendToCatalog, StationPermission.TEST_CATALOG_EDIT);
        routes.post(prefix + "/quiz/catalogs/csv-draft", this::draftFromCsv, StationPermission.TEST_CATALOG_EDIT);
        routes.get(
                prefix + "/quiz/catalogs/template/{format}",
                this::downloadTemplate,
                StationPermission.TEST_CATALOG_EDIT);
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
                                    catalog.metadata(),
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
                req.trainingEnabled() != null && req.trainingEnabled(),
                CatalogMetadata.orNone(req.metadata()));
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
                req.trainingEnabled() != null && req.trainingEnabled(),
                CatalogMetadata.orNone(req.metadata()))) {
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
        var catalog = guards.requireOwnedCatalog(ctx, pathInt(ctx, "id"));
        ctx.json(transferService.export(catalog));
    }

    /**
     * Creates a catalog from an uploaded file. A file with anything wrong in it is answered with
     * every problem at once and creates nothing, so the person correcting it sees the whole list
     * rather than the first line that failed.
     */
    private void importCatalog(Context ctx) {
        var session = UserSession.from(ctx);
        var transfer = transferService.read(ctx.bodyAsClass(JsonNode.class));
        var outcome = transferService.importInto(session.stationId(), transfer);
        if (!outcome.problems().isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST).json(new ImportRejected(outcome.problems()));
            return;
        }
        ctx.status(HttpStatus.CREATED).json(outcome.catalog());
    }

    /**
     * Adds the questions of an uploaded file to a catalog that already exists. Refused the same way
     * and for the same reasons as creating one, except that the file need not name a catalog.
     */
    private void appendToCatalog(Context ctx) {
        var catalog = guards.requireOwnedCatalog(ctx, pathInt(ctx, "id"));
        var transfer = transferService.read(ctx.bodyAsClass(JsonNode.class));
        var outcome = transferService.appendTo(catalog, transfer);
        if (!outcome.problems().isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST).json(new ImportRejected(outcome.problems()));
            return;
        }
        ctx.json(outcome.catalog());
    }

    /**
     * Reads an uploaded sheet into the same shape a catalog file has, without writing anything.
     * The wizard shows what came out, lets it be corrected, and sends the result back to one of the
     * two import endpoints, so what was confirmed on screen is what is created.
     */
    private void draftFromCsv(Context ctx) {
        var request = ctx.bodyAsClass(CsvDraftRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        if (request.mappings() == null) throw new BadRequestResponse("mappings is required");
        ctx.json(importService.draft(request.content(), request.mappings()));
    }

    /**
     * Hands out the example file the format panel and the help centre describe, so somebody
     * building their own starts from something that already imports rather than from a page of
     * prose about what the fields mean.
     */
    @StationFree("the parameter is a file format, not a row; the example file is the same for every station")
    private void downloadTemplate(Context ctx) {
        var template = QuizCatalogTemplate.byFormat(ctx.pathParam("format"));
        if (template == null) throw new NotFoundResponse();
        ctx.contentType(template.contentType())
                .header("Content-Disposition", "attachment; filename=\"" + template.fileName() + "\"")
                .result(template.read());
    }

    public record CatalogRequest(String name, String description, Boolean trainingEnabled, CatalogMetadata metadata) {}

    /**
     * @param problems every reason the uploaded file was refused
     */
    public record ImportRejected(List<TransferProblem> problems) {}

    /**
     * @param content  the decoded sheet
     * @param mappings which column carries which field, plus the parsing separators
     */
    public record CsvDraftRequest(String content, CsvMappings mappings) {}

    public record CategoryRequest(String name, String description, Integer position) {}

    public record CatalogDetail(
            int id,
            int stationId,
            String name,
            String description,
            boolean trainingEnabled,
            CatalogMetadata metadata,
            int questionCount,
            Map<String, Integer> questionTypeCounts,
            List<QuizCategory> categories,
            Instant createdAt,
            Instant updatedAt) {}

    private record CatalogListResponse(List<QuizCatalog> catalogs, List<SharedQuizCatalog> sharedCatalogs) {}
}
