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
import dev.chojo.ember.feature.events.route.EventVisibility;
import dev.chojo.ember.feature.generator.entity.RequirementSignature;
import dev.chojo.ember.feature.generator.route.AppointmentDocumentRoutes;
import dev.chojo.ember.feature.signing.entity.AgreementSigner;
import dev.chojo.ember.feature.signing.entity.DocumentAgreement;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.service.AgreementOffers;
import dev.chojo.ember.feature.signing.service.AgreementSigners;
import dev.chojo.ember.feature.signing.service.SignatureWithdrawals;
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
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * Agreements beyond asking for them: signing the agreement of an appointment that takes no registrations
 * from its page, the list of who signed for whoever runs an appointment, and withdrawing a signed agreement
 * from the appointment's page or from the document.
 */
@Singleton
public class AgreementRoutes implements Routes {
    private final AgreementOffers offers;
    private final AgreementSigners signers;
    private final SignatureWithdrawals withdrawals;
    private final EventVisibility visibility;

    @Inject
    public AgreementRoutes(
            AgreementOffers offers,
            AgreementSigners signers,
            SignatureWithdrawals withdrawals,
            EventVisibility visibility) {
        this.offers = offers;
        this.signers = signers;
        this.withdrawals = withdrawals;
        this.visibility = visibility;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(
                prefix + "/events/{id}/documents-to-bring/{templateId}/members/{memberId}/agreement",
                this::offer,
                StationPermission.LOGIN);
        routes.get(prefix + "/events/{id}/agreement-signers", this::signers, StationPermission.EVENT_REGISTRATION);
        routes.post(prefix + "/signing/requests/{requestUid}/withdrawal", this::withdraw, StationPermission.LOGIN);
        routes.get(prefix + "/signing/documents/{documentId}/agreements", this::onDocument, StationPermission.LOGIN);
    }

    /**
     * Why an agreement is withdrawn.
     *
     * @param reason the reason as written, at most 500 characters, or null where none is given
     */
    public record AgreementWithdrawalRequest(@Nullable String reason) {}

    /**
     * A withdrawal as it was recorded.
     *
     * @param requestUid  the request whose agreement was withdrawn
     * @param withdrawnAt when
     */
    public record AgreementWithdrawalResponse(UUID requestUid, Instant withdrawnAt) {}

    @OpenApi(
            path = "/api/v1/events/{id}/documents-to-bring/{templateId}/members/{memberId}/agreement",
            methods = HttpMethod.POST,
            summary =
                    "Ask for the signatures of an appointment's agreement for a member, on an appointment without registrations",
            tags = {"Signing"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "templateId", type = Integer.class, required = true),
                @OpenApiParam(name = "memberId", type = Integer.class, required = true)
            },
            queryParams = @OpenApiParam(name = "date", required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = RequirementSignature.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void offer(Context ctx) {
        var session = StationSession.from(ctx);
        var event = visibility.requireVisibleEvent(session, pathInt(ctx, "id"));
        ctx.json(offers.offer(
                session,
                event,
                AppointmentDocumentRoutes.date(ctx),
                pathInt(ctx, "templateId"),
                pathInt(ctx, "memberId")));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/agreement-signers",
            methods = HttpMethod.GET,
            summary = "Who signed the documents an appointment asks for on a date",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "date", required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AgreementSigner[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void signers(Context ctx) {
        var event = visibility.requireVisibleEvent(StationSession.from(ctx), pathInt(ctx, "id"));
        ctx.json(signers.of(event.id(), AppointmentDocumentRoutes.date(ctx)));
    }

    @OpenApi(
            path = "/api/v1/signing/requests/{requestUid}/withdrawal",
            methods = HttpMethod.POST,
            summary = "Withdraw a signed agreement",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "requestUid", type = UUID.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AgreementWithdrawalRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AgreementWithdrawalResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void withdraw(Context ctx) {
        var session = StationSession.from(ctx);
        var body = ctx.bodyAsClass(AgreementWithdrawalRequest.class);
        var withdrawal = withdrawals.requireOwnedThenWithdraw(
                session,
                pathUuid(ctx, "requestUid"),
                body.reason(),
                new SigningCircumstances(ctx.ip(), ctx.userAgent()));
        ctx.json(new AgreementWithdrawalResponse(pathUuid(ctx, "requestUid"), withdrawal.withdrawnAt()));
    }

    @OpenApi(
            path = "/api/v1/signing/documents/{documentId}/agreements",
            methods = HttpMethod.GET,
            summary = "The agreements signed on a member document that the reader signed or acts for",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "documentId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentAgreement[].class)))
    private void onDocument(Context ctx) {
        ctx.json(withdrawals.requireOwnedAgreements(StationSession.from(ctx), pathInt(ctx, "documentId")));
    }
}
