/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.service.EventBreakService;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFieldDefaultService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import io.javalin.http.BadRequestResponse;
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

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;
import static dev.chojo.ember.feature.events.route.EventOwnership.requireOwnedEvent;

/**
 * Local routes for the structures an event is filed under and described by: categories, break
 * periods, per-event fields and their defaults.
 *
 * <p>Must be bound before {@link EventRoutes}: Javalin answers a request with the first matching
 * handler, and its {@code GET /events/{id}} would otherwise swallow the literal
 * {@code /events/categories}, {@code /events/breaks}, {@code /events/field-names} and
 * {@code /events/overview-fields} reads registered here.
 */
@Singleton
public class EventStructureRoutes implements Routes {
    private final EventCrudService crudService;
    private final EventCategoryService categoryService;
    private final EventBreakService breakService;
    private final EventFieldDefaultService fieldDefaultService;
    private final EventFieldService eventFieldService;

    @Inject
    public EventStructureRoutes(
            EventCrudService crudService,
            EventCategoryService categoryService,
            EventBreakService breakService,
            EventFieldDefaultService fieldDefaultService,
            EventFieldService eventFieldService) {
        this.crudService = crudService;
        this.categoryService = categoryService;
        this.breakService = breakService;
        this.fieldDefaultService = fieldDefaultService;
        this.eventFieldService = eventFieldService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events/field-names", this::listFieldNames, StationPermission.EVENT_EDIT);
        routes.get(prefix + "/events/overview-fields", this::getOverviewFields, StationPermission.USER);

        routes.get(prefix + "/events/categories", this::listCategories, StationPermission.USER);
        routes.post(prefix + "/events/categories", this::createCategory, StationPermission.EVENT_MANAGE_CATEGORY);
        routes.put(
                prefix + "/events/categories/reorder",
                this::reorderCategories,
                StationPermission.EVENT_MANAGE_CATEGORY);
        routes.put(prefix + "/events/categories/{id}", this::updateCategory, StationPermission.EVENT_MANAGE_CATEGORY);
        routes.delete(
                prefix + "/events/categories/{id}", this::deleteCategory, StationPermission.EVENT_MANAGE_CATEGORY);

        routes.get(prefix + "/events/breaks", this::listBreaks, StationPermission.USER);
        routes.post(prefix + "/events/breaks", this::createBreak, StationPermission.EVENT_EDIT);
        routes.put(prefix + "/events/breaks/{id}", this::updateBreak, StationPermission.EVENT_EDIT);
        routes.delete(prefix + "/events/breaks/{id}", this::deleteBreak, StationPermission.EVENT_EDIT);

        routes.get(prefix + "/events/{id}/field-defaults", this::getFieldDefaults, StationPermission.USER);
        routes.put(prefix + "/events/{id}/field-defaults", this::setFieldDefaults, StationPermission.EVENT_EDIT);

        routes.get(prefix + "/events/{id}/fields", this::getFields, StationPermission.USER);
        routes.put(prefix + "/events/{id}/fields", this::setFields, StationPermission.EVENT_EDIT);
        routes.post(
                prefix + "/events/{eventId}/fields/{fieldId}/self-register",
                this::selfRegisterField,
                StationPermission.USER);
    }

