/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventBreakService;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFieldDefaultService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.question.FieldType;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;
import static dev.chojo.ember.feature.events.service.EventOwnership.requireOwnedEvent;

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
    private final OccurrenceCalendar occurrenceCalendar;
    private final EventVisibility visibility;

    @Inject
    public EventStructureRoutes(
            EventCrudService crudService,
            EventCategoryService categoryService,
            EventBreakService breakService,
            EventFieldDefaultService fieldDefaultService,
            EventFieldService eventFieldService,
            OccurrenceCalendar occurrenceCalendar,
            EventVisibility visibility) {
        this.crudService = crudService;
        this.visibility = visibility;
        this.categoryService = categoryService;
        this.breakService = breakService;
        this.fieldDefaultService = fieldDefaultService;
        this.eventFieldService = eventFieldService;
        this.occurrenceCalendar = occurrenceCalendar;
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
        routes.put(
                prefix + "/events/{eventId}/fields/{fieldId}/value",
                this::setFieldValueOnDate,
                StationPermission.EVENT_EDIT);
    }

    @OpenApi(
            path = "/api/v1/events/categories",
            methods = HttpMethod.GET,
            summary = "List event categories",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventCategory[].class)))
    private void listCategories(Context ctx) {
        StationSession session = StationSession.from(ctx);
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
        StationSession session = StationSession.from(ctx);
        var req = ctx.bodyAsClass(CategoryRequest.class);
        if (req.name() == null || req.name().isBlank()) throw EventRefusal.EVENT_CATEGORY_NEEDS_A_NAME.raise();
        ctx.status(HttpStatus.CREATED)
                .json(categoryService.create(
                        session.stationId(),
                        req.name(),
                        req.position(),
                        req.maxShownEvents(),
                        Boolean.TRUE.equals(req.isPublic()),
                        req.color()));
    }

    @OpenApi(
            path = "/api/v1/events/categories/{id}",
            methods = HttpMethod.PUT,
            summary = "Update an event category",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CategoryRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
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
                Boolean.TRUE.equals(req.isPublic()),
                req.color())) {
            throw EventRefusal.EVENT_CATEGORY_NOT_CHANGED.raise();
        }
        ctx.status(HttpStatus.OK).json(new MessageResponse("Updated"));
    }

    @OpenApi(
            path = "/api/v1/events/categories/reorder",
            methods = HttpMethod.PUT,
            summary = "Reorder the event categories",
            tags = {"Events"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ReorderCategoriesRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventCategory[].class)))
    private void reorderCategories(Context ctx) {
        var session = StationSession.from(ctx);
        var req = ctx.bodyAsClass(ReorderCategoriesRequest.class);
        for (int id : req.orderedIds()) {
            requireOwnedOrNotFound(ctx, id, categoryService::findById, EventCategory::stationId);
        }
        categoryService.reorder(session.stationId(), req.orderedIds());
        ctx.json(categoryService.findByStation(session.stationId()));
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
    private void deleteCategory(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, categoryService::findById, EventCategory::stationId);
        if (categoryService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw EventRefusal.EVENT_CATEGORY_NOT_DELETED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/events/breaks",
            methods = HttpMethod.GET,
            summary = "List event breaks",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventBreak[].class)))
    private void listBreaks(Context ctx) {
        StationSession session = StationSession.from(ctx);
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
        StationSession session = StationSession.from(ctx);
        var req = ctx.bodyAsClass(BreakRequest.class);
        if (req.name() == null || req.name().isBlank()) throw EventRefusal.EVENT_BREAK_NEEDS_A_NAME.raise();
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
            throw EventRefusal.EVENT_BREAK_NOT_CHANGED.raise();
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
            throw EventRefusal.EVENT_BREAK_NOT_DELETED.raise();
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
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        visibility.requireVisibleEvent(session, id);
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
        StationSession session = StationSession.from(ctx);
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
            queryParams = @OpenApiParam(name = "date", type = String.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AppointmentField[].class)))
    private void getFields(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        visibility.requireVisibleEvent(session, id);
        ctx.json(eventFieldService.findByEvent(id, askedDate(ctx)));
    }

    /**
     * The date a reader is asking about, or nothing where they are asking about the appointment
     * itself.
     *
     * <p>The editor asks about the appointment, because that is what it edits. Every screen showing
     * one occurrence asks about that occurrence, because a question answered per date has no answer
     * anywhere else.
     */
    private static @Nullable LocalDate askedDate(Context ctx) {
        String date = ctx.queryParam("date");
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw EventRefusal.EVENT_DAY_NOT_A_DATE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/events/{id}/fields",
            methods = HttpMethod.PUT,
            summary = "Replace all fields for an event",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetEventFieldsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AppointmentField[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setFields(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedEvent(crudService, id, session);
        var req = ctx.bodyAsClass(SetEventFieldsRequest.class);
        eventFieldService.replaceFields(
                id, req.fields().stream().map(EventFieldEntry::toDraft).toList());
        ctx.json(eventFieldService.findByEvent(id));
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/fields/{fieldId}/value",
            methods = HttpMethod.PUT,
            summary = "Set the answer a field carries on one date of the event",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventId", type = Integer.class, required = true),
                @OpenApiParam(name = "fieldId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FieldDateValueRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AppointmentField.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setFieldValueOnDate(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int eventId = pathInt(ctx, "eventId");
        int fieldId = pathInt(ctx, "fieldId");
        requireOwnedEvent(crudService, eventId, session);
        var req = ctx.bodyAsClass(FieldDateValueRequest.class);
        if (req.date() == null) throw EventRefusal.EVENT_FIELD_VALUE_NEEDS_A_DAY.raise();
        ctx.json(eventFieldService.setValueOn(eventId, fieldId, req.date(), req.value()));
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
            queryParams = @OpenApiParam(name = "date", type = String.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AppointmentField.class)))
    private void selfRegisterField(Context ctx) {
        var session = StationSession.from(ctx);
        int eventId = pathInt(ctx, "eventId");
        int fieldId = pathInt(ctx, "fieldId");
        visibility.requireVisibleEvent(session, eventId);
        ctx.json(eventFieldService.toggleSelfRegistration(
                eventId,
                fieldId,
                session.member().id(),
                askedDate(ctx),
                session.hasPermission(StationPermission.EVENT_MANAGER)));
    }

    /**
     * The questions marked for the overview, each answered for the occurrence its appointment is
     * drawn on, which for every list of appointments is the next one.
     *
     * <p>The answer maps each appointment's id to its questions, which an annotation cannot name.
     */
    @OpenApi(
            path = "/api/v1/events/overview-fields",
            methods = HttpMethod.GET,
            summary = "The overview questions of every event, by event",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200"))
    private void getOverviewFields(Context ctx) {
        var session = StationSession.from(ctx);
        var events = crudService.findByStation(session.stationId());
        var nextDates = occurrenceCalendar.datesInView(events);
        ctx.json(eventFieldService.findOverviewFieldsByEvents(
                events.stream().map(StationEvent::id).toList(), nextDates));
    }

    @OpenApi(
            path = "/api/v1/events/field-names",
            methods = HttpMethod.GET,
            summary = "List distinct event field names used across all events",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = String[].class)))
    private void listFieldNames(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(eventFieldService.findDistinctFieldNames(session.stationId()));
    }

    public record CategoryRequest(
            String name,
            int position,
            @Nullable Integer maxShownEvents,
            @Nullable Boolean isPublic,
            @Nullable String color) {}

    public record ReorderCategoriesRequest(List<Integer> orderedIds) {}

    public record BreakRequest(String name, LocalDate startDate, LocalDate endDate) {}

    public record FieldDefaultEntry(
            int fieldId, String source, @Nullable String value) {}

    public record SetEventFieldsRequest(List<EventFieldEntry> fields) {}

    public record FieldDateValueRequest(LocalDate date, String value) {}

    public record EventFieldEntry(
            @Nullable Integer id,
            String name,
            @Nullable FieldType fieldType,
            @Nullable EventQuestionSettings config,
            @Nullable String value,
            @Nullable Boolean overview,
            @Nullable Integer attendanceFieldId,
            @Nullable Boolean isPublic) {

        /** The question as the server writes it; what is left out reads as a plain, empty line of text. */
        EventFieldDraft toDraft() {
            return new EventFieldDraft(
                    id,
                    name,
                    Objects.requireNonNullElse(fieldType, FieldType.TEXT),
                    Objects.requireNonNullElse(config, EventQuestionSettings.empty())
                            .organisers(),
                    Objects.requireNonNullElse(value, ""),
                    Boolean.TRUE.equals(overview),
                    attendanceFieldId,
                    Boolean.TRUE.equals(isPublic));
        }
    }
}
