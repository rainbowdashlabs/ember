/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.legal.service.GdprDeletionService;
import dev.chojo.ember.feature.legal.service.GdprExportService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.util.DocumentName;
import dev.chojo.ember.util.DocumentPeriod;
import dev.chojo.ember.util.DocumentWord;
import dev.chojo.ember.util.SafeContentDisposition;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Routes covering the personal-data lifecycle of the current account: the GDPR data export and
 * the account deletion that anonymizes everything left behind.
 */
@Singleton
public class AccountDataRoutes implements Routes {
    private final GdprExportService gdprExportService;
    private final GdprDeletionService gdprDeletionService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public AccountDataRoutes(
            GdprExportService gdprExportService,
            GdprDeletionService gdprDeletionService,
            StationMemberRepository stationMemberRepository) {
        this.gdprExportService = gdprExportService;
        this.gdprDeletionService = gdprDeletionService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/session/gdpr-export", this::gdprExport, StationPermission.LOGIN);
        routes.delete(prefix + "/session/account", this::deleteAccount, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/session/gdpr-export",
            methods = HttpMethod.GET,
            summary = "Export all personal data (GDPR/DSGVO)",
            tags = {"Session"},
            responses = @OpenApiResponse(status = "200"))
    private void gdprExport(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String locale = ctx.queryParam("locale");
        byte[] zipData = gdprExportService.exportAccountDataAsZip(session.accountId(), locale);
        ctx.contentType("application/zip");
        ctx.header("Content-Disposition", dataExportName(session, locale));
        ctx.result(zipData);
    }

    /**
     * What a person's own data is called when they ask for a copy of it.
     *
     * <p>Their name is in it, because the usual reason for asking is to hand the file to somebody
     * else, and a folder of files all called the same thing helps nobody.
     */
    private static String dataExportName(UserSession session, String locale) {
        String language = "en".equals(locale) ? "en" : "de";
        String filename = DocumentName.of(
                "zip",
                DocumentWord.DATA_EXPORT.in(language),
                DocumentName.part(NameParts.of(session.account()).official()),
                DocumentPeriod.day(Instant.now(), ZoneOffset.UTC));
        return SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, filename);
    }

    /**
     * Deletes and anonymizes the calling account. Rejected while the account still administers a
     * station, because the station would be left without an owner.
     */
    @OpenApi(
            path = "/api/v1/session/account",
            methods = HttpMethod.DELETE,
            summary = "Delete account and anonymize all data (GDPR/DSGVO)",
            tags = {"Session"},
            responses = @OpenApiResponse(status = "204"))
    private void deleteAccount(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var memberships = stationMemberRepository.findAllByAccountId(session.accountId());
        for (var member : memberships) {
            var roles = stationMemberRepository.findPermissions(member.id());
            boolean isManager = roles.stream().anyMatch(r -> r.permission() == StationPermission.STATION_ADMINISTRATOR);
            if (isManager) {
                throw Refusal.ACCOUNT_STILL_ADMINISTERS_STATION.raise();
            }
        }
        gdprDeletionService.deleteAccount(session.accountId());
        ctx.status(HttpStatus.NO_CONTENT);
    }
}
