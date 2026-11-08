/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.signing.service.WitnessReachSettings;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Whom the station's witnesses reach, read and set next to the permission to collect signatures in person.
 * Whoever reads the permissions of members reads it; changing it is a change to what a permission grants,
 * so it takes the right to manage members and the step-up a change of permissions takes.
 */
@Singleton
public class WitnessReachRoutes implements Routes {
    private final WitnessReachSettings settings;

    @Inject
    public WitnessReachRoutes(WitnessReachSettings settings) {
        this.settings = settings;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/signing/witness/reach", this::reach, StationPermission.MEMBER_READ);
        routes.put(
                prefix + "/signing/witness/reach",
                this::setReach,
                StationPermission.MEMBER_MANAGER,
                StepUpCategory.ROLE_CHANGE);
    }

    @OpenApi(
            path = "/api/v1/signing/witness/reach",
            methods = HttpMethod.GET,
            summary = "Whom the station's witnesses may collect signatures from, beyond its members and trial users",
            tags = {"Signing"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RestrictionAudience.class)))
    private void reach(Context ctx) {
        ctx.json(settings.audience(StationSession.from(ctx)));
    }

    @OpenApi(
            path = "/api/v1/signing/witness/reach",
            methods = HttpMethod.PUT,
            summary = "Narrow whom the station's witnesses may collect signatures from",
            tags = {"Signing"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RestrictionAudience.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = RestrictionAudience.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setReach(Context ctx) {
        var audience = ctx.bodyAsClass(RestrictionAudience.class);
        ctx.json(settings.setAudience(StationSession.from(ctx), audience));
    }
}
