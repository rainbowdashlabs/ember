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
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.signing.entity.BatchAttempt;
import dev.chojo.ember.feature.signing.entity.BatchChoice;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.SignatureImageSource;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.SignerEntryDraft;
import dev.chojo.ember.feature.signing.entity.SigningAnswer;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.entity.SigningPicture;
import dev.chojo.ember.feature.signing.entity.SigningPictures;
import dev.chojo.ember.feature.signing.service.SigningActService;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Signing several fields in one go: every field the reader may sign now, across the documents they are
 * asked to sign and across the members in their care, read and confirmed field by field and proven once.
 *
 * <p>The start takes the chosen fields in the order they are signed, each with what was typed into the fields
 * of its document the signer fills in, and answers what the signer confirms for each with one passkey or
 * security key request covering all of them. The completion takes the start's token, one proof, and the
 * signature pictures: the account holder's, and one for each member signing their own field through the
 * account. Attempts are limited as a single field's are ({@link SigningConfirmations#throttle}).
 */
@Singleton
public class SigningBatchRoutes implements Routes {
    /** The largest confirmation taken: a few pictures of the largest size in Base64, with the rest of it. */
    static final int MAX_COMPLETE_BYTES = SigningRoutes.MAX_COMPLETE_BYTES * 4;

    private final SigningActService acts;
    private final AuthRateLimiter rateLimiter;

