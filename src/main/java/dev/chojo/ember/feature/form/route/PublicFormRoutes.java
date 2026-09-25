/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.form.service.PublicFormRateLimiter;
import dev.chojo.ember.feature.form.service.SubmitterHashService;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.page.route.SharedPageRoutes;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.util.ClientIp;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.GoneResponse;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * Public, unauthenticated routes for the form feature.
 *
 * <p>A form is reached one of two ways. Its own address, which names the station and the form, is
 * how a form put on a public page is answered: it is published by being there, and the address is as
 * public as the page around it. The link it was sent with reaches it without naming anything, and is
 * how a form goes to the people it is meant for and to nobody else.
 *
 * <p>A form that is only sent by link answers at its link alone: its own address refuses it, so
 * replacing the link really does end every way in that was given out. That is the whole reason for
 * having a link rather than an address.
 *
 * <p>Either way the form must be a {@link FormPurpose#CONTACT} or a {@link FormPurpose#POLL} and be
 * accepting answers, and either way submissions are keyed off a {@link SubmitterHashService} hash of
 * the real client IP, so no raw IP ever lands on disk.
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
    private final StationLogoService logoService;

    @Inject
    public PublicFormRoutes(
            FormService formService,
            StationRepository stationRepository,
            SubmitterHashService hashService,
            PublicFormRateLimiter rateLimiter,
            ConsentService consentService,
            Network network,
            StationLogoService logoService) {
        this.formService = formService;
        this.stationRepository = stationRepository;
        this.hashService = hashService;
        this.rateLimiter = rateLimiter;
        this.consentService = consentService;
        this.network = network;
        this.logoService = logoService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/shared-form/{token}", this::getSharedForm);
        routes.get(prefix + "/public/shared-form/{token}/brand", this::getSharedFormBrand);
        routes.post(prefix + "/public/shared-form/{token}/responses", this::submitToSharedForm);
        routes.get(prefix + "/public/{stationUid}/forms/{publicUid}", this::getPublicForm);
        routes.post(prefix + "/public/{stationUid}/forms/{publicUid}/responses", this::submitAnonymous);
    }

    @OpenApi(
            path = "/api/v1/public/shared-form/{token}",
            methods = HttpMethod.GET,
            summary = "A form reached by the link it was sent with",
            tags = {"Public Forms"},
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicForm.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree("the link is the authorisation: whoever was sent it may read the form, and which station it"
            + " belongs to is the answer rather than part of the question")
    private void getSharedForm(Context ctx) {
        ctx.json(publicView(resolveSharedForm(ctx)));
    }

    @OpenApi(
            path = "/api/v1/public/shared-form/{token}/responses",
            methods = HttpMethod.POST,
            summary = "Answer a form reached by the link it was sent with",
            tags = {"Public Forms"},
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PublicSubmitRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = PublicSubmitResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "429", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree("the same link, answering the form it leads to; the rate limit and the one-answer rule stand in"
            + " for a session here as they do for every public submit")
    private void submitToSharedForm(Context ctx) {
        submit(ctx, resolveSharedForm(ctx));
    }

    /**
     * The station behind a form's link: its name, its picture and its colours, for the wrapper drawn
     * around the form.
     *
     * <p>Its own route because the theme is chosen while the page is rendered on the server, before
     * anything the page asks for has come back, and because that choice must not count as a visit.
     */
    @OpenApi(
            path = "/api/v1/public/shared-form/{token}/brand",
            methods = HttpMethod.GET,
            summary = "The station behind a form's link",
            tags = {"Public Forms"},
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = SharedPageRoutes.SharedBrand.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree("the same link, answering only the name and colours of the station asking")
    private void getSharedFormBrand(Context ctx) {
        var form = resolveSharedForm(ctx);
        var station = stationRepository.findById(form.stationId()).orElseThrow(NotFoundResponse::new);
        ctx.json(SharedPageRoutes.brandOf(station, logoService));
    }

    private Form resolveSharedForm(Context ctx) {
        return formService
                .findByShareToken(ctx.pathParam("token"))
                .orElseThrow(() -> new NotFoundResponse("No form is reached by this link"));
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
        ctx.json(publicView(resolvePublicForm(ctx)));
    }

    /**
     * What a stranger is shown of a form.
     *
     * <p>A form that is not taking answers hands out no questions. It used to hand out all of them
     * whatever its state, so a form nobody had opened was readable in full by anybody holding its
     * address, and the reader learned it was closed only after filling it in and pressing send.
     *
     * <p>The state travels instead, so the page can say which of the three it is rather than
     * offering fields that will be refused. Closed covers both a form somebody closed and one whose
     * end date has passed, because a reader has no use for the difference.
     */
    private PublicForm publicView(Form form) {
        var state = stateOf(form);
        var questions = state == PublicFormState.OPEN
                ? formService.findQuestions(form.id()).stream()
                        .map(q -> new PublicFormQuestion(
                                q.id(),
                                q.formQuestionType().name(),
                                q.title(),
                                q.description(),
                                q.required(),
                                q.config()))
                        .toList()
                : List.<PublicFormQuestion>of();
        return new PublicForm(
                form.publicUid().toString(), form.title(), form.description(), form.purpose(), state, questions);
    }

    private PublicFormState stateOf(Form form) {
        if (formService.isAcceptingResponses(form)) return PublicFormState.OPEN;
        if (form.status() == Form.FormStatus.DRAFT) return PublicFormState.NOT_PUBLISHED;
        if (form.status() == Form.FormStatus.OPEN
                && form.startAt() != null
                && Instant.now().isBefore(form.startAt())) {
            return PublicFormState.NOT_OPEN_YET;
        }
        return PublicFormState.CLOSED;
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
                @OpenApiResponse(status = "410", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "429", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void submitAnonymous(Context ctx) {
        submit(ctx, resolvePublicForm(ctx));
    }

    /**
     * Takes one anonymous answer.
     *
     * <p>The state check stays, as the backstop for a form that closes between the page being opened
     * and the button being pressed. It is no longer the only check: the page above knows the state
     * before it draws a field.
     */
    private void submit(Context ctx, Form form) {
        if (!formService.isAcceptingResponses(form)) {
            throw new GoneResponse("This form is not taking answers");
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

    /**
     * The form a public address names.
     *
     * <p>The station part of the address is whatever the link was built from, its uid or its
     * readable name, the same as every other public address of that station.
     */
    private Form resolvePublicForm(Context ctx) {
        String stationAddress = ctx.pathParam("stationUid");
        UUID formUid = pathUuid(ctx, "publicUid");
        var station = stationRepository
                .findByAddress(stationAddress)
                .orElseThrow(() -> new NotFoundResponse("Unknown station: " + stationAddress));
        var form = formService
                .findByPublicUid(formUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown form: " + formUid));
        if (form.stationId() != station.id()) {
            throw new NotFoundResponse("Form " + formUid + " does not belong to station " + stationAddress);
        }
        if (form.purpose() != FormPurpose.CONTACT && form.purpose() != FormPurpose.POLL) {
            throw new NotFoundResponse("Form " + formUid + " has purpose " + form.purpose()
                    + " - only CONTACT and POLL forms are publicly submittable");
        }
        if (!form.visibility().openlyAddressed()) {
            throw new NotFoundResponse(
                    "Form " + formUid + " is reached by the link it was sent with, and by nothing else");
        }
        return form;
    }

    /**
     * Why a form is not taking answers, in the terms a visitor can act on.
     *
     * <p>{@code CLOSED} covers a form somebody closed and one whose end date has passed alike: both
     * mean the same thing to whoever is holding the link.
     */
    @OpenApiName("PublicFormState")
    public enum PublicFormState {
        OPEN,
        NOT_PUBLISHED,
        NOT_OPEN_YET,
        CLOSED
    }

    @OpenApiName("PublicForm")
    public record PublicForm(
            String publicUid,
            String title,
            String description,
            FormPurpose purpose,
            PublicFormState state,
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
