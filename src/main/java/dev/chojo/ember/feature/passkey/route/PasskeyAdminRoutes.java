/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.passkey.entity.PasswordlessReport;
import dev.chojo.ember.feature.passkey.entity.ResidueEntry;
import dev.chojo.ember.feature.passkey.service.PasskeyAdminService;
import io.javalin.http.Context;
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

import java.time.Instant;

/**
 * The operator's side of passkeys: the mode with its readiness block, and the report read before
 * the passwordless switch. Editable under Admin, Settings, Security, the same way the two-factor
 * settings are.
 */
@Singleton
public class PasskeyAdminRoutes implements Routes {
    private final PasskeyAdminService adminService;

    @Inject
    public PasskeyAdminRoutes(PasskeyAdminService adminService) {
        this.adminService = adminService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/config/auth/passkeys", this::getConfig, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/config/auth/passkeys",
                this::updateConfig,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(
                prefix + "/admin/config/auth/passkeys/report",
                this::passwordlessReport,
                InstancePermission.ADMINISTRATOR);
        // The residue and the retiring: the rope comes away only from somebody already holding
        // the other one, and never for a room full of people at once by accident.
        routes.get(prefix + "/admin/config/auth/passkeys/residue", this::residue, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/accounts/{id}/password/retire",
                this::retirePassword,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.post(
                prefix + "/admin/config/auth/passkeys/retire-all",
                this::retireAll,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/passkeys/residue",
            methods = HttpMethod.GET,
            summary = "List the password holders with no passkey they have used",
            tags = {"Admin Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ResidueEntry[].class)))
    private void residue(Context ctx) {
        ctx.json(adminService.residue());
    }

    @OpenApi(
            path = "/api/v1/admin/accounts/{id}/password/retire",
            methods = HttpMethod.POST,
            summary = "Retire one account's password",
            tags = {"Admin Settings"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void retirePassword(Context ctx) {
        var session = UserSession.from(ctx);
        int accountId = RouteSupport.pathInt(ctx, "id");
        var outcome = adminService.retirePassword(
                accountId, session.accountId(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        switch (outcome) {
            case RETIRED -> ctx.json(new MessageResponse("Password retired"));
            case NO_PASSWORD -> throw Refusal.ACCOUNT_HOLDS_NO_PASSWORD_ON_RETIRE.raise();
            case NO_TRIED_PASSKEY -> throw Refusal.NO_TRIED_PASSKEY_ON_RETIRE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/passkeys/retire-all",
            methods = HttpMethod.POST,
            summary = "Retire the password of every account that has used a passkey",
            tags = {"Admin Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BulkRetireResponse.class)))
    private void retireAll(Context ctx) {
        var session = UserSession.from(ctx);
        var result = adminService.retireAllEligible(session.accountId(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        ctx.json(new BulkRetireResponse(result.retired(), result.passedOver()));
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/passkeys",
            methods = HttpMethod.GET,
            summary = "Get the passkey mode with its readiness and adoption figures",
            tags = {"Admin Settings"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = PasskeysConfigResponse.class)))
    private void getConfig(Context ctx) {
        ctx.json(toResponse(adminService.status()));
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/passkeys",
            methods = HttpMethod.PUT,
            summary = "Change the passkey mode",
            tags = {"Admin Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PasskeysConfigRequest.class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = PasskeysConfigResponse.class)))
    private void updateConfig(Context ctx) {
        var request = ctx.bodyAsClass(PasskeysConfigRequest.class);
        var mode = request.mode();
        if (mode == null) throw Refusal.PASSKEY_MODE_UNKNOWN.raise();
        var result = adminService.setMode(mode);
        switch (result.outcome()) {
            case NO_MAIL_PROOF -> throw Refusal.PASSWORDLESS_NEEDS_WORKING_MAIL.raise();
            case ACCOUNTS_DEPEND -> throw Refusal.PASSWORDLESS_ACCOUNTS_DEPEND.raise();
            case OK -> ctx.json(toResponse(adminService.status()));
        }
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/passkeys/report",
            methods = HttpMethod.GET,
            summary = "Count what the passwordless mode would do to the accounts",
            tags = {"Admin Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PasswordlessReport.class)))
    private void passwordlessReport(Context ctx) {
        ctx.json(adminService.passwordlessReport());
    }

    private static PasskeysConfigResponse toResponse(PasskeyAdminService.ModeStatus status) {
        return new PasskeysConfigResponse(
                status.configured(),
                status.effective(),
                status.localhostFallback(),
                status.rpId(),
                status.lastMailSentAt(),
                status.dependentAccounts(),
                status.figures().accountsWithTriedPasskey(),
                status.figures().accountsWithPassword(),
                status.figures().accountsWithPasswordAndNoPasskey());
    }

    public record PasskeysConfigRequest(PasskeySettings.@Nullable Mode mode) {}

    public record PasskeysConfigResponse(
            PasskeySettings.Mode mode,
            PasskeySettings.Mode effectiveMode,
            boolean localhostFallback,
            String rpId,
            @Nullable Instant lastMailSentAt,
            int dependentAccounts,
            int accountsWithTriedPasskey,
            int accountsWithPassword,
            int accountsWithPasswordAndNoPasskey) {}

    public record BulkRetireResponse(int retired, int passedOver) {}
}
