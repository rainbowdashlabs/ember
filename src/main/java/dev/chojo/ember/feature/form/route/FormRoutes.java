/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.FormRefusal;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormDraft;
import dev.chojo.ember.feature.form.entity.FormPage;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.FormResponse;
import dev.chojo.ember.feature.form.entity.FormVisibility;
import dev.chojo.ember.feature.form.entity.PageEntry;
import dev.chojo.ember.feature.form.entity.PageTarget;
import dev.chojo.ember.feature.form.entity.QuestionAnswerCount;
import dev.chojo.ember.feature.form.entity.QuestionBranch;
import dev.chojo.ember.feature.form.entity.QuestionEntry;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler.FormAnalytics;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler.FormResponseDetail;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler.FormResponseEntry;
import dev.chojo.ember.feature.form.service.FormAnswersRefused;
import dev.chojo.ember.feature.form.service.FormAnswersRefused.AnswersRefusedBody;
import dev.chojo.ember.feature.form.service.FormDirectoryService;
import dev.chojo.ember.feature.form.service.FormDirectoryService.FormListEntry;
import dev.chojo.ember.feature.form.service.FormDirectoryService.FormSearchResult;
import dev.chojo.ember.feature.form.service.FormResponseExportService;
import dev.chojo.ember.feature.form.service.FormResultQuery;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.page.entity.PageUsingForm;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.util.CsvWriter;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * HTTP route handlers for form management, including CRUD operations, question management,
 * access restrictions, response submission, and analytics. Requires {@code POLL_MANAGER} role
 * for administrative endpoints and {@code USER} role for respondent endpoints.
 */
