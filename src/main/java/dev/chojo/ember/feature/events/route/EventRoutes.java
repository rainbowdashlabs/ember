/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.BatchFieldEntry;
import dev.chojo.ember.feature.events.entity.BatchRequest;
import dev.chojo.ember.feature.events.entity.BatchRow;
import dev.chojo.ember.feature.events.entity.DatedEvent;
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
import dev.chojo.ember.feature.events.service.EventDateResolver;
import dev.chojo.ember.feature.events.service.EventExportService;
import dev.chojo.ember.feature.events.service.EventFieldRegistrationService;
import dev.chojo.ember.feature.events.service.EventOccurrenceService;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.events.service.EventReminderService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private final EventFieldRegistrationService fieldRegistrationService;
    private final EventDateResolver dateResolver;

    @Inject
    public EventRoutes(
            EventCrudService crudService,
            EventOccurrenceService occurrenceService,
            EventRestrictionService restrictionService,
            EventReminderService reminderService,
            BatchEventService batchEventService,
            StationMemberService stationMemberService,
            EventExportService eventExportService,
            EventRegistrationFieldService registrationFieldService,
            EventFieldRegistrationService fieldRegistrationService,
            EventDateResolver dateResolver) {
        this.dateResolver = dateResolver;
        this.crudService = crudService;
        this.fieldRegistrationService = fieldRegistrationService;
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
        routes.get(prefix + "/events/past", this::listPast, StationPermission.USER);
        routes.get(prefix + "/events/paged", this::listPaged, StationPermission.USER);
        routes.get(prefix + "/events/today", this::listToday, StationPermission.USER);
        routes.post(prefix + "/events", this::create, StationPermission.EVENT_EDIT);

        routes.post(prefix + "/events/export", this::exportPdf, StationPermission.EVENT_EDIT);

        routes.get(prefix + "/events/restrictions", this::listAllRestrictions, StationPermission.USER);
        routes.get(prefix + "/events/eligible-members", this::listEligibleMembers, StationPermission.USER);

        routes.post(prefix + "/events/batch", this::batchCreate, StationPermission.EVENT_EDIT);
        routes.post(prefix + "/events/batch/generate-dates", this::generateDates, StationPermission.EVENT_EDIT);

        routes.post(prefix + "/events/{id}/cancel", this::cancelEvent, StationPermission.EVENT_EDIT);

        routes.get(prefix + "/events/{id}", this::get, StationPermission.USER);
        routes.get(prefix + "/events/{id}/next-date", this::getNextDate, StationPermission.USER);
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
                @OpenApiParam(name = "offset", type = Integer.class, description = "Pagination offset (default 0)"),
                @OpenApiParam(name = "from", description = "Earliest date to list, ISO yyyy-MM-dd (default today)"),
                @OpenApiParam(name = "to", description = "Latest date to list, ISO yyyy-MM-dd")
            },
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = UpcomingEventOccurrence[].class)))
    private void listUpcoming(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(occurrenceService.findUpcomingOccurrences(
                session.stationId(), resolveVisibleMemberIds(session), parseOccurrenceQuery(ctx)));
    }

    @OpenApi(
            path = "/api/v1/events/past",
            methods = HttpMethod.GET,
            summary = "List event occurrences before today, newest first",
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
                @OpenApiParam(name = "offset", type = Integer.class, description = "Pagination offset (default 0)"),
                @OpenApiParam(name = "from", description = "Earliest date to list, ISO yyyy-MM-dd"),
                @OpenApiParam(name = "to", description = "Latest date to list, ISO yyyy-MM-dd (default yesterday)")
            },
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = UpcomingEventOccurrence[].class)))
    private void listPast(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(occurrenceService.findPastOccurrences(
                session.stationId(), resolveVisibleMemberIds(session), parseOccurrenceQuery(ctx)));
    }

    @OpenApi(
            path = "/api/v1/events/paged",
            methods = HttpMethod.GET,
            summary = "List events with the dates they next fall on and last fell on, split into current and past",
            tags = {"Events"},
            queryParams = {
                @OpenApiParam(
                        name = "state",
                        description = "current for events that still come round, past for the rest (default current)"),
                @OpenApiParam(name = "kind", description = "one_time or repeating, both kinds when absent"),
                @OpenApiParam(name = "categoryId", type = Integer.class, description = "Filter by category ID"),
                @OpenApiParam(
                        name = "requiresRegistration",
                        type = Boolean.class,
                        description = "Filter by registration requirement"),
                @OpenApiParam(
                        name = "search",
                        description = "Free-text search over event name and description (case-insensitive)"),
                @OpenApiParam(name = "limit", type = Integer.class, description = "Max number of events (default 10)"),
                @OpenApiParam(name = "offset", type = Integer.class, description = "Pagination offset (default 0)"),
                @OpenApiParam(name = "from", description = "Earliest ordering date to list, ISO yyyy-MM-dd"),
                @OpenApiParam(name = "to", description = "Latest ordering date to list, ISO yyyy-MM-dd")
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DatedEvent[].class)))
    private void listPaged(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var query = new EventOccurrenceService.EventPageQuery(
                parseState(ctx.queryParam("state")), parseKind(ctx.queryParam("kind")), parseOccurrenceQuery(ctx));
        ctx.json(occurrenceService.findEventsPage(session.stationId(), resolveVisibleMemberIds(session), query));
    }

    /** The filters, the window of days and the page that the three event listings all read alike. */
    private EventOccurrenceService.OccurrenceQuery parseOccurrenceQuery(Context ctx) {
        var filter = parseCategoryFilter(ctx);
        return new EventOccurrenceService.OccurrenceQuery(
                filter.categoryId(),
                filter.requiresRegistration(),
                ctx.queryParam("search"),
                parseDate(ctx.queryParam("from")),
                parseDate(ctx.queryParam("to")),
                ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10),
                ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0));
    }

    /** An optional date bound, absent where the parameter was not sent or was sent empty. */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new BadRequestResponse("from and to must be dates of the form yyyy-MM-dd");
        }
    }

    /**
     * Reads which half of the appointments a page asks for, falling back to the ones still to come
     * where the parameter is absent or names no known state.
     */
    private static EventOccurrenceService.EventState parseState(String value) {
        if (value == null) return EventOccurrenceService.EventState.CURRENT;
        try {
            return EventOccurrenceService.EventState.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return EventOccurrenceService.EventState.CURRENT;
        }
    }

    /**
     * Reads which kind of appointment a page asks for, answering null for both of them where the
     * parameter is absent or names no known kind.
     */
    private static EventOccurrenceService.EventKind parseKind(String value) {
        if (value == null) return null;
        try {
            return EventOccurrenceService.EventKind.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
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
        var event = crudService.createWithoutEvent(
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
        applyAudiences(event.id(), req);
        if (req.templateId() != null) {
            registrationFieldService.copyTemplateFields(req.templateId(), event.id());
        }
        var withEnd = crudService
                .setRepeatEnd(event.id(), req.repeatUntil(), req.repeatCount())
                .orElse(event);
        crudService.announceCreated(session.stationId(), withEnd);

        ctx.status(HttpStatus.CREATED).json(withEnd);
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

    /**
     * The next day this appointment falls on, today counting as next.
     *
     * <p>Asked of the server rather than worked out from the appointment's weekday, because only
     * the server knows the rule it repeats by, the weeks the station is off, and the date the series
     * runs to. A page that worked it out itself could only step a week at a time, which named the
     * wrong day for everything repeating less often than weekly and offered a sign-up for a day the
     * appointment does not happen.
     */
    @OpenApi(
            path = "/api/v1/events/{id}/next-date",
            methods = HttpMethod.GET,
            summary = "The next day an appointment falls on",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = NextDate.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getNextDate(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        var event = requireOwnedEvent(crudService, id, session);
        ctx.json(new NextDate(dateResolver.nextDate(event).orElse(null)));
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
                            applyAudiences(id, req);
                            var saved = crudService
                                    .setRepeatEnd(id, req.repeatUntil(), req.repeatCount())
                                    .orElse(event);
                            fieldRegistrationService.reconcile(id);
                            ctx.json(saved);
                        },
                        () -> {
                            throw Refusal.EVENT_NOT_HERE.raise();
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
            throw Refusal.EVENT_NOT_HERE.raise();
        }
    }

    private void cancelEvent(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        var req = ctx.bodyAsClass(CancelEventRequest.class);
        if (!crudService.cancelEvent(session.stationId(), id, req.reason())) {
            throw Refusal.EVENT_NOT_HERE.raise();
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
        return stationMemberService.findSpokenForIds(session);
    }

    /**
     * Writes both audiences of an event from a create or update request.
     *
     * <p>An absent selection clears the audience rather than leaving the old one standing: the
     * editor always sends what it holds, and a request that omits an audience is saying it is empty.
     * The combination mode is only touched where a selection came with one, because that is the only
     * case in which the caller has an opinion about it.
     */
    private void applyAudiences(int eventId, EventRequest req) {
        var register = req.restriction() != null ? req.restriction() : RestrictionSelection.empty();
        restrictionService.setRestrictions(eventId, register);
        if (req.restriction() != null) {
            restrictionService.updateRestrictionMode(eventId, register.mode());
        }

        var view = req.viewRestriction() != null ? req.viewRestriction() : RestrictionSelection.empty();
        restrictionService.setViewRestrictions(eventId, view);
        if (req.viewRestriction() != null) {
            restrictionService.updateViewRestrictionMode(eventId, view.mode());
        }
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

        var memberIds = stationMemberService.findSpokenForIds(session);
        var allEvents = crudService.findByStation(session.stationId());
        var result = new HashMap<Integer, List<Integer>>();

        for (var event : allEvents) {
            var eligible = new ArrayList<Integer>();
            for (int mid : memberIds) {
                if (restrictionService.canRegister(event.id(), mid, session.permissions())) {
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
        ctx.json(audiencesOf(id));
    }

    /** Both audiences of one event, as the editor loads them. */
    private EventRestrictions audiencesOf(int eventId) {
        return new EventRestrictions(
                RestrictionAudience.of(restrictionService.findRestrictions(eventId)),
                RestrictionAudience.of(restrictionService.findViewRestrictions(eventId)));
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

        var register = req.register() != null ? req.register().toSelection() : RestrictionSelection.empty();
        restrictionService.setRestrictions(id, register);
        if (register.mode() != null) {
            restrictionService.updateRestrictionMode(id, register.mode());
        }

        var view = req.view() != null ? req.view().toSelection() : RestrictionSelection.empty();
        restrictionService.setViewRestrictions(id, view);
        if (view.mode() != null) {
            restrictionService.updateViewRestrictionMode(id, view.mode());
        }

        ctx.json(audiencesOf(id));
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
            var register = restrictionService.findRestrictions(event.id());
            var view = restrictionService.findViewRestrictions(event.id());
            if (register.hasRestrictions() || view.hasRestrictions()) {
                restrictionsMap.put(
                        event.id(),
                        new EventRestrictions(RestrictionAudience.of(register), RestrictionAudience.of(view)));
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
                req.restriction() != null ? req.restriction() : RestrictionSelection.empty(),
                req.viewRestriction() != null ? req.viewRestriction() : RestrictionSelection.empty());
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
        String generatedBy = NameParts.of(session.account()).official();
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
        ctx.header("Content-Disposition", pdf.get().contentDisposition());
        ctx.result(pdf.get().bytes());
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
            RestrictionSelection viewRestriction,
            Boolean isPublic,
            Integer registrationLimit,
            Integer minRegistrations,
            Instant thresholdDate,
            Integer registrationCloseDays,
            LocalDate repeatUntil,
            Integer repeatCount) {}

    public record CancelEventRequest(String reason) {}

    /**
     * The next day an appointment falls on, or nothing for one that has no date at all.
     *
     * @param date the day, named the way the station's own clock names it
     */
    public record NextDate(LocalDate date) {}

    /**
     * Both audiences of an event, as the editor reads and writes them in one go.
     *
     * @param register who the appointment is for, everybody else sees it and cannot answer it
     * @param view     who may know it exists, everybody else never meets it anywhere
     */
    public record EventRestrictions(RestrictionAudience register, RestrictionAudience view) {}

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
            RestrictionSelection restriction,
            RestrictionSelection viewRestriction) {}

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
