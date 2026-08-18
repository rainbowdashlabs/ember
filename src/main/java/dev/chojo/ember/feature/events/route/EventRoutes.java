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
import dev.chojo.ember.feature.events.entity.BatchFieldEntry;
import dev.chojo.ember.feature.events.entity.BatchRequest;
import dev.chojo.ember.feature.events.entity.BatchRow;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventSummary;
import dev.chojo.ember.feature.events.entity.IntervalConfig;
import dev.chojo.ember.feature.events.entity.IntervalType;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.entity.UpcomingEventOccurrence;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.service.BatchEventService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventExportService;
import dev.chojo.ember.feature.events.service.EventOccurrenceService;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.events.service.EventReminderService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.feature.events.route.EventOwnership.requireOwnedEvent;

/**
 * Local routes for the event entity itself: the station-wide listings, create/read/update/delete,
 * cancellation, restrictions, reminders, batch creation and the PDF export. Participation lives in
 * {@link EventRegistrationRoutes}, the categories, breaks and fields an event is described by in
 * {@link EventStructureRoutes}.
 *
 * <p>This class owns {@code GET /events/{id}}, which matches any single-segment value after
 * {@code /events}. Javalin answers a request with the first registered handler that matches, so
 * {@link EventStructureRoutes} - which registers the literal {@code /events/categories},
 * {@code /events/breaks}, {@code /events/field-names} and {@code /events/overview-fields} reads -
 * must be bound before this class.
 */
@Singleton
public class EventRoutes implements Routes {
    private final EventCrudService crudService;
    private final EventOccurrenceService occurrenceService;
    private final EventRestrictionService restrictionService;
    private final EventReminderService reminderService;
    private final BatchEventService batchEventService;
    private final StationMemberService stationMemberService;
    private final EventExportService eventExportService;
    private final EventRegistrationFieldService registrationFieldService;

    @Inject
    public EventRoutes(
            EventCrudService crudService,
            EventOccurrenceService occurrenceService,
            EventRestrictionService restrictionService,
            EventReminderService reminderService,
            BatchEventService batchEventService,
            StationMemberService stationMemberService,
            EventExportService eventExportService,
            EventRegistrationFieldService registrationFieldService) {
        this.crudService = crudService;
        this.occurrenceService = occurrenceService;
        this.restrictionService = restrictionService;
        this.reminderService = reminderService;
        this.batchEventService = batchEventService;
        this.stationMemberService = stationMemberService;
        this.eventExportService = eventExportService;
        this.registrationFieldService = registrationFieldService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events", this::list, StationPermission.USER);
        routes.get(prefix + "/events/search", this::searchPicker, StationPermission.PAGE_EDIT);
        routes.get(prefix + "/events/upcoming", this::listUpcoming, StationPermission.USER);
        routes.get(prefix + "/events/today", this::listToday, StationPermission.USER);
        routes.post(prefix + "/events", this::create, StationPermission.EVENT_EDIT);

        routes.post(prefix + "/events/export", this::exportPdf, StationPermission.EVENT_EDIT);

        routes.get(prefix + "/events/restrictions", this::listAllRestrictions, StationPermission.USER);
        routes.get(prefix + "/events/eligible-members", this::listEligibleMembers, StationPermission.USER);

        routes.post(prefix + "/events/batch", this::batchCreate, StationPermission.EVENT_EDIT);
        routes.post(prefix + "/events/batch/generate-dates", this::generateDates, StationPermission.EVENT_EDIT);

        routes.post(prefix + "/events/{id}/cancel", this::cancelEvent, StationPermission.EVENT_EDIT);

        routes.get(prefix + "/events/{id}", this::get, StationPermission.USER);
        routes.put(prefix + "/events/{id}", this::update, StationPermission.EVENT_EDIT);
        routes.delete(prefix + "/events/{id}", this::delete, StationPermission.EVENT_EDIT);

        routes.get(prefix + "/events/{id}/restrictions", this::getRestrictions, StationPermission.USER);
        routes.put(prefix + "/events/{id}/restrictions", this::setRestrictions, StationPermission.EVENT_EDIT);

        routes.get(prefix + "/events/{id}/reminders", this::getReminders, StationPermission.USER);
        routes.put(prefix + "/events/{id}/reminders", this::setReminders, StationPermission.EVENT_EDIT);
    }

