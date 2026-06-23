/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.form.service.PublicFormRateLimiter;
import dev.chojo.ember.feature.form.service.SubmitterHashService;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.ClientIp;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public, unauthenticated routes for the form feature.
 *
 * <p>Today: the anonymous submit endpoint behind public-page cells (FORMS_CTA,
 * POLL_EMBED). The endpoint accepts a submission for any {@link FormPurpose#CONTACT}
 * or {@link FormPurpose#POLL} form belonging to the station identified by
 * {@code stationUid} that is currently accepting responses. Submissions are
 * keyed off a {@link SubmitterHashService} hash of the real client IP — no raw
 * IP ever lands on disk.
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class PublicFormRoutes implements Routes {
    private final FormService formService;
    private final StationRepository stationRepository;
    private final SubmitterHashService hashService;
    private final PublicFormRateLimiter rateLimiter;
    private final ConsentService consentService;
    private final Network network;

    @Inject
    public PublicFormRoutes(
            FormService formService,
            StationRepository stationRepository,
            SubmitterHashService hashService,
            PublicFormRateLimiter rateLimiter,
            ConsentService consentService,
            Network network) {
        this.formService = formService;
        this.stationRepository = stationRepository;
        this.hashService = hashService;
        this.rateLimiter = rateLimiter;
        this.consentService = consentService;
        this.network = network;
    }

    private static UUID parseUid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Invalid UUID: " + raw);
        }
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/{stationUid}/forms/{publicUid}", this::getPublicForm);
        routes.post(prefix + "/public/{stationUid}/forms/{publicUid}/responses", this::submitAnonymous);
    }

    @OpenApi(
            path = "/api/v1/public/{stationUid}/forms/{publicUid}",
            methods = HttpMethod.GET,
            summary = "Public form definition for anonymous submission",
            tags = {"Public Forms"},
            pathParams = {
                @OpenApiParam(name = "stationUid", type = String.class, required = true),
                @OpenApiParam(name = "publicUid", type = String.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicForm.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getPublicForm(Context ctx) {
        Form form = resolvePublicForm(ctx);
        var questions = formService.findQuestions(form.id()).stream()
                .map(q -> new PublicFormQuestion(
                        q.id(), q.formQuestionType().name(), q.title(), q.description(), q.required(), q.config()))
                .toList();
        ctx.json(new PublicForm(
                form.publicUid().toString(), form.title(), form.description(), form.purpose(), questions));
    }

    @OpenApi(
            path = "/api/v1/public/{stationUid}/forms/{publicUid}/responses",
            methods = HttpMethod.POST,
            summary = "Submit an anonymous response to a public form",
            tags = {"Public Forms"},
            pathParams = {
                @OpenApiParam(name = "stationUid", type = String.class, required = true),
                @OpenApiParam(name = "publicUid", type = String.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PublicSubmitRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = PublicSubmitResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "429", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void submitAnonymous(Context ctx) {
        Form form = resolvePublicForm(ctx);
        if (!formService.isAcceptingResponses(form)) {
            throw new BadRequestResponse("Form is not accepting responses");
        }

        InetAddress clientIp = ClientIp.resolve(ctx, network);
        byte[] submitterHash = hashService.hash(clientIp, form.id());

        var retryAfter = rateLimiter.tryAcquire(form.id(), submitterHash);
        if (retryAfter.isPresent()) {
            ctx.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(retryAfter.get()))
                    .json(new ErrorResponseWrapper("Rate limit exceeded"));
            return;
        }

        if (form.purpose() == FormPurpose.POLL && formService.hasAnonymousResponded(form.id(), submitterHash)) {
            throw new ConflictResponse("Dieses Formular hast du bereits ausgefüllt.");
        }

        var req = ctx.bodyAsClass(PublicSubmitRequest.class);
        var consent =
                consentService.requireAcceptance(ctx, req.consentVersion(), req.privacyVersion(), req.tosVersion());
        try {
            var response = formService.submitAnonymousResponse(form.id(), submitterHash, req.answers(), consent);
            ctx.status(HttpStatus.CREATED).json(new PublicSubmitResponse(response.id()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private Form resolvePublicForm(Context ctx) {
        UUID stationUid = parseUid(ctx.pathParam("stationUid"));
        UUID formUid = parseUid(ctx.pathParam("publicUid"));
        var station = stationRepository
                .findByUid(stationUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown station: " + stationUid));
        var form = formService
                .findByPublicUid(formUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown form: " + formUid));
        if (form.stationId() != station.id()) {
            throw new NotFoundResponse("Form " + formUid + " does not belong to station " + stationUid);
        }
        if (form.purpose() != FormPurpose.CONTACT && form.purpose() != FormPurpose.POLL) {
            throw new NotFoundResponse("Form " + formUid + " has purpose " + form.purpose()
                    + " — only CONTACT and POLL forms are publicly submittable");
        }
        return form;
    }

    @OpenApiName("PublicForm")
    public record PublicForm(
            String publicUid,
            String title,
            String description,
            FormPurpose purpose,
            List<PublicFormQuestion> questions) {}

    @OpenApiName("PublicFormQuestion")
    public record PublicFormQuestion(
            int id,
            String questionType,
            String title,
            String description,
            boolean required,
            FormQuestionConfig config) {}

    @OpenApiName("PublicFormSubmitRequest")
    public record PublicSubmitRequest(
            Map<Integer, FormAnswerValue> answers, String consentVersion, String privacyVersion, String tosVersion) {}

    @OpenApiName("PublicFormSubmitResponse")
    public record PublicSubmitResponse(int responseId) {}
}
