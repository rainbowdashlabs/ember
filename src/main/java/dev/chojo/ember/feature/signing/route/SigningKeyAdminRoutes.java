/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.signing.entity.SigningKeyRecoveryEntry;
import dev.chojo.ember.feature.signing.entity.SigningKeyRecoveryRequest;
import dev.chojo.ember.feature.signing.entity.SigningKeyStatus;
import dev.chojo.ember.feature.signing.service.SigningKeyRecovery;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;

/**
 * The instance administrator's view of the signing keys: which keys no longer open under the at-rest
 * secret, and giving them up so the stations get new ones. Giving up asks for a fresh second factor and the
 * serial numbers of exactly the keys shown.
 */
@Singleton
public class SigningKeyAdminRoutes implements Routes {
    private final SigningKeyRecovery recovery;

    @Inject
    public SigningKeyAdminRoutes(SigningKeyRecovery recovery) {
        this.recovery = recovery;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/signing/keys", this::status, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/signing/keys/recover",
                this::recover,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
    }

    @OpenApi(
            path = "/api/v1/admin/signing/keys",
            methods = HttpMethod.GET,
            summary = "Which signing keys no longer open under the at-rest secret, and the earlier recoveries",
            tags = {"Signing"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SigningKeyStatus.class)))
    private void status(Context ctx) {
        ctx.json(recovery.status());
    }

    @OpenApi(
            path = "/api/v1/admin/signing/keys/recover",
            methods = HttpMethod.POST,
            summary = "Give up the signing keys that no longer open, so the next seal issues new ones",
            tags = {"Signing"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SigningKeyRecoveryRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = SigningKeyRecoveryEntry.class)),
                @OpenApiResponse(status = "409")
            })
    private void recover(Context ctx) {
        var confirmed = Objects.requireNonNullElse(
                ctx.bodyAsClass(SigningKeyRecoveryRequest.class).serialNumbers(), List.<String>of());
        ctx.json(recovery.recover(UserSession.from(ctx).accountId(), confirmed));
    }
}