@Singleton
public class FormRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(FormRoutes.class);

    private final FormService formService;
    private final GuardianPolicy guardianPolicy;
    private final FormAnalyticsAssembler analyticsAssembler;
    private final FormResponseExportService exportService;
    private final StationService stationService;
    private final PageService pageService;
    private final FormDirectoryService directory;

    @Inject
    public FormRoutes(
            FormService formService,
            FormDirectoryService directory,
            GuardianPolicy guardianPolicy,
            FormAnalyticsAssembler analyticsAssembler,
            FormResponseExportService exportService,
            StationService stationService,
            PageService pageService) {
        this.formService = formService;
        this.directory = directory;
        this.guardianPolicy = guardianPolicy;
        this.analyticsAssembler = analyticsAssembler;
        this.exportService = exportService;
        this.stationService = stationService;
        this.pageService = pageService;
    }

    /**
     * Loads a form and asserts it belongs to the caller's station, returning it. Answers 404
     * both when the form is absent and when it is owned by another station, so a form id from
     * one station cannot be read, answered, or have its analytics and responses exposed to another.
     */
    private Form requireOwnedForm(int formId, StationSession session) {
        var form = formService.findById(formId).orElseThrow(FormRefusal.FORM_NOT_HERE::raise);
        RouteSupport.requireSameStation(session.user(), form.stationId());
        return form;
    }

    /**
     * Answers with the form as it now stands, which is what every act on one ends with.
     */
    private void respondWithForm(Context ctx, int formId) {
        ctx.json(formService.findById(formId).orElseThrow(FormRefusal.FORM_NOT_HERE_ON_REREAD::raise));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/forms", this::list, StationPermission.POLL_VIEW_RESULTS);
        routes.post(prefix + "/forms", this::create, StationPermission.POLL_CREATE);
        routes.get(prefix + "/forms/available", this::listAvailable, StationPermission.USER);
        routes.get(prefix + "/forms/search", this::search, StationPermission.PAGE_EDIT);
        routes.get(prefix + "/forms/{id}", this::get, StationPermission.USER);
        routes.put(prefix + "/forms/{id}", this::update, StationPermission.POLL_CREATE);
        routes.delete(prefix + "/forms/{id}", this::delete, StationPermission.POLL_CREATE);
        routes.post(prefix + "/forms/{id}/publish", this::publish, StationPermission.POLL_CREATE);
        routes.post(prefix + "/forms/{id}/close", this::close, StationPermission.POLL_CREATE);
        routes.post(prefix + "/forms/{id}/duplicate", this::duplicate, StationPermission.POLL_CREATE);
        routes.delete(prefix + "/forms/{id}/responses", this::clearResponses, StationPermission.POLL_CREATE);
        routes.put(prefix + "/forms/{id}/visibility", this::setVisibility, StationPermission.POLL_CREATE);
        routes.get(prefix + "/forms/{id}/share-link", this::getShareLink, StationPermission.POLL_CREATE);
        routes.post(prefix + "/forms/{id}/share-link", this::replaceShareLink, StationPermission.POLL_CREATE);

        routes.get(prefix + "/forms/{id}/questions", this::listQuestions, StationPermission.USER);
        routes.get(prefix + "/forms/{id}/pages", this::listPages, StationPermission.USER);
        routes.put(prefix + "/forms/{id}/questions", this::setQuestions, StationPermission.POLL_CREATE);
        routes.get(
                prefix + "/forms/{id}/questions/answer-counts",
                this::countAnswersPerQuestion,
                StationPermission.POLL_CREATE);

        routes.get(prefix + "/forms/{id}/restrictions", this::getRestrictions, StationPermission.USER);
        routes.put(prefix + "/forms/{id}/restrictions", this::setRestrictions, StationPermission.POLL_CREATE);

        routes.get(prefix + "/forms/{id}/my-response", this::getMyResponse, StationPermission.USER);
        routes.get(prefix + "/forms/{id}/eligible-members", this::getEligibleMembers, StationPermission.USER);
        routes.post(prefix + "/forms/{id}/respond", this::submitResponse, StationPermission.USER);
        routes.put(prefix + "/forms/{id}/respond", this::updateResponse, StationPermission.USER);
        routes.get(prefix + "/forms/{id}/respond/{memberId}", this::getMemberResponse, StationPermission.USER);
        routes.post(prefix + "/forms/{id}/respond/{memberId}", this::submitForMember, StationPermission.USER);
        routes.put(prefix + "/forms/{id}/respond/{memberId}", this::updateForMember, StationPermission.USER);
        routes.get(prefix + "/forms/{id}/draft", this::getDraft, StationPermission.USER);
        routes.put(prefix + "/forms/{id}/draft", this::saveDraft, StationPermission.USER);
        routes.delete(prefix + "/forms/{id}/draft", this::discardDraft, StationPermission.USER);
        routes.get(prefix + "/forms/{id}/draft/{memberId}", this::getDraftFor, StationPermission.USER);
        routes.put(prefix + "/forms/{id}/draft/{memberId}", this::saveDraftFor, StationPermission.USER);
        routes.delete(prefix + "/forms/{id}/draft/{memberId}", this::discardDraftFor, StationPermission.USER);

        routes.get(prefix + "/forms/{id}/analytics", this::getAnalytics, StationPermission.POLL_VIEW_RESULTS);
        routes.post(prefix + "/forms/{id}/analytics/query", this::queryAnalytics, StationPermission.POLL_VIEW_RESULTS);
        routes.get(prefix + "/forms/{id}/responses/export", this::exportResponses, StationPermission.POLL_VIEW_RESULTS);
        routes.get(prefix + "/forms/{id}/responses", this::listResponses, StationPermission.POLL_VIEW_RESULTS);
        routes.get(
                prefix + "/forms/{id}/responses/{responseId}",
                this::getResponseDetail,
                StationPermission.POLL_VIEW_RESULTS);
    }

    @OpenApi(
            path = "/api/v1/forms",
            methods = HttpMethod.GET,
            summary = "List all forms for station",
            tags = {"Forms"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Form[].class)))
    private void list(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var purposeParam = ctx.queryParam("purpose");
        if (purposeParam == null || purposeParam.isBlank()) {
            ctx.json(formService.findByStation(session.stationId()));
            return;
        }
        FormPurpose purpose;
        try {
            purpose = FormPurpose.valueOf(purposeParam);
        } catch (IllegalArgumentException _) {
            throw FormRefusal.FORM_KIND_UNKNOWN.raise(purposeParam);
        }
        ctx.json(formService.findByStationAndPurpose(session.stationId(), purpose));
    }

    @OpenApi(
            path = "/api/v1/forms/search",
            methods = HttpMethod.GET,
            summary = "Search forms by title for the page-editor picker",
            description = "Returns a lightweight result shape (publicUid, title, purpose, status)"
                    + " scoped to the caller's station. Backs the POLL_EMBED and FORMS_CTA cell"
                    + " pickers. The purpose query parameter is required; empty q returns the"
                    + " most recent forms of the requested purpose. Only openly addressed forms are"
                    + " offered: a page fetches the form it carries by its own address, so a form"
                    + " reached by its link alone would show as missing on the page.",
            tags = {"Forms"},
            queryParams = {
                @OpenApiParam(name = "purpose", required = true),
                @OpenApiParam(name = "q"),
                @OpenApiParam(name = "limit", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormSearchResult[].class)))
    private void search(Context ctx) {
        StationSession session = StationSession.from(ctx);
        String purposeParam = ctx.queryParam("purpose");
        if (purposeParam == null || purposeParam.isBlank()) {
            throw FormRefusal.FORM_KIND_NOT_NAMED.raise();
        }
        FormPurpose purpose;
        try {
            purpose = FormPurpose.valueOf(purposeParam);
        } catch (IllegalArgumentException _) {
            throw FormRefusal.FORM_KIND_UNKNOWN_ON_SEARCH.raise(purposeParam);
        }
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10);
        ctx.json(directory.pickable(session.stationId(), purpose, ctx.queryParam("uid"), ctx.queryParam("q"), limit));
    }

    @OpenApi(
            path = "/api/v1/forms/available",
            methods = HttpMethod.GET,
            summary = "List forms available to the current user (self or managed members)",
            tags = {"Forms"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormListEntry[].class)))
    private void listAvailable(Context ctx) {
        var atStation = StationSession.optional(UserSession.from(ctx));
        if (atStation.isEmpty()) {
            ctx.json(Collections.emptyList());
            return;
        }
        StationSession session = atStation.get();
        boolean manager = session.hasPermission(RestrictionType.FORM.managerPermission());
        var wards = guardianPolicy.wards(session.user()).stream()
                .map(StationMember::id)
                .toList();
        ctx.json(directory.available(session.stationId(), session.member().id(), manager, wards));
    }

    @OpenApi(
            path = "/api/v1/forms",
            methods = HttpMethod.POST,
            summary = "Create a form",
            tags = {"Forms"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FormRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = Form.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var req = ctx.bodyAsClass(FormRequest.class);
        if (req.title() == null || req.title().isBlank()) throw FormRefusal.FORM_NEEDS_A_TITLE.raise();
        FormService.requireOfferableCompletionLink(req.completionLink());
        var form = formService.create(
                session.stationId(),
                req.title(),
                Objects.requireNonNullElse(req.description(), ""),
                Boolean.TRUE.equals(req.shuffleQuestions()),
                !Boolean.FALSE.equals(req.allowEdit()),
                Boolean.TRUE.equals(req.forced()),
                req.startAt(),
                req.endAt(),
                session.member().id(),
                Objects.requireNonNullElse(req.purpose(), FormPurpose.INTERNAL));
        formService.setCompletion(form.id(), req.completionMessage(), req.completionLink(), req.completionLinkLabel());
        ctx.status(HttpStatus.CREATED).json(formService.findById(form.id()).orElse(form));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}",
            methods = HttpMethod.GET,
            summary = "Get a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = Form.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        ctx.json(requireOwnedForm(id, session));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}",
            methods = HttpMethod.PUT,
            summary = "Update a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FormRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = Form.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedForm(id, StationSession.from(ctx));
        var req = ctx.bodyAsClass(FormRequest.class);
        FormService.requireOfferableCompletionLink(req.completionLink());
        if (!formService.update(
                id,
                req.title(),
                Objects.requireNonNullElse(req.description(), ""),
                Boolean.TRUE.equals(req.shuffleQuestions()),
                !Boolean.FALSE.equals(req.allowEdit()),
                Boolean.TRUE.equals(req.forced()),
                req.startAt(),
                req.endAt())) {
            throw FormRefusal.FORM_NOT_HERE_ON_CHANGE.raise();
        }
        formService.setCompletion(id, req.completionMessage(), req.completionLink(), req.completionLinkLabel());
        respondWithForm(ctx, id);
    }

    @OpenApi(
            path = "/api/v1/forms/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedForm(id, StationSession.from(ctx));
        if (formService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw FormRefusal.FORM_NOT_HERE_ON_DELETE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/publish",
            methods = HttpMethod.POST,
            summary = "Publish a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = Form.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void publish(Context ctx) {
        int id = pathInt(ctx, "id");
        var form = requireOwnedForm(id, StationSession.from(ctx));
        if (form.status() != Form.FormStatus.DRAFT) throw FormRefusal.FORM_NOT_A_DRAFT.raise();
        formService.publish(id);

        respondWithForm(ctx, id);
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/duplicate",
            methods = HttpMethod.POST,
            summary = "Copy a form as a new draft",
            description = "Settings, pages, branches, questions and restrictions are copied; answers, the link,"
                    + " the start and end dates and the status are not. The copy is always a draft.",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FormDuplicateRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = Form.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void duplicate(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = StationSession.from(ctx);
        requireOwnedForm(id, session);
        var request = ctx.bodyAsClass(FormDuplicateRequest.class);
        if (request.title() == null || request.title().isBlank()) throw FormRefusal.FORM_COPY_NEEDS_A_TITLE.raise();
        var copy = formService
                .duplicate(id, request.title().trim(), session.member().id())
                .orElseThrow(FormRefusal.FORM_NOT_HERE_ON_COPY::raise);
        ctx.status(HttpStatus.CREATED).json(copy);
    }

    /**
     * What a copy of a form is called, which the screen words in the reader's language.
     *
     * @param title the copy's title
     */
    public record FormDuplicateRequest(String title) {}

    @OpenApi(
            path = "/api/v1/forms/{id}/visibility",
            methods = HttpMethod.PUT,
            summary = "How far a public form reaches",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FormVisibilityRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormVisibilityResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setVisibility(Context ctx) {
        int id = pathInt(ctx, "id");
        var form = requireOwnedForm(id, StationSession.from(ctx));
        var request = ctx.bodyAsClass(FormVisibilityRequest.class);
        if (request.visibility() == null) {
            throw FormRefusal.FORM_REACH_NOT_SAID.raise();
        }
        if (!formService.setVisibility(id, request.visibility())) {
            throw FormRefusal.FORM_NOT_HERE_ON_VISIBILITY_CHANGE.raise();
        }
        ctx.json(new FormVisibilityResponse(
                formService.findById(id).orElseThrow(FormRefusal.FORM_NOT_HERE_AFTER_VISIBILITY_CHANGE::raise),
                pageService.pagesStrandedBy(form, request.visibility())));
    }

    /**
     * @param stillHeldBy the pages that put the form on themselves and stopped offering it with the
     *                    change, answered rather than refused so an editor can tidy them
     */
    public record FormVisibilityResponse(Form form, List<PageUsingForm> stillHeldBy) {}

    public record ClearedFormResponses(int cleared) {}

    /**
     * The link this form is sent with, minted the first time it is asked for so a form nobody sends
     * never carries one.
     */
    @OpenApi(
            path = "/api/v1/forms/{id}/share-link",
            methods = HttpMethod.GET,
            summary = "The link a form is sent with",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormShareLinkResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getShareLink(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = StationSession.from(ctx);
        var form = requireOwnedForm(id, session);
        requireSendableByLink(form);
        ctx.json(new FormShareLinkResponse(formService.shareLink(id).orElse(null)));
    }

    /**
     * Refuses a form that is not sent by link at all, in the same words wherever it is asked.
     *
     * <p>Reading and replacing used to disagree: reading answered a null link, which is also what a
     * form that simply has none yet answers, so a caller could not tell "this form never has one"
     * from "this one has not been given one yet". The second is the case the button offering to make
     * the first link stands on.
     *
     * <p>Refused where the form is answered by the station's own members.
     */
    private static void requireSendableByLink(Form form) {
        if (form.purpose() == FormPurpose.INTERNAL) {
            throw FormRefusal.INTERNAL_FORM_HAS_NO_LINK.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/share-link",
            methods = HttpMethod.POST,
            summary = "Replace the link a form is sent with",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ReplaceFormShareLinkRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormShareLinkResponse.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void replaceShareLink(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedForm(id, StationSession.from(ctx));
        var request = ctx.bodyAsClass(ReplaceFormShareLinkRequest.class);
        var replaced = formService
                .replaceShareLink(id, request.currentToken())
                .orElseThrow(FormRefusal.FORM_LINK_ALREADY_REPLACED::raise);
        ctx.json(new FormShareLinkResponse(replaced));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/close",
            methods = HttpMethod.POST,
            summary = "Close a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = Form.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void close(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedForm(id, StationSession.from(ctx));
        if (!formService.close(id)) throw FormRefusal.FORM_NOT_HERE_ON_CLOSE.raise();
        respondWithForm(ctx, id);
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/responses",
            methods = HttpMethod.DELETE,
            summary = "Throw away every answer a form has collected",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            description = "The form and its questions stay as they are, so it can be asked again.",
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClearedFormResponses.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void clearResponses(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedForm(id, StationSession.from(ctx));
        ctx.json(new ClearedFormResponses(formService.clearResponses(id)));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/questions",
            methods = HttpMethod.GET,
            summary = "List questions for a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormQuestion[].class)))
    private void listQuestions(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedForm(id, session);
        ctx.json(formService.findQuestions(id));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/questions/answer-counts",
            methods = HttpMethod.GET,
            summary = "How many answers each question of a form holds",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = QuestionAnswerCount[].class)))
    private void countAnswersPerQuestion(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedForm(id, StationSession.from(ctx));
        ctx.json(formService.countAnswersPerQuestion(id));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/pages",
            methods = HttpMethod.GET,
            summary = "List the pages of a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormPage[].class)))
    private void listPages(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedForm(id, StationSession.from(ctx));
        ctx.json(formService.findPages(id));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/questions",
            methods = HttpMethod.PUT,
            summary = "Save the pages and questions of a form",
            description =
                    "A question sent with its id is changed in place and keeps its answers, one sent without an id"
                            + " is added, and a question of the form that is not sent is removed with its answers."
                            + " Pages are kept by their key the same way.",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FormLayoutRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormLayout.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setQuestions(Context ctx) {
        int id = pathInt(ctx, "id");
        var form = requireOwnedForm(id, StationSession.from(ctx));
        var layout = ctx.bodyAsClass(FormLayoutRequest.class);
        var questions = layout.questions() == null ? List.<FormQuestionRequest>of() : layout.questions();
        var pages = layout.pages() == null ? List.<FormPageRequest>of() : layout.pages();
        if (questions.stream().map(FormQuestionRequest::questionType).anyMatch(t -> !t.allowedFor(form.purpose()))) {
            throw FormRefusal.QUESTIONS_NOT_FOR_THIS_KIND_OF_FORM.raise();
        }
        formService.saveLayout(
                id,
                pages.stream()
                        .map(p -> new PageEntry(p.key(), p.title(), p.description(), PageTarget.orNext(p.after())))
                        .toList(),
                questions.stream()
                        .map(q -> new QuestionEntry(
                                q.id(),
                                q.pageKey(),
                                q.questionType(),
                                q.title(),
                                Objects.requireNonNullElse(q.description(), ""),
                                Boolean.TRUE.equals(q.required()),
                                Boolean.TRUE.equals(q.shuffle()),
                                Objects.requireNonNullElseGet(q.config(), FormQuestionConfig.Unknown::new),
                                q.branch()))
                        .toList());
        ctx.json(new FormLayout(formService.findPages(id), formService.findQuestions(id)));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/restrictions",
            methods = HttpMethod.GET,
            summary = "Get form restrictions",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormRestrictions.class)))
    private void getRestrictions(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedForm(id, session);
        var restrictions = formService.findRestrictions(id);
        ctx.json(new FormRestrictions(
                restrictions.userTypes(),
                restrictions.groupIds(),
                restrictions.tagIds(),
                restrictions.memberIds(),
                restrictions.mode()));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/restrictions",
            methods = HttpMethod.PUT,
            summary = "Set form restrictions",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FormRestrictions.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormRestrictions.class)))
    private void setRestrictions(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedForm(id, StationSession.from(ctx));
        var req = ctx.bodyAsClass(FormRestrictions.class);
        var mode = req.mode();
        formService.setRestrictions(
                id, new RestrictionSelection(req.userTypes(), req.groupIds(), req.tagIds(), req.memberIds(), mode));
        if (mode != null) {
            formService.updateRestrictionMode(id, mode);
        }
        ctx.json(req);
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/my-response",
            methods = HttpMethod.GET,
            summary = "Get my response to a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormResponseDetail.class)))
    private void getMyResponse(Context ctx) {
        int id = pathInt(ctx, "id");
        StationSession session = StationSession.from(ctx);
        requireOwnedForm(id, session);
        respondWithAnswerOf(ctx, id, session.member().id());
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/respond/{memberId}",
            methods = HttpMethod.GET,
            summary = "Get the response of a managed member",
            tags = {"Forms"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "memberId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormResponseDetail.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getMemberResponse(Context ctx) {
        int id = pathInt(ctx, "id");
        int memberId = pathInt(ctx, "memberId");
        StationSession session = StationSession.from(ctx);
        requireFormForManagedMember(session, id, memberId);
        respondWithAnswerOf(ctx, id, memberId);
    }

    /**
     * Answers with what the member gave to the form, or with an empty detail when they have not
     * answered it yet, so the screen can tell a first answer from a correction.
     */
    private void respondWithAnswerOf(Context ctx, int formId, int memberId) {
        ctx.json(formService
                .findResponse(formId, memberId)
                .map(response -> analyticsAssembler.getResponseDetail(formId, response.id()))
                .orElseGet(() -> new FormResponseDetail(null, List.of())));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/eligible-members",
            methods = HttpMethod.GET,
            summary = "Get which members (self + managed) are eligible for this form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EligibleMembers.class)))
    private void getEligibleMembers(Context ctx) {
        int id = pathInt(ctx, "id");
        var atStation = StationSession.optional(UserSession.from(ctx));
        if (atStation.isEmpty()) {
            ctx.json(new EligibleMembers(false, List.of()));
            return;
        }
        StationSession session = atStation.get();
        requireOwnedForm(id, session);
        boolean selfEligible = formService.canMemberAccess(id, session.member().id());
        var managed = guardianPolicy.wards(session.user());
        var eligibleManagedIds = managed.stream()
                .map(StationMember::id)
                .filter(ided -> formService.canMemberAccess(id, ided))
                .toList();
        ctx.json(new EligibleMembers(selfEligible, eligibleManagedIds));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/respond",
            methods = HttpMethod.POST,
            summary = "Submit a response to a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FormSubmitRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = FormResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = AnswersRefusedBody.class))
            })
    private void submitResponse(Context ctx) {
        int id = pathInt(ctx, "id");
        StationSession session = StationSession.from(ctx);
        var form = requireOwnedForm(id, session);
        if (!formService.isAcceptingResponses(form)) throw FormRefusal.FORM_TAKES_NO_ANSWERS.raise();
        if (!formService.canMemberAccess(id, session.member().id())) {
            throw FormRefusal.FORM_NOT_YOURS_TO_ANSWER.raise();
        }
        if (formService.hasResponded(id, session.member().id())) {
            throw FormRefusal.FORM_ANSWER_ALREADY_ON_FILE.raise();
        }
        var req = ctx.bodyAsClass(FormSubmitRequest.class);
        try {
            var response = formService.submitResponse(
                    id, session.member().id(), session.member().id(), req.answers());
            ctx.status(HttpStatus.CREATED).json(response);
        } catch (FormAnswersRefused refused) {
            throw refused.as(FormRefusal.FORM_ANSWERS_NOT_SAVED);
        }
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/respond",
            methods = HttpMethod.PUT,
            summary = "Update my response to a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FormSubmitRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = AnswersRefusedBody.class))
            })
    private void updateResponse(Context ctx) {
        int id = pathInt(ctx, "id");
        StationSession session = StationSession.from(ctx);
        var form = requireOwnedForm(id, session);
        if (!form.allowEdit()) throw FormRefusal.FORM_ANSWER_NOT_CHANGEABLE.raise();
        if (!formService.canMemberAccess(id, session.member().id())) {
            throw FormRefusal.FORM_NOT_YOURS_TO_CHANGE_ANSWER.raise();
        }
        var req = ctx.bodyAsClass(FormSubmitRequest.class);
        try {
            var response = formService.submitResponse(
                    id, session.member().id(), session.member().id(), req.answers());
            ctx.json(response);
        } catch (FormAnswersRefused refused) {
            throw refused.as(FormRefusal.FORM_ANSWER_CHANGE_NOT_SAVED);
        }
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/respond/{memberId}",
            methods = HttpMethod.POST,
            summary = "Submit a response for a managed member",
            tags = {"Forms"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "memberId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FormSubmitRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = FormResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = AnswersRefusedBody.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void submitForMember(Context ctx) {
        respondForMember(ctx, true);
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/respond/{memberId}",
            methods = HttpMethod.PUT,
            summary = "Update a response for a managed member",
            tags = {"Forms"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "memberId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FormSubmitRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = AnswersRefusedBody.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateForMember(Context ctx) {
        respondForMember(ctx, false);
    }

    /**
     * Shared handler for submitting or updating a managed member's response. Verifies station
     * membership, that the caller manages the target member, and that the form belongs to the
     * caller's station before delegating to the form service. When {@code creating} is true the
     * form must be accepting responses, the member must not have answered yet, and a {@code 201}
     * is returned; otherwise the form must allow editing and a {@code 200} is returned.
     *
     * <p>Saving an answer replaces the one on file, so a first answer given for a member who has
     * answered already would overwrite theirs, and would do so even on a form whose answers cannot
     * be changed. The existing answer is corrected through the update instead.
     *
     * @param creating whether this is an initial submission ({@code true}) or an edit ({@code false})
     */
    private void respondForMember(Context ctx, boolean creating) {
        int id = pathInt(ctx, "id");
        int memberId = pathInt(ctx, "memberId");
        StationSession session = StationSession.from(ctx);
        var form = requireFormForManagedMember(session, id, memberId);
        if (creating) {
            if (!formService.isAcceptingResponses(form)) {
                throw FormRefusal.FORM_TAKES_NO_ANSWERS_FOR_MEMBER.raise();
            }
            if (formService.hasResponded(id, memberId)) {
                throw FormRefusal.FORM_ANSWER_ALREADY_ON_FILE.raise();
            }
        } else if (!form.allowEdit()) {
            throw FormRefusal.FORM_ANSWER_NOT_CHANGEABLE_FOR_MEMBER.raise();
        }
        var req = ctx.bodyAsClass(FormSubmitRequest.class);
        try {
            var response =
                    formService.submitResponse(id, memberId, session.member().id(), req.answers());
            if (creating) {
                ctx.status(HttpStatus.CREATED).json(response);
            } else {
                ctx.json(response);
            }
        } catch (FormAnswersRefused refused) {
            throw refused.as(FormRefusal.FORM_ANSWERS_FOR_MEMBER_NOT_SAVED);
        }
    }

    /**
     * Loads a form for somebody the caller answers for, refusing unless the caller manages that
     * member, the form belongs to the caller's station, and the form was put to the member.
     *
     * @param session  the current user session, which belongs to a station member
     * @param formId   the form to load
     * @param memberId the managed member the form is answered for
     * @return the form
     */
    private Form requireFormForManagedMember(StationSession session, int formId, int memberId) {
        verifyManages(session, memberId);
        var form = requireOwnedForm(formId, session);
        if (!formService.canMemberAccess(formId, memberId)) {
            throw FormRefusal.FORM_NOT_FOR_THIS_MEMBER.raise();
        }
        return form;
    }

    /**
     * Verifies that the current user acts for the specified member, and refuses otherwise. Acting
     * for a member is the guardian relation alone, which {@link GuardianPolicy} answers. Managing
     * polls does not reach into a member's own answers: whoever runs a form reads its results, and
     * only somebody who looks after the member answers or drafts on their behalf.
     *
     * @param session  the current user session
     * @param memberId the member ID to verify management of
     */
    private void verifyManages(StationSession session, int memberId) {
        if (!guardianPolicy.mayActFor(session.user(), memberId)) {
            throw FormRefusal.MEMBER_NOT_YOURS_TO_ANSWER_FOR.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/draft",
            methods = HttpMethod.GET,
            summary = "The half-filled form kept for the caller, if any",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormDraftResponse.class)))
    private void getDraft(Context ctx) {
        int id = pathInt(ctx, "id");
        int memberId = draftingMember(ctx, id);
        ctx.json(new FormDraftResponse(formService.findDraft(id, memberId).orElse(null)));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/draft",
            methods = HttpMethod.PUT,
            summary = "Keep what the caller filled in so far, to continue later",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FormDraftRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void saveDraft(Context ctx) {
        int id = pathInt(ctx, "id");
        int memberId = draftingMember(ctx, id);
        keepDraft(ctx, id, memberId, memberId);
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/draft",
            methods = HttpMethod.DELETE,
            summary = "Throw away the caller's half-filled form, to start over",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void discardDraft(Context ctx) {
        int id = pathInt(ctx, "id");
        formService.discardDraft(id, draftingMember(ctx, id));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/draft/{memberId}",
            methods = HttpMethod.GET,
            summary = "The half-filled form kept for a member in the caller's care, if any",
            tags = {"Forms"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "memberId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormDraftResponse.class)))
    private void getDraftFor(Context ctx) {
        int id = pathInt(ctx, "id");
        int memberId = managedDraftMember(ctx, id);
        ctx.json(new FormDraftResponse(formService.findDraft(id, memberId).orElse(null)));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/draft/{memberId}",
            methods = HttpMethod.PUT,
            summary = "Keep what was filled in so far for a member in the caller's care",
            tags = {"Forms"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "memberId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FormDraftRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void saveDraftFor(Context ctx) {
        int id = pathInt(ctx, "id");
        int memberId = managedDraftMember(ctx, id);
        keepDraft(ctx, id, memberId, StationSession.from(ctx).member().id());
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/draft/{memberId}",
            methods = HttpMethod.DELETE,
            summary = "Throw away the half-filled form of a member in the caller's care",
            tags = {"Forms"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "memberId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void discardDraftFor(Context ctx) {
        int id = pathInt(ctx, "id");
        formService.discardDraft(id, managedDraftMember(ctx, id));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * The caller as the member a draft of the form is kept for, once the form is theirs to answer.
     */
    private int draftingMember(Context ctx, int formId) {
        var session = StationSession.from(ctx);
        requireOwnedForm(formId, session);
        if (!formService.canMemberAccess(formId, session.member().id())) {
            throw FormRefusal.FORM_NOT_YOURS_TO_DRAFT.raise();
        }
        return session.member().id();
    }

    /**
     * The member in the caller's care a draft of the form is kept for.
     *
     * <p>An unsent answer is private to the member and whoever looks after them. Managing the station's
     * polls lets somebody answer for a member, starting from blank or from the answer sent, but never
     * read or change what the member has not sent.
     */
    private int managedDraftMember(Context ctx, int formId) {
        var session = StationSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        if (!guardianPolicy.mayActFor(session.user(), memberId)) throw FormRefusal.FORM_DRAFT_NOT_YOURS.raise();
        requireFormForManagedMember(session, formId, memberId);
        return memberId;
    }

    /** Keeps a draft while the form takes answers; a closed form keeps none. */
    private void keepDraft(Context ctx, int formId, int memberId, int savedBy) {
        var form = requireOwnedForm(formId, StationSession.from(ctx));
        if (!formService.isAcceptingResponses(form)) throw FormRefusal.FORM_TAKES_NO_DRAFTS.raise();
        var request = ctx.bodyAsClass(FormDraftRequest.class);
        formService.saveDraft(formId, memberId, savedBy, request.answers(), request.path());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * What a member filled in so far.
     *
     * @param answers the answers so far, by question id, in the shape a sent answer has
     * @param path    the pages visited so far, the page to continue on last
     */
    public record FormDraftRequest(Map<Integer, FormAnswerValue> answers, List<String> path) {}

    /**
     * The draft kept, where there is one.
     *
     * @param draft the draft, or {@code null} where nothing is kept
     */
    public record FormDraftResponse(@Nullable FormDraft draft) {}

    @OpenApi(
            path = "/api/v1/forms/{id}/analytics",
            methods = HttpMethod.GET,
            summary = "Get form analytics",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormAnalytics.class)))
    private void getAnalytics(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedForm(id, session);
        ctx.json(analyticsAssembler.buildAnalytics(id));
    }

    /**
     * The results of an internal form, filtered and grouped by who answered: their user type,
     * groups, tags, age and profile answers.
     *
     * <p>Only internal forms can be counted this way. A contact form or public poll is answered
     * without signing in, so there is nobody behind the answers to look up.
     */
    @OpenApi(
            path = "/api/v1/forms/{id}/analytics/query",
            methods = HttpMethod.POST,
            summary = "Get form analytics filtered and grouped by respondent",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FormResultQuery.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormAnalytics.class)))
    private void queryAnalytics(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        var form = requireOwnedForm(id, session);
        if (form.purpose() != FormPurpose.INTERNAL) {
            throw FormRefusal.ONLY_INTERNAL_FORM_GROUPED_BY_WHO_ANSWERED.raise();
        }
        ctx.json(analyticsAssembler.buildAnalytics(id, ctx.bodyAsClass(FormResultQuery.class)));
    }

    /** The answers as a spreadsheet or as a sheet, which until now could only be had as the former. */
    @OpenApi(
            path = "/api/v1/forms/{id}/responses/export",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void exportResponses(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        var form = requireOwnedForm(id, session);
        var station = stationService
                .findById(session.stationId())
                .orElseThrow(FormRefusal.STATION_NOT_HERE_FOR_FORM_EXPORT::raise);
        boolean asSpreadsheet = !"pdf".equalsIgnoreCase(ctx.queryParam("format"));
        try {
            var document = exportService.export(
                    id,
                    form.title(),
                    station,
                    NameParts.of(session.user().account()).official(),
                    asSpreadsheet ? CsvWriter.Separator.of(ctx.queryParam("separator")) : null);
            ctx.contentType(asSpreadsheet ? "text/csv" : "application/pdf");
            ctx.header("Content-Disposition", document.contentDisposition());
            ctx.result(document.bytes());
        } catch (Exception e) {
            log.warn("Answers of form {} could not be exported", id, e);
            throw FormRefusal.FORM_ANSWERS_NOT_EXPORTED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/responses",
            methods = HttpMethod.GET,
            summary = "List all responses for a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormResponseEntry[].class)))
    private void listResponses(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedForm(id, session);
        ctx.json(analyticsAssembler.listResponses(id));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/responses/{responseId}",
            methods = HttpMethod.GET,
            summary = "Get a specific response with answers",
            tags = {"Forms"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "responseId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormResponseDetail.class)))
    private void getResponseDetail(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int formId = pathInt(ctx, "id");
        int responseId = pathInt(ctx, "responseId");
        requireOwnedForm(formId, session);
        ctx.json(analyticsAssembler.getResponseDetail(formId, responseId));
    }

    /**
     * Request body for creating or updating a form.
     *
     * @param title            the form title
     * @param description      optional form description
     * @param shuffleQuestions whether to randomize question order
     * @param allowEdit        whether respondents may edit their response
     * @param startAt          optional start time for accepting responses
     * @param endAt            optional end time for accepting responses
     * @param purpose          what the form is for, which is fixed once it is made
     * @param completionMessage what the reader is told once the form is sent, or nothing for the general thanks
     * @param completionLink   where the reader may go on to after sending, or nothing
     * @param completionLinkLabel what that link says, or nothing for the address itself
     */
    public record FormRequest(
            String title,
            @Nullable String description,
            @Nullable Boolean shuffleQuestions,
            @Nullable Boolean allowEdit,
            @Nullable Boolean forced,
            @Nullable Instant startAt,
            @Nullable Instant endAt,
            @Nullable FormPurpose purpose,
            @Nullable String completionMessage,
            @Nullable String completionLink,
            @Nullable String completionLinkLabel) {}

    /**
     * One question of a form as the editor saves it.
     *
     * @param id           the question this updates, or {@code null} for a question that is new
     * @param pageKey      the key of the page the question stands on, or {@code null} for the first page
     * @param questionType the question type name (must match {@link FormQuestionType})
     * @param title        the question text
     * @param description  optional description
     * @param required     whether an answer is mandatory
     * @param shuffle      whether answer options should be randomized
     * @param config       type-specific configuration as JSON string
     * @param branch       where the page leads per option picked, for the question that decides it
     */
    public record FormQuestionRequest(
            @Nullable Integer id,
            @Nullable String pageKey,
            FormQuestionType questionType,
            String title,
            @Nullable String description,
            @Nullable Boolean required,
            @Nullable Boolean shuffle,
            @Nullable FormQuestionConfig config,
            @Nullable QuestionBranch branch) {}

    /**
     * One page of a form as the editor saves it.
     *
     * @param key         the page's key, kept by a stored page and made by the editor for a new one
     * @param title       optional title
     * @param description optional description
     * @param after       where the reader goes once the page is done; the next page where not given
     */
    public record FormPageRequest(
            String key,
            @Nullable String title,
            @Nullable String description,
            @Nullable PageTarget after) {}

    /**
     * The pages and questions of a form as the editor saves them, each list in its order.
     *
     * @param pages     the pages, at least one
     * @param questions the questions, each naming the page it stands on
     */
    public record FormLayoutRequest(List<FormPageRequest> pages, List<FormQuestionRequest> questions) {}

    /**
     * The pages and questions of a form as stored.
     *
     * @param pages     the pages, in their order
     * @param questions the questions, page by page
     */
    public record FormLayout(List<FormPage> pages, List<FormQuestion> questions) {}

    /**
     * Access restrictions for a form, specifying which roles, groups, and tags may access it.
     *
     * @param userTypes list of user type names that grant access
     * @param groupIds  list of group IDs that grant access
     * @param tagIds    list of tag IDs that grant access
     */
    public record FormRestrictions(
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            @Nullable List<Integer> memberIds,
            @Nullable RestrictionMode mode) {}

    /**
     * Request body for submitting or updating a form response.
     *
     * @param answers map of question ID to answer value (JSON string)
     */
    public record FormSubmitRequest(Map<Integer, FormAnswerValue> answers) {}

    public record FormVisibilityRequest(FormVisibility visibility) {}

    public record FormShareLinkResponse(@Nullable String token) {}

    public record ReplaceFormShareLinkRequest(@Nullable String currentToken) {}

    /**
     * Response indicating which members (self and managed) are eligible to respond to a form.
     *
     * @param selfEligible             whether the current user is eligible
     * @param eligibleManagedMemberIds IDs of managed members who are eligible
     */
    public record EligibleMembers(boolean selfEligible, List<Integer> eligibleManagedMemberIds) {}
}
