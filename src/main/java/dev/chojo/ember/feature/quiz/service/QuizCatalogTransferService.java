/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.feature.quiz.entity.CatalogMetadata;
import dev.chojo.ember.feature.quiz.entity.CatalogTransfer;
import dev.chojo.ember.feature.quiz.entity.CatalogTransfer.CatalogInfo;
import dev.chojo.ember.feature.quiz.entity.CatalogTransfer.CategoryEntry;
import dev.chojo.ember.feature.quiz.entity.CatalogTransfer.QuestionEntry;
import dev.chojo.ember.feature.quiz.entity.CreateQuestionCommand;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.util.Json;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads and writes the catalog transfer file: the shape a catalog leaves one station in and
 * arrives at another in.
 *
 * <p>An import writes nothing until the whole file has been read. A file assembled by hand tends
 * to be wrong in several places at once, and correcting it one rejected line per attempt is worse
 * than being handed the whole list. So the file is planned first, every problem is collected, and
 * only a file with none of them creates a catalog.
 */
@Singleton
public class QuizCatalogTransferService {
    private static final Logger log = LoggerFactory.getLogger(QuizCatalogTransferService.class);
    private static final String FALLBACK_CATEGORY_KEY = "category";

    private final QuizCatalogService catalogService;
    private final QuizQuestionService questionService;

    @Inject
    public QuizCatalogTransferService(QuizCatalogService catalogService, QuizQuestionService questionService) {
        this.catalogService = catalogService;
        this.questionService = questionService;
    }

