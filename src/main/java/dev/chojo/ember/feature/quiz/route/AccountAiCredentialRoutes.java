/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.QuizRefusal;
import dev.chojo.ember.feature.quiz.entity.AiVendor;
import dev.chojo.ember.feature.quiz.service.AiCredentialService;
import dev.chojo.ember.feature.quiz.service.AiCredentialService.AiCredentialSummary;
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

/**
 * The AI provider key a person keeps for themselves, under their account settings.
 *
 * <p>The key goes in and never comes back out: reading answers with the provider, the model and the
 * last four characters of the key only.
 */
@Singleton
public class AccountAiCredentialRoutes implements Routes {
    private final AiCredentialService credentials;

    @Inject
    public AccountAiCredentialRoutes(AiCredentialService credentials) {
        this.credentials = credentials;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/account/ai-credential", this::read, StationPermission.LOGIN);
        routes.put(prefix + "/account/ai-credential", this::save, StationPermission.LOGIN);
        routes.delete(prefix + "/account/ai-credential", this::delete, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/account/ai-credential",
            methods = HttpMethod.GET,
            summary = "Read the caller's own AI key settings",
            description =
                    "Answers with the provider, the model and the last four characters of the key. The key itself is never sent. Without a stored key every field is empty and usable is false.",
            tags = {"Quiz AI"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AiCredentialSummary.class)))
    private void read(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(credentials.summary(session.accountId()).orElse(new AiCredentialSummary(null, null, false, null)));
    }

    @OpenApi(
            path = "/api/v1/account/ai-credential",
            methods = HttpMethod.PUT,
            summary = "Save the caller's own AI key",
            description =
                    "Stores the key encrypted. Without a key the stored one is kept, which lets the model change without typing the key again; that works only for the provider the stored key is for.",
            tags = {"Quiz AI"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AiCredentialRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AiCredentialSummary.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void save(Context ctx) {
        var session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(AiCredentialRequest.class);
        switch (credentials.save(session.accountId(), request.provider(), request.model(), request.apiKey())) {
            case KEY_MISSING -> throw QuizRefusal.AI_KEY_MISSING.raise();
            case SAVED ->
                ctx.json(credentials.summary(session.accountId()).orElseThrow(QuizRefusal.AI_KEY_NOT_READ_BACK::raise));
        }
    }

    @OpenApi(
            path = "/api/v1/account/ai-credential",
            methods = HttpMethod.DELETE,
            summary = "Forget the caller's own AI key",
            tags = {"Quiz AI"},
            responses = @OpenApiResponse(status = "204"))
    private void delete(Context ctx) {
        credentials.delete(UserSession.from(ctx).accountId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * @param provider the provider the key is for
     * @param model    the model to ask by default, blank for the provider's default
     * @param apiKey   the key, blank to keep the stored one
     */
    public record AiCredentialRequest(
            AiVendor provider,
            @Nullable String model,
            @Nullable String apiKey) {}
}
