/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.util.Json;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Questions and wrong answers written by an AI provider for a station's catalogs.
 *
 * <p>Whole questions are written in the background, one conversation per requested type so the
 * provider keeps the context across turns, and collected by polling a job. Wrong answers for the
 * multiple-choice questions of a catalog are written at once and saved onto the questions.
 * Either way the catalog has to be one of the caller's station: its titles are sent to the
 * provider, and its questions are rewritten.
 */
@Singleton
public class QuizGenerationService {
    private static final Logger log = LoggerFactory.getLogger(QuizGenerationService.class);
    private static final String DEFAULT_PROVIDER = "openai";
    private static final int DEFAULT_TOTAL_OPTIONS = 5;

    private final ConcurrentHashMap<String, GenerationJob> jobs = new ConcurrentHashMap<>();
    private final AiService aiService;
    private final QuizCatalogService catalogService;
    private final QuizQuestionService questionService;
    private final TaskScheduler scheduler;

    @Inject
    public QuizGenerationService(
            AiService aiService,
            QuizCatalogService catalogService,
            QuizQuestionService questionService,
            TaskScheduler scheduler) {
        this.aiService = aiService;
        this.catalogService = catalogService;
        this.questionService = questionService;
        this.scheduler = scheduler;
    }

    /**
     * Starts writing the questions asked for and answers the job to collect them from.
     *
     * <p>Titles already in the named catalog, and every title written so far, are handed to the
     * provider so it does not write the same question twice.
     */
    public String startQuestions(int stationId, int accountId, GenerateQuestionsRequest request) {
        if (request.entries() == null || request.entries().isEmpty()) {
            throw Refusal.AI_GENERATION_NEEDS_ENTRIES.raise();
        }
        List<String> existingTitles = new ArrayList<>(existingTitles(stationId, request.catalogId()));
        Map<Integer, QuizCategory> categories = catalogService.findCategories(stationId).stream()
                .collect(Collectors.toMap(QuizCategory::id, Function.identity()));
        String jobId = UUID.randomUUID().toString();
        var job = new GenerationJob(stationId);
        jobs.put(jobId, job);
        scheduler.background("ai-question-generation", () -> {
            try {
                for (var entry : request.entries()) {
                    if (!entry.asksForQuestions()) continue;
                    var turn = new Turn(stationId, accountId, request, entry, categories.get(entry.categoryId()));
                    writeQuestions(turn, existingTitles, job);
                }
            } finally {
                job.setDone();
            }
        });
        return jobId;
    }

    /**
     * Hands over what a job has written since the last poll, and forgets the job once it is done.
     * A job is only ever handed to the station that started it.
     */
    public GenerationPollResponse poll(int stationId, String jobId) {
        var job = jobs.get(jobId);
        if (job == null || job.stationId() != stationId) throw Refusal.AI_GENERATION_NOT_HERE.raise();
        var results = job.drainResults();
        boolean done = job.isDone();
        if (done) jobs.remove(jobId);
        return new GenerationPollResponse(results, done);
    }

    /**
     * Tops up every multiple-choice question of a catalog with wrong answers until it offers the
     * requested number of options. A question that fails is named in the result and the rest
     * carry on.
     */
    public BatchResult fillDistractors(int stationId, int accountId, int catalogId, BatchGenerateRequest request) {
        ownedCatalog(stationId, catalogId);
        int targetTotal = Objects.requireNonNullElse(request.targetTotalOptions(), DEFAULT_TOTAL_OPTIONS);
        int generated = 0;
        var errors = new ArrayList<String>();
        for (var question : questionService.findQuestions(catalogId)) {
            try {
                if (fillDistractors(stationId, accountId, question, targetTotal, request)) generated++;
            } catch (Exception e) {
                errors.add(question.title() + ": " + e.getMessage());
            }
        }
        return new BatchResult(generated, errors);
    }

    private boolean fillDistractors(
            int stationId, int accountId, QuizQuestion question, int targetTotal, BatchGenerateRequest request) {
        if (!(question.config() instanceof QuestionConfig.MultipleChoice(var options, var pointsPerCorrect))) {
            return false;
        }
        if (options == null || options.isEmpty() || options.size() >= targetTotal) return false;
        var correctParts = options.stream()
                .filter(QuestionConfig.MultipleChoice.ChoiceOption::correct)
                .map(QuestionConfig.MultipleChoice.ChoiceOption::text)
                .toList();
        if (correctParts.isEmpty()) return false;
        var newAnswers = aiService.generate(
                stationId,
                accountId,
                providerOf(request.provider()),
                request.apiKey(),
                request.model(),
                question.title(),
                String.join(", ", correctParts),
                targetTotal - options.size());
        var updatedOptions = new ArrayList<>(options);
        newAnswers.forEach(answer -> updatedOptions.add(new QuestionConfig.MultipleChoice.ChoiceOption(answer, false)));
        String config =
                Json.MAPPER.writeValueAsString(new QuestionConfig.MultipleChoice(updatedOptions, pointsPerCorrect));
        questionService.updateQuestion(
                question.id(),
                question.categoryId(),
                question.title(),
                question.description(),
                question.imageUrl(),
                question.points(),
                question.autoPoints(),
                config,
                question.position());
        return true;
    }

