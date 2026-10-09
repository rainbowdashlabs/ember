/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.FileResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.route.EventVisibility;
import dev.chojo.ember.feature.generator.route.AppointmentDocumentRoutes;
import dev.chojo.ember.feature.signing.entity.PartnerSigner;
import dev.chojo.ember.feature.signing.entity.PartnerSignerDocument;
import dev.chojo.ember.feature.signing.service.PartnerAgreements;
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

import java.time.LocalDate;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Where the members partner stations registered for one of this station's appointments stand with the
 * documents it asks them to sign, for whoever manages its registrations: signed at the partner, taken on
 * there, or missing, with the signed copies that came back, and a signed paper copy to confirm where the
 * partner cannot sign.
 */
@Singleton
public class PartnerAgreementRoutes implements Routes {
    private static final String PDF = "application/pdf";

    private final PartnerAgreements agreements;
    private final EventVisibility visibility;

    @Inject
    public PartnerAgreementRoutes(PartnerAgreements agreements, EventVisibility visibility) {
        this.agreements = agreements;
        this.visibility = visibility;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events/{id}/partner-agreements", this::signers, StationPermission.EVENT_REGISTRATION);
        routes.post(
                prefix + "/events/{id}/partner-agreements/paper",
                this::confirmPaper,
                StationPermission.EVENT_REGISTRATION);
        routes.get(
                prefix + "/events/{id}/partner-agreements/{agreementId}/copy",
                this::copy,
                StationPermission.EVENT_REGISTRATION);
    }

    /**
     * A signed paper copy to confirm for a member a partner registered.
     *
     * @param registrationId the partner's registration
     * @param templateId     the document
     */
    public record PaperRequest(int registrationId, int templateId) {}

    @OpenApi(
            path = "/api/v1/events/{id}/partner-agreements",
            methods = HttpMethod.GET,
            summary = "Where the members of partner stations stand with the documents an appointment asks them to sign",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "date", type = LocalDate.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PartnerSigner[].class)))
    private void signers(Context ctx) {
        var event = visibility.requireVisibleEvent(StationSession.from(ctx), pathInt(ctx, "id"));
        ctx.json(agreements.signers(event, AppointmentDocumentRoutes.date(ctx)));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/partner-agreements/paper",
            methods = HttpMethod.POST,
            summary = "Confirm a signed paper copy of a document for a member of a partner station",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PaperRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PartnerSignerDocument.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void confirmPaper(Context ctx) {
        var session = StationSession.from(ctx);
        var event = visibility.requireVisibleEvent(session, pathInt(ctx, "id"));
        var request = ctx.bodyAsClass(PaperRequest.class);
        ctx.json(agreements.confirmPaper(session, event, request.registrationId(), request.templateId()));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/partner-agreements/{agreementId}/copy",
            methods = HttpMethod.GET,
            summary = "The newest signed copy of a document that came back from a partner station",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "agreementId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void copy(Context ctx) {
        var session = StationSession.from(ctx);
        visibility.requireVisibleEvent(session, pathInt(ctx, "id"));
        var copy = agreements.latestCopy(session, pathInt(ctx, "agreementId"));
        FileResponse.send(ctx, PDF, copy.fileName(), copy.pdf());
    }
}
