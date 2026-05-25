/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventLayoutField;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventLayoutRepository;
import dev.chojo.ember.feature.events.service.BatchEventService;
import dev.chojo.ember.feature.events.service.EventExportService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventLayoutService;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes for event management including CRUD operations on events, categories, breaks,
 * registrations, event fields, restrictions, PDF export, and notification handling.
 */
@Singleton
public class EventRoutes implements Routes {
    private final EventService eventService;
    private final EventFieldService eventFieldService;
    private final EventLayoutService eventLayoutService;
    private final BatchEventService batchEventService;
    private final StationMemberService stationMemberService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final AttendanceService attendanceService;
    private final EventExportService eventExportService;

    @Inject
    public EventRoutes(
            EventService eventService,
            EventFieldService eventFieldService,
            EventLayoutService eventLayoutService,
            BatchEventService batchEventService,
            StationMemberService stationMemberService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            AttendanceService attendanceService,
            EventExportService eventExportService) {
        this.eventService = eventService;
        this.eventFieldService = eventFieldService;
        this.eventLayoutService = eventLayoutService;
        this.batchEventService = batchEventService;
        this.stationMemberService = stationMemberService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.attendanceService = attendanceService;
        this.eventExportService = eventExportService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events", this::list, Roles.USER);
        routes.get(prefix + "/events/today", this::listToday, Roles.USER);
        routes.post(prefix + "/events", this::create, Roles.EVENT_MANAGER);

        routes.post(prefix + "/events/export", this::exportPdf, Roles.EVENT_MANAGER);
        routes.get(prefix + "/events/field-names", this::listFieldNames, Roles.EVENT_MANAGER);

        routes.get(prefix + "/events/categories", this::listCategories, Roles.USER);
        routes.post(prefix + "/events/categories", this::createCategory, Roles.EVENT_MANAGER);
        routes.put(prefix + "/events/categories/reorder", this::reorderCategories, Roles.EVENT_MANAGER);
        routes.put(prefix + "/events/categories/{id}", this::updateCategory, Roles.EVENT_MANAGER);
        routes.delete(prefix + "/events/categories/{id}", this::deleteCategory, Roles.EVENT_MANAGER);

        routes.get(prefix + "/events/breaks", this::listBreaks, Roles.USER);
        routes.post(prefix + "/events/breaks", this::createBreak, Roles.EVENT_MANAGER);
        routes.put(prefix + "/events/breaks/{id}", this::updateBreak, Roles.EVENT_MANAGER);
        routes.delete(prefix + "/events/breaks/{id}", this::deleteBreak, Roles.EVENT_MANAGER);

        routes.get(prefix + "/events/registrations/mine", this::listMyRegistrations, Roles.USER);
        routes.get(prefix + "/events/registrations/pending", this::listPendingRegistrations, Roles.EVENT_MANAGER);
        routes.get(prefix + "/events/registrations/counts", this::listRegistrationCounts, Roles.USER);
        routes.put(prefix + "/events/registrations/{id}/status", this::updateRegistrationStatus, Roles.EVENT_MANAGER);
        routes.delete(prefix + "/events/registrations/{id}", this::withdrawRegistration, Roles.USER);

        routes.get(prefix + "/events/restrictions", this::listAllRestrictions, Roles.USER);
        routes.get(prefix + "/events/eligible-members", this::listEligibleMembers, Roles.USER);

        // Overview fields
        routes.get(prefix + "/events/overview-fields", this::getOverviewFields, Roles.USER);

        // Layouts
        routes.get(prefix + "/events/layouts", this::listLayouts, Roles.EVENT_MANAGER);
        routes.post(prefix + "/events/layouts", this::createLayout, Roles.EVENT_MANAGER);
        routes.get(prefix + "/events/layouts/{id}", this::getLayout, Roles.EVENT_MANAGER);
        routes.put(prefix + "/events/layouts/{id}", this::updateLayout, Roles.EVENT_MANAGER);
        routes.delete(prefix + "/events/layouts/{id}", this::deleteLayout, Roles.EVENT_MANAGER);
        routes.get(prefix + "/events/layouts/{id}/fields", this::getLayoutFields, Roles.EVENT_MANAGER);
        routes.put(prefix + "/events/layouts/{id}/fields", this::setLayoutFields, Roles.EVENT_MANAGER);

        // Batch creation
        routes.post(prefix + "/events/batch", this::batchCreate, Roles.EVENT_MANAGER);
        routes.post(prefix + "/events/batch/generate-dates", this::generateDates, Roles.EVENT_MANAGER);

        routes.get(prefix + "/events/{eventId}/registration-stats", this::getRegistrationStats, Roles.EVENT_MANAGER);
        routes.get(prefix + "/events/{eventId}/registrations", this::listRegistrations, Roles.USER);
        routes.post(prefix + "/events/{eventId}/register", this::register, Roles.USER);
        routes.post(prefix + "/events/{eventId}/decline", this::decline, Roles.USER);

        routes.get(prefix + "/events/{id}", this::get, Roles.USER);
        routes.put(prefix + "/events/{id}", this::update, Roles.EVENT_MANAGER);
        routes.delete(prefix + "/events/{id}", this::delete, Roles.EVENT_MANAGER);

        routes.get(prefix + "/events/{id}/restrictions", this::getRestrictions, Roles.USER);
        routes.put(prefix + "/events/{id}/restrictions", this::setRestrictions, Roles.EVENT_MANAGER);

        routes.get(prefix + "/events/{id}/field-defaults", this::getFieldDefaults, Roles.USER);
        routes.put(prefix + "/events/{id}/field-defaults", this::setFieldDefaults, Roles.EVENT_MANAGER);

        routes.get(prefix + "/events/{id}/fields", this::getFields, Roles.USER);
        routes.put(prefix + "/events/{id}/fields", this::setFields, Roles.EVENT_MANAGER);

        routes.get(prefix + "/events/{id}/absences", this::listAbsencesForDate, Roles.EVENT_MANAGER);
    }

