/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormAnswer;
import dev.chojo.ember.feature.form.entity.FormQuestion;
import dev.chojo.ember.feature.form.entity.FormResponse;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * HTTP route handlers for form management, including CRUD operations, question management,
 * access restrictions, response submission, and analytics. Requires {@code POLL_MANAGEMENT} role
 * for administrative endpoints and {@code USER} role for respondent endpoints.
 */
@Singleton
public class FormRoutes implements Routes {
    private final FormService formService;
    private final NotificationService notificationService;
    private final StationMemberService stationMemberService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;

    @Inject
    public FormRoutes(
            FormService formService,
            NotificationService notificationService,
            StationMemberService stationMemberService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository) {
        this.formService = formService;
        this.notificationService = notificationService;
        this.stationMemberService = stationMemberService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
    }

    /** {@inheritDoc} */
    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Management
        routes.get(prefix + "/forms", this::list, Roles.POLL_MANAGEMENT);
        routes.post(prefix + "/forms", this::create, Roles.POLL_MANAGEMENT);
        routes.get(prefix + "/forms/available", this::listAvailable, Roles.USER);
        routes.get(prefix + "/forms/{id}", this::get, Roles.USER);
        routes.put(prefix + "/forms/{id}", this::update, Roles.POLL_MANAGEMENT);
        routes.delete(prefix + "/forms/{id}", this::delete, Roles.POLL_MANAGEMENT);
        routes.post(prefix + "/forms/{id}/publish", this::publish, Roles.POLL_MANAGEMENT);
        routes.post(prefix + "/forms/{id}/close", this::close, Roles.POLL_MANAGEMENT);

        // Questions
        routes.get(prefix + "/forms/{id}/questions", this::listQuestions, Roles.USER);
        routes.put(prefix + "/forms/{id}/questions", this::setQuestions, Roles.POLL_MANAGEMENT);

        // Restrictions
        routes.get(prefix + "/forms/{id}/restrictions", this::getRestrictions, Roles.USER);
        routes.put(prefix + "/forms/{id}/restrictions", this::setRestrictions, Roles.POLL_MANAGEMENT);

        // Responding
        routes.get(prefix + "/forms/{id}/my-response", this::getMyResponse, Roles.USER);
        routes.get(prefix + "/forms/{id}/eligible-members", this::getEligibleMembers, Roles.USER);
        routes.post(prefix + "/forms/{id}/respond", this::submitResponse, Roles.USER);
        routes.put(prefix + "/forms/{id}/respond", this::updateResponse, Roles.USER);
        routes.post(prefix + "/forms/{id}/respond/{memberId}", this::submitForMember, Roles.GUARDIAN);
        routes.put(prefix + "/forms/{id}/respond/{memberId}", this::updateForMember, Roles.GUARDIAN);

        // Analytics
        routes.get(prefix + "/forms/{id}/analytics", this::getAnalytics, Roles.POLL_MANAGEMENT);
        routes.get(prefix + "/forms/{id}/responses", this::listResponses, Roles.POLL_MANAGEMENT);
        routes.get(prefix + "/forms/{id}/responses/{responseId}", this::getResponseDetail, Roles.POLL_MANAGEMENT);
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
        ctx.json(formService.findByStation(session.stationId()));
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
        var openForms = formService.findByStation(session.stationId()).stream()
                .filter(f -> f.status() == Form.FormStatus.OPEN)
                .filter(formService::isAcceptingResponses)
                .toList();

