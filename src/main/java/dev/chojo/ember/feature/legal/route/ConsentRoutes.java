/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.legal.service.ConsentService;
import io.javalin.http.BadRequestResponse;
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

import java.time.Instant;

@Singleton
public class ConsentRoutes implements Routes {
    private final ConsentService consentService;

    @Inject
    public ConsentRoutes(ConsentService consentService) {
        this.consentService = consentService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/consent", this::getConsentText);
        routes.get(prefix + "/public/privacy-policy", this::getPrivacyPolicy);
        routes.get(prefix + "/public/tos", this::getTermsOfService);
        routes.get(prefix + "/public/imprint", this::getImprint);
        routes.get(prefix + "/public/legal-versions", this::getLegalVersions);
        routes.post(prefix + "/session/consent", this::recordConsent, Roles.LOGIN);
        routes.get(prefix + "/session/consent", this::getConsentStatus, Roles.LOGIN);
        routes.get(prefix + "/session/consent/changes", this::getConsentChanges, Roles.LOGIN);
    }

    private String resolveLocale(Context ctx) {
        String lang = ctx.queryParam("lang");
        return lang != null && !lang.isBlank() ? lang : "de";
    }

    @OpenApi(
            path = "/api/v1/public/consent",
            methods = HttpMethod.GET,
            summary = "Get GDPR consent text",
            description = "Returns the current consent text as HTML with a version hash for proof of consent.",
            tags = {"GDPR"},
            queryParams = @OpenApiParam(name = "lang", description = "Locale (e.g. de, en)", required = false),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentResponse.class)))
    private void getConsentText(Context ctx) {
        var content = consentService.getConsentText(resolveLocale(ctx));
        ctx.json(new DocumentResponse(content.html(), content.version()));
    }

    @OpenApi(
            path = "/api/v1/public/privacy-policy",
            methods = HttpMethod.GET,
            summary = "Get privacy policy",
            description = "Returns the current privacy policy as HTML.",
            tags = {"GDPR"},
            queryParams = @OpenApiParam(name = "lang", description = "Locale (e.g. de, en)", required = false),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentResponse.class)))
    private void getPrivacyPolicy(Context ctx) {
        var content = consentService.getPrivacyPolicy(resolveLocale(ctx));
        ctx.json(new DocumentResponse(content.html(), content.version()));
    }

    @OpenApi(
            path = "/api/v1/public/tos",
            methods = HttpMethod.GET,
            summary = "Get terms of service",
            description = "Returns the current terms of service as HTML.",
            tags = {"GDPR"},
            queryParams = @OpenApiParam(name = "lang", description = "Locale (e.g. de, en)", required = false),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentResponse.class)))
    private void getTermsOfService(Context ctx) {
        var content = consentService.getTermsOfService(resolveLocale(ctx));
        ctx.json(new DocumentResponse(content.html(), content.version()));
    }

    @OpenApi(
            path = "/api/v1/public/imprint",
            methods = HttpMethod.GET,
            summary = "Get imprint",
            description = "Returns the imprint / Impressum as HTML.",
            tags = {"GDPR"},
            queryParams = @OpenApiParam(name = "lang", description = "Locale (e.g. de, en)", required = false),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentResponse.class)))
    private void getImprint(Context ctx) {
        var content = consentService.getImprint(resolveLocale(ctx));
        ctx.json(new DocumentResponse(content.html(), content.version()));
    }

    @OpenApi(
            path = "/api/v1/public/legal-versions",
            methods = HttpMethod.GET,
            summary = "Get current legal document versions",
            description = "Returns the current version hashes of all legal documents.",
            tags = {"GDPR"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LegalVersionsResponse.class)))
    private void getLegalVersions(Context ctx) {
        var versions = consentService.getCurrentVersions();
        ctx.json(
                new LegalVersionsResponse(versions.privacyVersion(), versions.tosVersion(), versions.consentVersion()));
    }

    @OpenApi(
            path = "/api/v1/session/consent",
            methods = HttpMethod.POST,
            summary = "Record GDPR consent proof",
            description =
                    "Records proof of consent for the current user including all document versions, IP, country (from Cloudflare), and user agent.",
            tags = {"GDPR"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RecordConsentRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void recordConsent(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(RecordConsentRequest.class);
        if (request.consentVersion() == null || request.consentVersion().isBlank()) {
            throw new BadRequestResponse("consentVersion is required");
        }

        String ipAddress = ctx.ip();
        String country = ctx.header("CF-IPCountry");
        String userAgent = ctx.userAgent();

        consentService.recordConsent(
                session.accountId(),
                request.consentVersion(),
                request.privacyVersion(),
                request.tosVersion(),
                ipAddress,
                country,
                userAgent);
        ctx.json(new MessageResponse("Consent recorded"));
    }

    @OpenApi(
            path = "/api/v1/session/consent",
            methods = HttpMethod.GET,
            summary = "Get current consent status",
            description = "Returns the latest consent record and whether it matches current document versions.",
            tags = {"GDPR"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ConsentStatusResponse.class)))
    private void getConsentStatus(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var consent = consentService.findLatestConsent(session.accountId());
        var currentVersions = consentService.getCurrentVersions();

        if (consent.isPresent()) {
            var c = consent.get();
            boolean isCurrent = currentVersions.consentVersion().equals(c.consentVersion())
                    && (c.privacyVersion() == null
                            || currentVersions.privacyVersion().equals(c.privacyVersion()))
                    && (c.tosVersion() == null || currentVersions.tosVersion().equals(c.tosVersion()));
            ctx.json(new ConsentStatusResponse(
                    true,
                    isCurrent,
                    c.consentVersion(),
                    c.privacyVersion(),
                    c.tosVersion(),
                    c.consentedAt(),
                    currentVersions.privacyVersion(),
                    currentVersions.tosVersion(),
                    currentVersions.consentVersion()));
        } else {
            ctx.json(new ConsentStatusResponse(
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    currentVersions.privacyVersion(),
                    currentVersions.tosVersion(),
                    currentVersions.consentVersion()));
        }
    }

    @OpenApi(
            path = "/api/v1/session/consent/changes",
            methods = HttpMethod.GET,
            summary = "Get changes since user's last consent",
            description =
                    "Returns diffs and current document HTML for documents that changed since the user last consented.",
            tags = {"GDPR"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = ConsentChangesResponse.class)))
    private void getConsentChanges(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String locale = resolveLocale(ctx);
        var consent = consentService.findLatestConsent(session.accountId());
        var currentVersions = consentService.getCurrentVersions();

        String privacyDiff = null;
        String tosDiff = null;
        String privacyHtml = null;
        String tosHtml = null;
        boolean privacyChanged = false;
        boolean tosChanged = false;

        if (consent.isPresent()) {
            var c = consent.get();
            if (c.privacyVersion() != null && !currentVersions.privacyVersion().equals(c.privacyVersion())) {
                privacyChanged = true;
                privacyDiff = consentService.getPrivacyDiff(c.privacyVersion(), currentVersions.privacyVersion());
                privacyHtml = consentService.getPrivacyPolicy(locale).html();
            }
            if (c.tosVersion() != null && !currentVersions.tosVersion().equals(c.tosVersion())) {
                tosChanged = true;
                tosDiff = consentService.getTosDiff(c.tosVersion(), currentVersions.tosVersion());
                tosHtml = consentService.getTermsOfService(locale).html();
            }
        }

        ctx.json(new ConsentChangesResponse(
                privacyChanged,
                tosChanged,
                privacyDiff,
                tosDiff,
                privacyHtml,
                tosHtml,
                currentVersions.privacyVersion(),
                currentVersions.tosVersion(),
                currentVersions.consentVersion()));
    }

    public record DocumentResponse(String html, String version) {}

    public record RecordConsentRequest(String consentVersion, String privacyVersion, String tosVersion) {}

    public record LegalVersionsResponse(String privacyVersion, String tosVersion, String consentVersion) {}

    public record ConsentStatusResponse(
            boolean consented,
            boolean current,
            String consentVersion,
            String privacyVersion,
            String tosVersion,
            Instant consentedAt,
            String currentPrivacyVersion,
            String currentTosVersion,
            String currentConsentVersion) {}

    public record ConsentChangesResponse(
            boolean privacyChanged,
            boolean tosChanged,
            String privacyDiff,
            String tosDiff,
            String privacyHtml,
            String tosHtml,
            String currentPrivacyVersion,
            String currentTosVersion,
            String currentConsentVersion) {}
}
