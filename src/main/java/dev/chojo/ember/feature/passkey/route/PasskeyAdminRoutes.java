/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.passkey.repository.PasskeyRepository;
import dev.chojo.ember.feature.passkey.service.PasskeyAdminService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

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

    private void residue(Context ctx) {
        ctx.json(adminService.residue().stream()
                .map(entry -> new ResidueEntryResponse(
                        entry.accountId(),
                        entry.firstName(),
                        entry.lastName(),
                        entry.lastSignInAt(),
                        entry.reachable(),
                        entry.hasGuardian()))
                .toList());
    }

    private void retirePassword(Context ctx) {
        var session = dev.chojo.ember.api.UserSession.from(ctx);
        int accountId = dev.chojo.ember.api.RouteSupport.pathInt(ctx, "id");
        var outcome = adminService.retirePassword(
                accountId, session.accountId(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        switch (outcome) {
            case RETIRED -> ctx.json(Map.of("message", "Password retired"));
            case NO_PASSWORD ->
                throw new HttpResponseException(
                        HttpStatus.CONFLICT.getCode(), "This account holds no password", Map.of());
            case NO_TRIED_PASSKEY ->
                throw new HttpResponseException(
                        HttpStatus.CONFLICT.getCode(),
                        "No passkey has completed a sign-in for this account; the rope stays",
                        Map.of());
        }
    }

    private void retireAll(Context ctx) {
        var session = dev.chojo.ember.api.UserSession.from(ctx);
        var result = adminService.retireAllEligible(session.accountId(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        ctx.json(new BulkRetireResponse(result.retired(), result.passedOver()));
    }

    private void getConfig(Context ctx) {
        ctx.json(toResponse(adminService.status()));
    }

    private void updateConfig(Context ctx) {
        var request = ctx.bodyAsClass(PasskeysConfigRequest.class);
        PasskeySettings.Mode mode;
        try {
            mode = PasskeySettings.Mode.valueOf(
                    request.mode() == null ? "" : request.mode().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("mode must be one of OFF, OPTIONAL, ENCOURAGED, PREFERRED, PASSWORDLESS");
        }

        var result = adminService.setMode(mode);
        switch (result.outcome()) {
            case NO_MAIL_PROOF ->
                throw new HttpResponseException(
                        HttpStatus.CONFLICT.getCode(),
                        "The passwordless mode needs working mail, proven by a test mail that went out",
                        Map.of());
            case ACCOUNTS_DEPEND ->
                throw new HttpResponseException(
                        HttpStatus.CONFLICT.getCode(),
                        result.dependentAccounts()
                                + " account(s) have no way in without a passkey; the mode cannot go below ENCOURAGED",
                        Map.of());
            case OK -> ctx.json(toResponse(adminService.status()));
        }
    }

    private void passwordlessReport(Context ctx) {
        PasskeyRepository.PasswordlessReport report = adminService.passwordlessReport();
        ctx.json(new PasswordlessReportResponse(
                report.wouldKeepPassword(),
                report.withoutPasskey(),
                report.reachableOnlyByQr(),
                report.dormantForAYear()));
    }

    private static PasskeysConfigResponse toResponse(PasskeyAdminService.ModeStatus status) {
        return new PasskeysConfigResponse(
                status.configured().name(),
                status.effective().name(),
                status.localhostFallback(),
                status.rpId(),
                status.lastMailSentAt(),
                status.dependentAccounts(),
                status.figures().accountsWithTriedPasskey(),
                status.figures().accountsWithPassword(),
                status.figures().accountsWithPasswordAndNoPasskey());
    }

    public record PasskeysConfigRequest(String mode) {}

    public record PasskeysConfigResponse(
            String mode,
            String effectiveMode,
            boolean localhostFallback,
            String rpId,
            Instant lastMailSentAt,
            int dependentAccounts,
            int accountsWithTriedPasskey,
            int accountsWithPassword,
            int accountsWithPasswordAndNoPasskey) {}

    public record PasswordlessReportResponse(
            int wouldKeepPassword, int withoutPasskey, int reachableOnlyByQr, int dormantForAYear) {}

    public record ResidueEntryResponse(
            int accountId,
            String firstName,
            String lastName,
            Instant lastSignInAt,
            boolean reachable,
            boolean hasGuardian) {}

    public record BulkRetireResponse(int retired, int passedOver) {}
}