        // Include forms accessible by self OR any managed member
        var managed = stationMemberService.findManaged(memberId);
        var result = openForms.stream()
                .filter(f -> {
                    if (formService.canMemberAccess(f.id(), memberId)) return true;
                    return managed.stream().anyMatch(m -> formService.canMemberAccess(f.id(), m.id()));
                })
                .map(f -> new FormListEntry(
                        f.id(),
                        f.stationId(),
                        f.title(),
                        f.description(),
                        f.status().name(),
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
                req.startAt(),
                req.endAt(),
                session.member().id());
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        formService.findById(id).ifPresentOrElse(item -> ctx.json(item), () -> {
            throw new NotFoundResponse();
        });
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(FormRequest.class);
        if (!formService.update(
                id,
                req.title(),
                req.description() != null ? req.description() : "",
                req.shuffleQuestions() != null && req.shuffleQuestions(),
                req.allowEdit() == null || req.allowEdit(),
                req.startAt(),
                req.endAt())) {
            throw new NotFoundResponse();
        }
        formService.findById(id).ifPresentOrElse(item -> ctx.json(item), () -> {
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (formService.delete(id)) {
            deleteFormNotifications(id);
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
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var form = formService.findById(id).orElseThrow(NotFoundResponse::new);
        if (form.status() != Form.FormStatus.DRAFT) throw new BadRequestResponse("Form is not in DRAFT status");
        formService.publish(id);

        notificationService.notifyStation(
                session.stationId(),
                NotificationType.NEW_FORM,
                NotificationData.of(
                        "notification.newForm",
                        Map.of("title", form.title()),
                        new NotificationData.NotificationLink("forms-fill", Map.of("id", String.valueOf(id)))));

        formService.findById(id).ifPresentOrElse(item -> ctx.json(item), () -> {
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (!formService.close(id)) throw new NotFoundResponse();
        deleteFormNotifications(id);
        formService.findById(id).ifPresentOrElse(item -> ctx.json(item), () -> {
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var questions = ctx.bodyAsClass(QuestionRequest[].class);
        formService.replaceQuestions(
                id,
                Arrays.stream(questions)
                        .map(q -> new FormService.QuestionEntry(
                                FormQuestion.QuestionType.valueOf(q.questionType()),
                                q.title(),
                                q.description() != null ? q.description() : "",
                                q.required() != null && q.required(),
                                q.shuffle() != null && q.shuffle(),
                                q.config() != null ? q.config() : "{}"))
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(new FormRestrictions(
                formService.findRoleRestrictions(id),
                formService.findGroupRestrictions(id),
                formService.findTagRestrictions(id)));
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(FormRestrictions.class);
        formService.setRestrictions(id, req.roleIds(), req.groupIds(), req.tagIds());
        ctx.json(req);
    }

    // -- Responding --

    @OpenApi(
            path = "/api/v1/forms/{id}/my-response",
            methods = HttpMethod.GET,
            summary = "Get my response to a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ResponseDetail.class)))
    private void getMyResponse(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        var response = formService.findResponse(id, session.member().id());
        if (response.isEmpty()) {
            ctx.json(new ResponseDetail(null, List.of()));
            return;
        }
        var answers = formService.findAnswers(response.get().id());
        ctx.json(new ResponseDetail(toResponseEntry(response.get()), answers));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/eligible-members",
            methods = HttpMethod.GET,
            summary = "Get which members (self + managed) are eligible for this form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EligibleMembers.class)))
    private void getEligibleMembers(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.json(new EligibleMembers(false, List.of()));
            return;
        }
        boolean selfEligible = formService.canMemberAccess(id, session.member().id());
        var managed = stationMemberService.findManaged(session.member().id());
        var eligibleManagedIds = managed.stream()
                .filter(m -> formService.canMemberAccess(id, m.id()))
                .map(m -> m.id())
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        var form = formService.findById(id).orElseThrow(NotFoundResponse::new);
        if (!formService.isAcceptingResponses(form)) throw new BadRequestResponse("Form is not accepting responses");
        if (!formService.canMemberAccess(id, session.member().id())) {
            throw new ForbiddenResponse("You do not have access to this form");
        }
        var req = ctx.bodyAsClass(SubmitRequest.class);
        var response = formService.submitResponse(
                id, session.member().id(), session.member().id(), req.answers());
        ctx.status(HttpStatus.CREATED).json(response);
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        var form = formService.findById(id).orElseThrow(NotFoundResponse::new);
        if (!form.allowEdit()) throw new BadRequestResponse("Form does not allow editing");
        if (!formService.canMemberAccess(id, session.member().id())) {
            throw new ForbiddenResponse("You do not have access to this form");
        }
        var req = ctx.bodyAsClass(SubmitRequest.class);
        var response = formService.submitResponse(
                id, session.member().id(), session.member().id(), req.answers());
        ctx.json(response);
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        verifyManages(session, memberId);
        var form = formService.findById(id).orElseThrow(NotFoundResponse::new);
        if (!formService.isAcceptingResponses(form)) throw new BadRequestResponse("Form is not accepting responses");
        if (!formService.canMemberAccess(id, memberId)) {
            throw new ForbiddenResponse("The member does not have access to this form");
        }
        var req = ctx.bodyAsClass(SubmitRequest.class);
        var response = formService.submitResponse(id, memberId, session.member().id(), req.answers());
        ctx.status(HttpStatus.CREATED).json(response);
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        verifyManages(session, memberId);
        var form = formService.findById(id).orElseThrow(NotFoundResponse::new);
        if (!form.allowEdit()) throw new BadRequestResponse("Form does not allow editing");
        if (!formService.canMemberAccess(id, memberId)) {
            throw new ForbiddenResponse("The member does not have access to this form");
        }
        var req = ctx.bodyAsClass(SubmitRequest.class);
        var response = formService.submitResponse(id, memberId, session.member().id(), req.answers());
        ctx.json(response);
    }

    /**
     * Deletes all NEW_FORM notifications associated with a specific form, used when a form is deleted or closed.
     *
     * @param formId the form ID whose notifications should be removed
     */
    private void deleteFormNotifications(int formId) {
        notificationService.deleteByTypeContaining(
                NotificationType.NEW_FORM,
                NotificationData.of(
                                "notification.newForm",
                                Map.of(),
                                new NotificationData.NotificationLink(
                                        "forms-fill", Map.of("id", String.valueOf(formId))))
                        .toJson());
    }

    /**
     * Verifies that the current user manages the specified member or has POLL_MANAGEMENT role.
     *
     * @param session  the current user session
     * @param memberId the member ID to verify management of
     * @throws ForbiddenResponse if the user does not manage the member and lacks POLL_MANAGEMENT role
     */
    private void verifyManages(UserSession session, int memberId) {
        boolean manages =
                stationMemberService.findManaged(session.member().id()).stream().anyMatch(m -> m.id() == memberId);
        if (!manages && !session.hasRole(Roles.POLL_MANAGEMENT)) {
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
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormAnalytics.class)))
    private void getAnalytics(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var questions = formService.findQuestions(id);
        var questionAnalytics = questions.stream()
                .map(q -> {
                    var answers = formService.findAllAnswersForQuestion(q.id());
                    var values = answers.stream().map(FormAnswer::value).toList();
                    return new QuestionAnalytics(q.id(), q.questionType().name(), q.title(), q.config(), values);
                })
                .toList();
        ctx.json(new FormAnalytics(id, formService.countResponses(id), questionAnalytics));
    }

    @OpenApi(
            path = "/api/v1/forms/{id}/responses",
            methods = HttpMethod.GET,
            summary = "List all responses for a form",
            tags = {"Forms"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormResponse[].class)))
    /**
     * Resolves a member ID to their full name by looking up the station member and account.
     *
     * @param memberId the member ID
     * @return the member's full name, or {@code null} if the member or account cannot be found
     */
    private String resolveMemberName(int memberId) {
        return stationMemberRepository
                .findById(memberId)
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse(null);
    }

    /**
     * Converts a {@link FormResponse} to a {@link FormResponseEntry}, resolving the submitter name
     * when the response was submitted by a different member (e.g., a guardian).
     *
     * @param r the form response
     * @return the enriched response entry
     */
    private FormResponseEntry toResponseEntry(FormResponse r) {
        String submittedByName = r.submittedBy() != r.memberId() ? resolveMemberName(r.submittedBy()) : null;
        return new FormResponseEntry(
                r.id(), r.formId(), r.memberId(), r.submittedBy(), submittedByName, r.submittedAt(), r.updatedAt());
    }

    private void listResponses(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(formService.findResponses(id).stream()
                .map(this::toResponseEntry)
                .toList());
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
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ResponseDetail.class)))
    private void getResponseDetail(Context ctx) {
        int responseId = ctx.pathParamAsClass("responseId", Integer.class).get();
        var answers = formService.findAnswers(responseId);
        // Find the response object from the list
        var responses = formService.findResponses(
                ctx.pathParamAsClass("id", Integer.class).get());
        var response =
                responses.stream().filter(r -> r.id() == responseId).findFirst().orElseThrow(NotFoundResponse::new);
        ctx.json(new ResponseDetail(toResponseEntry(response), answers));
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
            Instant startAt,
            Instant endAt) {}

    /**
     * Request body for creating a form question.
     *
     * @param questionType the question type name (must match {@link FormQuestion.QuestionType})
     * @param title        the question text
     * @param description  optional description
     * @param required     whether an answer is mandatory
     * @param shuffle      whether answer options should be randomized
     * @param config       type-specific configuration as JSON string
     */
    public record QuestionRequest(
            String questionType, String title, String description, Boolean required, Boolean shuffle, String config) {}

    /**
     * Access restrictions for a form, specifying which roles, groups, and tags may access it.
     *
     * @param roleIds  list of role IDs that grant access
     * @param groupIds list of group IDs that grant access
     * @param tagIds   list of tag IDs that grant access
     */
    public record FormRestrictions(List<Integer> roleIds, List<Integer> groupIds, List<Integer> tagIds) {}

    /**
     * Request body for submitting or updating a form response.
     *
     * @param answers map of question ID to answer value (JSON string)
     */
    @OpenApiName("FormSubmitRequest")
    public record SubmitRequest(Map<Integer, String> answers) {}

    /**
     * Enriched form response entry with optional submitter name resolution.
     *
     * @param id              unique response identifier
     * @param formId          the form this response belongs to
     * @param memberId        the member this response is for
     * @param submittedBy     the member who submitted the response
     * @param submittedByName resolved name of the submitter, or {@code null} if submitted by the member themselves
     * @param submittedAt     timestamp of submission
     * @param updatedAt       timestamp of last update
     */
    public record FormResponseEntry(
            int id,
            int formId,
            int memberId,
            int submittedBy,
            String submittedByName,
            Instant submittedAt,
            Instant updatedAt) {}

    /**
     * Detailed view of a form response including all individual answers.
     *
     * @param response the response metadata, or {@code null} if the member has not responded
     * @param answers  list of answers within the response
     */
    public record ResponseDetail(FormResponseEntry response, List<FormAnswer> answers) {}

    /**
     * Summary entry for listing available forms, including response statistics for the current user.
     *
     * @param id            unique form identifier
     * @param stationId     the station this form belongs to
     * @param title         form title
     * @param description   form description
     * @param status        form status name
     * @param startAt       optional start time
     * @param endAt         optional end time
     * @param responseCount total number of responses submitted
     * @param hasResponded  whether the current user has already responded
     */
    public record FormListEntry(
            int id,
            int stationId,
            String title,
            String description,
            String status,
            Instant startAt,
            Instant endAt,
            int responseCount,
            boolean hasResponded) {}

    /**
     * Aggregated analytics for a form, including per-question answer data.
     *
     * @param formId         the form ID
     * @param totalResponses total number of responses
     * @param questions      analytics for each question
     */
    public record FormAnalytics(int formId, int totalResponses, List<QuestionAnalytics> questions) {}

    /**
     * Analytics data for a single question, containing all submitted answer values.
     *
     * @param questionId   the question ID
     * @param questionType the question type name
     * @param title        the question text
     * @param config       type-specific configuration as JSON
     * @param values       all answer values submitted for this question
     */
    public record QuestionAnalytics(
            int questionId, String questionType, String title, String config, List<String> values) {}

    /**
     * Response indicating which members (self and managed) are eligible to respond to a form.
     *
     * @param selfEligible             whether the current user is eligible
     * @param eligibleManagedMemberIds IDs of managed members who are eligible
     */
    public record EligibleMembers(boolean selfEligible, List<Integer> eligibleManagedMemberIds) {}
}
