/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.quiz.entity.StationAiProvider;
import dev.chojo.ember.feature.quiz.service.AiService;
import dev.chojo.ember.feature.quiz.service.AiService.ModelInfo;
import dev.chojo.ember.feature.quiz.service.QuizGenerationService;
import dev.chojo.ember.feature.quiz.service.QuizGenerationService.BatchGenerateRequest;
import dev.chojo.ember.feature.quiz.service.QuizGenerationService.BatchResult;
import dev.chojo.ember.feature.quiz.service.QuizGenerationService.GenerateQuestionsRequest;
import dev.chojo.ember.feature.quiz.service.QuizGenerationService.GenerationPollResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;

@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class AiRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(AiRoutes.class);

    private final AiService aiService;
    private final QuizGenerationService generationService;

    @Inject
    public AiRoutes(AiService aiService, QuizGenerationService generationService) {
        this.aiService = aiService;
        this.generationService = generationService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/ai/settings", this::getSettings, StationPermission.TEST_CATALOG_EDIT);
        routes.put(prefix + "/ai/settings/prompt", this::savePrompt, StationPermission.TEST_CATALOG_EDIT);
        routes.put(prefix + "/ai/providers/{provider}", this::saveProvider, StationPermission.TEST_CATALOG_EDIT);
        routes.delete(prefix + "/ai/providers/{provider}", this::deleteProvider, StationPermission.TEST_CATALOG_EDIT);
        routes.post(prefix + "/ai/providers/{provider}/models", this::fetchModels, StationPermission.TEST_CATALOG_EDIT);
        routes.post(prefix + "/ai/generate", this::generate, StationPermission.TEST_CATALOG_EDIT);
        routes.post(prefix + "/ai/generate-questions", this::generateQuestions, StationPermission.TEST_CATALOG_EDIT);
        routes.get(
                prefix + "/ai/generate-questions/{jobId}", this::pollGeneration, StationPermission.TEST_CATALOG_EDIT);
        routes.post(
                prefix + "/ai/batch-generate/{catalogId}", this::batchGenerate, StationPermission.TEST_CATALOG_EDIT);
    }

    @OpenApi(
            path = "/api/v1/ai/settings",
            methods = HttpMethod.GET,
            summary = "Get AI settings including providers and prompt",
            tags = {"Quiz AI"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AiSettingsResponse.class)))
    private void getSettings(Context ctx) {
        var session = UserSession.from(ctx);
        var providers = aiService.getProviders(session.stationId()).stream()
                .map(StationAiProvider::withoutKey)
                .toList();
        var prompt = aiService.getPrompt(session.stationId());
        ctx.json(new AiSettingsResponse(providers, prompt, aiService.getDefaultPrompt()));
    }

    @OpenApi(
            path = "/api/v1/ai/settings/prompt",
            methods = HttpMethod.PUT,
            summary = "Save the AI generation prompt",
            tags = {"Quiz AI"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AiPromptRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AiSuccessResponse.class)))
    private void savePrompt(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(AiPromptRequest.class);
        aiService.setPrompt(session.stationId(), req.prompt() != null ? req.prompt() : "");
        ctx.json(new AiSuccessResponse(true));
    }

    @OpenApi(
            path = "/api/v1/ai/providers/{provider}",
            methods = HttpMethod.PUT,
            summary = "Save an AI provider configuration",
            tags = {"Quiz AI"},
            pathParams = @OpenApiParam(name = "provider", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AiProviderRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AiSuccessResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void saveProvider(Context ctx) {
        var session = UserSession.from(ctx);
        String provider = ctx.pathParam("provider");
        var req = ctx.bodyAsClass(AiProviderRequest.class);
        if (req.apiKey() == null || req.apiKey().isBlank()) {
            throw Refusal.AI_PROVIDER_NEEDS_A_KEY.raise();
        }
        aiService.saveProvider(session.stationId(), provider, req.apiKey(), req.model());
        ctx.json(new AiSuccessResponse(true));
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
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ModelInfo[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void fetchModels(Context ctx) {
        var session = UserSession.from(ctx);
        String provider = ctx.pathParam("provider");
        var req = ctx.bodyAsClass(TransientKeyRequest.class);
        try {
            var models = aiService.fetchModels(session.stationId(), session.accountId(), provider, req.apiKey());
            ctx.json(models);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument fetching AI models for provider {}", provider, e);
            throw Refusal.AI_MODELS_NOT_LISTED_KEY_NOT_GOOD.raise();
        } catch (Exception e) {
            log.warn("Failed to fetch AI models for provider {}", provider, e);
            throw Refusal.AI_MODELS_NOT_LISTED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/ai/generate",
            methods = HttpMethod.POST,
            summary = "Generate distractor answers for a question",
            tags = {"Quiz AI"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AiGenerateRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AiGenerateResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void generate(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(AiGenerateRequest.class);
        if (req.question() == null || req.question().isBlank()) {
            throw Refusal.AI_GENERATION_NEEDS_A_QUESTION.raise();
        }
        if (req.correctAnswer() == null || req.correctAnswer().isBlank()) {
            throw Refusal.AI_GENERATION_NEEDS_THE_RIGHT_ANSWER.raise();
        }
        try {
            var results = aiService.generate(
                    session.stationId(),
                    session.accountId(),
                    req.provider() != null ? req.provider() : "openai",
                    req.apiKey(),
                    req.model(),
                    req.question(),
                    req.correctAnswer(),
                    Objects.requireNonNullElse(req.count(), 3));
            ctx.json(new AiGenerateResponse(results));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument during AI generation", e);
            throw Refusal.AI_GENERATION_REFUSED.raise();
        } catch (Exception e) {
            log.warn("AI generation failed", e);
            throw Refusal.AI_GENERATION_FAILED.raise();
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
        var request = ctx.bodyAsClass(GenerateQuestionsRequest.class);
        ctx.json(
                new JobIdResponse(generationService.startQuestions(session.stationId(), session.accountId(), request)));
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
        ctx.json(generationService.poll(UserSession.from(ctx).stationId(), ctx.pathParam("jobId")));
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
        var request = ctx.bodyAsClass(BatchGenerateRequest.class);
        ctx.json(generationService.fillDistractors(
                session.stationId(), session.accountId(), pathInt(ctx, "catalogId"), request));
    }

    public record AiSuccessResponse(boolean success) {}

    public record JobIdResponse(String jobId) {}

    public record AiSettingsResponse(List<StationAiProvider> providers, String prompt, String defaultPrompt) {}

    public record AiPromptRequest(@Nullable String prompt) {}

    public record AiProviderRequest(String apiKey, @Nullable String model) {}

    public record TransientKeyRequest(@Nullable String apiKey) {}

    public record AiGenerateRequest(
            @Nullable String provider,
            @Nullable String apiKey,
            @Nullable String model,
            String question,
            String correctAnswer,
            @Nullable Integer count) {}

    public record AiGenerateResponse(List<String> answers) {}
}