    @Inject
    public SigningBatchRoutes(SigningActService acts, AuthRateLimiter rateLimiter) {
        this.acts = acts;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/signing/batch/start", this::start, StationPermission.LOGIN);
        routes.post(prefix + "/signing/batch/complete", this::complete, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/signing/batch/start",
            methods = HttpMethod.POST,
            summary = "Start signing several fields with one proof",
            tags = {"Signing"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BatchStartRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BatchStartResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void start(Context ctx) {
        var session = StationSession.from(ctx);
        var body = ctx.bodyAsClass(BatchStartRequest.class);
        var choices = Objects.requireNonNullElse(body.fields(), List.<BatchFieldChoice>of()).stream()
                .map(choice -> new BatchChoice(choice.fieldId(), choice.entries()))
                .toList();
        ctx.json(BatchStartResponse.of(acts.startBatch(session, choices)));
    }

    @OpenApi(
            path = "/api/v1/signing/batch/complete",
            methods = HttpMethod.POST,
            summary = "Confirm a started act on several fields with one proof",
            tags = {"Signing"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BatchCompleteRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BatchCompleteResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "410", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "429", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void complete(Context ctx) {
        var session = StationSession.from(ctx);
        var body = SigningConfirmations.body(ctx, MAX_COMPLETE_BYTES, BatchCompleteRequest.class);
        String startToken = body.startToken();
        StepUpProof proof = body.proof();
        if (startToken == null || startToken.isBlank() || proof == null) {
            throw DocumentRefusal.SIGNING_ANSWER_MISSING.raise();
        }
        var pictures = picturesOf(body.pictures());
        SigningConfirmations.throttle(ctx, rateLimiter, session.accountId(), proof);
        var outcomes = acts.completeBatch(
                session,
                startToken,
                new SigningAnswer(proof, body.credentialJson(), body.secret()),
                pictures,
                new SigningCircumstances(ctx.ip(), ctx.userAgent()));
        ctx.json(new BatchCompleteResponse(
                outcomes.stream().map(SigningRoutes.SigningCompleteResponse::of).toList()));
    }

    /** The pictures as sent, one per person, refusing two for one person. */
    private static SigningPictures picturesOf(@Nullable List<BatchPicture> sent) {
        SigningPicture accountHolder = null;
        var members = new HashMap<Integer, SigningPicture>();
        for (var picture : Objects.requireNonNullElse(sent, List.<BatchPicture>of())) {
            var decoded = SigningConfirmations.picture(
                    picture.signatureImage(), picture.signatureSource(), picture.keepSignature());
            Integer memberId = picture.memberId();
            boolean twice = memberId == null ? accountHolder != null : members.containsKey(memberId);
            if (twice) throw DocumentRefusal.SIGNING_PICTURE_TWICE.raise();
            if (memberId == null) {
                accountHolder = decoded;
            } else {
                members.put(memberId, decoded);
            }
        }
        return new SigningPictures(Objects.requireNonNullElse(accountHolder, SigningPicture.SAVED), members);
    }

    /**
     * The fields to sign in one go.
     *
     * @param fields the fields in the order they are signed, or null for none
     */
    public record BatchStartRequest(@Nullable List<BatchFieldChoice> fields) {}

    /**
     * One field to sign in one go.
     *
     * @param fieldId the field
     * @param entries what the signer typed into the fields of its document they fill in, or null for none
     */
    public record BatchFieldChoice(int fieldId, @Nullable List<SignerEntryDraft> entries) {}

    /**
     * A started act on several fields.
     *
     * @param startToken          the token to complete the act with
     * @param expiresAt           when the start can no longer be completed
     * @param batchUid            the batch the fields are confirmed in
     * @param fields              the fields in the order they are signed, each with what is confirmed for it
     * @param acceptedProofs      the proofs the signer may confirm with
     * @param webAuthnOptionsJson what to hand to {@code navigator.credentials.get}, or null where no passkey
     *                            or security key is accepted
     */
    public record BatchStartResponse(
            String startToken,
            Instant expiresAt,
            UUID batchUid,
            List<BatchFieldResponse> fields,
            Set<StepUpProof> acceptedProofs,
            @Nullable String webAuthnOptionsJson) {
        static BatchStartResponse of(BatchAttempt attempt) {
            return new BatchStartResponse(
                    attempt.startToken(),
                    attempt.expiresAt(),
                    attempt.batchUid(),
                    attempt.items().stream().map(BatchFieldResponse::of).toList(),
                    attempt.acceptedProofs(),
                    attempt.webAuthnOptionsJson());
        }
    }

    /**
     * What the signer confirms for one field of a started act.
     *
     * @param fieldId            the field
     * @param requestUid         the request it belongs to
     * @param documentId         the member document it is on, or null once that was deleted
     * @param documentTitle      the title the document is filed under, or null once it was deleted
     * @param fieldName          the field's name in the document
     * @param role               who the field asks for
     * @param capacity           in what capacity the signer signs
     * @param memberId           the member the field concerns, or null where the account holder signs for
     *                           themselves
     * @param statement          the statement the signer confirms
     * @param signerName         the official name of whoever signs
     * @param accountHolderName  the official name of the holder of the confirming account
     * @param memberName         the official name of the member the act concerns, or null
     * @param documentMemberName the official name of the member the document is about
     * @param contentSha256      SHA-256 of the document the act binds to, lower-case hexadecimal
     */
    public record BatchFieldResponse(
            int fieldId,
            UUID requestUid,
            @Nullable Integer documentId,
            @Nullable String documentTitle,
            String fieldName,
            FieldRole role,
            SignerCapacity capacity,
            @Nullable Integer memberId,
            String statement,
            String signerName,
            String accountHolderName,
            @Nullable String memberName,
            String documentMemberName,
            String contentSha256) {
        static BatchFieldResponse of(BatchAttempt.Item item) {
            var pending = item.field();
            var field = pending.field();
            return new BatchFieldResponse(
                    field.id(),
                    pending.requestUid(),
                    pending.documentId(),
                    pending.documentTitle(),
                    field.fieldName(),
                    field.role(),
                    item.signer().capacity(),
                    item.signer().memberId(),
                    field.statement(),
                    item.signerName(),
                    item.accountHolderName(),
                    item.memberName(),
                    pending.memberName(),
                    item.contentSha256());
        }
    }

    /**
     * The signer's one confirmation of a started act on several fields.
     *
     * @param startToken     the token the start answered with
     * @param proof          the proof given; a backup code or another device is never taken
     * @param credentialJson the passkey or security key answer as {@code navigator.credentials.get} returned
     *                       it, for those two
     * @param secret         the authenticator app code or the password, for those two; checked and never kept
     * @param pictures       the signature pictures, at most one per person; the account holder's saved picture
     *                       where none is sent for them
     */
    public record BatchCompleteRequest(
            @Nullable String startToken,
            @Nullable StepUpProof proof,
            @Nullable String credentialJson,
            @Nullable String secret,
            @Nullable List<BatchPicture> pictures) {}

    /**
     * The signature picture one person signs with in a confirmation.
     *
     * @param memberId        the member signing their own field through the account, or null for the account
     *                        holder, who signs with it for themselves and as guardian
     * @param signatureImage  the picture made for this act, a PNG, JPEG or WebP picture in Base64, or null to
     *                        sign with the picture the account keeps, which only the account holder may
     * @param signatureSource how that picture was made, or null
     * @param keepSignature   whether the account holder's picture replaces the one the account keeps
     */
    public record BatchPicture(
            @Nullable Integer memberId,
            @Nullable String signatureImage,
            @Nullable SignatureImageSource signatureSource,
            @Nullable Boolean keepSignature) {}

    /**
     * Where each field stands after the act.
     *
     * @param fields the fields in the order they were signed
     */
    public record BatchCompleteResponse(List<SigningRoutes.SigningCompleteResponse> fields) {}
}