    @OpenApi(
            path = "/api/v1/events",
            methods = HttpMethod.GET,
            summary = "List events with optional server-side filters",
            tags = {"Events"},
            queryParams = {
                @OpenApiParam(name = "categoryId", type = Integer.class, description = "Filter by category ID"),
                @OpenApiParam(
                        name = "requiresRegistration",
                        type = Boolean.class,
                        description = "Filter by registration requirement")
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationEvent[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var filter = parseCategoryFilter(ctx);
        List<Integer> memberIds = resolveVisibleMemberIds(session);
        var events = crudService.findFilteredForMembers(
                session.stationId(), memberIds, filter.categoryId(), filter.requiresRegistration());
        ctx.json(events.stream().map(EventSummary::of).toList());
    }

    /**
     * Parses the optional category and registration-requirement filters shared by the event listings.
     */
    private CategoryFilter parseCategoryFilter(Context ctx) {
        String catParam = ctx.queryParam("categoryId");
        Integer categoryId = catParam != null ? Integer.valueOf(catParam) : null;
        String regParam = ctx.queryParam("requiresRegistration");
        Boolean requiresRegistration = regParam != null ? Boolean.valueOf(regParam) : null;
        return new CategoryFilter(categoryId, requiresRegistration);
    }

    private void searchPicker(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String q = ctx.queryParam("q");
        var mode = parsePickerMode(ctx.queryParam("mode"));
        int requested = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10);
        int limit = Math.clamp(requested, 1, 20);
        ctx.json(crudService.searchEventPicker(session.stationId(), q, mode, limit));
    }

    /**
     * Reads the picker's time-window filter, falling back to upcoming events when the parameter
     * is absent or names no known mode.
     */
    private EventRepository.PickerMode parsePickerMode(String modeParam) {
        if (modeParam == null) return EventRepository.PickerMode.FUTURE;
        try {
            return EventRepository.PickerMode.valueOf(modeParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EventRepository.PickerMode.FUTURE;
        }
    }

    @OpenApi(
            path = "/api/v1/events/today",
            methods = HttpMethod.GET,
            summary = "List today's events",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationEvent[].class)))
    private void listToday(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(occurrenceService.findTodayEvents(session.stationId()).stream()
                .map(EventSummary::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/events/upcoming",
            methods = HttpMethod.GET,
            summary = "List upcoming event occurrences with server-side filters and pagination",
            tags = {"Events"},
            queryParams = {
                @OpenApiParam(name = "categoryId", type = Integer.class, description = "Filter by category ID"),
                @OpenApiParam(
                        name = "requiresRegistration",
                        type = Boolean.class,
                        description = "Filter by registration requirement"),
                @OpenApiParam(
                        name = "search",
                        description = "Free-text search over event name and description (case-insensitive)"),
                @OpenApiParam(
                        name = "limit",
                        type = Integer.class,
                        description = "Max number of occurrences (default 10)"),
                @OpenApiParam(name = "offset", type = Integer.class, description = "Pagination offset (default 0)")
            },
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = UpcomingEventOccurrence[].class)))
    private void listUpcoming(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var filter = parseCategoryFilter(ctx);
        String search = ctx.queryParam("search");
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        List<Integer> memberIds = resolveVisibleMemberIds(session);
        ctx.json(occurrenceService.findUpcomingOccurrences(
                session.stationId(),
                memberIds,
                filter.categoryId(),
                filter.requiresRegistration(),
                search,
                limit,
                offset));
    }

