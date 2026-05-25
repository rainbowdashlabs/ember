/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.entity.EventTemplateField;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class EventTemplateRoutes implements Routes {
    private final EventTemplateService eventTemplateService;

    @Inject
    public EventTemplateRoutes(EventTemplateService eventTemplateService) {
        this.eventTemplateService = eventTemplateService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/event-templates", this::list, Roles.EVENT_MANAGER);
        routes.post(prefix + "/event-templates", this::create, Roles.EVENT_MANAGER);
        routes.get(prefix + "/event-templates/{id}", this::get, Roles.EVENT_MANAGER);
        routes.put(prefix + "/event-templates/{id}", this::update, Roles.EVENT_MANAGER);
        routes.delete(prefix + "/event-templates/{id}", this::delete, Roles.EVENT_MANAGER);
        routes.put(prefix + "/event-templates/{id}/fields", this::setFields, Roles.EVENT_MANAGER);
        routes.put(prefix + "/event-templates/{id}/restrictions", this::setRestrictions, Roles.EVENT_MANAGER);
    }

    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(eventTemplateService.findByStation(session.stationId()));
    }

    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CreateTemplateRequest.class);
        if (req.name() == null || req.name().isBlank()) {
            throw new BadRequestResponse("name is required");
        }
        ctx.status(HttpStatus.CREATED).json(eventTemplateService.create(session.stationId(), req.name()));
    }

    private void get(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var template = eventTemplateService.findById(id).orElseThrow(NotFoundResponse::new);
        if (template.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var fields = eventTemplateService.findFields(id);
        var restrictionRoleIds = eventTemplateService.findRestrictions(id);
        ctx.json(new TemplateDetailResponse(template, fields, restrictionRoleIds));
    }

    private void update(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var existing = eventTemplateService.findById(id).orElseThrow(NotFoundResponse::new);
        if (existing.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
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

    private void delete(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var existing = eventTemplateService.findById(id).orElseThrow(NotFoundResponse::new);
        if (existing.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        if (eventTemplateService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    private void setFields(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var existing = eventTemplateService.findById(id).orElseThrow(NotFoundResponse::new);
        if (existing.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(SetFieldsRequest.class);
        eventTemplateService.replaceFields(id, req.fields());
        ctx.json(eventTemplateService.findFields(id));
    }

    private void setRestrictions(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var existing = eventTemplateService.findById(id).orElseThrow(NotFoundResponse::new);
        if (existing.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(SetRestrictionsRequest.class);
        eventTemplateService.setRestrictions(id, req.roleIds());
        ctx.json(eventTemplateService.findRestrictions(id));
    }

    public record CreateTemplateRequest(String name) {}

    public record UpdateTemplateRequest(
            String name,
            String title,
            String description,
            Integer categoryId,
            String eventType,
            Boolean requiresRegistration,
            String registrationDeadlineOffset,
            Boolean requiresConfirmation,
            String restrictionMode,
            Integer attendanceTemplateId,
            Integer registrationLimit) {}

    public record TemplateDetailResponse(
            EventTemplate template, List<EventTemplateField> fields, List<Integer> restrictionRoleIds) {}

    public record SetFieldsRequest(List<EventTemplateRepository.EventTemplateFieldData> fields) {}

    public record SetRestrictionsRequest(List<Integer> roleIds) {}
}
