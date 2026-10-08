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
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.SignatureAsk;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.service.SignatureRequestService;
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
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * A manager asking for the signatures a generated document's fields call for: first a look at who would
 * be asked and what each confirms, then the request itself. Generating never asks on its own; the screens
 * offer this right after a document was generated and from the list of generated documents.
 */
@Singleton
public class SignatureRequestRoutes implements Routes {
    private final SignatureRequestService requests;

    @Inject
    public SignatureRequestRoutes(SignatureRequestService requests) {
        this.requests = requests;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/signing/generations/{generationId}", this::ask, StationPermission.DOCUMENT_EDIT_MEMBER);
        routes.post(
                prefix + "/signing/generations/{generationId}/request",
                this::request,
                StationPermission.DOCUMENT_EDIT_MEMBER);
    }

    @OpenApi(
            path = "/api/v1/signing/generations/{generationId}",
            methods = HttpMethod.GET,
            summary = "Who a generated document would ask to sign, or the request already made for it",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "generationId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = SignatureAskResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void ask(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(SignatureAskResponse.of(requests.requireOwnedAsk(session, pathInt(ctx, "generationId"))));
    }

    @OpenApi(
            path = "/api/v1/signing/generations/{generationId}/request",
            methods = HttpMethod.POST,
            summary = "Ask for the signatures a generated document's fields call for",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "generationId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = SignatureAskResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void request(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.status(HttpStatus.CREATED)
                .json(SignatureAskResponse.of(requests.requireOwnedThenRequest(session, pathInt(ctx, "generationId"))));
    }

    /**
     * The signatures a generated document asks for.
     *
     * @param request the request made for it, or null before anybody was asked
     * @param fields  each field with who signs it and what they confirm, in the order the document has them;
     *                empty where the document carries no field left to sign
     */
    public record SignatureAskResponse(@Nullable AskedRequestResponse request, List<AskedFieldResponse> fields) {
        static SignatureAskResponse of(SignatureAsk ask) {
            var request = ask.request();
            return new SignatureAskResponse(
                    request == null ? null : AskedRequestResponse.of(request),
                    ask.fields().stream().map(AskedFieldResponse::of).toList());
        }
    }

    /**
     * A request made for a generated document.
     *
     * @param uid             the request
     * @param state           how it stands
     * @param retentionMonths how many months it is kept after the member has gone, as the template said when
     *                        it was made, or null for only while the member is a member
     * @param copyAttached    whether each signer's copy by mail carries the sealed PDF
     */
    public record AskedRequestResponse(
            UUID uid, RequestState state, @Nullable Integer retentionMonths, boolean copyAttached) {
        static AskedRequestResponse of(SignatureRequest request) {
            return new AskedRequestResponse(
                    request.uid(), request.state(), request.retentionMonths(), request.copyAttached());
        }
    }

    /**
     * A signature field of a generated document.
     *
     * @param fieldName  the field's name in the document
     * @param role       who the field asks for
     * @param signerName the official name of the person it names, or null where any guardian may sign or the
     *                   place is empty
     * @param capacity   in what capacity it is signed
     * @param statement  what its signer confirms
     * @param state      how it stands, or null before anybody was asked
     */
    public record AskedFieldResponse(
            String fieldName,
            FieldRole role,
            @Nullable String signerName,
            SignerCapacity capacity,
            String statement,
            @Nullable FieldState state) {
        static AskedFieldResponse of(SignatureAsk.AskedField asked) {
            var field = asked.field();
            return new AskedFieldResponse(
                    field.fieldName(),
                    field.role(),
                    field.signerName(),
                    field.capacity(),
                    field.statement(),
                    asked.state());
        }
    }
}
