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
import dev.chojo.ember.feature.signing.entity.ManagedSignatureRequest;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.SignatureAsk;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.SigningEvidence;
import dev.chojo.ember.feature.signing.entity.StoredEvidence;
import dev.chojo.ember.feature.signing.service.SignatureManagementService;
import dev.chojo.ember.feature.signing.service.SignatureRequestService;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
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
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * A manager asking for the signatures a generated document's fields call for and looking after them: first
 * a look at who would be asked and what each confirms, then the request itself. Generating never asks on
 * its own; the screens offer this right after a document was generated and from the list of generated
 * documents.
 *
 * <p>Once asked, the manager sees each field with its signer, statement and the act that signed it, and
 * settles what no signing act will: a field confirmed on paper, waived or withdrawn, the whole request
 * withdrawn, or asked anew on a corrected document. Every route needs the right to change member documents,
 * and the services check the document's own edit rule and the station.
 */
@Singleton
public class SignatureRequestRoutes implements Routes {
    private static final String REQUEST = "/signing/requests/{requestUid}";
    private static final String FIELD = REQUEST + "/fields/{fieldName}";

    private final SignatureRequestService requests;
    private final SignatureManagementService management;

    @Inject
    public SignatureRequestRoutes(SignatureRequestService requests, SignatureManagementService management) {
        this.requests = requests;
        this.management = management;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/signing/generations/{generationId}", this::ask, StationPermission.DOCUMENT_EDIT_MEMBER);
        routes.post(
                prefix + "/signing/generations/{generationId}/request",
                this::request,
                StationPermission.DOCUMENT_EDIT_MEMBER);
        routes.get(prefix + REQUEST, this::view, StationPermission.DOCUMENT_EDIT_MEMBER);
        routes.post(prefix + REQUEST + "/withdraw", this::withdraw, StationPermission.DOCUMENT_EDIT_MEMBER);
        routes.post(prefix + REQUEST + "/rectify", this::rectify, StationPermission.DOCUMENT_EDIT_MEMBER);
        routes.post(prefix + FIELD + "/paper", this::confirmOnPaper, StationPermission.DOCUMENT_EDIT_MEMBER);
        routes.post(prefix + FIELD + "/waive", this::waive, StationPermission.DOCUMENT_EDIT_MEMBER);
        routes.post(prefix + FIELD + "/withdraw", this::withdrawField, StationPermission.DOCUMENT_EDIT_MEMBER);
    }

    /** Request body naming the corrected document a request is asked anew on. */
    public record RectifyRequest(@Nullable Integer generationId) {}

