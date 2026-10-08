/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.FileResponse;
import dev.chojo.ember.api.RateLimits;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.OpenSignature;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.SignatureImageSource;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.SignerEntryDraft;
import dev.chojo.ember.feature.signing.entity.SigningAnswer;
import dev.chojo.ember.feature.signing.entity.SigningAttempt;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.entity.SigningOutcome;
import dev.chojo.ember.feature.signing.entity.SigningPicture;
import dev.chojo.ember.feature.signing.service.SignatureRequestService;
import dev.chojo.ember.feature.signing.service.SigningActService;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
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
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * A member's own signing acts at their station: the fields they are asked to sign, at home and for the
 * members in their care, and the two halves of the act on one field.
 *
 * <p>A field waiting for the reader can be read on its own, with the document it asks them to sign served
 * exactly as the request froze it, so the signing screen shows the bytes the act binds to and nothing else.
 *
 * <p>Starting an act keeps it on the server and answers what the signer confirms: the statement, the
 * official names, the hash of the document and the proofs they may give, with the passkey or security key
 * request built around the act's challenge. Completing it takes the token of the start and one proof. The
 * code and password attempts are limited by the same buckets as every other step-up of the account, the
 * passkey and security key answers by the second-factor bucket, so signing opens no way around them.
 */
@Singleton
public class SigningRoutes implements Routes {
    private static final String PDF = "application/pdf";

    private final SigningActService acts;
    private final SignatureRequestService requests;
    private final AuthRateLimiter rateLimiter;

