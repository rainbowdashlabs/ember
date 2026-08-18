/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldConfig;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.entity.EventTemplateField;
import dev.chojo.ember.feature.events.entity.EventTemplateFieldData;
import dev.chojo.ember.feature.events.entity.EventTemplateRegistrationField;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository.FieldEntry;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

@Singleton
public class EventTemplateRoutes implements Routes {
    private final EventTemplateService eventTemplateService;
    private final EventRegistrationFieldService registrationFieldService;

    @Inject
    public EventTemplateRoutes(
            EventTemplateService eventTemplateService, EventRegistrationFieldService registrationFieldService) {
        this.eventTemplateService = eventTemplateService;
        this.registrationFieldService = registrationFieldService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/event-templates",
                this::list,
                StationPermission.EVENT_EDIT,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.post(prefix + "/event-templates", this::create, StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.get(
                prefix + "/event-templates/{id}",
                this::get,
                StationPermission.EVENT_EDIT,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.put(prefix + "/event-templates/{id}", this::update, StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.delete(prefix + "/event-templates/{id}", this::delete, StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.put(prefix + "/event-templates/{id}/fields", this::setFields, StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.put(
                prefix + "/event-templates/{id}/registration-fields",
                this::setRegistrationFields,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.put(
                prefix + "/event-templates/{id}/restrictions",
                this::setRestrictions,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.get(
                prefix + "/event-templates/{id}/reminders",
                this::getReminders,
                StationPermission.EVENT_EDIT,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.put(
                prefix + "/event-templates/{id}/reminders",
                this::setReminders,
                StationPermission.EVENT_MANAGE_TEMPLATE);
    }

    @OpenApi(
            path = "/api/v1/event-templates",
            methods = HttpMethod.GET,
            summary = "List all event templates for the current station",
            tags = {"Event Templates"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventTemplate[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(eventTemplateService.findByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/event-templates",
            methods = HttpMethod.POST,
            summary = "Create a new event template",
            tags = {"Event Templates"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateTemplateRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = EventTemplate.class)))
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CreateTemplateRequest.class);
        if (req.name() == null || req.name().isBlank()) {
            throw new BadRequestResponse("name is required");
        }
        ctx.status(HttpStatus.CREATED).json(eventTemplateService.create(session.stationId(), req.name()));
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}",
            methods = HttpMethod.GET,
            summary = "Get an event template with its fields and restrictions",
            tags = {"Event Templates"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TemplateDetailResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        int id = pathInt(ctx, "id");
        var template = requireOwnedOrNotFound(ctx, id, eventTemplateService::findById, EventTemplate::stationId);
        var fields = eventTemplateService.findFields(id);
        var restrictionUserTypes = eventTemplateService.findRestrictions(id);
        var reminderDays = eventTemplateService.findReminderDays(id);
        var registrationFields = registrationFieldService.findByTemplate(id);
        ctx.json(new TemplateDetailResponse(template, fields, restrictionUserTypes, reminderDays, registrationFields));
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}/registration-fields",
            methods = HttpMethod.PUT,
            summary = "Replace the registration questions an event template carries",
            tags = {"Event Templates"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetRegistrationFieldsRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void setRegistrationFields(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, eventTemplateService::findById, EventTemplate::stationId);
        var req = ctx.bodyAsClass(SetRegistrationFieldsRequest.class);
        var fields = req.fields() == null ? List.<RegistrationFieldDefinition>of() : req.fields();
        registrationFieldService.replaceTemplateFields(
                id,
                fields.stream()
                        .map(f -> new FieldEntry(
                                f.name(),
                                f.fieldType(),
                                f.config() != null ? f.config() : EventRegistrationFieldConfig.empty(),
                                f.overview()))
                        .toList());
        ctx.json(registrationFieldService.findByTemplate(id));
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}",
            methods = HttpMethod.PUT,
            summary = "Update an event template",
            tags = {"Event Templates"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateTemplateRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventTemplate.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, eventTemplateService::findById, EventTemplate::stationId);
        var req = ctx.bodyAsClass(UpdateTemplateRequest.class);
        if (!eventTemplateService.update(
                id,
                req.name(),
                req.title(),
                req.description(),
                req.categoryId(),
                req.eventType(),
                req.requiresRegistration(),
                req.registrationDeadlineOffset(),
                req.requiresConfirmation(),
                req.restrictionMode(),
                req.attendanceTemplateId(),
                req.registrationLimit())) {
            throw new NotFoundResponse();
        }
        ctx.json(eventTemplateService.findById(id).orElseThrow());
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an event template",
            tags = {"Event Templates"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, eventTemplateService::findById, EventTemplate::stationId);
        if (eventTemplateService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}/fields",
            methods = HttpMethod.PUT,
            summary = "Replace all fields for an event template",
            tags = {"Event Templates"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetFieldsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventTemplateField[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setFields(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, eventTemplateService::findById, EventTemplate::stationId);
        var req = ctx.bodyAsClass(SetFieldsRequest.class);
        eventTemplateService.replaceFields(id, req.fields());
        ctx.json(eventTemplateService.findFields(id));
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}/restrictions",
            methods = HttpMethod.PUT,
            summary = "Set role restrictions for an event template",
            tags = {"Event Templates"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetRestrictionsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = Integer[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setRestrictions(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, eventTemplateService::findById, EventTemplate::stationId);
        var req = ctx.bodyAsClass(SetRestrictionsRequest.class);
        eventTemplateService.setRestrictions(id, req.userTypes());
        ctx.json(eventTemplateService.findRestrictions(id));
    }

    private void getReminders(Context ctx) {
        int id = pathInt(ctx, "id");
        ctx.json(eventTemplateService.findReminderDays(id));
    }

    private void setReminders(Context ctx) {
        int id = pathInt(ctx, "id");
        var req = ctx.bodyAsClass(SetRemindersRequest.class);
        eventTemplateService.setReminders(id, req.daysBefore() != null ? req.daysBefore() : List.of());
        ctx.json(eventTemplateService.findReminderDays(id));
    }

    public record CreateTemplateRequest(String name) {}

    public record UpdateTemplateRequest(
            String name,
            String title,
            String description,
            Integer categoryId,
            StationEvent.EventType eventType,
            Boolean requiresRegistration,
            String registrationDeadlineOffset,
            Boolean requiresConfirmation,
            RestrictionMode restrictionMode,
            Integer attendanceTemplateId,
            Integer registrationLimit) {}

    public record TemplateDetailResponse(
            EventTemplate template,
            List<EventTemplateField> fields,
            List<String> restrictionUserTypes,
            List<Integer> reminderDays,
            List<EventTemplateRegistrationField> registrationFields) {}

    public record SetFieldsRequest(List<EventTemplateFieldData> fields) {}

    public record SetRegistrationFieldsRequest(List<RegistrationFieldDefinition> fields) {}

    /**
     * A registration question as the template editor submits it, before it has a row of its own.
     */
    public record RegistrationFieldDefinition(
            String name, EventFieldType fieldType, EventRegistrationFieldConfig config, boolean overview) {}

    public record SetRestrictionsRequest(List<StationUserType> userTypes) {}

    public record SetRemindersRequest(List<Integer> daysBefore) {}
}
