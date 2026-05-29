/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizCategory;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.entity.StationAiProvider;
import dev.chojo.ember.feature.quiz.service.AiService;
import dev.chojo.ember.feature.quiz.service.QuizService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class AiRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(AiRoutes.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private final ConcurrentHashMap<String, GenerationJob> generationJobs = new ConcurrentHashMap<>();

    private final AiService aiService;
    private final QuizService quizService;

    @Inject
    public AiRoutes(AiService aiService, QuizService quizService) {
        this.aiService = aiService;
        this.quizService = quizService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Settings
        routes.get(prefix + "/ai/settings", this::getSettings, Roles.QUIZ_MANAGER);
        routes.put(prefix + "/ai/settings/prompt", this::savePrompt, Roles.QUIZ_MANAGER);

        // Provider management
        routes.put(prefix + "/ai/providers/{provider}", this::saveProvider, Roles.QUIZ_MANAGER);
        routes.delete(prefix + "/ai/providers/{provider}", this::deleteProvider, Roles.QUIZ_MANAGER);

        // Model listing
        routes.post(prefix + "/ai/providers/{provider}/models", this::fetchModels, Roles.QUIZ_MANAGER);

        // Generation
        routes.post(prefix + "/ai/generate", this::generate, Roles.QUIZ_MANAGER);
        routes.post(prefix + "/ai/generate-questions", this::generateQuestions, Roles.QUIZ_MANAGER);
        routes.get(prefix + "/ai/generate-questions/{jobId}", this::pollGeneration, Roles.QUIZ_MANAGER);
        routes.post(prefix + "/ai/batch-generate/{catalogId}", this::batchGenerate, Roles.QUIZ_MANAGER);
    }

    @OpenApi(
            path = "/api/v1/ai/settings",
            methods = HttpMethod.GET,
            summary = "Get AI settings including providers and prompt",
            tags = {"Quiz AI"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SettingsResponse.class)))
    private void getSettings(Context ctx) {
        var session = UserSession.from(ctx);
        var providers = aiService.getProviders(session.stationId()).stream()
                .map(StationAiProvider::withoutKey)
                .toList();
        var prompt = aiService.getPrompt(session.stationId());
        ctx.json(new SettingsResponse(providers, prompt, aiService.getDefaultPrompt()));
    }

    @OpenApi(
            path = "/api/v1/ai/settings/prompt",
            methods = HttpMethod.PUT,
            summary = "Save the AI generation prompt",
            tags = {"Quiz AI"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PromptRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SuccessResponse.class)))
    private void savePrompt(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(PromptRequest.class);
        aiService.setPrompt(session.stationId(), req.prompt() != null ? req.prompt() : "");
        ctx.json(new SuccessResponse(true));
    }

    @OpenApi(
            path = "/api/v1/ai/providers/{provider}",
            methods = HttpMethod.PUT,
            summary = "Save an AI provider configuration",
            tags = {"Quiz AI"},
            pathParams = @OpenApiParam(name = "provider", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProviderRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = SuccessResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void saveProvider(Context ctx) {
        var session = UserSession.from(ctx);
        String provider = ctx.pathParam("provider");
        var req = ctx.bodyAsClass(ProviderRequest.class);
        if (req.apiKey() == null || req.apiKey().isBlank()) {
            throw new BadRequestResponse("apiKey is required");
        }
        aiService.saveProvider(session.stationId(), provider, req.apiKey(), req.model());
        ctx.json(new SuccessResponse(true));
    }

    @OpenApi(
            path = "/api/v1/ai/providers/{provider}",
            methods = HttpMethod.DELETE,
            summary = "Delete an AI provider configuration",
            tags = {"Quiz AI"},
            pathParams = @OpenApiParam(name = "provider", type = String.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void deleteProvider(Context ctx) {
        var session = UserSession.from(ctx);
        String provider = ctx.pathParam("provider");
        aiService.deleteProvider(session.stationId(), provider);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/ai/providers/{provider}/models",
            methods = HttpMethod.POST,
            summary = "Fetch available models for an AI provider",
            tags = {"Quiz AI"},
            pathParams = @OpenApiParam(name = "provider", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TransientKeyRequest.class)),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void fetchModels(Context ctx) {
        var session = UserSession.from(ctx);
        String provider = ctx.pathParam("provider");
        var req = ctx.bodyAsClass(TransientKeyRequest.class);
        try {
            var models = aiService.fetchModels(session.stationId(), provider, req.apiKey());
            ctx.json(models);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument fetching AI models for provider {}", provider, e);
            throw new BadRequestResponse(e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to fetch AI models for provider {}", provider, e);
            throw new BadRequestResponse("Failed to fetch models");
        }
    }

    @OpenApi(
            path = "/api/v1/ai/generate",
            methods = HttpMethod.POST,
            summary = "Generate distractor answers for a question",
            tags = {"Quiz AI"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = GenerateRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = GenerateResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void generate(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(GenerateRequest.class);
        if (req.question() == null || req.question().isBlank()) {
            throw new BadRequestResponse("question is required");
        }
        if (req.correctAnswer() == null || req.correctAnswer().isBlank()) {
            throw new BadRequestResponse("correctAnswer is required");
        }
        try {
            var results = aiService.generate(
                    session.stationId(),
                    req.provider() != null ? req.provider() : "openai",
                    req.apiKey(),
                    req.model(),
                    req.question(),
                    req.correctAnswer(),
                    req.count() != null ? req.count() : 3);
            ctx.json(new GenerateResponse(results));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument during AI generation", e);
            throw new BadRequestResponse(e.getMessage());
        } catch (Exception e) {
            log.warn("AI generation failed", e);
            throw new BadRequestResponse("Generation failed");
        }
    }

    @OpenApi(
            path = "/api/v1/ai/generate-questions",
            methods = HttpMethod.POST,
            summary = "Start async question generation job",
            tags = {"Quiz AI"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = GenerateQuestionsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = JobIdResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void generateQuestions(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(GenerateQuestionsRequest.class);
        if (req.entries() == null || req.entries().isEmpty()) {
            throw new BadRequestResponse("entries is required");
        }
        String provider = req.provider() != null ? req.provider() : "openai";
        var categories = quizService.findCategories(session.stationId());
        var categoryMap = new HashMap<Integer, QuizCategory>();
        for (var cat : categories) categoryMap.put(cat.id(), cat);

        // Collect existing question titles from the catalog to avoid duplicates
        var existingTitles = new ArrayList<String>();
        if (req.catalogId() != null) {
            var existingQuestions = quizService.findQuestions(req.catalogId());
            for (var q : existingQuestions) {
                existingTitles.add(q.title());
            }
        }

        // Start async generation
        String jobId = UUID.randomUUID().toString();
        var job = new GenerationJob();
        generationJobs.put(jobId, job);

        Thread.startVirtualThread(() -> {
            try {
                for (var entry : req.entries()) {
                    if (entry.quizQuestionType() == null || entry.count() == null || entry.count() < 1) continue;
                    var type = entry.quizQuestionType();
                    String catName = null;
                    String catDesc = null;
                    if (entry.categoryId() != null) {
                        var cat = categoryMap.get(entry.categoryId());
                        if (cat != null) {
                            catName = cat.name();
                            catDesc = cat.description();
                        }
                    }

                    // Create one session per entry type — context accumulates across turns
                    var chatSession = aiService.createQuestionSession(
                            session.stationId(),
                            provider,
                            req.apiKey(),
                            req.model(),
                            type,
                            req.userPrompt(),
                            req.locale(),
                            catName,
                            catDesc,
                            existingTitles);

                    for (int i = 0; i < entry.count(); i++) {
                        try {
                            var generated = aiService.generateNextQuestion(chatSession, type);
                            for (var q : generated) {
                                job.addResult(
                                        new GeneratedQuestionWithMeta(q.title(), q.config(), type, entry.categoryId()));
                                existingTitles.add(q.title());
                            }
                        } catch (Exception e) {
                            log.warn(
                                    "AI generation failed for question {}/{}: {}",
                                    i + 1,
                                    entry.count(),
                                    e.getMessage());
                        }
                    }
                }
            } finally {
                job.setDone();
            }
        });

        ctx.json(new JobIdResponse(jobId));
    }

    @OpenApi(
            path = "/api/v1/ai/generate-questions/{jobId}",
            methods = HttpMethod.GET,
            summary = "Poll for question generation results",
            tags = {"Quiz AI"},
            pathParams = @OpenApiParam(name = "jobId", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = GenerationPollResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void pollGeneration(Context ctx) {
        String jobId = ctx.pathParam("jobId");
        var job = generationJobs.get(jobId);
        if (job == null) throw new NotFoundResponse();
        var results = job.drainResults();
        boolean done = job.isDone();
        if (done) generationJobs.remove(jobId);
        ctx.json(new GenerationPollResponse(results, done));
    }

    @OpenApi(
            path = "/api/v1/ai/batch-generate/{catalogId}",
            methods = HttpMethod.POST,
            summary = "Batch generate distractor options for all MC questions in a catalog",
            tags = {"Quiz AI"},
            pathParams = @OpenApiParam(name = "catalogId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BatchGenerateRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BatchResult.class)))
    private void batchGenerate(Context ctx) {
        var session = UserSession.from(ctx);
        int catalogId = ctx.pathParamAsClass("catalogId", Integer.class).get();
        var req = ctx.bodyAsClass(BatchGenerateRequest.class);
        int targetTotal = req.targetTotalOptions() != null ? req.targetTotalOptions() : 5;
        String provider = req.provider() != null ? req.provider() : "openai";

        var questions = quizService.findQuestions(catalogId);
        int generated = 0;
        var errors = new ArrayList<String>();

        for (var q : questions) {
            try {
                if (!(q.config()
                        instanceof
                        QuestionConfig.MultipleChoice(
                                List<QuestionConfig.MultipleChoice.Option> options,
                                double pointsPerCorrect))) continue;
                if (options == null || options.isEmpty()) continue;
                if (options.size() >= targetTotal) continue;

                int need = targetTotal - options.size();
                var correctParts = options.stream()
                        .filter(QuestionConfig.MultipleChoice.Option::correct)
                        .map(QuestionConfig.MultipleChoice.Option::text)
                        .toList();
                if (correctParts.isEmpty()) continue;

                var newAnswers = aiService.generate(
                        session.stationId(),
                        provider,
                        req.apiKey(),
                        req.model(),
                        q.title(),
                        String.join(", ", correctParts),
                        need);

                var updatedOptions = new ArrayList<>(options);
                for (var answer : newAnswers) {
                    updatedOptions.add(new QuestionConfig.MultipleChoice.Option(answer, false));
                }

                var updatedMc = new QuestionConfig.MultipleChoice(updatedOptions, pointsPerCorrect);
                String newConfig = MAPPER.writeValueAsString(updatedMc);
                quizService.updateQuestion(
                        q.id(),
                        q.categoryId(),
                        q.title(),
                        q.description(),
                        q.imageUrl(),
                        q.points(),
                        q.autoPoints(),
                        newConfig,
                        q.position());
                generated++;
            } catch (Exception e) {
                errors.add(q.title() + ": " + e.getMessage());
            }
        }
        ctx.json(new BatchResult(generated, errors));
    }

    // -- Records --

    @OpenApiName("AiSuccessResponse")
    public record SuccessResponse(boolean success) {}

    public record JobIdResponse(String jobId) {}

    @OpenApiName("AiSettingsResponse")
    public record SettingsResponse(List<StationAiProvider> providers, String prompt, String defaultPrompt) {}

    public record PromptRequest(String prompt) {}

    public record ProviderRequest(String apiKey, String model) {}

    public record TransientKeyRequest(String apiKey) {}

    public record GenerateRequest(
            String provider, String apiKey, String model, String question, String correctAnswer, Integer count) {}

    public record BatchGenerateRequest(String provider, String apiKey, String model, Integer targetTotalOptions) {}

    public record GenerateResponse(List<String> answers) {}

    public record GenerateQuestionsRequest(
            String provider,
            String apiKey,
            String model,
            String userPrompt,
            String locale,
            Integer catalogId,
            List<GenerateEntry> entries) {}

    public record GenerateEntry(QuizQuestionType quizQuestionType, Integer count, Integer categoryId) {}

    public record GeneratedQuestionWithMeta(
            String title, String config, QuizQuestionType quizQuestionType, Integer categoryId) {}

    public record GenerateQuestionsResponse(List<GeneratedQuestionWithMeta> questions) {}

    public record GenerationPollResponse(List<GeneratedQuestionWithMeta> questions, boolean done) {}

    public record BatchResult(int generatedCount, List<String> errors) {}

    private static class GenerationJob {
        private final List<GeneratedQuestionWithMeta> results = new ArrayList<>();
        private volatile boolean done = false;

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