    @Inject
    public SigningRoutes(SigningActService acts, SignatureRequestService requests, AuthRateLimiter rateLimiter) {
        this.acts = acts;
        this.requests = requests;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/signing/open", this::open, StationPermission.LOGIN);
        routes.get(prefix + "/signing/fields/{fieldId}", this::field, StationPermission.LOGIN);
        routes.get(prefix + "/signing/fields/{fieldId}/document", this::document, StationPermission.LOGIN);
        routes.post(prefix + "/signing/fields/{fieldId}/start", this::start, StationPermission.LOGIN);
        routes.post(prefix + "/signing/fields/{fieldId}/complete", this::complete, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/signing/open",
            methods = HttpMethod.GET,
            summary = "The signature fields waiting for the reader, and for the members in their care",
            tags = {"Signing"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = OpenSignatureResponse[].class)))
    private void open(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(requests.openFor(session).stream()
                .map(OpenSignatureResponse::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/signing/fields/{fieldId}",
            methods = HttpMethod.GET,
            summary = "One signature field waiting for the reader, with how they would sign it",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "fieldId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = OpenSignatureResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void field(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(OpenSignatureResponse.of(acts.requireOwnedField(session, pathInt(ctx, "fieldId"))));
    }

    @OpenApi(
            path = "/api/v1/signing/fields/{fieldId}/document",
            methods = HttpMethod.GET,
            summary = "The document a field asks the reader to sign, exactly as it was frozen",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "fieldId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void document(Context ctx) {
        var session = StationSession.from(ctx);
        var document = acts.requireOwnedFieldDocument(session, pathInt(ctx, "fieldId"));
        FileResponse.send(ctx, PDF, document.fileName(), document.pdf());
    }

    @OpenApi(
            path = "/api/v1/signing/fields/{fieldId}/start",
            methods = HttpMethod.POST,
            summary = "Start signing a field",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "fieldId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SigningStartRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = SigningStartResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void start(Context ctx) {
        var session = StationSession.from(ctx);
        var field = acts.requireOwnedField(session, pathInt(ctx, "fieldId"));
        var body = ctx.bodyAsClass(SigningStartRequest.class);
        ctx.json(SigningStartResponse.of(acts.start(session, field, body.entries())));
    }

    @OpenApi(
            path = "/api/v1/signing/fields/{fieldId}/complete",
            methods = HttpMethod.POST,
            summary = "Confirm a started signing act",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "fieldId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SigningCompleteRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = SigningCompleteResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "410", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "429", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void complete(Context ctx) {
        var session = StationSession.from(ctx);
        var body = ctx.bodyAsClass(SigningCompleteRequest.class);
        String startToken = body.startToken();
        StepUpProof proof = body.proof();
        if (startToken == null || startToken.isBlank() || proof == null) {
            throw DocumentRefusal.SIGNING_ANSWER_MISSING.raise();
        }
        throttle(ctx, session.accountId(), proof);
        var outcome = acts.complete(
                session,
                pathInt(ctx, "fieldId"),
                startToken,
                new SigningAnswer(proof, body.credentialJson(), body.secret()),
                pictureOf(body),
                new SigningCircumstances(ctx.ip(), ctx.userAgent()));
        ctx.json(SigningCompleteResponse.of(outcome));
    }

    /** The signature picture the signer sent, or the saved one where they sent none. */
    private static SigningPicture pictureOf(SigningCompleteRequest body) {
        String encoded = body.signatureImage();
        if (encoded == null || encoded.isBlank()) return SigningPicture.SAVED;
        try {
            byte[] made = Base64.getDecoder().decode(encoded.strip());
            return new SigningPicture(made, body.signatureSource(), Boolean.TRUE.equals(body.keepSignature()));
        } catch (IllegalArgumentException e) {
            throw DocumentRefusal.SIGNATURE_IMAGE_NOT_A_PICTURE.raise();
        }
    }

    private void throttle(Context ctx, int accountId, StepUpProof proof) {
        if (proof == StepUpProof.PASSWORD) {
            RateLimits.enforce(
                    DocumentRefusal.SIGNING_PASSWORD_TOO_OFTEN, rateLimiter.tryPasswordStepUp(ctx.ip(), accountId));
            return;
        }
        RateLimits.enforce(
                DocumentRefusal.SIGNING_CONFIRMATION_TOO_OFTEN, rateLimiter.tryTwoFactor(ctx.ip(), accountId));
    }

    /**
     * A signature field waiting for the reader.
     *
     * @param fieldId       the field
     * @param requestUid    the request it belongs to
     * @param documentId    the member document it is on, or null once that was deleted
     * @param documentTitle the title the document is filed under, or null once it was deleted
     * @param memberName    the official name of the member the document is about
     * @param fieldName     the field's name in the document
     * @param role          who the field asks for
     * @param capacity      in what capacity the reader would sign it
     * @param memberId      the member the reader signs for or lets sign through their account, or null
     *                      where they sign for themselves
     * @param signerName    the official name of the person the field names, or null where it names nobody
     * @param statement     the statement the signer confirms
     */
    public record OpenSignatureResponse(
            int fieldId,
            UUID requestUid,
            @Nullable Integer documentId,
            @Nullable String documentTitle,
            String memberName,
            String fieldName,
            FieldRole role,
            SignerCapacity capacity,
            @Nullable Integer memberId,
            @Nullable String signerName,
            String statement) {
        static OpenSignatureResponse of(OpenSignature open) {
            var pending = open.pending();
            var field = pending.field();
            return new OpenSignatureResponse(
                    field.id(),
                    pending.requestUid(),
                    pending.documentId(),
                    pending.documentTitle(),
                    pending.memberName(),
                    field.fieldName(),
                    field.role(),
                    open.signer().capacity(),
                    open.signer().memberId(),
                    field.signerName(),
                    field.statement());
        }
    }

    /**
     * What a signer types into fields of their own, bound into the act.
     *
     * @param entries the filled-in fields, or null for none
     */
    public record SigningStartRequest(@Nullable List<SignerEntryDraft> entries) {}

    /**
     * A started signing act.
     *
     * @param startToken          the token to complete the act with
     * @param expiresAt           when the start can no longer be completed
     * @param fieldId             the field
     * @param requestUid          the request it belongs to
     * @param fieldName           the field's name in the document
     * @param role                who the field asks for
     * @param capacity            in what capacity the signer signs
     * @param statement           the statement the signer confirms
     * @param signerName          the official name of whoever signs
     * @param accountHolderName   the official name of the holder of the confirming account
     * @param memberName          the official name of the member the act concerns, or null where the
     *                            account holder signs for themselves
     * @param documentMemberName  the official name of the member the document is about
     * @param contentSha256       SHA-256 of the document the act binds to, lower-case hexadecimal
     * @param acceptedProofs      the proofs the signer may confirm with
     * @param webAuthnOptionsJson what to hand to {@code navigator.credentials.get}, or null where no passkey
     *                            or security key is accepted
     */
    public record SigningStartResponse(
            String startToken,
            Instant expiresAt,
            int fieldId,
            UUID requestUid,
            String fieldName,
            FieldRole role,
            SignerCapacity capacity,
            String statement,
            String signerName,
            String accountHolderName,
            @Nullable String memberName,
            String documentMemberName,
            String contentSha256,
            Set<StepUpProof> acceptedProofs,
            @Nullable String webAuthnOptionsJson) {
        static SigningStartResponse of(SigningAttempt attempt) {
            var field = attempt.field().field();
            return new SigningStartResponse(
                    attempt.startToken(),
                    attempt.expiresAt(),
                    field.id(),
                    attempt.field().requestUid(),
                    field.fieldName(),
                    field.role(),
                    attempt.signer().capacity(),
                    field.statement(),
                    attempt.signerName(),
                    attempt.accountHolderName(),
                    attempt.memberName(),
                    attempt.field().memberName(),
                    attempt.contentSha256(),
                    attempt.acceptedProofs(),
                    attempt.webAuthnOptionsJson());
        }
    }

    /**
     * The signer's confirmation of a started act.
     *
     * @param startToken     the token the start answered with
     * @param proof          the proof given; a backup code or another device is never taken
     * @param credentialJson the passkey or security key answer as {@code navigator.credentials.get} returned
     *                       it, for those two
     * @param secret         the authenticator app code or the password, for those two; checked and never kept
     * @param signatureImage the signature picture made for this act, a PNG, JPEG or WebP picture in Base64, or
     *                       null to sign with the picture the account keeps
     * @param signatureSource how that picture was made, or null
     * @param keepSignature  whether that picture replaces the one the account keeps, which a member signing
     *                       through another person's account never does
     */
    public record SigningCompleteRequest(
            @Nullable String startToken,
            @Nullable StepUpProof proof,
            @Nullable String credentialJson,
            @Nullable String secret,
            @Nullable String signatureImage,
            @Nullable SignatureImageSource signatureSource,
            @Nullable Boolean keepSignature) {}

    /**
     * Where a field stands after the act.
     *
     * @param fieldId      the field
     * @param fieldName    the field's name in the document
     * @param state        where the field stands
     * @param settledAt    when it was signed
     * @param requestUid   the request it belongs to
     * @param requestState where the request stands, complete once no field waits any more
     * @param proof        the proof the act was confirmed with
     * @param bound        whether that proof is bound to the document
     */
    public record SigningCompleteResponse(
            int fieldId,
            String fieldName,
            FieldState state,
            @Nullable Instant settledAt,
            UUID requestUid,
            RequestState requestState,
            StepUpProof proof,
            boolean bound) {
        static SigningCompleteResponse of(SigningOutcome outcome) {
            var field = outcome.field().field();
            return new SigningCompleteResponse(
                    field.id(),
                    field.fieldName(),
                    field.state(),
                    field.settledAt(),
                    outcome.field().requestUid(),
                    outcome.requestState(),
                    outcome.proof(),
                    outcome.bound());
        }
    }
}
