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
}
