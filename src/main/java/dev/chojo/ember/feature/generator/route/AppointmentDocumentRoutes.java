/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.route.EventVisibility;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.RequiredTemplate;
import dev.chojo.ember.feature.generator.entity.TemplateSort;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService.AppointmentDocuments;
import dev.chojo.ember.feature.generator.service.DocumentGenerationService.GeneratedDocumentResponse;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.generator.service.TemplateQuery.TemplatePage;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * The documents appointments ask participants to bring: which ones an appointment or an appointment
 * template asks for, written with the right that writes them, and the copies a participant or their
 * guardian gets, read with nothing more than the right to see the appointment.
 */
@Singleton
public class AppointmentDocumentRoutes implements Routes {
    private final EventRequirementService requirements;
    private final AppointmentDocumentService documents;
    private final EventVisibility visibility;
    private final EventTemplateService eventTemplates;

    @Inject
    public AppointmentDocumentRoutes(
            EventRequirementService requirements,
            AppointmentDocumentService documents,
            EventVisibility visibility,
            EventTemplateService eventTemplates) {
        this.requirements = requirements;
        this.documents = documents;
        this.visibility = visibility;
        this.eventTemplates = eventTemplates;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/document-requirements/templates",
                this::offered,
                StationPermission.EVENT_EDIT,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.get(prefix + "/events/{id}/document-requirements", this::forEvent, StationPermission.EVENT_EDIT);
        routes.put(prefix + "/events/{id}/document-requirements", this::setForEvent, StationPermission.EVENT_EDIT);
        routes.get(
                prefix + "/event-templates/{id}/document-requirements",
                this::forEventTemplate,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.put(
                prefix + "/event-templates/{id}/document-requirements",
                this::setForEventTemplate,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.get(prefix + "/events/{id}/documents-to-bring", this::toBring, StationPermission.USER);
        routes.post(
                prefix + "/events/{id}/documents-to-bring/{templateId}/members/{memberId}",
                this::generate,
                StationPermission.USER);
    }

    /**
     * The documents to ask for, in their order.
     *
     * @param templateIds the document templates
     */
    public record RequirementsRequest(@Nullable List<Integer> templateIds) {}

    @OpenApi(
            path = "/api/v1/document-requirements/templates",
            methods = HttpMethod.GET,
            summary = "One page of the document templates appointments may ask participants to bring",
            tags = {"Events"},
            queryParams = {
                @OpenApiParam(name = "q", description = "What the name contains"),
                @OpenApiParam(name = "kind", type = DocumentTemplateKind.class),
                @OpenApiParam(name = "sort", type = TemplateSort.class),
                @OpenApiParam(name = "page", type = Integer.class, description = "Counted from 0"),
                @OpenApiParam(name = "size", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TemplatePage.class)))
    private void offered(Context ctx) {
        ctx.json(requirements.offered(StationSession.from(ctx).owner(), TemplateQueries.of(ctx)));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/document-requirements",
            methods = HttpMethod.GET,
            summary = "The documents an appointment asks participants to bring",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RequiredTemplate[].class)))
    private void forEvent(Context ctx) {
        var event = visibility.requireVisibleEvent(StationSession.from(ctx), pathInt(ctx, "id"));
        ctx.json(requirements.forEvent(event.id()));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/document-requirements",
            methods = HttpMethod.PUT,
            summary = "Set the documents an appointment asks participants to bring",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RequirementsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = RequiredTemplate[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setForEvent(Context ctx) {
        var session = StationSession.from(ctx);
        var event = visibility.requireVisibleEvent(session, pathInt(ctx, "id"));
        ctx.json(requirements.setForEvent(session.owner(), event.id(), templateIds(ctx)));
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}/document-requirements",
            methods = HttpMethod.GET,
            summary = "The documents an appointment template hands to the appointments made from it",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RequiredTemplate[].class)))
    private void forEventTemplate(Context ctx) {
        ctx.json(requirements.forEventTemplate(requireOwnedEventTemplate(ctx)));
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}/document-requirements",
            methods = HttpMethod.PUT,
            summary = "Set the documents an appointment template hands to the appointments made from it",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RequirementsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = RequiredTemplate[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setForEventTemplate(Context ctx) {
        var session = StationSession.from(ctx);
        int eventTemplateId = requireOwnedEventTemplate(ctx);
        ctx.json(requirements.setForEventTemplate(session.owner(), eventTemplateId, templateIds(ctx)));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/documents-to-bring",
            methods = HttpMethod.GET,
            summary =
                    "The documents an appointment asks for on a date, the reader's own copies and, for whoever manages the registrations, every participant's",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "date", required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AppointmentDocuments.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void toBring(Context ctx) {
        var session = StationSession.from(ctx);
        var event = visibility.requireVisibleEvent(session, pathInt(ctx, "id"));
        boolean overview = session.hasPermission(StationPermission.EVENT_REGISTRATION);
        ctx.json(documents.documentsToBring(session, event, date(ctx), overview));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/documents-to-bring/{templateId}/members/{memberId}",
            methods = HttpMethod.POST,
            summary = "Generate a participant's copy of a document the appointment asks for and file it with them",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "templateId", type = Integer.class, required = true),
                @OpenApiParam(name = "memberId", type = Integer.class, required = true)
            },
            queryParams = @OpenApiParam(name = "date", required = true),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = GeneratedDocumentResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void generate(Context ctx) {
        var session = StationSession.from(ctx);
        var event = visibility.requireVisibleEvent(session, pathInt(ctx, "id"));
        ctx.status(HttpStatus.CREATED)
                .json(documents.generate(
                        session, event, date(ctx), pathInt(ctx, "templateId"), pathInt(ctx, "memberId")));
    }

    private int requireOwnedEventTemplate(Context ctx) {
        return requireOwnedOrNotFound(ctx, pathInt(ctx, "id"), eventTemplates::findById, EventTemplate::stationId)
                .id();
    }

    private static List<Integer> templateIds(Context ctx) {
        return Objects.requireNonNullElse(
                ctx.bodyAsClass(RequirementsRequest.class).templateIds(), List.of());
    }

    /**
     * The date of the appointment the documents are for, which every request about them names.
     *
     * @param ctx the request, carrying the date as the query parameter {@code date}
     * @return the date
     */
    public static LocalDate date(Context ctx) {
        String value = ctx.queryParam("date");
        if (value == null || value.isBlank()) throw EventRefusal.EVENT_DOCUMENTS_DATE_MISSING.raise();
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw EventRefusal.EVENT_DOCUMENTS_DATE_MISSING.raise();
        }
    }
}