    /**
     * Writes a catalog out. Only the categories its own questions use travel with it: categories
     * belong to the station, and the ones this catalog never touches are none of the receiving
     * station's business.
     */
    public CatalogTransfer export(QuizCatalog catalog) {
        var questions = questionService.findQuestions(catalog.id());
        var used = questions.stream()
                .map(QuizQuestion::categoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        var keysById = new HashMap<Integer, String>();
        var taken = new HashSet<String>();
        var categories = new ArrayList<CategoryEntry>();
        for (var category : catalogService.findCategories(catalog.stationId())) {
            if (!used.contains(category.id())) continue;
            String key = uniqueKey(category.name(), taken);
            keysById.put(category.id(), key);
            categories.add(new CategoryEntry(key, category.name(), category.description(), category.position()));
        }

        var entries = questions.stream()
                .map(question -> new QuestionEntry(
                        keysById.get(question.categoryId()),
                        question.quizQuestionType().name(),
                        question.title(),
                        question.description(),
                        question.imageUrl(),
                        question.points(),
                        question.autoPoints(),
                        configNode(question.config()),
                        question.position()))
                .toList();

        var info =
                new CatalogInfo(catalog.name(), catalog.description(), catalog.trainingEnabled(), catalog.metadata());
        return new CatalogTransfer(CatalogTransfer.FORMAT_VERSION, info, categories, entries);
    }

    /**
     * Reads an uploaded body into the transfer shape, accepting both the current file and the one
     * earlier versions wrote, which carried the catalog's fields at the top level and addressed
     * categories by the database id of the station that exported them.
     *
     * @throws BadRequestResponse when the body is not a catalog file at all
     */
    public CatalogTransfer read(JsonNode body) {
        if (body == null || !body.isObject()) throw new BadRequestResponse("The file is not a catalog export");
        if (body.has("catalog")) return readCurrent(body);
        if (body.has("name")) return readLegacy(body);
        throw new BadRequestResponse("The file is not a catalog export");
    }

    /**
     * Creates the catalog the file describes, together with the questions it carries and the
     * categories those questions actually refer to.
     *
     * @return the created catalog, or the problems that stopped anything from being created
     */
    public ImportOutcome importInto(int stationId, CatalogTransfer transfer) {
        var plan = plan(transfer, true);
        if (plan.rejected()) return plan.refusal();

        var info = transfer.catalog();
        var catalog = catalogService.createCatalog(
                stationId, info.name().trim(), info.description(), info.trainingEnabled(), info.metadata());
        int written = write(catalog, plan, 0);

        log.info("Imported quiz catalog {} into station {} with {} questions", catalog.id(), stationId, written);
        return new ImportOutcome(catalog, List.of());
    }

    /**
     * Adds the questions a file carries to a catalog that already exists, behind the ones already
     * in it. The catalog keeps its own name, description and provenance: a file appended to it
     * contributes questions, and does not get to rewrite what the catalog is.
     *
     * @return the catalog, or the problems that stopped anything from being added
     */
    public ImportOutcome appendTo(QuizCatalog catalog, CatalogTransfer transfer) {
        var plan = plan(transfer, false);
        if (plan.rejected()) return plan.refusal();

        int nextPosition = questionService.findQuestions(catalog.id()).stream()
                        .mapToInt(QuizQuestion::position)
                        .max()
                        .orElse(-1)
                + 1;
        int written = write(catalog, plan, nextPosition);

        log.info("Appended {} questions to quiz catalog {}", written, catalog.id());
        return new ImportOutcome(catalog, List.of());
    }

    private ImportPlan plan(CatalogTransfer transfer, boolean catalogNameRequired) {
        var problems = new ArrayList<TransferProblem>();
        var categoriesByKey = planCategories(transfer.categories(), problems);
        var planned = planQuestions(transfer.questions(), categoriesByKey.keySet(), problems);
        var info = transfer.catalog();

        if (transfer.formatVersion() > CatalogTransfer.FORMAT_VERSION) {
            problems.add(new TransferProblem(
                    "formatVersion", "The file was written for a newer version of Ember and cannot be read here"));
        }
        if (catalogNameRequired
                && (info == null || info.name() == null || info.name().isBlank())) {
            problems.add(new TransferProblem("catalog.name", "The file does not say what the catalog is called"));
        }
        return new ImportPlan(List.copyOf(problems), categoriesByKey, planned);
    }

    private int write(QuizCatalog catalog, ImportPlan plan, int positionOffset) {
        var resolver = new QuizCategoryResolver(catalogService, catalog.stationId());
        var categoryIds = new HashMap<String, Integer>();
        for (var question : plan.questions()) {
            if (question.categoryKey() == null) continue;
            categoryIds.computeIfAbsent(question.categoryKey(), key -> {
                var entry = plan.categories().get(key);
                return resolver.resolve(entry.name(), entry.description(), entry.position());
            });
        }

        for (var question : plan.questions()) {
            var entry = question.entry();
            questionService.createQuestion(CreateQuestionCommand.builder(
                            catalog.id(), question.type(), entry.title().trim())
                    .category(categoryIds.get(question.categoryKey()))
                    .description(entry.description())
                    .imageUrl(entry.imageUrl())
                    .points(entry.points())
                    .autoPoints(entry.autoPoints())
                    .config(question.config())
                    .position(positionOffset + question.position())
                    .build());
        }
        return plan.questions().size();
    }

    private CatalogTransfer readCurrent(JsonNode body) {
        try {
            return Json.CONFIG_MAPPER.treeToValue(body, CatalogTransfer.class);
        } catch (Exception e) {
            log.warn("Rejected a catalog file that does not fit the transfer shape", e);
            throw new BadRequestResponse("The file is not a catalog export");
        }
    }

    /**
     * Reads the shape earlier versions exported. Its categories carry the database id they had at
     * the station that wrote the file, which becomes the key the questions of that same file refer
     * to. The id itself is never used to look anything up here.
     */
    private CatalogTransfer readLegacy(JsonNode body) {
        var info = new CatalogInfo(
                body.path("name").asString(null),
                body.path("description").asString(""),
                body.path("trainingEnabled").asBoolean(false),
                CatalogMetadata.none());

        var categories = new ArrayList<CategoryEntry>();
        for (var node : body.path("categories")) {
            categories.add(new CategoryEntry(
                    legacyKey(node.path("id")),
                    node.path("name").asString(null),
                    node.path("description").asString(""),
                    node.path("position").asInt(0)));
        }

        var questions = new ArrayList<QuestionEntry>();
        for (var node : body.path("questions")) {
            questions.add(new QuestionEntry(
                    legacyKey(node.path("categoryId")),
                    node.path("quizQuestionType").asString(null),
                    node.path("title").asString(null),
                    node.path("description").asString(""),
                    node.path("imageUrl").asString(null),
                    node.has("points") ? node.path("points").asDouble() : null,
                    node.has("autoPoints") ? node.path("autoPoints").asBoolean() : null,
                    node.get("config"),
                    node.has("position") ? node.path("position").asInt() : null));
        }
        return new CatalogTransfer(CatalogTransfer.FORMAT_VERSION, info, categories, questions);
    }

    private static String legacyKey(JsonNode id) {
        return id == null || id.isNull() || id.isMissingNode() ? null : id.asString();
    }

    private Map<String, CategoryEntry> planCategories(List<CategoryEntry> entries, List<TransferProblem> problems) {
        var byKey = new LinkedHashMap<String, CategoryEntry>();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            String location = "categories[%d]".formatted(i);
            if (entry.key() == null || entry.key().isBlank()) {
                problems.add(new TransferProblem(location, "The category has no key for questions to refer to"));
                continue;
            }
            if (entry.name() == null || entry.name().isBlank()) {
                problems.add(new TransferProblem(location, "The category has no name"));
                continue;
            }
            if (byKey.putIfAbsent(entry.key(), entry) != null) {
                problems.add(new TransferProblem(
                        location, "Another category already uses the key %s".formatted(entry.key())));
            }
        }
        return byKey;
    }