    @OpenApi(
            path = "/api/v1/events",
            methods = HttpMethod.POST,
            summary = "Create an event",
            tags = {"Events"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = StationEvent.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(EventRequest.class);
        validate(req);
        var eventType = req.eventType();
        var event = crudService.create(
                session.stationId(),
                req.name(),
                req.description(),
                eventType,
                req.dayOfWeek(),
                req.startTime(),
                req.endTime(),
                req.templateId(),
                req.requiresRegistration() != null && req.requiresRegistration(),
                req.registrationDeadline(),
                req.requiresConfirmation() != null && req.requiresConfirmation(),
                req.categoryId(),
                req.registrationLimit(),
                req.minRegistrations(),
                req.thresholdDate(),
                req.registrationCloseDays());
        var restriction = req.restriction() != null ? req.restriction() : RestrictionSelection.empty();
        restrictionService.setRestrictions(event.id(), restriction);
        if (req.restriction() != null) {
            restrictionService.updateRestrictionMode(event.id(), restriction.mode());
        }
        if (req.templateId() != null) {
            registrationFieldService.copyTemplateFields(req.templateId(), event.id());
        }

        ctx.status(HttpStatus.CREATED).json(event);
    }

    @OpenApi(
            path = "/api/v1/events/{id}",
            methods = HttpMethod.GET,
            summary = "Get an event",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationEvent.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        ctx.json(requireOwnedEvent(crudService, id, session));
    }

    @OpenApi(
            path = "/api/v1/events/{id}",
            methods = HttpMethod.PUT,
            summary = "Update an event",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationEvent.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedEvent(crudService, id, session);
        var req = ctx.bodyAsClass(EventRequest.class);
        validate(req);
        var eventType = req.eventType();
        crudService
                .update(
                        id,
                        req.name(),
                        req.description(),
                        eventType,
                        req.dayOfWeek(),
                        req.startTime(),
                        req.endTime(),
                        req.templateId(),
                        req.requiresRegistration() != null && req.requiresRegistration(),
                        req.registrationDeadline(),
                        req.requiresConfirmation() != null && req.requiresConfirmation(),
                        req.categoryId(),
                        req.isPublic(),
                        req.registrationLimit(),
                        req.minRegistrations(),
                        req.thresholdDate(),
                        req.registrationCloseDays())
                .ifPresentOrElse(
                        event -> {
                            var restriction =
                                    req.restriction() != null ? req.restriction() : RestrictionSelection.empty();
                            restrictionService.setRestrictions(id, restriction);
                            if (req.restriction() != null) {
                                restrictionService.updateRestrictionMode(id, restriction.mode());
                            }
                            ctx.json(event);
                        },
                        () -> {
                            throw new NotFoundResponse();
                        });
    }

    @OpenApi(
            path = "/api/v1/events/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an event",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedEvent(crudService, id, session);
        if (crudService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    private void cancelEvent(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        var req = ctx.bodyAsClass(CancelEventRequest.class);
        if (!crudService.cancelEvent(session.stationId(), id, req.reason())) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private List<Integer> resolveVisibleMemberIds(UserSession session) {
        if (session.hasPermission(StationPermission.EVENT_MANAGER)) {
            return null;
        }
        if (session.member() == null) {
            return List.of(-1);
        }
        var ids = new ArrayList<Integer>();
        ids.add(session.member().id());
        if (session.hasPermission(StationPermission.MEMBER_GUARDIAN)) {
            stationMemberService.findManaged(session.member().id()).forEach(m -> ids.add(m.id()));
        }
        return ids;
    }

    private void validate(EventRequest req) {
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        if (req.startTime() == null || req.endTime() == null)
            throw new BadRequestResponse("startTime and endTime are required");
        if (req.eventType() == null) throw new BadRequestResponse("eventType is required");
    }

    /**
     * For each event, returns which member IDs (from self + managed) are eligible.
     * If an event has no restrictions, all members are eligible and the event is omitted from the result
     * (the frontend treats missing = all eligible).
     */
    @OpenApi(
            path = "/api/v1/events/eligible-members",
            methods = HttpMethod.GET,
            summary = "List eligible members per event",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200"))
    private void listEligibleMembers(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.json(Collections.emptyMap());
            return;
        }

        var memberIds = new ArrayList<Integer>();
        memberIds.add(session.member().id());
        if (session.hasPermission(StationPermission.MEMBER_GUARDIAN)) {
            stationMemberService.findManaged(session.member().id()).forEach(m -> memberIds.add(m.id()));
        }

        var allEvents = crudService.findByStation(session.stationId());
        var result = new HashMap<Integer, List<Integer>>();

        for (var event : allEvents) {
            var eligible = new ArrayList<Integer>();
            for (int mid : memberIds) {
                if (restrictionService.isMemberEligible(event.id(), mid, session.permissions())) {
                    eligible.add(mid);
                }
            }
            if (!eligible.isEmpty()) {
                result.put(event.id(), eligible);
            }
        }
        ctx.json(result);
    }

    @OpenApi(
            path = "/api/v1/events/{id}/restrictions",
            methods = HttpMethod.GET,
            summary = "Get event restrictions",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventRestrictions.class)))
    private void getRestrictions(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedEvent(crudService, id, session);
        var restrictions = restrictionService.findRestrictions(id);
        ctx.json(new EventRestrictions(
                restrictions.userTypes(),
                restrictions.groupIds(),
                restrictions.tagIds(),
                restrictions.memberIds(),
                restrictions.mode()));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/restrictions",
            methods = HttpMethod.PUT,
            summary = "Set event restrictions",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventRestrictions.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventRestrictions.class)))
    private void setRestrictions(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedEvent(crudService, id, session);
        var req = ctx.bodyAsClass(EventRestrictions.class);
        restrictionService.setRestrictions(
                id,
                new RestrictionSelection(req.userTypes(), req.groupIds(), req.tagIds(), req.memberIds(), req.mode()));
        if (req.mode() != null) {
            restrictionService.updateRestrictionMode(id, req.mode());
        }
        ctx.json(req);
    }