    private void writeQuestions(Turn turn, List<String> existingTitles, GenerationJob job) {
        var entry = turn.entry();
        var category = turn.category();
        var chat = aiService.createQuestionSession(
                turn.stationId(),
                turn.accountId(),
                providerOf(turn.request().provider()),
                turn.request().apiKey(),
                turn.request().model(),
                entry.quizQuestionType(),
                turn.request().userPrompt(),
                turn.request().locale(),
                category == null ? null : category.name(),
                category == null ? null : category.description(),
                existingTitles);
        int count = Objects.requireNonNullElse(entry.count(), 0);
        for (int i = 0; i < count; i++) {
            try {
                for (var question : aiService.generateNextQuestion(chat, entry.quizQuestionType())) {
                    job.addResult(new GeneratedQuestionWithMeta(
                            question.title(), question.config(), entry.quizQuestionType(), entry.categoryId()));
                    existingTitles.add(question.title());
                }
            } catch (Exception e) {
                log.warn("AI generation failed for question {}/{}: {}", i + 1, entry.count(), e.getMessage());
            }
        }
    }

    private List<String> existingTitles(int stationId, @Nullable Integer catalogId) {
        if (catalogId == null) return List.of();
        ownedCatalog(stationId, catalogId);
        return questionService.findQuestions(catalogId).stream()
                .map(QuizQuestion::title)
                .toList();
    }

    private QuizCatalog ownedCatalog(int stationId, int catalogId) {
        return catalogService
                .findCatalog(catalogId)
                .filter(catalog -> catalog.stationId() == stationId)
                .orElseThrow(Refusal.AI_GENERATION_CATALOG_NOT_HERE::raise);
    }

    private static String providerOf(@Nullable String provider) {
        return Objects.requireNonNullElse(provider, DEFAULT_PROVIDER);
    }

    private record Turn(
            int stationId,
            int accountId,
            GenerateQuestionsRequest request,
            GenerateEntry entry,
            @Nullable QuizCategory category) {}

    public record GenerateQuestionsRequest(
            @Nullable String provider,
            @Nullable String apiKey,
            @Nullable String model,
            @Nullable String userPrompt,
            @Nullable String locale,
            @Nullable Integer catalogId,
            List<GenerateEntry> entries) {}

    public record GenerateEntry(
            QuizQuestionType quizQuestionType,
            @Nullable Integer count,
            @Nullable Integer categoryId) {
        boolean asksForQuestions() {
            return quizQuestionType != null && count != null && count >= 1;
        }
    }

    public record BatchGenerateRequest(
            @Nullable String provider,
            @Nullable String apiKey,
            @Nullable String model,
            @Nullable Integer targetTotalOptions) {}

    public record GeneratedQuestionWithMeta(
            String title,
            String config,
            QuizQuestionType quizQuestionType,
            @Nullable Integer categoryId) {}

    public record GenerationPollResponse(List<GeneratedQuestionWithMeta> questions, boolean done) {}

    public record BatchResult(int generatedCount, List<String> errors) {}

    /**
     * A question-writing job and what it has written so far.
     *
     * <p>A job id is unguessable, but it is still a name in a map shared by the whole instance, and
     * polling both reads the questions and clears the job, so whoever polls has to be from the
     * station that asked for them.
     */
    private static final class GenerationJob {
        private final int stationId;
        private final List<GeneratedQuestionWithMeta> results = new ArrayList<>();
        private volatile boolean done = false;

        GenerationJob(int stationId) {
            this.stationId = stationId;
        }

        int stationId() {
            return stationId;
        }

        synchronized void addResult(GeneratedQuestionWithMeta result) {
            results.add(result);
        }

        synchronized List<GeneratedQuestionWithMeta> drainResults() {
            var drained = new ArrayList<>(results);
            results.clear();
            return drained;
        }

        void setDone() {
            this.done = true;
        }

        boolean isDone() {
            return done;
        }
    }
}