    private String resolveCreatedByName(Integer createdBy) {
        if (createdBy == null) return null;
        return stationMemberRepository
                .findById(createdBy)
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse(null);
    }

    // -- Events --

    @OpenApi(
            path = "/api/v1/events",
            methods = HttpMethod.GET,
            summary = "List all events",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationEvent[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.hasRole(Roles.EVENT_MANAGER)) {
            ctx.json(eventService.findByStation(session.stationId()));
        } else if (session.member() != null) {
            ctx.json(eventService.findByStationForMember(
                    session.stationId(), session.member().id()));
        } else {
            ctx.json(List.of());
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
        ctx.json(eventService.findTodayEvents(session.stationId()));
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
        var eventType = StationEvent.EventType.valueOf(req.eventType());
        var event = eventService.create(
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
                req.categoryId());
        eventService.setRestrictions(
                event.id(), req.restrictedRoleIds(), req.restrictedGroupIds(), req.restrictedTagIds(), List.of());

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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var event = eventService.findById(id).orElseThrow(NotFoundResponse::new);
        if (event.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        ctx.json(event);
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var existing = eventService.findById(id).orElseThrow(NotFoundResponse::new);
        if (existing.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(EventRequest.class);
        validate(req);
        var eventType = StationEvent.EventType.valueOf(req.eventType());
        eventService
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
                        req.isPublic())
                .ifPresentOrElse(
                        event -> {
                            eventService.setRestrictions(
                                    id,
                                    req.restrictedRoleIds(),
                                    req.restrictedGroupIds(),
                                    req.restrictedTagIds(),
                                    List.of());
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var event = eventService.findById(id).orElseThrow(NotFoundResponse::new);
        if (event.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        if (eventService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Breaks --

    @OpenApi(
            path = "/api/v1/events/breaks",
            methods = HttpMethod.GET,
            summary = "List event breaks",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventBreak[].class)))
    private void listBreaks(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(eventService.findBreaksByStation(session.stationId()));
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
                .json(eventService.createBreak(
                        session.stationId(),
                        req.name(),
                        LocalDate.parse(req.startDate()),
                        LocalDate.parse(req.endDate())));
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
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var existing = eventService.findBreakById(id).orElseThrow(NotFoundResponse::new);
        if (existing.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(BreakRequest.class);
        eventService
                .updateBreak(id, req.name(), LocalDate.parse(req.startDate()), LocalDate.parse(req.endDate()))
                .ifPresentOrElse(ctx::json, () -> {
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
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var existing = eventService.findBreakById(id).orElseThrow(NotFoundResponse::new);
        if (existing.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        if (eventService.deleteBreak(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Helpers --

    private void validate(EventRequest req) {
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        if (req.startTime() == null || req.endTime() == null)
            throw new BadRequestResponse("startTime and endTime are required");
        if (req.eventType() == null) throw new BadRequestResponse("eventType is required");
    }

    // -- Registrations --

    @OpenApi(
            path = "/api/v1/events/registrations/mine",
            methods = HttpMethod.GET,
            summary = "List my registrations",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventRegistration[].class)))
    private void listMyRegistrations(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.json(Collections.emptyList());
            return;
        }
        // Include own + managed members' registrations
        var registrations = new ArrayList<>(
                eventService.findRegistrationsByMember(session.member().id()));
        if (session.hasRole(Roles.GUARDIAN)) {
            for (var managed : stationMemberService.findManaged(session.member().id())) {
                registrations.addAll(eventService.findRegistrationsByMember(managed.id()));
            }
        }
        ctx.json(registrations.stream()
                .map(r -> {
                    String memberName = stationMemberRepository
                            .findById(r.memberId())
                            .flatMap(m -> accountRepository.findById(m.accountId()))
                            .map(a -> (a.firstName() + " " + a.lastName()).trim())
                            .orElse("");
                    String createdByName = resolveCreatedByName(r.createdBy());
                    return new RegistrationResponse(
                            r.id(),
                            r.eventId(),
                            r.memberId(),
                            memberName,
                            r.eventDate(),
                            r.status().name(),
                            r.createdAt(),
                            createdByName);
                })
                .toList());
    }

    @OpenApi(
            path = "/api/v1/events/registrations/pending",
            methods = HttpMethod.GET,
            summary = "List pending registrations",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventRegistration[].class)))
    private void listPendingRegistrations(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(eventService.findPendingRegistrationsByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/registrations",
            methods = HttpMethod.GET,
            summary = "List registrations for an event",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventRegistration[].class)))
    private void getRegistrationStats(Context ctx) {
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        var event = eventService.findById(eventId).orElseThrow(NotFoundResponse::new);
        String catParam = ctx.queryParam("categoryId");
        Integer categoryId = catParam != null ? Integer.parseInt(catParam) : event.categoryId();
        String monthsParam = ctx.queryParam("months");
        int months = monthsParam != null ? Integer.parseInt(monthsParam) : 12;

        var stats = eventService.findRegistrationStats(eventId, categoryId, months);
        var result = stats.stream()
                .map(s -> {
                    String name = resolveCreatedByName(s.memberId());
                    int decisions = s.accepted() + s.denied();
                    double acceptRate = decisions > 0 ? (double) s.accepted() / decisions : 1.0;
                    String priority;
                    if (decisions == 0) priority = "NONE";
                    else if (acceptRate < 0.5) priority = "HIGH";
                    else if (acceptRate < 0.75) priority = "MEDIUM";
                    else priority = "LOW";
                    // Fairness score: higher = should be prioritized
                    // Factors: denial ratio (0-1) + denied count bonus - accepted count penalty
                    double denialRatio = decisions > 0 ? (double) s.denied() / decisions : 0;
                    double fairnessScore =
                            Math.round((denialRatio * 50 + s.denied() * 5 - s.accepted() * 2 + 50) * 10) / 10.0;
                    return new RegistrationStatsResponse(
                            s.memberId(),
                            name != null ? name : "#" + s.memberId(),
                            s.registered(),
                            s.accepted(),
                            s.denied(),
                            s.declined(),
                            Math.round(acceptRate * 100) / 100.0,
                            s.lastDenied() != null ? s.lastDenied().toString() : null,
                            priority,
                            Math.max(0, fairnessScore));
                })
                .toList();
        ctx.json(result);
    }

    private void listRegistrations(Context ctx) {
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        String dateStr = ctx.queryParam("date");
        var regs = dateStr != null
                ? eventService.findRegistrations(eventId, LocalDate.parse(dateStr))
                : eventService.findAllRegistrations(eventId);
        ctx.json(regs.stream()
                .map(r -> {
                    String memberName = stationMemberRepository
                            .findById(r.memberId())
                            .flatMap(m -> accountRepository.findById(m.accountId()))
                            .map(a -> (a.firstName() + " " + a.lastName()).trim())
                            .orElse("");
                    String createdByName = resolveCreatedByName(r.createdBy());
                    return new RegistrationResponse(
                            r.id(),
                            r.eventId(),
                            r.memberId(),
                            memberName,
                            r.eventDate(),
                            r.status().name(),
                            r.createdAt(),
                            createdByName);
                })
                .toList());
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/register",
            methods = HttpMethod.POST,
            summary = "Register for an event",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RegisterRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = EventRegistration.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void register(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        var req = ctx.bodyAsClass(RegisterRequest.class);
        LocalDate date = req.eventDate() != null ? LocalDate.parse(req.eventDate()) : LocalDate.now();

        var event = eventService.findById(eventId).orElseThrow(NotFoundResponse::new);
        if (!event.requiresRegistration()) {
            throw new BadRequestResponse("Event does not require registration");
        }
        if (event.registrationDeadline() != null && Instant.now().isAfter(event.registrationDeadline())) {
            throw new BadRequestResponse("Registration deadline has passed");
        }

        if (session.member() == null) throw new BadRequestResponse("Not a station member");

        // Determine which member to register
        int memberId;
        if (req.memberId() != null) {
            // Member manager registering a managed member
            memberId = req.memberId();
            if (memberId != session.member().id()) {
                // Verify the caller manages this member
                boolean manages = stationMemberService
                        .findManaged(session.member().id())
                        .stream()
                        .anyMatch(m -> m.id() == memberId);
                if (!manages && !session.hasRole(Roles.EVENT_MANAGER)) {
                    throw new ForbiddenResponse("You do not manage this member");
                }
            }
        } else {
            memberId = session.member().id();
        }

        // Check eligibility using restrictions (DB resolves roles/groups/tags + manager bypass)
        if (!eventService.isMemberEligible(eventId, memberId)) {
            throw new BadRequestResponse("Member is not eligible for this event");
        }

        boolean autoAccept = !event.requiresConfirmation();
        Integer createdBy = memberId != session.member().id() ? session.member().id() : null;
        ctx.status(HttpStatus.CREATED).json(eventService.register(eventId, memberId, date, autoAccept, createdBy));
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/decline",
            methods = HttpMethod.POST,
            summary = "Decline an event",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RegisterRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = EventRegistration.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void decline(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        var req = ctx.bodyAsClass(RegisterRequest.class);
        LocalDate date = req.eventDate() != null ? LocalDate.parse(req.eventDate()) : LocalDate.now();

        if (session.member() == null) throw new BadRequestResponse("Not a station member");

        int memberId;
        if (req.memberId() != null) {
            memberId = req.memberId();
            if (memberId != session.member().id()) {
                boolean manages = stationMemberService
                        .findManaged(session.member().id())
                        .stream()
                        .anyMatch(m -> m.id() == memberId);
                if (!manages && !session.hasRole(Roles.EVENT_MANAGER)) {
                    throw new ForbiddenResponse("You do not manage this member");
                }
            }
        } else {
            memberId = session.member().id();
        }

        Integer createdBy = memberId != session.member().id() ? session.member().id() : null;
        ctx.status(HttpStatus.CREATED).json(eventService.decline(eventId, memberId, date, createdBy));
    }

    @OpenApi(
            path = "/api/v1/events/registrations/counts",
            methods = HttpMethod.GET,
            summary = "List registration counts per event",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200"))
    private void listRegistrationCounts(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(eventService.findRegistrationCounts(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/events/registrations/{id}/status",
            methods = HttpMethod.PUT,
            summary = "Accept or deny a registration",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StatusUpdateRequest.class)),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateRegistrationStatus(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(StatusUpdateRequest.class);
        var status = EventRegistration.RegistrationStatus.valueOf(req.status());
        if (status != EventRegistration.RegistrationStatus.ACCEPTED
                && status != EventRegistration.RegistrationStatus.DENIED) {
            throw new BadRequestResponse("status must be ACCEPTED or DENIED");
        }
        var registration = eventService.findRegistrationById(id).orElseThrow(NotFoundResponse::new);
        var regEvent = eventService.findById(registration.eventId()).orElseThrow(NotFoundResponse::new);
        if (regEvent.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        if (!eventService.updateRegistrationStatus(id, status)) {
            throw new NotFoundResponse();
        }
        ctx.json(new MessageResponse("Status updated"));
    }

    @OpenApi(
            path = "/api/v1/events/registrations/{id}",
            methods = HttpMethod.DELETE,
            summary = "Withdraw a registration",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void withdrawRegistration(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var reg = eventService.findRegistrationById(id).orElseThrow(NotFoundResponse::new);

        // Allow if it's the user's own registration, they manage the member, or they have EVENT_MANAGER
        int regMemberId = reg.memberId();
        boolean isOwn = session.member() != null && session.member().id() == regMemberId;
        boolean manages = session.member() != null
                && session.hasRole(Roles.GUARDIAN)
                && stationMemberService.findManaged(session.member().id()).stream()
                        .anyMatch(m -> m.id() == regMemberId);
        if (!isOwn && !manages && !session.hasRole(Roles.EVENT_MANAGER)) {
            throw new ForbiddenResponse("You cannot withdraw this registration");
        }

        if (!eventService.withdrawRegistration(id)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/categories",
            methods = HttpMethod.GET,
            summary = "List event categories",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventCategory[].class)))
    private void listCategories(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(eventService.findCategoriesByStation(session.stationId()));
    }

    // -- Categories --

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
                .json(eventService.createCategory(session.stationId(), req.name(), req.position()));
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
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var existing = eventService.findCategoryById(id).orElseThrow(NotFoundResponse::new);
        if (existing.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(CategoryRequest.class);
        if (!eventService.updateCategory(
                id, req.name(), req.position(), req.maxShownEvents(), req.isPublic() != null && req.isPublic())) {
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
        // Verify all categories belong to this station
        for (int id : req.orderedIds()) {
            var cat = eventService.findCategoryById(id).orElseThrow(NotFoundResponse::new);
            if (cat.stationId() != session.stationId()) {
                throw new ForbiddenResponse("Cannot reorder categories from another station");
            }
        }
        eventService.reorderCategories(req.orderedIds());
        ctx.json(eventService.findCategoriesByStation(session.stationId()));
    }

    private void deleteCategory(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var existing = eventService.findCategoryById(id).orElseThrow(NotFoundResponse::new);
        if (existing.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        if (eventService.deleteCategory(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
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

        // Collect self + managed member IDs
        var memberIds = new ArrayList<Integer>();
        memberIds.add(session.member().id());
        if (session.hasRole(Roles.GUARDIAN)) {
            stationMemberService.findManaged(session.member().id()).forEach(m -> memberIds.add(m.id()));
        }

        var allEvents = eventService.findByStation(session.stationId());
        var result = new HashMap<Integer, List<Integer>>();

        for (var event : allEvents) {
            var eligible = new ArrayList<Integer>();
            for (int mid : memberIds) {
                if (eventService.isMemberEligible(event.id(), mid)) {
                    eligible.add(mid);
                }
            }
            if (!eligible.isEmpty()) {
                result.put(event.id(), eligible);
            }
        }
        ctx.json(result);
    }

    // -- Eligible Members --

    @OpenApi(
            path = "/api/v1/events/{id}/restrictions",
            methods = HttpMethod.GET,
            summary = "Get event restrictions",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventRestrictions.class)))
    private void getRestrictions(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        eventService.findById(id).orElseThrow(NotFoundResponse::new);
        var restrictions = eventService.findRestrictions(id);
        ctx.json(new EventRestrictions(
                restrictions.roleIds(),
                restrictions.groupIds(),
                restrictions.tagIds(),
                restrictions.memberIds(),
                restrictions.mode()));
    }

    // -- Restrictions --

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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var event = eventService.findById(id).orElseThrow(NotFoundResponse::new);
        if (event.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(EventRestrictions.class);
        eventService.setRestrictions(id, req.roleIds(), req.groupIds(), req.tagIds(), req.memberIds());
        if (req.mode() != null) {
            eventService.updateRestrictionMode(id, req.mode());
        }
        ctx.json(req);
    }

    @OpenApi(
            path = "/api/v1/events/{id}/field-defaults",
            methods = HttpMethod.GET,
            summary = "Get event field defaults",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventFieldDefault[].class)))
    private void getFieldDefaults(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(eventService.findFieldDefaults(id));
    }

    // -- Field Defaults --

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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var event = eventService.findById(id).orElseThrow(NotFoundResponse::new);
        if (event.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(FieldDefaultEntry[].class);
        var defaults = Arrays.stream(req)
                .map(e -> new EventFieldDefault(id, e.fieldId(), e.source(), e.value()))
                .toList();
        eventService.setFieldDefaults(id, defaults);
        ctx.json(eventService.findFieldDefaults(id));
    }

    @OpenApi(
            path = "/api/v1/events/restrictions",
            methods = HttpMethod.GET,
            summary = "List all event restrictions",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200"))
    private void listAllRestrictions(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var events = eventService.findByStation(session.stationId());
        var restrictionsMap = new HashMap<Integer, EventRestrictions>();
        for (var event : events) {
            var restrictions = eventService.findRestrictions(event.id());
            if (restrictions.hasRestrictions()) {
                restrictionsMap.put(
                        event.id(),
                        new EventRestrictions(
                                restrictions.roleIds(),
                                restrictions.groupIds(),
                                restrictions.tagIds(),
                                restrictions.memberIds(),
                                restrictions.mode()));
            }
        }
        ctx.json(restrictionsMap);
    }

    @OpenApi(
            path = "/api/v1/events/{id}/fields",
            methods = HttpMethod.GET,
            summary = "Get fields for an event",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventField[].class)))
    private void getFields(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(eventFieldService.findByEvent(id));
    }

    // -- Records --

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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var event = eventService.findById(id).orElseThrow(NotFoundResponse::new);
        if (event.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
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

    // -- Overview Fields --

    private void getOverviewFields(Context ctx) {
        var session = UserSession.from(ctx);
        var eventIds = eventService.findByStation(session.stationId()).stream()
                .map(StationEvent::id)
                .toList();
        ctx.json(eventFieldService.findOverviewFieldsByEvents(eventIds));
    }

    // -- Layouts --

    private void listLayouts(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(eventLayoutService.findByStation(session.stationId()));
    }

    private void createLayout(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(LayoutRequest.class);
        if (req.name() == null || req.name().isBlank()) {
            throw new BadRequestResponse("name is required");
        }
        ctx.json(eventLayoutService.create(session.stationId(), req.name()));
    }

    private void getLayout(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var layout = eventLayoutService.findById(id).orElseThrow(NotFoundResponse::new);
        if (layout.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        ctx.json(layout);
    }

    private void updateLayout(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var layout = eventLayoutService.findById(id).orElseThrow(NotFoundResponse::new);
        if (layout.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(LayoutRequest.class);
        if (!eventLayoutService.update(id, req.name())) {
            throw new NotFoundResponse();
        }
        ctx.json(eventLayoutService.findById(id).orElseThrow());
    }

    private void deleteLayout(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var layout = eventLayoutService.findById(id).orElseThrow(NotFoundResponse::new);
        if (layout.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        if (!eventLayoutService.delete(id)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void getLayoutFields(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(eventLayoutService.findFieldsByLayout(id));
    }

    private void setLayoutFields(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(SetLayoutFieldsRequest.class);
        eventLayoutService.replaceLayoutFields(
                id,
                req.fields().stream()
                        .map(f -> new EventLayoutRepository.LayoutFieldEntry(
                                f.name(),
                                f.fieldType(),
                                f.config(),
                                f.overview() != null && f.overview(),
                                f.attendanceFieldId()))
                        .toList());
        ctx.json(eventLayoutService.findFieldsByLayout(id));
    }

    // -- Batch Creation --

    private void generateDates(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(GenerateDatesRequest.class);
        var interval = new BatchEventService.IntervalConfig(
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
        List<EventLayoutField> inlineFields = req.inlineFields() != null
                ? req.inlineFields().stream()
                        .map(f -> new EventLayoutField(
                                0,
                                0,
                                f.name(),
                                f.fieldType() != null ? f.fieldType() : "string",
                                f.config() != null ? f.config() : "{}",
                                0,
                                f.overview() != null && f.overview(),
                                f.attendanceFieldId()))
                        .toList()
                : null;
        var batchRows = req.rows().stream()
                .map(r -> new BatchEventService.BatchRow(
                        r.name(), r.startTime(), r.endTime(), r.fieldValues() != null ? r.fieldValues() : Map.of()))
                .toList();
        var batchReq = new BatchEventService.BatchRequest(
                req.name(),
                req.description(),
                req.templateId(),
                req.categoryId(),
                req.layoutId(),
                inlineFields,
                batchRows,
                req.requiresRegistration(),
                req.requiresConfirmation(),
                req.registrationDeadline(),
                req.restrictedRoleIds(),
                req.restrictedGroupIds(),
                req.restrictedTagIds());
        var created = batchEventService.createBatch(session.stationId(), batchReq);
        ctx.json(created);
    }

    @OpenApi(
            path = "/api/v1/events/{id}/absences",
            methods = HttpMethod.GET,
            summary = "List absent members for a given date",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "date"),
            responses = @OpenApiResponse(status = "200"))
    private void listAbsencesForDate(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String dateStr = ctx.queryParam("date");
        LocalDate date = dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now();
        var absences = attendanceService.findAbsencesByStationOnDate(session.stationId(), date);
        ctx.json(absences.stream()
                .map(a -> {
                    String memberName = stationMemberRepository
                            .findById(a.memberId())
                            .flatMap(m -> accountRepository.findById(m.accountId()))
                            .map(acc -> (acc.firstName() + " " + acc.lastName()).trim())
                            .orElse("");
                    return new AbsentMemberResponse(
                            a.memberId(), memberName, a.absentFrom(), a.absentUntil(), a.reason());
                })
                .toList());
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

    public record RegistrationResponse(
            int id,
            int eventId,
            int memberId,
            String memberName,
            LocalDate eventDate,
            String status,
            Instant createdAt,
            String createdByName) {}

    public record EventRequest(
            String name,
            String description,
            String eventType,
            Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            Integer templateId,
            Boolean requiresRegistration,
            Instant registrationDeadline,
            Boolean requiresConfirmation,
            Integer categoryId,
            List<Integer> restrictedRoleIds,
            List<Integer> restrictedGroupIds,
            List<Integer> restrictedTagIds,
            Boolean isPublic) {}

    public record BreakRequest(String name, String startDate, String endDate) {}

    public record CategoryRequest(String name, int position, Integer maxShownEvents, Boolean isPublic) {}

    public record ReorderCategoriesRequest(List<Integer> orderedIds) {}

    // -- Event Fields (per-event) --

    @OpenApiName("EventRegisterRequest")
    public record RegisterRequest(String eventDate, Integer memberId) {}

    public record StatusUpdateRequest(String status) {}

    public record EventRestrictions(
            List<Integer> roleIds,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds,
            RestrictionMode mode) {}

    public record FieldDefaultEntry(int fieldId, String source, String value) {}

    public record AbsentMemberResponse(
            int memberId, String memberName, LocalDate absentFrom, LocalDate absentUntil, String reason) {}

    public record EventExportRequest(
            List<Integer> categoryIds, List<ExportColumnRequest> columns, String from, String to) {}

    public record ExportColumnRequest(String type, String key, String fieldName, String label) {}

    @OpenApiName("SetEventFieldsRequest")
    public record SetEventFieldsRequest(List<EventFieldEntry> fields) {}

    @OpenApiName("EventFieldEntry")
    public record EventFieldEntry(
            String name,
            String fieldType,
            String config,
            String value,
            Boolean overview,
            Integer attendanceFieldId,
            Boolean isPublic) {}

    public record LayoutRequest(String name) {}

    public record LayoutFieldEntry(
            String name, String fieldType, String config, Boolean overview, Integer attendanceFieldId) {}

    public record SetLayoutFieldsRequest(List<LayoutFieldEntry> fields) {}

    public record GenerateDatesRequest(
            String intervalType,
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
            Integer layoutId,
            List<LayoutFieldEntry> inlineFields,
            List<BatchRowEntry> rows,
            Boolean requiresRegistration,
            Boolean requiresConfirmation,
            Instant registrationDeadline,
            List<Integer> restrictedRoleIds,
            List<Integer> restrictedGroupIds,
            List<Integer> restrictedTagIds) {}

    public record BatchRowEntry(String name, Instant startTime, Instant endTime, Map<String, String> fieldValues) {}

    public record RegistrationStatsResponse(
            int memberId,
            String memberName,
            int registered,
            int accepted,
            int denied,
            int declined,
            double acceptRate,
            String lastDenied,
            String priority,
            double fairnessScore) {}
}
