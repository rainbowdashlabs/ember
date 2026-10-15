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
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.SignerEntryDraft;
import dev.chojo.ember.feature.signing.entity.SigningAnswer;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.entity.WitnessPerson;
import dev.chojo.ember.feature.signing.entity.WitnessedField;
import dev.chojo.ember.feature.signing.route.SigningBatchRoutes.BatchCompleteRequest;
import dev.chojo.ember.feature.signing.route.SigningBatchRoutes.BatchCompleteResponse;
import dev.chojo.ember.feature.signing.route.SigningBatchRoutes.BatchStartResponse;
import dev.chojo.ember.feature.signing.route.SigningRoutes.FillInResponse;
import dev.chojo.ember.feature.signing.route.SigningRoutes.SigningCompleteResponse;
import dev.chojo.ember.feature.signing.service.WitnessedSigningService;
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

import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Signatures collected in person by a witness who holds {@link StationPermission#DOCUMENT_SIGN_OTHERS}: the
 * people they reach, what they may collect from one of them, a document generated on the spot, and the two
 * halves of an act a person signs in front of them.
 *
 * <p>A field is read, its document served and its act started only while the witness may collect it, and
 * the document comes exactly as its request froze it. The completion takes the picture the person drew and
 * the witness's own proof, limited by the witness's step-up buckets ({@link SigningConfirmations#throttle}).
 */
@Singleton
public class WitnessedSigningRoutes implements Routes {
    private static final String PDF = "application/pdf";

    private final WitnessedSigningService witnessed;
    private final AuthRateLimiter rateLimiter;

    @Inject
    public WitnessedSigningRoutes(WitnessedSigningService witnessed, AuthRateLimiter rateLimiter) {
        this.witnessed = witnessed;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        var role = StationPermission.DOCUMENT_SIGN_OTHERS;
        routes.get(prefix + "/signing/witness/people", this::people, role);
        routes.get(prefix + "/signing/witness/people/{memberId}/offers", this::offers, role);
        routes.post(prefix + "/signing/witness/people/{memberId}/templates/{templateId}", this::generate, role);
        routes.get(prefix + "/signing/witness/fields/{fieldId}", this::field, role);
        routes.get(prefix + "/signing/witness/fields/{fieldId}/document", this::document, role);
        routes.get(prefix + "/signing/witness/fields/{fieldId}/fill-ins", this::fillIns, role);
        routes.post(prefix + "/signing/witness/fields/{fieldId}/start", this::start, role);
        routes.post(prefix + "/signing/witness/complete", this::complete, role);
    }

    @OpenApi(
            path = "/api/v1/signing/witness/people",
            methods = HttpMethod.GET,
            summary = "The people the reader may collect signatures from in person",
            tags = {"Signing"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = WitnessPersonResponse[].class)))
    private void people(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(witnessed.people(session).stream()
                .map(WitnessPersonResponse::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/signing/witness/people/{memberId}/offers",
            methods = HttpMethod.GET,
            summary = "What the reader may collect in person from one person: open fields and templates",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = WitnessOffersResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void offers(Context ctx) {
        var session = StationSession.from(ctx);
        var person = witnessed.requireOwnedPerson(session, pathInt(ctx, "memberId"));
        var offers = witnessed.offers(session, person);
        ctx.json(new WitnessOffersResponse(
                offers.fields().stream().map(WitnessFieldResponse::of).toList(),
                offers.templates().stream().map(WitnessTemplateResponse::of).toList()));
    }

    @OpenApi(
            path = "/api/v1/signing/witness/people/{memberId}/templates/{templateId}",
            methods = HttpMethod.POST,
            summary = "Generate a document for a person on the spot, to sign it in front of the reader",
            tags = {"Signing"},
            pathParams = {
                @OpenApiParam(name = "memberId", type = Integer.class, required = true),
                @OpenApiParam(name = "templateId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = WitnessFieldResponse[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void generate(Context ctx) {
        var session = StationSession.from(ctx);
        var person = witnessed.requireOwnedPerson(session, pathInt(ctx, "memberId"));
        ctx.json(witnessed.generate(session, person, pathInt(ctx, "templateId")).stream()
                .map(WitnessFieldResponse::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/signing/witness/fields/{fieldId}",
            methods = HttpMethod.GET,
            summary = "One open field the reader may collect in person, with the people it names",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "fieldId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = WitnessFieldResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void field(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(WitnessFieldResponse.of(witnessed.requireOwnedField(session, pathInt(ctx, "fieldId"))));
    }

    @OpenApi(
            path = "/api/v1/signing/witness/fields/{fieldId}/document",
            methods = HttpMethod.GET,
            summary = "The document of a field the reader may collect in person, exactly as it was frozen",
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
        var document = witnessed.document(session, witnessed.requireOwnedField(session, pathInt(ctx, "fieldId")));
        FileResponse.send(ctx, PDF, document.fileName(), document.pdf());
    }

    @OpenApi(
            path = "/api/v1/signing/witness/fields/{fieldId}/fill-ins",
            methods = HttpMethod.GET,
            summary = "The fields the person fills in when they sign a field in front of the reader",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "fieldId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FillInResponse[].class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void fillIns(Context ctx) {
        var session = StationSession.from(ctx);
        var field = witnessed.requireOwnedField(session, pathInt(ctx, "fieldId"));
        ctx.json(witnessed.fillIns(session, field).stream()
                .map(FillInResponse::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/signing/witness/fields/{fieldId}/start",
            methods = HttpMethod.POST,
            summary = "Start the act of a person signing a field in front of the reader",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "fieldId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WitnessStartRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BatchStartResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void start(Context ctx) {
        var session = StationSession.from(ctx);
        var field = witnessed.requireOwnedField(session, pathInt(ctx, "fieldId"));
        var body = ctx.bodyAsClass(WitnessStartRequest.class);
        Integer signerId = body.signerId();
        if (signerId == null) throw DocumentRefusal.SIGNING_WITNESS_SIGNER_NOT_NAMED.raise();
        ctx.json(BatchStartResponse.of(witnessed.start(session, field, signerId, body.entries())));
    }

    @OpenApi(
            path = "/api/v1/signing/witness/complete",
            methods = HttpMethod.POST,
            summary = "Confirm, as the witness, that a person signed in front of the reader",
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
        var body = SigningConfirmations.body(ctx, SigningBatchRoutes.MAX_COMPLETE_BYTES, BatchCompleteRequest.class);
        String startToken = body.startToken();
        StepUpProof proof = body.proof();
        if (startToken == null || startToken.isBlank() || proof == null) {
            throw DocumentRefusal.SIGNING_ANSWER_MISSING.raise();
        }
        var pictures = SigningBatchRoutes.picturesOf(body.pictures());
        SigningConfirmations.throttle(ctx, rateLimiter, session.accountId(), proof);
        var outcomes = witnessed.complete(
                session,
                startToken,
                new SigningAnswer(proof, body.credentialJson(), body.secret()),
                pictures,
                new SigningCircumstances(ctx.ip(), ctx.userAgent()));
        ctx.json(new BatchCompleteResponse(
                outcomes.stream().map(SigningCompleteResponse::of).toList()));
    }

    /**
     * A person the reader may pick.
     *
     * @param memberId the member
     * @param name     their official name
     * @param userType what kind of member they are
     */
    public record WitnessPersonResponse(int memberId, String name, StationUserType userType) {
        static WitnessPersonResponse of(WitnessPerson person) {
            return new WitnessPersonResponse(person.memberId(), person.name(), person.userType());
        }
    }

    /**
     * A template a document can be generated from on the spot.
     *
     * @param templateId the template
     * @param name       what it is called
     */
    public record WitnessTemplateResponse(int templateId, String name) {
        static WitnessTemplateResponse of(DocumentTemplate template) {
            return new WitnessTemplateResponse(template.id(), template.name());
        }
    }

    /**
     * An open field the reader may collect in person.
     *
     * @param fieldId       the field
     * @param requestUid    the request it belongs to
     * @param documentId    the member document it is on, or null once that was deleted
     * @param documentTitle the title the document is filed under, or null once it was deleted
     * @param memberId      the member the document is about
     * @param memberName    their official name
     * @param fieldName     the field's name in the document
     * @param role          who the field asks for
     * @param statement     the statement the signer confirms
     * @param signers       the people the field names, one of whom signs in front of the reader
     */
    public record WitnessFieldResponse(
            int fieldId,
            UUID requestUid,
            @Nullable Integer documentId,
            @Nullable String documentTitle,
            @Nullable Integer memberId,
            String memberName,
            String fieldName,
            FieldRole role,
            String statement,
            List<WitnessPersonResponse> signers) {
        static WitnessFieldResponse of(WitnessedField witnessed) {
            var pending = witnessed.pending();
            var field = pending.field();
            return new WitnessFieldResponse(
                    field.id(),
                    pending.requestUid(),
                    pending.documentId(),
                    pending.documentTitle(),
                    field.memberId(),
                    pending.memberName(),
                    field.fieldName(),
                    field.role(),
                    field.statement(),
                    witnessed.signers().stream().map(WitnessPersonResponse::of).toList());
        }
    }

    /**
     * What the reader may collect in person from one person.
     *
     * @param fields    the open fields of the person's documents
     * @param templates the templates a document can be generated from for them on the spot
     */
    public record WitnessOffersResponse(List<WitnessFieldResponse> fields, List<WitnessTemplateResponse> templates) {}

    /**
     * Who signs in front of the reader, and what they typed.
     *
     * @param signerId the person, one of those the field names
     * @param entries  what they typed into the fields of the document they fill in, or null for none
     */
    public record WitnessStartRequest(
            @Nullable Integer signerId, @Nullable List<SignerEntryDraft> entries) {}
}
