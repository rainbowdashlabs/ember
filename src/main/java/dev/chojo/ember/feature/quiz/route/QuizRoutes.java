/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederatedContentService;
import dev.chojo.ember.feature.media.service.ImageCategory;
import dev.chojo.ember.feature.media.service.ImageService;
import dev.chojo.ember.feature.quiz.entity.AttemptStatus;
import dev.chojo.ember.feature.quiz.entity.QuestionType;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTest;
import dev.chojo.ember.feature.quiz.entity.QuizTestAnswer;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttempt;
import dev.chojo.ember.feature.quiz.entity.QuizTestAttemptQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizTestSectionSource;
import dev.chojo.ember.feature.quiz.entity.SectionEntry;
import dev.chojo.ember.feature.quiz.entity.SourceEntry;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
import dev.chojo.ember.feature.quiz.service.QuizPdfService;
import dev.chojo.ember.feature.quiz.service.QuizService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.CsvParser;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Singleton
public class QuizRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(QuizRoutes.class);
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final QuizService quizService;
    private final QuizPdfService pdfService;
    private final ImageService imageService;
    private final FederatedContentService federatedContentService;
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final Api apiConfig;

    @Inject
    public QuizRoutes(
            QuizService quizService,
            QuizPdfService pdfService,
            ImageService imageService,
            FederatedContentService federatedContentService,
            FederationRepository federationRepository,
            StationRepository stationRepository,
            Api apiConfig) {
        this.quizService = quizService;
        this.pdfService = pdfService;
        this.imageService = imageService;
        this.federatedContentService = federatedContentService;
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.apiConfig = apiConfig;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Catalogs
        routes.get(prefix + "/quiz/catalogs", this::listCatalogs, Roles.QUIZ_MANAGER);
        routes.post(prefix + "/quiz/catalogs", this::createCatalog, Roles.QUIZ_MANAGER);
        routes.get(prefix + "/quiz/catalogs/{id}", this::getCatalog, Roles.QUIZ_MANAGER);
        routes.put(prefix + "/quiz/catalogs/{id}", this::updateCatalog, Roles.QUIZ_MANAGER);
        routes.delete(prefix + "/quiz/catalogs/{id}", this::deleteCatalog, Roles.QUIZ_MANAGER);

        // Categories (station-scoped, shared across catalogs)
        routes.get(prefix + "/quiz/categories", this::listCategories, Roles.QUIZ_MANAGER);
        routes.post(prefix + "/quiz/categories", this::createCategory, Roles.QUIZ_MANAGER);
        routes.put(prefix + "/quiz/categories/{id}", this::updateCategory, Roles.QUIZ_MANAGER);
        routes.delete(prefix + "/quiz/categories/{id}", this::deleteCategory, Roles.QUIZ_MANAGER);

        // Questions
        routes.get(prefix + "/quiz/catalogs/{id}/questions", this::listQuestions, Roles.QUIZ_MANAGER);
        routes.post(prefix + "/quiz/catalogs/{id}/questions", this::createQuestion, Roles.QUIZ_MANAGER);
        routes.get(prefix + "/quiz/questions/{id}", this::getQuestion, Roles.USER);
        routes.put(prefix + "/quiz/questions/{id}", this::updateQuestion, Roles.QUIZ_MANAGER);
        routes.delete(prefix + "/quiz/questions/{id}", this::deleteQuestion, Roles.QUIZ_MANAGER);

        // Tests
        routes.get(prefix + "/quiz/tests", this::listTests, Roles.QUIZ_MANAGER);
        routes.get(prefix + "/quiz/tests/available", this::listAvailableTests, Roles.USER);
        routes.post(prefix + "/quiz/tests", this::createTest, Roles.QUIZ_MANAGER);
        routes.get(prefix + "/quiz/tests/{id}", this::getTest, Roles.USER);
        routes.put(prefix + "/quiz/tests/{id}", this::updateTest, Roles.QUIZ_MANAGER);
        routes.delete(prefix + "/quiz/tests/{id}", this::deleteTest, Roles.QUIZ_MANAGER);
        routes.post(prefix + "/quiz/tests/{id}/activate", this::activateTest, Roles.QUIZ_MANAGER);
        routes.post(prefix + "/quiz/tests/{id}/close", this::closeTest, Roles.QUIZ_MANAGER);
        routes.post(prefix + "/quiz/tests/{id}/generate-questions", this::generateFrozenQuestions, Roles.QUIZ_MANAGER);
        routes.get(prefix + "/quiz/tests/{id}/frozen-questions", this::listFrozenQuestions, Roles.QUIZ_MANAGER);
        routes.put(
                prefix + "/quiz/tests/{id}/frozen-questions/{position}",
                this::replaceFrozenQuestion,
                Roles.QUIZ_MANAGER);
        routes.post(
                prefix + "/quiz/tests/{id}/frozen-questions/{position}/random",
                this::randomReplaceFrozenQuestion,
                Roles.QUIZ_MANAGER);
        routes.get(
                prefix + "/quiz/tests/{id}/available-questions", this::listAvailableReplacements, Roles.QUIZ_MANAGER);

        // Sections
        routes.get(prefix + "/quiz/tests/{id}/sections", this::listSections, Roles.QUIZ_MANAGER);
        routes.put(prefix + "/quiz/tests/{id}/sections", this::replaceSections, Roles.QUIZ_MANAGER);

        // Test taking
        routes.post(prefix + "/quiz/tests/{id}/start", this::startAttempt, Roles.USER);
        routes.get(prefix + "/quiz/tests/{id}/my-attempt", this::getMyAttempt, Roles.USER);
        routes.post(prefix + "/quiz/attempts/{id}/answer", this::saveAnswer, Roles.USER);
        routes.post(prefix + "/quiz/attempts/{id}/submit", this::submitAttempt, Roles.USER);

        // Grading
        routes.get(prefix + "/quiz/tests/{id}/attempts", this::listAttempts, Roles.QUIZ_MANAGER);
        routes.get(prefix + "/quiz/attempts/{id}", this::getAttemptDetail, Roles.QUIZ_MANAGER);
        routes.post(prefix + "/quiz/answers/{id}/grade", this::gradeAnswer, Roles.QUIZ_MANAGER);
        routes.post(prefix + "/quiz/attempts/{id}/grade", this::gradeAttempt, Roles.QUIZ_MANAGER);

        // Restrictions
        routes.get(prefix + "/quiz/tests/{id}/restrictions", this::getRestrictions, Roles.QUIZ_MANAGER);
        routes.put(prefix + "/quiz/tests/{id}/restrictions", this::setRestrictions, Roles.QUIZ_MANAGER);

        // Member access
        routes.post(prefix + "/quiz/tests/{id}/access", this::grantAccess, Roles.QUIZ_MANAGER);
        routes.delete(prefix + "/quiz/tests/{testId}/access/{memberId}", this::revokeAccess, Roles.QUIZ_MANAGER);

        // Training
        routes.get(prefix + "/quiz/training/catalogs", this::listTrainingCatalogs, Roles.USER);
        routes.get(prefix + "/quiz/training/catalogs/{id}/questions", this::getTrainingQuestions, Roles.USER);

        // Question images
        routes.get(prefix + "/quiz/questions/{id}/image", this::getQuestionImage, Roles.USER);
        routes.post(prefix + "/quiz/questions/{id}/image", this::uploadQuestionImage, Roles.QUIZ_MANAGER);
        routes.delete(prefix + "/quiz/questions/{id}/image", this::deleteQuestionImage, Roles.QUIZ_MANAGER);

        // PDF export
        routes.get(prefix + "/quiz/tests/{id}/export/questions", this::exportQuestionPdf, Roles.QUIZ_MANAGER);
        routes.get(prefix + "/quiz/tests/{id}/export/solutions", this::exportSolutionPdf, Roles.QUIZ_MANAGER);

        // Import/Export
        routes.get(prefix + "/quiz/catalogs/{id}/export", this::exportCatalog, Roles.QUIZ_MANAGER);
        routes.post(prefix + "/quiz/catalogs/import", this::importCatalog, Roles.QUIZ_MANAGER);

        // CSV Import
        routes.post(prefix + "/quiz/catalogs/{id}/import-csv", this::importCsv, Roles.QUIZ_MANAGER);

        // Federated (user-facing, bearer token auth)
        routes.get(prefix + "/federated/quiz/catalogs", this::federatedBrowseCatalogs, Roles.USER);
        routes.get(
                prefix + "/federated/{stationuid}/quiz/catalogs/{id}", this::federatedGetCatalog, Roles.QUIZ_MANAGER);
        routes.post(prefix + "/federated/quiz/catalogs/{id}/copy", this::federatedCopyCatalog, Roles.QUIZ_MANAGER);
        routes.post(
                prefix + "/federated/{stationuid}/quiz/catalogs/{id}/copy",
                this::federatedCopyCatalog,
                Roles.QUIZ_MANAGER);

        // Remote (server-to-server, RSA signature auth)
        routes.get(prefix + "/remote/quiz/catalogs", this::remoteBrowseCatalogs);
        routes.get(prefix + "/remote/quiz/catalogs/{id}", this::remoteGetCatalog);
    }

    // -- Catalogs --

    private void listCatalogs(Context ctx) {
        var session = UserSession.from(ctx);
        var catalogs = quizService.findCatalogs(session.stationId());
        var sharedItems = federatedContentService.browseSharedQuiz(session.stationId());
        var sharedCatalogs = sharedItems.stream()
                .map(i -> {
                    String name = stationRepository
                            .findById(i.sourceStationId())
                            .map(Station::name)
                            .orElse("Unknown");
                    return new SharedCatalogItem(i.id(), i.name(), i.description(), name, i.sourceStationId());
                })
                .toList();
        ctx.json(new CatalogListResponse(catalogs, sharedCatalogs));
    }

    private void getCatalog(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        quizService
                .findCatalog(id)
                .ifPresentOrElse(
                        catalog -> {
                            var questions = quizService.findQuestions(id);
                            var categories = quizService.findCategories(catalog.stationId());
                            var typeCounts = new LinkedHashMap<String, Integer>();
                            for (var q : questions) {
                                typeCounts.merge(q.questionType().name(), 1, Integer::sum);
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
        var catalog = quizService.createCatalog(
                session.stationId(),
                req.name(),
                req.description() != null ? req.description() : "",
                req.trainingEnabled() != null && req.trainingEnabled());
        ctx.status(HttpStatus.CREATED).json(catalog);
    }

    private void updateCatalog(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var catalog = quizService.findCatalog(id).orElseThrow(NotFoundResponse::new);
        if (catalog.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot modify a catalog from another station");
        }
        var req = ctx.bodyAsClass(CatalogRequest.class);
        if (!quizService.updateCatalog(
                id,
                req.name(),
                req.description() != null ? req.description() : "",
                req.trainingEnabled() != null && req.trainingEnabled())) {
            throw new NotFoundResponse();
        }
        quizService.findCatalog(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void deleteCatalog(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var catalog = quizService.findCatalog(id).orElseThrow(NotFoundResponse::new);
        if (catalog.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot delete a catalog from another station");
        }
        if (quizService.deleteCatalog(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Categories --

    private void listCategories(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(quizService.findCategories(session.stationId()));
    }

    private void createCategory(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CategoryRequest.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        ctx.status(HttpStatus.CREATED)
                .json(quizService.createCategory(
                        session.stationId(),
                        req.name(),
                        req.description() != null ? req.description() : "",
                        req.position() != null ? req.position() : 0));
    }

    private void updateCategory(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var category = quizService.findCategory(id).orElseThrow(NotFoundResponse::new);
        if (category.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot modify a category from another station");
        }
        var req = ctx.bodyAsClass(CategoryRequest.class);
        if (!quizService.updateCategory(
                id,
                req.name(),
                req.description() != null ? req.description() : "",
                req.position() != null ? req.position() : 0)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.OK).json(Map.of("success", true));
    }

    private void deleteCategory(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var category = quizService.findCategory(id).orElseThrow(NotFoundResponse::new);
        if (category.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot delete a category from another station");
        }
        if (quizService.deleteCategory(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Questions --

    private void listQuestions(Context ctx) {
        int catalogId = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(quizService.findQuestions(catalogId));
    }

    private void getQuestion(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var question = quizService.findQuestion(id).orElseThrow(NotFoundResponse::new);
        if (session.roles().contains(Roles.QUIZ_MANAGER)) {
            ctx.json(question);
        } else {
            ctx.json(sanitizeQuestion(question));
        }
    }

    private Map<String, Object> sanitizeQuestion(QuizQuestion question) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", question.id());
        result.put("catalogId", question.catalogId());
        result.put("categoryId", question.categoryId());
        result.put("questionType", question.questionType().name());
        result.put("title", question.title());
        result.put("description", question.description());
        result.put("imageUrl", question.imageUrl());
        result.put("points", question.points());
        result.put("position", question.position());
        result.put("config", sanitizeConfig(question.questionType(), question.configNode()));
        return result;
    }

    @SuppressWarnings("unchecked")
    private String sanitizeConfig(QuestionType type, JsonNode node) {
        try {
            var mapper = new ObjectMapper();
            return switch (type) {
                case MULTIPLE_CHOICE -> {
                    var options = node.get("options");
                    var sanitized = new ArrayList<Map<String, Object>>();
                    if (options != null && options.isArray()) {
                        for (var opt : options) {
                            sanitized.add(Map.of("text", opt.get("text").asString()));
                        }
                    }
                    yield mapper.writeValueAsString(Map.of("options", sanitized));
                }
                case TRUE_FALSE -> "{}";
                case FREE_ANSWER -> {
                    int lines = node.has("lines") ? node.get("lines").asInt() : 3;
                    yield mapper.writeValueAsString(Map.of("lines", lines));
                }
                case FILL_IN_THE_BLANK -> {
                    var answers = new ArrayList<String>();
                    var distractors = new ArrayList<String>();
                    if (node.has("answers") && node.get("answers").isArray())
                        for (var a : node.get("answers")) answers.add(a.asString());
                    if (node.has("distractors") && node.get("distractors").isArray())
                        for (var d : node.get("distractors")) distractors.add(d.asString());
                    var wordBank = new ArrayList<>(answers);
                    wordBank.addAll(distractors);
                    Collections.shuffle(wordBank);
                    String text = node.has("text") ? node.get("text").asString() : "";
                    boolean useDropdown =
                            node.has("useDropdown") && node.get("useDropdown").asBoolean();
                    yield mapper.writeValueAsString(Map.of(
                            "text", text,
                            "wordBank", wordBank,
                            "gapCount", answers.size(),
                            "useDropdown", useDropdown));
                }
                case CONNECT -> {
                    var pairs = node.get("pairs");
                    var leftItems = new ArrayList<String>();
                    var rightItems = new ArrayList<String>();
                    if (pairs != null && pairs.isArray()) {
                        for (var pair : pairs) {
                            leftItems.add(pair.get("left").asString());
                            rightItems.add(pair.get("right").asString());
                        }
                    }
                    Collections.shuffle(rightItems);
                    yield mapper.writeValueAsString(Map.of("leftItems", leftItems, "rightItems", rightItems));
                }
                case ORDERING -> {
                    var items = new ArrayList<String>();
                    if (node.has("items") && node.get("items").isArray())
                        for (var item : node.get("items")) items.add(item.asString());
                    Collections.shuffle(items);
                    yield mapper.writeValueAsString(Map.of("items", items));
                }
                case IMAGE_TEXT -> "{}";
                case ENUMERATION -> {
                    int requiredCount = node.has("requiredCount")
                            ? node.get("requiredCount").asInt()
                            : 3;
                    yield mapper.writeValueAsString(Map.of("requiredCount", requiredCount));
                }
            };
        } catch (Exception e) {
            return "{}";
        }
    }

    private void createQuestion(Context ctx) {
        int catalogId = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(QuestionRequest.class);
        if (req.title() == null || req.title().isBlank()) throw new BadRequestResponse("title is required");
        if (req.questionType() == null) throw new BadRequestResponse("questionType is required");
        var question = quizService.createQuestion(
                catalogId,
                req.categoryId(),
                req.questionType(),
                req.title(),
                req.description() != null ? req.description() : "",
                req.imageUrl(),
                req.points() != null ? req.points() : 1.0,
                req.autoPoints() == null || req.autoPoints(),
                req.questionType().parseConfig(req.configString()),
                req.position() != null ? req.position() : 0);
        ctx.status(HttpStatus.CREATED).json(question);
    }

    private void updateQuestion(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var question = quizService.findQuestion(id).orElseThrow(NotFoundResponse::new);
        var catalog = quizService.findCatalog(question.catalogId()).orElseThrow(NotFoundResponse::new);
        if (catalog.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot modify a question from another station");
        }
        var req = ctx.bodyAsClass(QuestionRequest.class);
        if (!quizService.updateQuestion(
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
        quizService.findQuestion(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void deleteQuestion(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var question = quizService.findQuestion(id).orElseThrow(NotFoundResponse::new);
        var catalog = quizService.findCatalog(question.catalogId()).orElseThrow(NotFoundResponse::new);
        if (catalog.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot delete a question from another station");
        }
        if (quizService.deleteQuestion(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Tests --

    private void listTests(Context ctx) {
        var session = UserSession.from(ctx);
        // QUIZ_MANAGER sees all tests; regular members see only restriction-permitted tests
        List<QuizTest> tests;
        if (session.member() != null && !session.roles().contains(Roles.QUIZ_MANAGER)) {
            tests = quizService.findTestsForMember(
                    session.stationId(), session.member().id());
        } else {
            tests = quizService.findTests(session.stationId());
        }
        var result = tests.stream()
                .map(t -> new TestSummary(t, quizService.countAttempts(t.id())))
                .toList();
        ctx.json(result);
    }

    private void listAvailableTests(Context ctx) {
        var session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.json(List.of());
            return;
        }
        int memberId = session.member().id();
        // findTestsForMember already handles restriction filtering + manager bypass via DB function
        var tests = quizService.findTestsForMember(session.stationId(), memberId).stream()
                .filter(t -> t.status() == TestStatus.ACTIVE)
                .toList();
        ctx.json(tests);
    }

    private void getTest(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var test = quizService.findTest(id).orElseThrow(NotFoundResponse::new);
        var sections = quizService.findSections(id);
        var sectionDetails = sections.stream()
                .map(s -> {
                    var sources = quizService.findSources(s.id());
                    return new SectionDetail(s.id(), s.testId(), s.title(), s.description(), s.position(), sources);
                })
                .toList();
        ctx.json(new TestDetail(
                test, sectionDetails, quizService.findAttempts(id).size()));
    }

    private void createTest(Context ctx) {
        var session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        var req = ctx.bodyAsClass(TestRequest.class);
        if (req.title() == null || req.title().isBlank()) throw new BadRequestResponse("title is required");
        var test = quizService.createTest(
                session.stationId(),
                req.title(),
                req.description() != null ? req.description() : "",
                req.timeLimit(),
                req.shuffle() != null && req.shuffle(),
                session.member().id());
        ctx.status(HttpStatus.CREATED).json(test);
    }

    private void updateTest(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var test = quizService.findTest(id).orElseThrow(NotFoundResponse::new);
        if (test.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot modify a test from another station");
        }
        var req = ctx.bodyAsClass(TestRequest.class);
        if (!quizService.updateTest(
                id,
                req.title(),
                req.description() != null ? req.description() : "",
                req.timeLimit(),
                req.shuffle() != null && req.shuffle(),
                req.startAt(),
                req.endAt())) {
            throw new NotFoundResponse();
        }
        quizService.findTest(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void deleteTest(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var test = quizService.findTest(id).orElseThrow(NotFoundResponse::new);
        if (test.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot delete a test from another station");
        }
        if (quizService.deleteTest(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    private void activateTest(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var test = quizService.findTest(id).orElseThrow(NotFoundResponse::new);
        if (test.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot activate a test from another station");
        }
        if (test.status() != TestStatus.DRAFT) throw new BadRequestResponse("Test is not in DRAFT status");
        quizService.activateTest(id);
        quizService.findTest(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void closeTest(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var test = quizService.findTest(id).orElseThrow(NotFoundResponse::new);
        if (test.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot close a test from another station");
        }
        if (!quizService.closeTest(id)) throw new NotFoundResponse();
        quizService.findTest(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    // -- Frozen Questions --

    private void generateFrozenQuestions(Context ctx) {
        int testId = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var test = quizService.findTest(testId).orElseThrow(NotFoundResponse::new);
        if (test.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot generate questions for a test from another station");
        }
        if (test.status() == TestStatus.ACTIVE) throw new BadRequestResponse("Cannot regenerate for active test");
        quizService.generateFrozenQuestions(testId);
        ctx.json(buildFrozenQuestionResponse(testId));
    }

    private void listFrozenQuestions(Context ctx) {
        int testId = ctx.pathParamAsClass("id", Integer.class).get();
        quizService.findTest(testId).orElseThrow(NotFoundResponse::new);
        ctx.json(buildFrozenQuestionResponse(testId));
    }

    private void replaceFrozenQuestion(Context ctx) {
        int testId = ctx.pathParamAsClass("id", Integer.class).get();
        int position = ctx.pathParamAsClass("position", Integer.class).get();
        var session = UserSession.from(ctx);
        var test = quizService.findTest(testId).orElseThrow(NotFoundResponse::new);
        if (test.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot modify a test from another station");
        }
        if (test.status() == TestStatus.ACTIVE) throw new BadRequestResponse("Cannot modify active test");
        var req = ctx.bodyAsClass(ReplaceQuestionRequest.class);
        quizService.replaceFrozenQuestion(testId, position, req.questionId());
        ctx.json(buildFrozenQuestionResponse(testId));
    }

    private void randomReplaceFrozenQuestion(Context ctx) {
        int testId = ctx.pathParamAsClass("id", Integer.class).get();
        int position = ctx.pathParamAsClass("position", Integer.class).get();
        var session = UserSession.from(ctx);
        var test = quizService.findTest(testId).orElseThrow(NotFoundResponse::new);
        if (test.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot modify a test from another station");
        }
        if (test.status() == TestStatus.ACTIVE) throw new BadRequestResponse("Cannot modify active test");
        quizService.replaceWithRandomQuestion(testId, position);
        ctx.json(buildFrozenQuestionResponse(testId));
    }

    private void listAvailableReplacements(Context ctx) {
        int testId = ctx.pathParamAsClass("id", Integer.class).get();
        quizService.findTest(testId).orElseThrow(NotFoundResponse::new);
        ctx.json(quizService.findAvailableReplacements(testId));
    }

    private List<FrozenQuestionDetail> buildFrozenQuestionResponse(int testId) {
        var frozen = quizService.findFrozenQuestions(testId);
        return frozen.stream()
                .map(fq -> {
                    var question = quizService.findQuestion(fq.questionId()).orElse(null);
                    return new FrozenQuestionDetail(fq.position(), fq.sectionId(), question);
                })
                .toList();
    }

    public record ReplaceQuestionRequest(int questionId) {}

    public record FrozenQuestionDetail(int position, Integer sectionId, QuizQuestion question) {}

    // -- Sections --

    private void listSections(Context ctx) {
        int testId = ctx.pathParamAsClass("id", Integer.class).get();
        var sections = quizService.findSections(testId);
        var result = sections.stream()
                .map(s -> {
                    var sources = quizService.findSources(s.id());
                    return new SectionDetail(s.id(), s.testId(), s.title(), s.description(), s.position(), sources);
                })
                .toList();
        ctx.json(result);
    }

    private void replaceSections(Context ctx) {
        int testId = ctx.pathParamAsClass("id", Integer.class).get();
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
        quizService.replaceSections(testId, entries);
        ctx.json(quizService.findSections(testId));
    }

    // -- Test Taking --

    private void startAttempt(Context ctx) {
        int testId = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        var test = quizService.findTest(testId).orElseThrow(NotFoundResponse::new);
        int memberId = session.member().id();
        if (!quizService.isTestAccessible(test, memberId)) {
            throw new ForbiddenResponse("Test is not currently accessible");
        }
        var existing = quizService.findAttempt(testId, session.member().id());
        if (existing.isPresent()) {
            ctx.json(new AttemptDetail(
                    existing.get(),
                    quizService.findAttemptQuestions(existing.get().id()),
                    quizService.findAnswers(existing.get().id())));
            return;
        }
        var attempt = quizService.startAttempt(testId, session.member().id());
        ctx.status(HttpStatus.CREATED)
                .json(new AttemptDetail(attempt, quizService.findAttemptQuestions(attempt.id()), List.of()));
    }

    private void getMyAttempt(Context ctx) {
        int testId = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        var attempt = quizService.findAttempt(testId, session.member().id());
        if (attempt.isEmpty()) {
            ctx.json(Map.of("attempt", (Object) Map.of()));
            return;
        }
        ctx.json(new AttemptDetail(
                attempt.get(),
                quizService.findAttemptQuestions(attempt.get().id()),
                quizService.findAnswers(attempt.get().id())));
    }

    private void saveAnswer(Context ctx) {
        int attemptId = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        var attempt = quizService.findAttemptById(attemptId).orElseThrow(NotFoundResponse::new);
        if (attempt.memberId() != session.member().id()) throw new ForbiddenResponse();
        if (attempt.status() != AttemptStatus.IN_PROGRESS) {
            throw new BadRequestResponse("Attempt already submitted");
        }
        var req = ctx.bodyAsClass(AnswerRequest.class);
        quizService.saveAnswer(attemptId, req.questionId(), req.answer());
        ctx.json(Map.of("success", true));
    }

    private void submitAttempt(Context ctx) {
        int attemptId = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        var attempt = quizService.findAttemptById(attemptId).orElseThrow(NotFoundResponse::new);
        if (attempt.memberId() != session.member().id()) throw new ForbiddenResponse();
        quizService.submitAttempt(attemptId);
        quizService.findAttemptById(attemptId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    // -- Grading --

    private void listAttempts(Context ctx) {
        int testId = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(quizService.findAttempts(testId));
    }

    private void getAttemptDetail(Context ctx) {
        int attemptId = ctx.pathParamAsClass("id", Integer.class).get();
        var attempt = quizService.findAttemptById(attemptId).orElseThrow(NotFoundResponse::new);
        ctx.json(new AttemptDetail(
                attempt, quizService.findAttemptQuestions(attemptId), quizService.findAnswers(attemptId)));
    }

    private void gradeAnswer(Context ctx) {
        int answerId = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(GradeRequest.class);
        if (req.points() == null) throw new BadRequestResponse("points is required");
        quizService.gradeAnswer(answerId, req.points());
        ctx.json(Map.of("success", true));
    }

    private void gradeAttempt(Context ctx) {
        int attemptId = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        quizService.gradeAttempt(attemptId, session.member().id());
        quizService.findAttemptById(attemptId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    // -- Restrictions --

    private void getRestrictions(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        quizService.findTest(id).orElseThrow(NotFoundResponse::new);
        var restrictions = quizService.findRestrictions(id);
        ctx.json(new TestRestrictions(
                restrictions.roleIds(),
                restrictions.groupIds(),
                restrictions.tagIds(),
                restrictions.memberIds(),
                restrictions.mode()));
    }

    private void setRestrictions(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var test = quizService.findTest(id).orElseThrow(NotFoundResponse::new);
        if (test.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot modify restrictions for a test from another station");
        }
        var req = ctx.bodyAsClass(TestRestrictions.class);
        quizService.setRestrictions(
                id, req.roleIds(), req.groupIds(), req.tagIds(), req.memberIds() != null ? req.memberIds() : List.of());
        if (req.mode() != null) {
            quizService.updateRestrictionMode(id, req.mode());
        }
        ctx.json(req);
    }

    // -- Member Access --

    private void grantAccess(Context ctx) {
        int testId = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var test = quizService.findTest(testId).orElseThrow(NotFoundResponse::new);
        if (test.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot grant access to a test from another station");
        }
        var req = ctx.bodyAsClass(AccessRequest.class);
        if (req.memberId() == null) throw new BadRequestResponse("memberId is required");
        quizService.grantMemberAccess(testId, req.memberId(), req.closesAt());
        ctx.json(Map.of("success", true));
    }

    private void revokeAccess(Context ctx) {
        int testId = ctx.pathParamAsClass("testId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        var session = UserSession.from(ctx);
        var test = quizService.findTest(testId).orElseThrow(NotFoundResponse::new);
        if (test.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot revoke access to a test from another station");
        }
        quizService.revokeMemberAccess(testId, memberId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Training --

    private void listTrainingCatalogs(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(quizService.findTrainingCatalogs(session.stationId()));
    }

    private void getTrainingQuestions(Context ctx) {
        int catalogId = ctx.pathParamAsClass("id", Integer.class).get();
        var catalog = quizService.findCatalog(catalogId).orElseThrow(NotFoundResponse::new);
        if (!catalog.trainingEnabled()) throw new ForbiddenResponse("Training not enabled for this catalog");
        ctx.json(quizService.findQuestions(catalogId));
    }

    // -- PDF Export --

    private void exportQuestionPdf(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        quizService.findTest(id).orElseThrow(NotFoundResponse::new);
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        quizService.findTest(id).orElseThrow(NotFoundResponse::new);
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

    // -- Import/Export --

    private void exportCatalog(Context ctx) {
        int catalogId = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var catalog = quizService.findCatalog(catalogId).orElseThrow(NotFoundResponse::new);
        if (catalog.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot export a catalog from another station");
        }
        var categories = quizService.findCategories(catalog.stationId());
        var questions = quizService.findQuestions(catalogId);
        ctx.json(new CatalogExport(
                catalog.name(), catalog.description(), catalog.trainingEnabled(), categories, questions));
    }

    private void importCatalog(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CatalogExport.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        var catalog = quizService.createCatalog(
                session.stationId(),
                req.name(),
                req.description() != null ? req.description() : "",
                req.trainingEnabled());

        // Import categories and build ID mapping
        var categoryIdMap = new HashMap<Integer, Integer>();
        if (req.categories() != null) {
            for (var cat : req.categories()) {
                var created = quizService.createCategory(catalog.id(), cat.name(), cat.description(), cat.position());
                categoryIdMap.put(cat.id(), created.id());
            }
        }

        // Import questions
        if (req.questions() != null) {
            for (var q : req.questions()) {
                Integer newCategoryId = q.categoryId() != null ? categoryIdMap.get(q.categoryId()) : null;
                quizService.createQuestion(
                        catalog.id(),
                        newCategoryId,
                        q.questionType(),
                        q.title(),
                        q.description(),
                        q.imageUrl(),
                        q.points(),
                        q.autoPoints(),
                        q.questionType().parseConfig(q.configString()),
                        q.position());
            }
        }
        ctx.status(HttpStatus.CREATED).json(catalog);
    }

    // -- CSV Import --

    private void importCsv(Context ctx) {
        int catalogId = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var catalog = quizService.findCatalog(catalogId).orElseThrow(NotFoundResponse::new);
        if (catalog.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot import into a catalog from another station");
        }

        var csvFile = ctx.uploadedFile("file");
        if (csvFile == null) throw new BadRequestResponse("No CSV file uploaded");

        String mappingsJson = ctx.formParam("mappings");
        if (mappingsJson == null || mappingsJson.isBlank()) throw new BadRequestResponse("mappings is required");

        var mapper = new ObjectMapper();
        CsvMappings mappings;
        try {
            mappings = mapper.readValue(mappingsJson, CsvMappings.class);
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

        String sep = mappings.separator() != null ? mappings.separator() : ",";
        String answerSep = mappings.answerSeparator() != null ? mappings.answerSeparator() : ";";

        CsvParser.ParsedCsv parsed;
        try {
            parsed = CsvParser.parse(csvContent, sep.charAt(0));
        } catch (IOException e) {
            log.warn("Failed to parse CSV for catalog {}", catalogId, e);
            throw new BadRequestResponse("Failed to parse CSV");
        }

        var headerList = parsed.headers();
        int questionIdx = headerList.indexOf(mappings.questionColumn());
        int answerIdx = headerList.indexOf(mappings.answerColumn());
        int categoryIdx = headerList.indexOf(mappings.categoryColumn());
        int typeIdx = headerList.indexOf(mappings.typeColumn());
        int pointsIdx = headerList.indexOf(mappings.pointsColumn());

        if (questionIdx < 0) throw new BadRequestResponse("Question column not found in CSV headers");

        var existingCategories = quizService.findCategories(catalog.stationId());
        var categoryCache = new HashMap<String, Integer>();
        for (var cat : existingCategories) {
            categoryCache.put(cat.name().toLowerCase(), cat.id());
        }

        int imported = 0;
        for (int i = 0; i < parsed.rows().size(); i++) {
            var cells = parsed.rows().get(i);
            String title = questionIdx < cells.size() ? cells.get(questionIdx).trim() : "";
            if (title.isEmpty()) continue;

            String answer = answerIdx >= 0 && answerIdx < cells.size()
                    ? cells.get(answerIdx).trim()
                    : "";
            String categoryName = categoryIdx >= 0 && categoryIdx < cells.size()
                    ? cells.get(categoryIdx).trim()
                    : "";
            String typeStr =
                    typeIdx >= 0 && typeIdx < cells.size() ? cells.get(typeIdx).trim() : "";
            String pointsStr = pointsIdx >= 0 && pointsIdx < cells.size()
                    ? cells.get(pointsIdx).trim()
                    : "";

            // Determine question type
            QuestionType questionType = mappings.defaultType() != null
                    ? parseQuestionType(mappings.defaultType())
                    : QuestionType.MULTIPLE_CHOICE;
            if (!typeStr.isEmpty()) {
                questionType = parseQuestionType(typeStr);
            }

            // Determine points
            double points = 1;
            if (!pointsStr.isEmpty()) {
                try {
                    points = Double.parseDouble(pointsStr);
                } catch (NumberFormatException ignored) {
                }
            }

            // Find or create category
            Integer categoryId2 = null;
            if (!categoryName.isEmpty()) {
                String key = categoryName.toLowerCase();
                if (categoryCache.containsKey(key)) {
                    categoryId2 = categoryCache.get(key);
                } else {
                    var created = quizService.createCategory(
                            catalog.stationId(), categoryName, "", existingCategories.size());
                    categoryCache.put(key, created.id());
                    categoryId2 = created.id();
                }
            }

            // Build config JSON
            String config = buildCsvConfig(questionType, answer, answerSep);

            quizService.createQuestion(
                    catalogId,
                    categoryId2,
                    questionType,
                    title,
                    "",
                    null,
                    points,
                    true,
                    questionType.parseConfig(config),
                    i);
            imported++;
        }

        ctx.json(Map.of("imported", imported));
    }

    private QuestionType parseQuestionType(String type) {
        return switch (type.toUpperCase().replace(" ", "_").replace("-", "_")) {
            case "MULTIPLE_CHOICE", "MC" -> QuestionType.MULTIPLE_CHOICE;
            case "TRUE_FALSE", "TF", "WAHR_FALSCH" -> QuestionType.TRUE_FALSE;
            case "FREE_ANSWER", "FREE", "FREITEXT" -> QuestionType.FREE_ANSWER;
            case "FILL_IN_THE_BLANK", "FILL_BLANK", "LUECKENTEXT", "LÜCKENTEXT" -> QuestionType.FILL_IN_THE_BLANK;
            case "CONNECT", "ZUORDNUNG" -> QuestionType.CONNECT;
            case "ORDERING", "REIHENFOLGE" -> QuestionType.ORDERING;
            case "IMAGE_TEXT" -> QuestionType.IMAGE_TEXT;
            case "ENUMERATION", "AUFZÄHLUNG", "AUFZAEHLUNG" -> QuestionType.ENUMERATION;
            default -> QuestionType.MULTIPLE_CHOICE;
        };
    }

    private String buildCsvConfig(QuestionType type, String answer, String answerSep) {
        var mapper = new ObjectMapper();
        try {
            return switch (type) {
                case MULTIPLE_CHOICE -> {
                    var parts = Arrays.stream(answer.split(Pattern.quote(answerSep)))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList();
                    var options = new ArrayList<Map<String, Object>>();
                    for (int j = 0; j < parts.size(); j++) {
                        options.add(Map.of("text", parts.get(j), "correct", j == 0));
                    }
                    yield mapper.writeValueAsString(Map.of("options", options, "pointsPerCorrect", 0.5));
                }
                case TRUE_FALSE -> {
                    boolean correct =
                            answer.equalsIgnoreCase("true") || answer.equals("1") || answer.equalsIgnoreCase("wahr");
                    yield mapper.writeValueAsString(Map.of("correctAnswer", correct));
                }
                case FREE_ANSWER -> {
                    var answers = Arrays.stream(answer.split(Pattern.quote(answerSep)))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList();
                    yield mapper.writeValueAsString(Map.of("lines", 3, "answers", answers));
                }
                case FILL_IN_THE_BLANK -> {
                    var answers = Arrays.stream(answer.split(Pattern.quote(answerSep)))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList();
                    yield mapper.writeValueAsString(Map.of("text", "", "answers", answers, "wordBank", answers));
                }
                case CONNECT -> {
                    var pairs = Arrays.stream(answer.split(Pattern.quote(answerSep)))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(s -> {
                                var split = s.split("=", 2);
                                return Map.of(
                                        "left",
                                        (Object) split[0].trim(),
                                        "right",
                                        split.length > 1 ? split[1].trim() : "");
                            })
                            .toList();
                    yield mapper.writeValueAsString(Map.of("pairs", pairs));
                }
                case ORDERING -> {
                    var items = Arrays.stream(answer.split(Pattern.quote(answerSep)))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList();
                    yield mapper.writeValueAsString(Map.of("items", items));
                }
                case IMAGE_TEXT -> "{}";
                case ENUMERATION -> {
                    var items = Arrays.stream(answer.split(Pattern.quote(answerSep)))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList();
                    yield mapper.writeValueAsString(Map.of(
                            "answers", items, "requiredCount", Math.min(3, items.size()), "orderedRequired", false));
                }
            };
        } catch (Exception e) {
            return "{}";
        }
    }

    // -- Question Images --

    private void getQuestionImage(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
        imageService
                .read(ImageCategory.QUIZ_QUESTIONS, String.valueOf(id), size)
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        quizService.findQuestion(id).orElseThrow(NotFoundResponse::new);
        var file = ctx.uploadedFile("image");
        if (file == null) {
            throw new BadRequestResponse("No file uploaded");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(file.contentType())) {
            throw new BadRequestResponse("Invalid file type. Allowed: PNG, JPEG, WebP");
        }
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            imageService.store(
                    ImageCategory.QUIZ_QUESTIONS,
                    String.valueOf(id),
                    data,
                    file.contentType(),
                    apiConfig.maxImageSizeBytes());
            ctx.json(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument storing question image for question {}", id, e);
            throw new BadRequestResponse(e.getMessage());
        } catch (IOException e) {
            log.error("Failed to process question image for question {}", id, e);
            throw new InternalServerErrorResponse("Failed to process image");
        }
    }

    private void deleteQuestionImage(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        imageService.delete(ImageCategory.QUIZ_QUESTIONS, String.valueOf(id));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Request/Response records --

    public record CatalogRequest(String name, String description, Boolean trainingEnabled) {}

    public record CategoryRequest(String name, String description, Integer position) {}

    public record QuestionRequest(
            QuestionType questionType,
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

    public record TestRequest(
            String title, String description, Integer timeLimit, Boolean shuffle, Instant startAt, Instant endAt) {}

    public record SectionRequest(String title, String description, List<SourceRequest> sources) {}

    public record SourceRequest(int catalogId, Integer categoryId, int questionCount) {}

    public record AnswerRequest(int questionId, String answer) {}

    public record GradeRequest(Double points) {}

    public record AccessRequest(Integer memberId, Instant closesAt) {}

    public record TestRestrictions(
            List<Integer> roleIds,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds,
            RestrictionMode mode) {}

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

    public record TestSummary(QuizTest test, int attemptCount) {}

    public record TestDetail(QuizTest test, List<SectionDetail> sections, int attemptCount) {}

    public record SectionDetail(
            int id, int testId, String title, String description, int position, List<QuizTestSectionSource> sources) {}

    public record AttemptDetail(
            QuizTestAttempt attempt, List<QuizTestAttemptQuestion> questions, List<QuizTestAnswer> answers) {}

    public record CsvMappings(
            String questionColumn,
            String answerColumn,
            String categoryColumn,
            String typeColumn,
            String pointsColumn,
            String separator,
            String answerSeparator,
            String defaultType) {}

    public record CatalogExport(
            String name,
            String description,
            boolean trainingEnabled,
            List<QuizCategory> categories,
            List<QuizQuestion> questions) {}

    private record CatalogListResponse(List<QuizCatalog> catalogs, List<SharedCatalogItem> sharedCatalogs) {}

    private record SharedCatalogItem(
            int id, String name, String description, String stationName, int sourceStationId) {}

    // -- Federated endpoints (user-facing, aggregates from partners) --

    private void federatedBrowseCatalogs(Context ctx) {
        var session = UserSession.from(ctx);
        var items = federatedContentService.browseSharedQuiz(session.stationId());
        ctx.json(items.stream()
                .map(i -> {
                    String name = stationRepository
                            .findById(i.sourceStationId())
                            .map(Station::name)
                            .orElse("Unknown");
                    return new SharedCatalogItem(i.id(), i.name(), i.description(), name, i.sourceStationId());
                })
                .toList());
    }

    private void federatedGetCatalog(Context ctx) {
        var session = UserSession.from(ctx);
        var stationUid = java.util.UUID.fromString(ctx.pathParam("stationuid"));
        int catalogId = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(federatedContentService.getFederatedQuizCatalog(session.stationId(), stationUid, catalogId));
    }

    private void federatedCopyCatalog(Context ctx) {
        var session = UserSession.from(ctx);
        int catalogId = ctx.pathParamAsClass("id", Integer.class).get();
        var copied = federatedContentService.copyQuizCatalog(catalogId, session.stationId());
        ctx.status(HttpStatus.CREATED).json(copied);
    }

    // -- Remote endpoints (server-to-server, RSA signature auth) --

    private void remoteBrowseCatalogs(Context ctx) {
        var partner = requireFederationPartner(ctx);
        var shares = federationRepository.findQuizShares(partner.stationId());
        var result = shares.stream()
                .filter(s -> s.catalogId() != null)
                .flatMap(s -> quizService.findCatalog(s.catalogId()).stream())
                .filter(catalog -> catalog.stationId() == partner.stationId())
                .map(catalog -> Map.<String, Object>of(
                        "id", catalog.id(),
                        "name", catalog.name(),
                        "description", catalog.description(),
                        "updatedAt", catalog.updatedAt().toString()))
                .toList();
        ctx.json(result);
    }

    private void remoteGetCatalog(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int catalogId = ctx.pathParamAsClass("id", Integer.class).get();
        var catalog = quizService.findCatalog(catalogId).orElseThrow(NotFoundResponse::new);
        if (catalog.stationId() != partner.stationId()) {
            throw new ForbiddenResponse("Catalog not shared with this partner");
        }
        var categories = quizService.findCategories(catalog.stationId());
        var questions = quizService.findQuestions(catalog.id());
        ctx.json(Map.of("catalog", catalog, "categories", categories, "questions", questions));
    }

    // -- Federation helpers --

    private FederationPartner requireFederationPartner(Context ctx) {
        var session = FederationSession.from(ctx);
        if (session == null) {
            throw new ForbiddenResponse("Missing or invalid federation signature");
        }
        return session.partner();
    }
}