    @OpenApi(
            path = "/api/v1/events/restrictions",
            methods = HttpMethod.GET,
            summary = "List all event restrictions",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200"))
    private void listAllRestrictions(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var events = crudService.findByStation(session.stationId());
        var restrictionsMap = new HashMap<Integer, EventRestrictions>();
        for (var event : events) {
            var restrictions = restrictionService.findRestrictions(event.id());
            if (restrictions.hasRestrictions()) {
                restrictionsMap.put(
                        event.id(),
                        new EventRestrictions(
                                restrictions.userTypes(),
                                restrictions.groupIds(),
                                restrictions.tagIds(),
                                restrictions.memberIds(),
                                restrictions.mode()));
            }
        }
        ctx.json(restrictionsMap);
    }

    private void getReminders(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedEvent(crudService, id, session);
        ctx.json(reminderService.findDays(id));
    }

    private void setReminders(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedEvent(crudService, id, session);
        var req = ctx.bodyAsClass(SetRemindersRequest.class);
        reminderService.setDays(id, req.daysBefore() != null ? req.daysBefore() : List.of());
        ctx.json(reminderService.findDays(id));
    }

    private void generateDates(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(GenerateDatesRequest.class);
        var interval = new IntervalConfig(
                req.intervalType(),
                req.dayOfWeek() != null ? req.dayOfWeek() : 1,
                LocalDate.parse(req.startDate()),
                LocalDate.parse(req.endDate()),
                req.startTime() != null ? LocalTime.parse(req.startTime()) : null,
                req.endTime() != null ? LocalTime.parse(req.endTime()) : null);
        var rows = batchEventService.generateDates(
                session.stationId(), interval, req.ignoreBreaks() != null && req.ignoreBreaks());
        ctx.json(rows);
    }

    private void batchCreate(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(BatchCreateRequest.class);
        if (req.rows() == null || req.rows().isEmpty()) {
            throw new BadRequestResponse("rows are required");
        }
        List<BatchFieldEntry> inlineFields = req.inlineFields() != null
                ? req.inlineFields().stream()
                        .map(f -> new BatchFieldEntry(
                                f.name(),
                                f.fieldType() != null ? f.fieldType() : EventFieldType.STRING,
                                f.config() != null ? f.config() : EventFieldConfig.parse("{}"),
                                f.overview() != null && f.overview(),
                                f.attendanceFieldId()))
                        .toList()
                : null;
        var batchRows = req.rows().stream()
                .map(r -> new BatchRow(
                        r.name(), r.startTime(), r.endTime(), r.fieldValues() != null ? r.fieldValues() : Map.of()))
                .toList();
        var batchReq = new BatchRequest(
                req.name(),
                req.description(),
                req.templateId(),
                req.categoryId(),
                inlineFields,
                batchRows,
                req.requiresRegistration(),
                req.requiresConfirmation(),
                req.registrationDeadline(),
                req.restriction() != null ? req.restriction() : RestrictionSelection.empty());
        var created = batchEventService.createBatch(session.stationId(), batchReq);
        ctx.json(created);
    }

    @OpenApi(
            path = "/api/v1/events/export",
            methods = HttpMethod.POST,
            summary = "Export event list as PDF",
            tags = {"Events"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventExportRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void exportPdf(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(EventExportRequest.class);
        String generatedBy = session.account().fullName();
        var columns = req.columns() != null
                ? req.columns().stream()
                        .map(c -> new EventExportService.ExportColumn(
                                c.type() != null ? c.type() : "builtin", c.key(), c.fieldName(), c.label()))
                        .toList()
                : List.<EventExportService.ExportColumn>of();
        var pdf = eventExportService.exportPdf(
                session.stationId(),
                req.categoryIds() != null ? req.categoryIds() : List.of(),
                columns,
                LocalDate.parse(req.from()),
                LocalDate.parse(req.to()),
                generatedBy);
        if (pdf.isEmpty()) {
            throw new InternalServerErrorResponse("PDF generation failed");
        }
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", "attachment; filename=\"events.pdf\"");
        ctx.result(pdf.get());
    }

    public record EventRequest(
            String name,
            String description,
            StationEvent.EventType eventType,
            Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            Integer templateId,
            Boolean requiresRegistration,
            Instant registrationDeadline,
            Boolean requiresConfirmation,
            Integer categoryId,
            RestrictionSelection restriction,
            Boolean isPublic,
            Integer registrationLimit,
            Integer minRegistrations,
            Instant thresholdDate,
            Integer registrationCloseDays) {}

    public record CancelEventRequest(String reason) {}

    public record EventRestrictions(
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds,
            RestrictionMode mode) {}

    public record SetRemindersRequest(List<Integer> daysBefore) {}

    public record EventExportRequest(
            List<Integer> categoryIds, List<ExportColumnRequest> columns, String from, String to) {}

    public record ExportColumnRequest(String type, String key, String fieldName, String label) {}

    public record GenerateDatesRequest(
            IntervalType intervalType,
            Integer dayOfWeek,
            String startDate,
            String endDate,
            String startTime,
            String endTime,
            Boolean ignoreBreaks) {}

    public record BatchCreateRequest(
            String name,
            String description,
            Integer templateId,
            Integer categoryId,
            List<BatchFieldEntryDto> inlineFields,
            List<BatchRowEntry> rows,
            Boolean requiresRegistration,
            Boolean requiresConfirmation,
            Instant registrationDeadline,
            RestrictionSelection restriction) {}

    public record BatchFieldEntryDto(
            String name,
            EventFieldType fieldType,
            EventFieldConfig config,
            Boolean overview,
            Integer attendanceFieldId) {}

    public record BatchRowEntry(String name, Instant startTime, Instant endTime, Map<String, String> fieldValues) {}

    /**
     * The optional category and registration-requirement filters shared by the event listings.
     */
    private record CategoryFilter(Integer categoryId, Boolean requiresRegistration) {}
}