    @OpenApi(
            path = "/api/v1" + REQUEST,
            methods = HttpMethod.GET,
            summary = "A request for signatures with each field, its signer, its statement and the act that signed it",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "requestUid", type = UUID.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ManagedRequestResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void view(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(ManagedRequestResponse.of(management.requireOwnedView(session, pathUuid(ctx, "requestUid"))));
    }

    @OpenApi(
            path = "/api/v1" + REQUEST + "/withdraw",
            methods = HttpMethod.POST,
            summary = "Stop asking for every signature a request still waits for",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "requestUid", type = UUID.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ManagedRequestResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void withdraw(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(ManagedRequestResponse.of(management.requireOwnedThenWithdraw(session, pathUuid(ctx, "requestUid"))));
    }

    @OpenApi(
            path = "/api/v1" + REQUEST + "/rectify",
            methods = HttpMethod.POST,
            summary = "Ask a request's signatures anew on a document generated again after a change",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "requestUid", type = UUID.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RectifyRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ManagedRequestResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void rectify(Context ctx) {
        var session = StationSession.from(ctx);
        var body = ctx.bodyAsClass(RectifyRequest.class);
        var replacement = management.requireOwnedThenRectify(session, pathUuid(ctx, "requestUid"), body.generationId());
        ctx.status(HttpStatus.CREATED).json(ManagedRequestResponse.of(replacement));
    }

    @OpenApi(
            path = "/api/v1" + FIELD + "/paper",
            methods = HttpMethod.POST,
            summary = "Record that a signature field was signed on paper",
            tags = {"Signing"},
            pathParams = {
                @OpenApiParam(name = "requestUid", type = UUID.class, required = true),
                @OpenApiParam(name = "fieldName", required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ManagedRequestResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void confirmOnPaper(Context ctx) {
        var session = StationSession.from(ctx);
        var uid = pathUuid(ctx, "requestUid");
        ctx.json(ManagedRequestResponse.of(
                management.requireOwnedThenConfirmOnPaper(session, uid, ctx.pathParam("fieldName"))));
    }

    @OpenApi(
            path = "/api/v1" + FIELD + "/waive",
            methods = HttpMethod.POST,
            summary = "Let a signature field go without a signature",
            tags = {"Signing"},
            pathParams = {
                @OpenApiParam(name = "requestUid", type = UUID.class, required = true),
                @OpenApiParam(name = "fieldName", required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ManagedRequestResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void waive(Context ctx) {
        var session = StationSession.from(ctx);
        var uid = pathUuid(ctx, "requestUid");
        ctx.json(ManagedRequestResponse.of(management.requireOwnedThenWaive(session, uid, ctx.pathParam("fieldName"))));
    }

    @OpenApi(
            path = "/api/v1" + FIELD + "/withdraw",
            methods = HttpMethod.POST,
            summary = "Stop asking for one signature field",
            tags = {"Signing"},
            pathParams = {
                @OpenApiParam(name = "requestUid", type = UUID.class, required = true),
                @OpenApiParam(name = "fieldName", required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ManagedRequestResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void withdrawField(Context ctx) {
        var session = StationSession.from(ctx);
        var uid = pathUuid(ctx, "requestUid");
        ctx.json(ManagedRequestResponse.of(
                management.requireOwnedThenWithdrawField(session, uid, ctx.pathParam("fieldName"))));
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

    /**
     * A request for signatures as its document's manager sees it.
     *
     * @param uid             the request
     * @param state           how it stands
     * @param documentId      the member document it is on, or null once that was deleted
     * @param documentTitle   that document's title, or null once it was deleted
     * @param memberName      the official name of the member the document is about, as it was when asked
     * @param contentSha256   SHA-256 of the frozen document every signature binds to
     * @param createdAt       when the signatures were asked for
     * @param closedAt        when the request stopped waiting, or null while it is open
     * @param supersededBy    the request that replaced it after a correction, or null
     * @param retentionMonths how many months it is kept after the member has gone, or null for only while the
     *                        member is a member
     * @param fields          each field, in the order they were asked for
     * @param corrections     documents generated for the member since, which the request can be asked anew on
     */
    public record ManagedRequestResponse(
            UUID uid,
            RequestState state,
            @Nullable Integer documentId,
            @Nullable String documentTitle,
            String memberName,
            String contentSha256,
            Instant createdAt,
            @Nullable Instant closedAt,
            @Nullable UUID supersededBy,
            @Nullable Integer retentionMonths,
            List<ManagedFieldResponse> fields,
            List<CorrectionResponse> corrections) {
        static ManagedRequestResponse of(ManagedSignatureRequest managed) {
            var request = managed.request();
            return new ManagedRequestResponse(
                    request.uid(),
                    request.state(),
                    request.documentId(),
                    managed.documentTitle(),
                    request.memberName(),
                    request.contentSha256(),
                    request.createdAt(),
                    request.closedAt(),
                    managed.supersededBy(),
                    request.retentionMonths(),
                    managed.fields().stream().map(ManagedFieldResponse::of).toList(),
                    managed.corrections().stream().map(CorrectionResponse::of).toList());
        }
    }

    /**
     * One field of a request as its document's manager sees it.
     *
     * @param fieldName     the field's name in the document
     * @param role          who the field asks for
     * @param signerName    the official name of the person it names, or null where any guardian may sign or
     *                      nobody is named
     * @param capacity      in what capacity it is signed
     * @param statement     what its signer confirms
     * @param state         how it stands
     * @param nobodyCanSign whether it is open and nobody can sign it, so only paper, waiving or withdrawing
     *                      settles it
     * @param settledAt     when it stopped being open, or null while it is open
     * @param settledByName the official name of whoever settled it, or null
     * @param act           the act that signed it, or null where no act did
     */
    public record ManagedFieldResponse(
            String fieldName,
            FieldRole role,
            @Nullable String signerName,
            SignerCapacity capacity,
            String statement,
            FieldState state,
            boolean nobodyCanSign,
            @Nullable Instant settledAt,
            @Nullable String settledByName,
            @Nullable SignedActResponse act) {
        static ManagedFieldResponse of(ManagedSignatureRequest.ManagedField managed) {
            var field = managed.field();
            var evidence = managed.evidence();
            return new ManagedFieldResponse(
                    field.fieldName(),
                    field.role(),
                    field.signerName(),
                    field.capacity(),
                    field.statement(),
                    field.state(),
                    managed.nobodyCanSign(),
                    field.settledAt(),
                    field.settledByName(),
                    evidence == null ? null : SignedActResponse.of(evidence));
        }
    }

    /**
     * What the act that signed a field proves, in short. The whole evidence is in the sealed document.
     *
     * @param signerName        the official name of the person whose signature it is
     * @param accountHolderName the official name of the account holder whose step-up confirmed it
     * @param capacity          in what capacity it was signed
     * @param proof             how the account holder confirmed it
     * @param bound             whether the proof itself is bound to the document, rather than only recorded
     * @param userVerified      whether the passkey or security key checked who held it, or null for another proof
     * @param signedAt          when it was signed, by this server's clock
     * @param sealed            whether a sealed version of the document carries it yet
     */
    public record SignedActResponse(
            String signerName,
            String accountHolderName,
            SignerCapacity capacity,
            StepUpProof proof,
            boolean bound,
            @Nullable Boolean userVerified,
            Instant signedAt,
            boolean sealed) {
        static SignedActResponse of(StoredEvidence stored) {
            var evidence = stored.evidence();
            var act = evidence.act();
            return new SignedActResponse(
                    act.signerName(),
                    act.accountHolderName(),
                    act.signer().capacity(),
                    evidence.proof(),
                    evidence.boundToDocument(),
                    evidence instanceof SigningEvidence.WebAuthnBound bound ? bound.userVerified() : null,
                    act.signedAt(),
                    stored.sealedSha256() != null);
        }
    }

    /**
     * A document a request can be asked anew on after a correction.
     *
     * @param generationId the generation log entry of the document
     * @param templateName what its template is called now
     * @param generatedAt  when it was generated
     */
    public record CorrectionResponse(int generationId, String templateName, Instant generatedAt) {
        static CorrectionResponse of(ManagedSignatureRequest.Correction correction) {
            return new CorrectionResponse(
                    correction.generationId(), correction.templateName(), correction.generatedAt());
        }
    }
}
