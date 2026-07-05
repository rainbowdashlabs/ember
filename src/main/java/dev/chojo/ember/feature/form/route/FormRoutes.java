/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswerValue;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.entity.FormResponse;
import dev.chojo.ember.feature.form.entity.QuestionEntry;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler.FormAnalyticsDto;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler.FormResponseEntryDto;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler.ResponseDetailDto;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * HTTP route handlers for form management, including CRUD operations, question management,
 * access restrictions, response submission, and analytics. Requires {@code POLL_MANAGER} role
 * for administrative endpoints and {@code USER} role for respondent endpoints.
 */
@Singleton
public class FormRoutes implements Routes {
    private final FormService formService;
    private final StationMemberService stationMemberService;
    private final FormAnalyticsAssembler analyticsAssembler;

    @Inject
    public FormRoutes(
            FormService formService,
            StationMemberService stationMemberService,
            FormAnalyticsAssembler analyticsAssembler) {
        this.formService = formService;
        this.stationMemberService = stationMemberService;
        this.analyticsAssembler = analyticsAssembler;
    }

    /**
     * Loads a form and asserts it belongs to the caller's station, returning it. Answers 404
     * when the form is absent and 403 when owned by another station, so a form id from one
     * station cannot be read, answered, or have its analytics and responses exposed to another.
     */
    private Form requireOwnedForm(int formId, UserSession session) {
        var form = formService.findById(formId).orElseThrow(NotFoundResponse::new);
        if (form.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        return form;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Management
        routes.get(prefix + "/forms", this::list, StationPermission.POLL_VIEW_RESULTS);
        routes.post(prefix + "/forms", this::create, StationPermission.POLL_CREATE);
        routes.get(prefix + "/forms/available", this::listAvailable, StationPermission.USER);
        routes.get(prefix + "/forms/search", this::search, StationPermission.PAGE_EDIT);
        routes.get(prefix + "/forms/{id}", this::get, StationPermission.USER);
        routes.put(prefix + "/forms/{id}", this::update, StationPermission.POLL_CREATE);
        routes.delete(prefix + "/forms/{id}", this::delete, StationPermission.POLL_CREATE);
        routes.post(prefix + "/forms/{id}/publish", this::publish, StationPermission.POLL_CREATE);
        routes.post(prefix + "/forms/{id}/close", this::close, StationPermission.POLL_CREATE);

        // Questions
        routes.get(prefix + "/forms/{id}/questions", this::listQuestions, StationPermission.USER);
        routes.put(prefix + "/forms/{id}/questions", this::setQuestions, StationPermission.POLL_CREATE);

        // Restrictions
        routes.get(prefix + "/forms/{id}/restrictions", this::getRestrictions, StationPermission.USER);
        routes.put(prefix + "/forms/{id}/restrictions", this::setRestrictions, StationPermission.POLL_CREATE);

        // Responding
        routes.get(prefix + "/forms/{id}/my-response", this::getMyResponse, StationPermission.USER);
        routes.get(prefix + "/forms/{id}/eligible-members", this::getEligibleMembers, StationPermission.USER);
        routes.post(prefix + "/forms/{id}/respond", this::submitResponse, StationPermission.USER);
        routes.put(prefix + "/forms/{id}/respond", this::updateResponse, StationPermission.USER);
        routes.post(
                prefix + "/forms/{id}/respond/{memberId}", this::submitForMember, StationPermission.MEMBER_GUARDIAN);
        routes.put(prefix + "/forms/{id}/respond/{memberId}", this::updateForMember, StationPermission.MEMBER_GUARDIAN);

        // Analytics
        routes.get(prefix + "/forms/{id}/analytics", this::getAnalytics, StationPermission.POLL_VIEW_RESULTS);
        routes.get(prefix + "/forms/{id}/responses", this::listResponses, StationPermission.POLL_VIEW_RESULTS);
        routes.get(
                prefix + "/forms/{id}/responses/{responseId}",
                this::getResponseDetail,
                StationPermission.POLL_VIEW_RESULTS);
    }

    // -- Form CRUD --

    @OpenApi(
            path = "/api/v1/forms",
            methods = HttpMethod.GET,
            summary = "List all forms for station",
            tags = {"Forms"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Form[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var purposeParam = ctx.queryParam("purpose");
        if (purposeParam == null || purposeParam.isBlank()) {
            ctx.json(formService.findByStation(session.stationId()));
            return;
        }
        FormPurpose purpose;
        try {
            purpose = FormPurpose.valueOf(purposeParam);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Unknown form purpose: " + purposeParam);
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
                    + " most recent forms of the requested purpose.",
            tags = {"Forms"},
            queryParams = {
                @OpenApiParam(name = "purpose", required = true),
                @OpenApiParam(name = "q"),
                @OpenApiParam(name = "limit", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormSearchResult[].class)))
    private void search(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String purposeParam = ctx.queryParam("purpose");
        if (purposeParam == null || purposeParam.isBlank()) {
            throw new BadRequestResponse("purpose query parameter is required");
        }
        FormPurpose purpose;
        try {
            purpose = FormPurpose.valueOf(purposeParam);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Unknown form purpose: " + purposeParam);
        }
        String uidParam = ctx.queryParam("uid");
        if (uidParam != null && !uidParam.isBlank()) {
            UUID lookup;
            try {
                lookup = UUID.fromString(uidParam);
            } catch (IllegalArgumentException e) {
                ctx.json(List.of());
                return;
            }
            var result = formService
                    .findByPublicUid(lookup)
                    .filter(f -> f.stationId() == session.stationId())
                    .filter(f -> f.purpose() == purpose)
                    .map(f -> List.of(new FormSearchResult(f.publicUid(), f.title(), f.purpose(), f.status())))
                    .orElseGet(List::of);
            ctx.json(result);
            return;
        }
        String q = ctx.queryParam("q");
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        int requested = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10);
        int limit = Math.clamp(requested, 1, 20);

        var results = formService.findByStationAndPurpose(session.stationId(), purpose).stream()
                .filter(f ->
                        needle.isEmpty() || f.title().toLowerCase(Locale.ROOT).contains(needle))
                .limit(limit)
                .map(f -> new FormSearchResult(f.publicUid(), f.title(), f.purpose(), f.status()))
                .toList();
        ctx.json(results);
    }

    @OpenApi(
            path = "/api/v1/forms/available",
            methods = HttpMethod.GET,
            summary = "List forms available to the current user (self or managed members)",
            tags = {"Forms"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormListEntry[].class)))
    private void listAvailable(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.json(Collections.emptyList());
            return;
        }
        int memberId = session.member().id();

        // findByStationForMember uses DB restriction check (manager bypass + role inheritance)
        var accessibleForms = formService.findByStationForMember(session.stationId(), memberId).stream()
                .filter(f -> f.status() == Form.FormStatus.OPEN)
                .filter(formService::isAcceptingResponses)
                .toList();

        // Also include forms where a managed member has access but the current member does not
        var managed = stationMemberService.findManaged(memberId);
        var managedAccessible = managed.isEmpty()
                ? Set.<Integer>of()
                : managed.stream()
                        .flatMap(m -> formService.findByStationForMember(session.stationId(), m.id()).stream())
                        .filter(f -> f.status() == Form.FormStatus.OPEN)
                        .filter(formService::isAcceptingResponses)
                        .map(Form::id)
                        .collect(Collectors.toSet());

        var seen = new HashSet<Integer>();
        var combined = new ArrayList<>(accessibleForms);
        accessibleForms.forEach(f -> seen.add(f.id()));
        if (!managedAccessible.isEmpty()) {
            formService.findByStation(session.stationId()).stream()
                    .filter(f -> managedAccessible.contains(f.id()) && !seen.contains(f.id()))
                    .filter(f -> f.status() == Form.FormStatus.OPEN)
                    .filter(formService::isAcceptingResponses)
                    .forEach(combined::add);
        }

        var result = combined.stream()
                .map(f -> new FormListEntry(
                        f.id(),
                        f.stationId(),
                        f.title(),
                        f.description(),
                        f.status(),
                        f.startAt(),
                        f.endAt(),
                        formService.countResponses(f.id()),
                        formService.hasResponded(f.id(), memberId)))
                .toList();
        ctx.json(result);
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
        UserSession session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(FormRequest.class);
        if (req.title() == null || req.title().isBlank()) throw new BadRequestResponse("title is required");
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        var form = formService.create(
                session.stationId(),
                req.title(),
                req.description() != null ? req.description() : "",
                req.shuffleQuestions() != null && req.shuffleQuestions(),
                req.allowEdit() == null || req.allowEdit(),
                req.forced() != null && req.forced(),
                req.startAt(),
                req.endAt(),
                session.member().id(),
                req.purpose() != null ? req.purpose() : FormPurpose.INTERNAL);
        ctx.status(HttpStatus.CREATED).json(form);
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
        UserSession session = UserSession.from(ctx);
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
        requireOwnedForm(id, UserSession.from(ctx));
        var req = ctx.bodyAsClass(FormRequest.class);
        if (!formService.update(
                id,
                req.title(),
                req.description() != null ? req.description() : "",
                req.shuffleQuestions() != null && req.shuffleQuestions(),
                req.allowEdit() == null || req.allowEdit(),
                req.forced() != null && req.forced(),
                req.startAt(),
                req.endAt())) {
            throw new NotFoundResponse();
        }
        formService.findById(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
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
        requireOwnedForm(id, UserSession.from(ctx));
        if (formService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
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
        var form = requireOwnedForm(id, UserSession.from(ctx));
        if (form.status() != Form.FormStatus.DRAFT) throw new BadRequestResponse("Form is not in DRAFT status");
        formService.publish(id);

        formService.findById(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
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
        requireOwnedForm(id, UserSession.from(ctx));
        if (!formService.close(id)) throw new NotFoundResponse();
        formService.findById(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    // -- Questions --

    @OpenApi(
            path = "/api/v1/forms/{id}/questions",
            methods = HttpMethod.GET,
            summary = "List questions for a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormQuestion[].class)))
    private void listQuestions(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedForm(id, session);
        ctx.json(formService.findQuestions(id));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/questions",
            methods = HttpMethod.PUT,
            summary = "Replace all questions for a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuestionRequest[].class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormQuestion[].class)))
    private void setQuestions(Context ctx) {
        int id = pathInt(ctx, "id");
        var form = requireOwnedForm(id, UserSession.from(ctx));
        var questions = ctx.bodyAsClass(QuestionRequest[].class);
        var disallowed = Arrays.stream(questions)
                .map(QuestionRequest::questionType)
                .filter(t -> !t.allowedFor(form.purpose()))
                .distinct()
                .toList();
        if (!disallowed.isEmpty()) {
            throw new BadRequestResponse(
                    "Question type(s) %s are not allowed for form purpose %s".formatted(disallowed, form.purpose()));
        }
        formService.replaceQuestions(
                id,
                Arrays.stream(questions)
                        .map(q -> new QuestionEntry(
                                q.questionType(),
                                q.title(),
                                q.description() != null ? q.description() : "",
                                q.required() != null && q.required(),
                                q.shuffle() != null && q.shuffle(),
                                q.config() != null ? q.config() : new FormQuestionConfig.Unknown()))
                        .toList());
        ctx.json(formService.findQuestions(id));
    }

    // -- Restrictions --

    @OpenApi(
            path = "/api/v1/forms/{id}/restrictions",
            methods = HttpMethod.GET,
            summary = "Get form restrictions",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormRestrictions.class)))
    private void getRestrictions(Context ctx) {
        UserSession session = UserSession.from(ctx);
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
        requireOwnedForm(id, UserSession.from(ctx));
        var req = ctx.bodyAsClass(FormRestrictions.class);
        formService.setRestrictions(
                id,
                new RestrictionSelection(req.userTypes(), req.groupIds(), req.tagIds(), req.memberIds(), req.mode()));
        if (req.mode() != null) {
            formService.updateRestrictionMode(id, req.mode());
        }
        ctx.json(req);
    }

    // -- Responding --

    @OpenApi(
            path = "/api/v1/forms/{id}/my-response",
            methods = HttpMethod.GET,
            summary = "Get my response to a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ResponseDetailDto.class)))
    private void getMyResponse(Context ctx) {
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        requireOwnedForm(id, session);
        var response = formService.findResponse(id, session.member().id());
        if (response.isEmpty()) {
            ctx.json(new ResponseDetailDto(null, List.of()));
            return;
        }
        ctx.json(analyticsAssembler.getResponseDetail(id, response.get().id()));
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
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.json(new EligibleMembers(false, List.of()));
            return;
        }
        requireOwnedForm(id, session);
        boolean selfEligible = formService.canMemberAccess(id, session.member().id());
        var managed = stationMemberService.findManaged(session.member().id());
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
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SubmitRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = FormResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void submitResponse(Context ctx) {
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        var form = requireOwnedForm(id, session);
        if (!formService.isAcceptingResponses(form)) throw new BadRequestResponse("Form is not accepting responses");
        if (!formService.canMemberAccess(id, session.member().id())) {
            throw new ForbiddenResponse("You do not have access to this form");
        }
        var req = ctx.bodyAsClass(SubmitRequest.class);
        try {
            var response = formService.submitResponse(
                    id, session.member().id(), session.member().id(), req.answers());
            ctx.status(HttpStatus.CREATED).json(response);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/respond",
            methods = HttpMethod.PUT,
            summary = "Update my response to a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SubmitRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateResponse(Context ctx) {
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        var form = requireOwnedForm(id, session);
        if (!form.allowEdit()) throw new BadRequestResponse("Form does not allow editing");
        if (!formService.canMemberAccess(id, session.member().id())) {
            throw new ForbiddenResponse("You do not have access to this form");
        }
        var req = ctx.bodyAsClass(SubmitRequest.class);
        try {
            var response = formService.submitResponse(
                    id, session.member().id(), session.member().id(), req.answers());
            ctx.json(response);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
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
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SubmitRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = FormResponse.class)),
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
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SubmitRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateForMember(Context ctx) {
        respondForMember(ctx, false);
    }

    /**
     * Shared handler for submitting or updating a managed member's response. Verifies station
     * membership, that the caller manages the target member, and that the form belongs to the
     * caller's station before delegating to the form service. When {@code creating} is true the
     * form must be accepting responses and a {@code 201} is returned; otherwise the form must
     * allow editing and a {@code 200} is returned.
     *
     * @param creating whether this is an initial submission ({@code true}) or an edit ({@code false})
     */
    private void respondForMember(Context ctx, boolean creating) {
        int id = pathInt(ctx, "id");
        int memberId = pathInt(ctx, "memberId");
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        verifyManages(session, memberId);
        var form = requireOwnedForm(id, session);
        if (creating) {
            if (!formService.isAcceptingResponses(form)) {
                throw new BadRequestResponse("Form is not accepting responses");
            }
        } else if (!form.allowEdit()) {
            throw new BadRequestResponse("Form does not allow editing");
        }
        if (!formService.canMemberAccess(id, memberId)) {
            throw new ForbiddenResponse("The member does not have access to this form");
        }
        var req = ctx.bodyAsClass(SubmitRequest.class);
        try {
            var response =
                    formService.submitResponse(id, memberId, session.member().id(), req.answers());
            if (creating) {
                ctx.status(HttpStatus.CREATED).json(response);
            } else {
                ctx.json(response);
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    /**
     * Verifies that the current user manages the specified member or has POLL_MANAGER role.
     *
     * @param session  the current user session
     * @param memberId the member ID to verify management of
     * @throws ForbiddenResponse if the user does not manage the member and lacks POLL_MANAGER role
     */
    private void verifyManages(UserSession session, int memberId) {
        boolean manages =
                stationMemberService.findManaged(session.member().id()).stream().anyMatch(m -> m.id() == memberId);
        if (!manages && !session.hasPermission(StationPermission.POLL_MANAGER)) {
            throw new ForbiddenResponse("You do not manage this member");
        }
    }

    // -- Analytics --

    @OpenApi(
            path = "/api/v1/forms/{id}/analytics",
            methods = HttpMethod.GET,
            summary = "Get form analytics",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormAnalyticsDto.class)))
    private void getAnalytics(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedForm(id, session);
        ctx.json(analyticsAssembler.buildAnalytics(id));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/responses",
            methods = HttpMethod.GET,
            summary = "List all responses for a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormResponseEntryDto[].class)))
    private void listResponses(Context ctx) {
        UserSession session = UserSession.from(ctx);
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
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ResponseDetailDto.class)))
    private void getResponseDetail(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int formId = pathInt(ctx, "id");
        int responseId = pathInt(ctx, "responseId");
        requireOwnedForm(formId, session);
        ctx.json(analyticsAssembler.getResponseDetail(formId, responseId));
    }

    // -- Records --

    /**
     * Request body for creating or updating a form.
     *
     * @param title            the form title
     * @param description      optional form description
     * @param shuffleQuestions whether to randomize question order
     * @param allowEdit        whether respondents may edit their response
     * @param startAt          optional start time for accepting responses
     * @param endAt            optional end time for accepting responses
     */
    public record FormRequest(
            String title,
            String description,
            Boolean shuffleQuestions,
            Boolean allowEdit,
            Boolean forced,
            Instant startAt,
            Instant endAt,
            FormPurpose purpose) {}

    /**
     * Request body for creating a form question.
     *
     * @param questionType the question type name (must match {@link FormQuestionType})
     * @param title        the question text
     * @param description  optional description
     * @param required     whether an answer is mandatory
     * @param shuffle      whether answer options should be randomized
     * @param config       type-specific configuration as JSON string
     */
    public record QuestionRequest(
            FormQuestionType questionType,
            String title,
            String description,
            Boolean required,
            Boolean shuffle,
            FormQuestionConfig config) {}

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
            List<Integer> memberIds,
            RestrictionMode mode) {}

    /**
     * Request body for submitting or updating a form response.
     *
     * @param answers map of question ID to answer value (JSON string)
     */
    @OpenApiName("FormSubmitRequest")
    public record SubmitRequest(Map<Integer, FormAnswerValue> answers) {}

    /**
     * Lightweight picker result shape for {@code GET /api/v1/forms/search}. Used by the
     * POLL_EMBED and FORMS_CTA cell pickers in the page editor.
     */
    public record FormSearchResult(UUID publicUid, String title, FormPurpose purpose, Form.FormStatus status) {}

    public record FormListEntry(
            int id,
            int stationId,
            String title,
            String description,
            Form.FormStatus status,
            Instant startAt,
            Instant endAt,
            int responseCount,
            boolean hasResponded) {}

    /**
     * Response indicating which members (self and managed) are eligible to respond to a form.
     *
     * @param selfEligible             whether the current user is eligible
     * @param eligibleManagedMemberIds IDs of managed members who are eligible
     */
    public record EligibleMembers(boolean selfEligible, List<Integer> eligibleManagedMemberIds) {}
}