    @OpenApi(
            path = "/api/v1/events/categories",
            methods = HttpMethod.GET,
            summary = "List event categories",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventCategory[].class)))
    private void listCategories(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(categoryService.findByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/events/categories",
            methods = HttpMethod.POST,
            summary = "Create an event category",
            tags = {"Events"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CategoryRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = EventCategory.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createCategory(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CategoryRequest.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        ctx.status(HttpStatus.CREATED)
                .json(categoryService.create(session.stationId(), req.name(), req.position(), req.color()));
    }

    @OpenApi(
            path = "/api/v1/events/categories/{id}",
            methods = HttpMethod.PUT,
            summary = "Update an event category",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CategoryRequest.class)),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateCategory(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, categoryService::findById, EventCategory::stationId);
        var req = ctx.bodyAsClass(CategoryRequest.class);
        if (!categoryService.update(
                id,
                req.name(),
                req.position(),
                req.maxShownEvents(),
                req.isPublic() != null && req.isPublic(),
                req.color())) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.OK).json(new MessageResponse("Updated"));
    }

    @OpenApi(
            path = "/api/v1/events/categories/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an event category",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void reorderCategories(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(ReorderCategoriesRequest.class);
        for (int id : req.orderedIds()) {
            requireOwnedOrNotFound(ctx, id, categoryService::findById, EventCategory::stationId);
        }
        categoryService.reorder(session.stationId(), req.orderedIds());
        ctx.json(categoryService.findByStation(session.stationId()));
    }

    private void deleteCategory(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, categoryService::findById, EventCategory::stationId);
        if (categoryService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/events/breaks",
            methods = HttpMethod.GET,
            summary = "List event breaks",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventBreak[].class)))
    private void listBreaks(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(breakService.findByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/events/breaks",
            methods = HttpMethod.POST,
            summary = "Create a break",
            tags = {"Events"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BreakRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = EventBreak.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createBreak(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(BreakRequest.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        ctx.status(HttpStatus.CREATED)
                .json(breakService.create(session.stationId(), req.name(), req.startDate(), req.endDate()));
    }

    @OpenApi(
            path = "/api/v1/events/breaks/{id}",
            methods = HttpMethod.PUT,
            summary = "Update a break",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BreakRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventBreak.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateBreak(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, breakService::findById, EventBreak::stationId);
        var req = ctx.bodyAsClass(BreakRequest.class);
        breakService.update(id, req.name(), req.startDate(), req.endDate()).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/events/breaks/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a break",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteBreak(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, breakService::findById, EventBreak::stationId);
        if (breakService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/events/{id}/field-defaults",
            methods = HttpMethod.GET,
            summary = "Get event field defaults",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventFieldDefault[].class)))
    private void getFieldDefaults(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedEvent(crudService, id, session);
        ctx.json(fieldDefaultService.findByEvent(id));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/field-defaults",
            methods = HttpMethod.PUT,
            summary = "Set event field defaults",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FieldDefaultEntry[].class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventFieldDefault[].class)))
    private void setFieldDefaults(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedEvent(crudService, id, session);
        var req = ctx.bodyAsClass(FieldDefaultEntry[].class);
        var defaults = Arrays.stream(req)
                .map(e -> new EventFieldDefault(id, e.fieldId(), e.source(), e.value()))
                .toList();
        fieldDefaultService.setForEvent(id, defaults);
        ctx.json(fieldDefaultService.findByEvent(id));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/fields",
            methods = HttpMethod.GET,
            summary = "Get fields for an event",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventField[].class)))
    private void getFields(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedEvent(crudService, id, session);
        ctx.json(eventFieldService.findByEvent(id));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/fields",
            methods = HttpMethod.PUT,
            summary = "Replace all fields for an event",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetEventFieldsRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventField[].class)))
    private void setFields(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedEvent(crudService, id, session);
        var req = ctx.bodyAsClass(SetEventFieldsRequest.class);
        eventFieldService.replaceFields(
                id,
                req.fields().stream()
                        .map(e -> new EventFieldRepository.FieldEntry(
                                e.name(),
                                e.fieldType(),
                                e.config(),
                                e.value() != null ? e.value() : "",
                                e.overview() != null && e.overview(),
                                e.attendanceFieldId(),
                                e.isPublic() != null && e.isPublic()))
                        .toList());
        ctx.json(eventFieldService.findByEvent(id));
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/fields/{fieldId}/self-register",
            methods = HttpMethod.POST,
            summary = "Toggle the caller's presence on a self-registration member field",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventId", type = Integer.class, required = true),
                @OpenApiParam(name = "fieldId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventField.class)))
    private void selfRegisterField(Context ctx) {
        var session = UserSession.from(ctx);
        int eventId = pathInt(ctx, "eventId");
        int fieldId = pathInt(ctx, "fieldId");
        requireOwnedEvent(crudService, eventId, session);
        ctx.json(eventFieldService.toggleSelfRegistration(
                eventId, fieldId, session.member().id()));
    }

    private void getOverviewFields(Context ctx) {
        var session = UserSession.from(ctx);
        var eventIds = crudService.findByStation(session.stationId()).stream()
                .map(StationEvent::id)
                .toList();
        ctx.json(eventFieldService.findOverviewFieldsByEvents(eventIds));
    }

    @OpenApi(
            path = "/api/v1/events/field-names",
            methods = HttpMethod.GET,
            summary = "List distinct event field names used across all events",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200"))
    private void listFieldNames(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(eventFieldService.findDistinctFieldNames(session.stationId()));
    }

    public record CategoryRequest(String name, int position, Integer maxShownEvents, Boolean isPublic, String color) {}

    public record ReorderCategoriesRequest(List<Integer> orderedIds) {}

    public record BreakRequest(String name, LocalDate startDate, LocalDate endDate) {}

    public record FieldDefaultEntry(int fieldId, String source, String value) {}

    @OpenApiName("SetEventFieldsRequest")
    public record SetEventFieldsRequest(List<EventFieldEntry> fields) {}

    @OpenApiName("EventFieldEntry")
    public record EventFieldEntry(
            String name,
            EventFieldType fieldType,
            EventFieldConfig config,
            String value,
            Boolean overview,
            Integer attendanceFieldId,
            Boolean isPublic) {}
}
