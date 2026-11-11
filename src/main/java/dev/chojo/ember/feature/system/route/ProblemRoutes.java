/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.system.entity.ProblemState;
import dev.chojo.ember.feature.system.entity.StoredProblem;
import dev.chojo.ember.feature.system.service.ProblemLogService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Locale;
import java.util.Set;

/**
 * Admin-only routes for working through the problem log: the warnings, errors and refusals the
 * application recorded.
 */
@Singleton
public class ProblemRoutes implements Routes {
    private final ProblemLogService problems;

    @Inject
    public ProblemRoutes(ProblemLogService problems) {
        this.problems = problems;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/problems", this::list, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/problems/resolve-all", this::resolveAll, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/problems/{id}/resolve", this::resolve, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/problems/{id}/mute", this::mute, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/problems/{id}/unmute", this::unmute, InstancePermission.ADMINISTRATOR);
        routes.delete(prefix + "/admin/problems/{id}", this::delete, InstancePermission.ADMINISTRATOR);
    }

    /**
     * Which states a list asks for: one state by its name, every state for {@code all}, the open ones
     * when it names none. A name that is no state is refused as unusable input.
     */
    private static Set<ProblemState> states(Context ctx) {
        String state = ctx.queryParam("state");
        if (state == null || state.isBlank()) return Set.of(ProblemState.OPEN);
        if ("all".equalsIgnoreCase(state)) return Set.of();
        return Set.of(ProblemState.valueOf(state.toUpperCase(Locale.ROOT)));
    }

    private static boolean refusals(Context ctx) {
        return ctx.queryParamAsClass("refusals", Boolean.class).getOrDefault(false);
    }

    private static int account(Context ctx) {
        return UserSession.from(ctx).account().id();
    }

    @OpenApi(
            path = "/api/v1/admin/problems",
            methods = HttpMethod.GET,
            summary = "List the recorded problems",
            tags = {"Problems"},
            queryParams = {
                @OpenApiParam(name = "state", description = "OPEN, RESOLVED, MUTED or all; OPEN when left out"),
                @OpenApiParam(name = "refusals", type = Boolean.class, description = "the refusals instead of faults")
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StoredProblem[].class)))
    private void list(Context ctx) {
        ctx.json(problems.list(states(ctx), refusals(ctx)));
    }

    @OpenApi(
            path = "/api/v1/admin/problems/{id}/resolve",
            methods = HttpMethod.POST,
            summary = "Mark a problem as dealt with",
            tags = {"Problems"},
            pathParams = @OpenApiParam(name = "id", type = Long.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void resolve(Context ctx) {
        problems.resolve(ctx.pathParamAsClass("id", Long.class).get(), account(ctx));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/admin/problems/{id}/mute",
            methods = HttpMethod.POST,
            summary = "Keep a problem out of the waiting count and the beacon for good",
            tags = {"Problems"},
            pathParams = @OpenApiParam(name = "id", type = Long.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void mute(Context ctx) {
        problems.mute(ctx.pathParamAsClass("id", Long.class).get(), account(ctx));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/admin/problems/{id}/unmute",
            methods = HttpMethod.POST,
            summary = "Take a muted problem back to open",
            tags = {"Problems"},
            pathParams = @OpenApiParam(name = "id", type = Long.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void unmute(Context ctx) {
        problems.unmute(ctx.pathParamAsClass("id", Long.class).get(), account(ctx));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/admin/problems/resolve-all",
            methods = HttpMethod.POST,
            summary = "Mark every open fault, or every open refusal, as dealt with",
            tags = {"Problems"},
            queryParams =
                    @OpenApiParam(
                            name = "refusals",
                            type = Boolean.class,
                            description = "the refusals instead of faults"),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ResolveResult.class)))
    private void resolveAll(Context ctx) {
        ctx.json(new ResolveResult(problems.resolveAll(refusals(ctx), account(ctx))));
    }

    @OpenApi(
            path = "/api/v1/admin/problems/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a problem now",
            tags = {"Problems"},
            pathParams = @OpenApiParam(name = "id", type = Long.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        problems.delete(ctx.pathParamAsClass("id", Long.class).get());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * How many problems a resolve-all marked as dealt with.
     *
     * @param resolved the number
     */
    public record ResolveResult(int resolved) {}
}