    private List<PlannedQuestion> planQuestions(
            List<QuestionEntry> entries, Set<String> categoryKeys, List<TransferProblem> problems) {
        var planned = new ArrayList<PlannedQuestion>();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            String location = "questions[%d]".formatted(i);

            if (entry.title() == null || entry.title().isBlank()) {
                problems.add(new TransferProblem(location, "The question has no text"));
                continue;
            }
            var type = questionType(entry.quizQuestionType());
            if (type == null) {
                problems.add(new TransferProblem(
                        location, "%s is not a question type Ember knows".formatted(entry.quizQuestionType())));
                continue;
            }
            var config = type.readConfig(configText(entry.config()));
            if (config.isEmpty()) {
                problems.add(new TransferProblem(
                        location, "The answers do not fit a question of type %s".formatted(type.name())));
                continue;
            }
            if (entry.categoryKey() != null && !categoryKeys.contains(entry.categoryKey())) {
                problems.add(new TransferProblem(
                        location, "No category in the file has the key %s".formatted(entry.categoryKey())));
                continue;
            }
            planned.add(new PlannedQuestion(
                    entry.categoryKey(), type, config.get(), entry, entry.position() != null ? entry.position() : i));
        }
        return planned;
    }

    private static QuizQuestionType questionType(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return QuizQuestionType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String configText(JsonNode config) {
        return config == null || config.isNull() ? "{}" : config.toString();
    }

    private static JsonNode configNode(QuestionConfig config) {
        return Json.EMPTY_TOLERANT_CONFIG_MAPPER.valueToTree(config);
    }

    /**
     * Builds the key a category travels under from its name, so a file stays readable to whoever
     * opens it. Accents are folded away and anything that is not a letter or a digit becomes a
     * hyphen; two categories whose names reduce to the same key are told apart by a number.
     */
    private static String uniqueKey(String name, Set<String> taken) {
        String folded = Normalizer.normalize(name.replace("ß", "ss"), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        String base = folded.isEmpty() ? FALLBACK_CATEGORY_KEY : folded;
        String key = base;
        int suffix = 2;
        while (!taken.add(key)) {
            key = base + "-" + suffix++;
        }
        return key;
    }

    /**
     * One reason a file was refused, named so the person who wrote it can find the place.
     *
     * @param location where in the file it sits, as {@code questions[4]} or {@code catalog.name}
     * @param message  what is wrong with it
     */
    public record TransferProblem(String location, String message) {}

    /**
     * @param catalog  the created catalog, or {@code null} when the file was refused
     * @param problems every reason the file was refused, empty when it was not
     */
    public record ImportOutcome(QuizCatalog catalog, List<TransferProblem> problems) {}

    private record PlannedQuestion(
            String categoryKey, QuizQuestionType type, QuestionConfig config, QuestionEntry entry, int position) {}

    /**
     * A file read through in full before anything is written: the problems it carries, the
     * categories its questions may refer to, and the questions themselves.
     */
    private record ImportPlan(
            List<TransferProblem> problems, Map<String, CategoryEntry> categories, List<PlannedQuestion> questions) {

        private boolean rejected() {
            return !problems.isEmpty();
        }

        private ImportOutcome refusal() {
            return new ImportOutcome(null, problems);
        }
    }
}
