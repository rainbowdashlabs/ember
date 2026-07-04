/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes;
import dev.chojo.ember.feature.events.entity.BatchFieldEntry;
import dev.chojo.ember.feature.events.entity.BatchRequest;
import dev.chojo.ember.feature.events.entity.BatchRow;
import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.EventSummary;
import dev.chojo.ember.feature.events.entity.IntervalConfig;
import dev.chojo.ember.feature.events.entity.IntervalType;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.entity.UpcomingEventOccurrence;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.events.service.BatchEventService;
import dev.chojo.ember.feature.events.service.EventExportService;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationDisplayNames;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Routes for event management including CRUD operations on events, categories, breaks,
 * registrations, event fields, restrictions, PDF export, and notification handling.
 */
@Singleton
public class EventRoutes implements Routes {
    private final EventService eventService;
    private final EventFieldService eventFieldService;
    private final BatchEventService batchEventService;
    private final StationMemberService stationMemberService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final AttendanceService attendanceService;
    private final EventExportService eventExportService;
    private final EventFederationService eventFederationService;
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public EventRoutes(
            EventService eventService,
            EventFieldService eventFieldService,
            BatchEventService batchEventService,
            StationMemberService stationMemberService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            AttendanceService attendanceService,
            EventExportService eventExportService,
            EventFederationService eventFederationService,
            FederationService federationService,
            FederationRepository federationRepository,
            StationRepository stationRepository,
            MemberIdentityFactory memberIdentityFactory) {
        this.eventService = eventService;
        this.eventFieldService = eventFieldService;
        this.batchEventService = batchEventService;
        this.stationMemberService = stationMemberService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.attendanceService = attendanceService;
        this.eventExportService = eventExportService;
        this.eventFederationService = eventFederationService;
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    /**
     * Loads an event and asserts it belongs to the caller's station. Answers 404 when the
     * event is absent and 403 when it is owned by another station, so an event id from one
     * station cannot be used to read or act on another station's event.
     */
    private StationEvent requireOwnedEvent(int eventId, UserSession session) {
        var event = eventService.findById(eventId).orElseThrow(NotFoundResponse::new);
        if (event.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        return event;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events", this::list, StationPermission.USER);
        routes.get(prefix + "/events/search", this::searchPicker, StationPermission.PAGE_EDIT);
        routes.get(prefix + "/events/upcoming", this::listUpcoming, StationPermission.USER);
        routes.get(prefix + "/events/today", this::listToday, StationPermission.USER);
        routes.post(prefix + "/events", this::create, StationPermission.EVENT_EDIT);

        routes.post(prefix + "/events/export", this::exportPdf, StationPermission.EVENT_EDIT);
        routes.get(prefix + "/events/field-names", this::listFieldNames, StationPermission.EVENT_EDIT);

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

        routes.get(prefix + "/events/registrations/mine", this::listMyRegistrations, StationPermission.USER);
        routes.get(
                prefix + "/events/registrations/pending",
                this::listPendingRegistrations,
                StationPermission.EVENT_REGISTRATION);
        routes.get(prefix + "/events/registrations/counts", this::listRegistrationCounts, StationPermission.USER);
        routes.put(
                prefix + "/events/registrations/{id}/status",
                this::updateRegistrationStatus,
                StationPermission.EVENT_REGISTRATION);
        routes.delete(prefix + "/events/registrations/{id}", this::withdrawRegistration, StationPermission.USER);

        routes.get(prefix + "/events/restrictions", this::listAllRestrictions, StationPermission.USER);
        routes.get(prefix + "/events/eligible-members", this::listEligibleMembers, StationPermission.USER);

        // Overview fields
        routes.get(prefix + "/events/overview-fields", this::getOverviewFields, StationPermission.USER);

        // Federation sharing
        routes.get(prefix + "/events/{id}/federation", this::getFederationShare, StationPermission.EVENTS_FEDERATE);
        routes.put(prefix + "/events/{id}/federation", this::setFederationShare, StationPermission.EVENTS_FEDERATE);
        routes.delete(
                prefix + "/events/{id}/federation", this::removeFederationShare, StationPermission.EVENTS_FEDERATE);
        routes.get(
                prefix + "/events/{id}/federation-registrations",
                this::listFederationRegistrations,
                StationPermission.EVENT_REGISTRATION);
        routes.put(
                prefix + "/events/federation-registrations/{id}/status",
                this::updateFederationRegistrationStatus,
                StationPermission.EVENT_REGISTRATION);

        // Batch creation
        routes.post(prefix + "/events/batch", this::batchCreate, StationPermission.EVENT_EDIT);
        routes.post(prefix + "/events/batch/generate-dates", this::generateDates, StationPermission.EVENT_EDIT);

        routes.get(
                prefix + "/events/{eventId}/registration-stats",
                this::getRegistrationStats,
                StationPermission.EVENT_REGISTRATION);
        routes.get(prefix + "/events/{eventId}/registrations", this::listRegistrations, StationPermission.USER);
        routes.post(prefix + "/events/{eventId}/register", this::register, StationPermission.USER);
        routes.post(prefix + "/events/{eventId}/decline", this::decline, StationPermission.USER);

        routes.post(prefix + "/events/{id}/cancel", this::cancelEvent, StationPermission.EVENT_EDIT);

        routes.get(prefix + "/events/{id}", this::get, StationPermission.USER);
        routes.put(prefix + "/events/{id}", this::update, StationPermission.EVENT_EDIT);
        routes.delete(prefix + "/events/{id}", this::delete, StationPermission.EVENT_EDIT);

        routes.get(prefix + "/events/{id}/restrictions", this::getRestrictions, StationPermission.USER);
        routes.put(prefix + "/events/{id}/restrictions", this::setRestrictions, StationPermission.EVENT_EDIT);

        routes.get(prefix + "/events/{id}/reminders", this::getReminders, StationPermission.USER);
        routes.put(prefix + "/events/{id}/reminders", this::setReminders, StationPermission.EVENT_EDIT);

        routes.get(prefix + "/events/{id}/field-defaults", this::getFieldDefaults, StationPermission.USER);
        routes.put(prefix + "/events/{id}/field-defaults", this::setFieldDefaults, StationPermission.EVENT_EDIT);

        routes.get(prefix + "/events/{id}/fields", this::getFields, StationPermission.USER);
        routes.put(prefix + "/events/{id}/fields", this::setFields, StationPermission.EVENT_EDIT);
        routes.post(
                prefix + "/events/{eventId}/fields/{fieldId}/self-register",
                this::selfRegisterField,
                StationPermission.USER);

        routes.get(
                prefix + "/events/{id}/absences",
                this::listAbsencesForDate,
                StationPermission.EVENT_EDIT,
                StationPermission.ATTENDANCE_EDIT);

        // Federated (user-facing, bearer token auth)
        routes.get(prefix + "/federated/events", this::federatedListEvents, StationPermission.USER);
        routes.get(prefix + "/federated/{stationuid}/events/{id}", this::federatedGetEvent, StationPermission.USER);
        routes.post(
                prefix + "/federated/{stationuid}/events/{id}/register",
                this::federatedRegister,
                StationPermission.USER);
        routes.delete(
                prefix + "/federated/{stationuid}/events/{id}/register",
                this::federatedWithdraw,
                StationPermission.USER);
        routes.get(prefix + "/federated/my-registrations", this::federatedMyRegistrations, StationPermission.USER);

        // Federated event comment proxy endpoints (bearer auth)
        routes.get(
                prefix + "/federated/{stationuid}/events/{eventId}/comments",
                this::federatedListComments,
                StationPermission.LOGIN);
        routes.post(
                prefix + "/federated/{stationuid}/events/{eventId}/comments",
                this::federatedCreateComment,
                StationPermission.LOGIN);
        routes.put(
                prefix + "/federated/{stationuid}/events/comments/{commentId}",
                this::federatedUpdateComment,
                StationPermission.LOGIN);
        routes.delete(
                prefix + "/federated/{stationuid}/events/comments/{commentId}",
                this::federatedDeleteComment,
                StationPermission.LOGIN);

        // Remote (server-to-server, RSA signature auth)
        routes.get(prefix + "/remote/events", this::remoteListEvents);
        routes.get(prefix + "/remote/events/{id}", this::remoteGetEvent);
        routes.post(prefix + "/remote/events/{id}/register", this::remoteRegister);
        routes.delete(prefix + "/remote/events/{id}/register", this::remoteWithdraw);
        routes.get(prefix + "/remote/events/{id}/registrations", this::remoteListRegistrations);
        routes.get(prefix + "/remote/registrations/{memberUid}", this::remoteListMemberRegistrations);
        routes.post(prefix + "/remote/webhook/event-registration-status", this::remoteOnRegistrationStatus);

        // Remote event comment endpoints (federation, RSA auth)
        routes.get(prefix + "/remote/events/{eventId}/comments", this::remoteListComments);
        routes.post(prefix + "/remote/events/{eventId}/comments", this::remoteCreateComment);
        routes.put(prefix + "/remote/events/comments/{commentId}", this::remoteUpdateComment);
        routes.delete(prefix + "/remote/events/comments/{commentId}", this::remoteDeleteComment);
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
        String catParam = ctx.queryParam("categoryId");
        Integer categoryId = catParam != null ? Integer.valueOf(catParam) : null;
        String regParam = ctx.queryParam("requiresRegistration");
        Boolean requiresRegistration = regParam != null ? Boolean.valueOf(regParam) : null;
        List<Integer> memberIds = resolveVisibleMemberIds(session);
        var events =
                eventService.findFilteredForMembers(session.stationId(), memberIds, categoryId, requiresRegistration);
        ctx.json(events.stream().map(EventSummary::of).toList());
    }

    private void searchPicker(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String q = ctx.queryParam("q");
        String modeParam = ctx.queryParam("mode");
        var mode = EventRepository.PickerMode.FUTURE;
        if (modeParam != null) {
            try {
                mode = EventRepository.PickerMode.valueOf(modeParam.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall back to default
            }
        }
        int requested = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10);
        int limit = Math.clamp(requested, 1, 20);
        ctx.json(eventService.searchEventPicker(session.stationId(), q, mode, limit));
    }

    @OpenApi(
            path = "/api/v1/events/today",
            methods = HttpMethod.GET,
            summary = "List today's events",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationEvent[].class)))
    private void listToday(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(eventService.findTodayEvents(session.stationId()).stream()
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
        String catParam = ctx.queryParam("categoryId");
        Integer categoryId = catParam != null ? Integer.valueOf(catParam) : null;
        String regParam = ctx.queryParam("requiresRegistration");
        Boolean requiresRegistration = regParam != null ? Boolean.valueOf(regParam) : null;
        String search = ctx.queryParam("search");
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10);
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        List<Integer> memberIds = resolveVisibleMemberIds(session);
        ctx.json(eventService.findUpcomingOccurrences(
                session.stationId(), memberIds, categoryId, requiresRegistration, search, limit, offset));
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
                req.categoryId(),
                req.registrationLimit(),
                req.minRegistrations(),
                req.thresholdDate(),
                req.registrationCloseDays());
        eventService.setRestrictions(
                event.id(), req.restrictedUserTypes(), req.restrictedGroupIds(), req.restrictedTagIds(), List.of());

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
        var eventType = req.eventType();
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
                        req.isPublic(),
                        req.registrationLimit(),
                        req.minRegistrations(),
                        req.thresholdDate(),
                        req.registrationCloseDays())
                .ifPresentOrElse(
                        event -> {
                            eventService.setRestrictions(
                                    id,
                                    req.restrictedUserTypes(),
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

    private void cancelEvent(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(CancelEventRequest.class);
        if (!eventService.cancelEvent(session.stationId(), id, req.reason())) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
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
                .json(eventService.createBreak(session.stationId(), req.name(), req.startDate(), req.endDate()));
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
        eventService.updateBreak(id, req.name(), req.startDate(), req.endDate()).ifPresentOrElse(ctx::json, () -> {
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
        if (session.hasPermission(StationPermission.MEMBER_GUARDIAN)) {
            for (var managed : stationMemberService.findManaged(session.member().id())) {
                registrations.addAll(eventService.findRegistrationsByMember(managed.id()));
            }
        }
        ctx.json(registrations.stream()
                .map(r -> {
                    var member = stationMemberRepository.findById(r.memberId());
                    String memberName = member.flatMap(m -> accountRepository.findById(m.accountId()))
                            .map(a -> (a.firstName() + " " + a.lastName()).trim())
                            .orElse("");
                    MemberIdentity memberIdentity = member.map(
                                    m -> memberIdentityFactory.local(m.stationId(), r.memberId()))
                            .orElse(null);
                    String createdByName = resolveCreatedByName(r.createdBy());
                    return new RegistrationResponse(
                            r.id(),
                            r.eventId(),
                            r.memberId(),
                            memberName,
                            memberIdentity,
                            r.eventDate(),
                            r.status(),
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
        var regs = eventService.findPendingRegistrationsByStation(session.stationId());
        ctx.json(regs.stream()
                .map(r -> {
                    var member = stationMemberRepository.findById(r.memberId());
                    String memberName = member.flatMap(m -> accountRepository.findById(m.accountId()))
                            .map(a -> (a.firstName() + " " + a.lastName()).trim())
                            .orElse("");
                    MemberIdentity identity = member.map(m -> memberIdentityFactory.local(m.stationId(), r.memberId()))
                            .orElse(null);
                    String createdByName = resolveCreatedByName(r.createdBy());
                    return new RegistrationResponse(
                            r.id(),
                            r.eventId(),
                            r.memberId(),
                            memberName,
                            identity,
                            r.eventDate(),
                            r.status(),
                            r.createdAt(),
                            createdByName);
                })
                .toList());
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/registrations",
            methods = HttpMethod.GET,
            summary = "List registrations for an event",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventRegistration[].class)))
    private void getRegistrationStats(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        var event = requireOwnedEvent(eventId, session);
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
        UserSession session = UserSession.from(ctx);
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        requireOwnedEvent(eventId, session);
        String dateStr = ctx.queryParam("date");
        var regs = dateStr != null
                ? eventService.findRegistrations(eventId, LocalDate.parse(dateStr))
                : eventService.findAllRegistrations(eventId);
        ctx.json(regs.stream()
                .map(r -> {
                    var member = stationMemberRepository.findById(r.memberId());
                    String memberName = member.flatMap(m -> accountRepository.findById(m.accountId()))
                            .map(a -> (a.firstName() + " " + a.lastName()).trim())
                            .orElse("");
                    MemberIdentity memberIdentity = member.map(
                                    m -> memberIdentityFactory.local(m.stationId(), r.memberId()))
                            .orElse(null);
                    String createdByName = resolveCreatedByName(r.createdBy());
                    return new RegistrationResponse(
                            r.id(),
                            r.eventId(),
                            r.memberId(),
                            memberName,
                            memberIdentity,
                            r.eventDate(),
                            r.status(),
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

        var event = requireOwnedEvent(eventId, session);
        LocalDate date = resolveEventDate(req, event);
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
                if (!manages && !session.hasPermission(StationPermission.EVENT_MANAGER)) {
                    throw new ForbiddenResponse("You do not manage this member");
                }
            }
        } else {
            memberId = session.member().id();
        }

        // Check eligibility — skip when an event manager registers on behalf of another member
        boolean isManagerRegistration = req.memberId() != null
                && req.memberId() != session.member().id()
                && session.hasPermission(StationPermission.EVENT_MANAGER);
        if (!isManagerRegistration && !eventService.isMemberEligible(eventId, memberId, session.permissions())) {
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

        var event = requireOwnedEvent(eventId, session);
        LocalDate date = resolveEventDate(req, event);

        if (session.member() == null) throw new BadRequestResponse("Not a station member");

        int memberId;
        if (req.memberId() != null) {
            memberId = req.memberId();
            if (memberId != session.member().id()) {
                boolean manages = stationMemberService
                        .findManaged(session.member().id())
                        .stream()
                        .anyMatch(m -> m.id() == memberId);
                if (!manages && !session.hasPermission(StationPermission.EVENT_MANAGER)) {
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
        if (req.status() != RegistrationStatus.ACCEPTED && req.status() != RegistrationStatus.DENIED) {
            throw new BadRequestResponse("status must be ACCEPTED or DENIED");
        }
        var registration = eventService.findRegistrationById(id).orElseThrow(NotFoundResponse::new);
        var regEvent = eventService.findById(registration.eventId()).orElseThrow(NotFoundResponse::new);
        if (regEvent.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        if (!eventService.updateRegistrationStatus(id, req.status())) {
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
                && session.hasPermission(StationPermission.MEMBER_GUARDIAN)
                && stationMemberService.findManaged(session.member().id()).stream()
                        .anyMatch(m -> m.id() == regMemberId);
        if (!isOwn
                && !manages
                && !session.hasPermission(StationPermission.EVENT_MANAGER)
                && !session.hasPermission(StationPermission.EVENT_REGISTRATION)) {
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
                .json(eventService.createCategory(session.stationId(), req.name(), req.position(), req.color()));
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
        if (session.hasPermission(StationPermission.MEMBER_GUARDIAN)) {
            stationMemberService.findManaged(session.member().id()).forEach(m -> memberIds.add(m.id()));
        }

        var allEvents = eventService.findByStation(session.stationId());
        var result = new HashMap<Integer, List<Integer>>();

        for (var event : allEvents) {
            var eligible = new ArrayList<Integer>();
            for (int mid : memberIds) {
                if (eventService.isMemberEligible(event.id(), mid, session.permissions())) {
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
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        requireOwnedEvent(id, session);
        var restrictions = eventService.findRestrictions(id);
        ctx.json(new EventRestrictions(
                restrictions.userTypes(),
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
        eventService.setRestrictions(id, req.userTypes(), req.groupIds(), req.tagIds(), req.memberIds());
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
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        requireOwnedEvent(id, session);
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
                                restrictions.userTypes(),
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
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        requireOwnedEvent(id, session);
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
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        var event = eventService.findById(eventId).orElseThrow(NotFoundResponse::new);
        if (event.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        ctx.json(eventFieldService.toggleSelfRegistration(
                eventId, fieldId, session.member().id()));
    }

    // -- Overview Fields --

    private void getOverviewFields(Context ctx) {
        var session = UserSession.from(ctx);
        var eventIds = eventService.findByStation(session.stationId()).stream()
                .map(StationEvent::id)
                .toList();
        ctx.json(eventFieldService.findOverviewFieldsByEvents(eventIds));
    }

    // -- Batch Creation --

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
                req.restrictedUserTypes(),
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
                    var member = stationMemberRepository.findById(a.memberId());
                    String memberName = member.flatMap(m -> accountRepository.findById(m.accountId()))
                            .map(acc -> (acc.firstName() + " " + acc.lastName()).trim())
                            .orElse("");
                    MemberIdentity memberIdentity = member.map(
                                    m -> memberIdentityFactory.local(m.stationId(), a.memberId()))
                            .orElse(null);
                    return new AbsentMemberResponse(
                            a.memberId(), memberName, memberIdentity, a.absentFrom(), a.absentUntil(), a.reason());
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

    private LocalDate resolveEventDate(RegisterRequest req, StationEvent event) {
        if (event.eventType() == StationEvent.EventType.ONE_TIME) {
            if (event.startTime() == null) throw new BadRequestResponse("Event has no start time");
            return event.startTime().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (req.eventDate() == null) {
            throw new BadRequestResponse("eventDate is required for recurring events");
        }
        LocalDate date = LocalDate.parse(req.eventDate());
        if (event.dayOfWeek() != null) {
            int isoDow = date.getDayOfWeek().getValue();
            if (isoDow != event.dayOfWeek()) {
                throw new BadRequestResponse("eventDate does not match the event's day of week");
            }
        }
        return date;
    }

    private void getFederationShare(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var event = eventService.findById(id).orElseThrow(NotFoundResponse::new);
        if (event.stationId() != session.stationId()) throw new ForbiddenResponse();
        var share = eventFederationService.findShareByEvent(id);
        if (share.isEmpty()) {
            ctx.json(new FederationShareResponse(false, null, null));
            return;
        }
        var targets = eventFederationService.findShareTargets(share.get().id());
        ctx.json(new FederationShareResponse(true, share.get().scope(), targets));
    }

    private void setFederationShare(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var event = eventService.findById(id).orElseThrow(NotFoundResponse::new);
        if (event.stationId() != session.stationId()) throw new ForbiddenResponse();
        var req = ctx.bodyAsClass(SetFederationShareRequest.class);
        eventFederationService.setShare(id, req.scope(), req.partnerIds() != null ? req.partnerIds() : List.of());
        ctx.json(new FederationShareResponse(true, req.scope(), null));
    }

    private void removeFederationShare(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var event = eventService.findById(id).orElseThrow(NotFoundResponse::new);
        if (event.stationId() != session.stationId()) throw new ForbiddenResponse();
        eventFederationService.removeShare(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listFederationRegistrations(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var event = eventService.findById(id).orElseThrow(NotFoundResponse::new);
        if (event.stationId() != session.stationId()) throw new ForbiddenResponse();
        String dateParam = ctx.queryParam("date");
        LocalDate date = dateParam != null ? LocalDate.parse(dateParam) : null;
        var registrations = date != null
                ? eventFederationService.findRegistrations(id, date)
                : eventFederationService.findRegistrations(id, null);
        var enriched = registrations.stream()
                .map(r -> {
                    var partner =
                            federationRepository.findPartnerById(r.partnerId()).orElse(null);
                    UUID partnerStationUid = partner != null ? partner.partnerStationId() : null;

                    // Try to resolve as a local member (same-instance federation)
                    MemberIdentity identity = null;
                    if (partnerStationUid != null) {
                        var partnerStation = stationRepository.findByUid(partnerStationUid);
                        if (partnerStation.isPresent()) {
                            var localMember = stationMemberRepository.findByUid(
                                    partnerStation.get().id(), r.remoteMemberId());
                            if (localMember.isPresent()) {
                                identity = memberIdentityFactory.local(
                                        localMember.get().stationId(),
                                        localMember.get().id());
                            }
                        }
                    }

                    // Fall back to federated identity with cached name
                    if (identity == null && partnerStationUid != null) {
                        String cachedName = eventFederationService
                                .getCachedName(r.partnerId(), r.remoteMemberId())
                                .orElse(null);
                        String stationName = stationRepository
                                .findByUid(partnerStationUid)
                                .map(Station::name)
                                .orElse(null);
                        identity = new MemberIdentity(partnerStationUid, r.remoteMemberId())
                                .withDisplay(cachedName, stationName, null, null);
                    }

                    return new EnrichedFederationRegistration(r, identity);
                })
                .toList();
        ctx.json(enriched);
    }

    private void federatedListEvents(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(eventFederationService.browseFederatedEvents(session.stationId()));
    }

    // -- Event Fields (per-event) --

    private void federatedGetEvent(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var stationUid = UUID.fromString(ctx.pathParam("stationuid"));
        int eventId = ctx.pathParamAsClass("id", Integer.class).get();
        var event = eventFederationService.getFederatedEvent(session.stationId(), stationUid, eventId);
        var fields = eventFieldService.findByEvent(eventId).stream()
                .filter(EventField::isPublic)
                .toList();
        ctx.json(new FederatedEventDetail(event, fields));
    }

    private void federatedRegister(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var partner = resolvePartner(ctx, session.stationId());
        int eventId = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(FederatedRegBody.class);
        UUID remoteMemberId =
                req.memberId() != null ? req.memberId() : session.member().uid();

        if (partner.isRemote()) {
            boolean success = eventFederationService.registerForFederatedEvent(
                    partner.remoteHost(),
                    partner.partnerStationId(),
                    eventId,
                    remoteMemberId,
                    req.eventDate(),
                    station.id(),
                    station.federationPrivateKey());
            if (!success) throw new BadRequestResponse("Registration failed");
        } else {
            eventFederationService.registerFederated(
                    eventId, partner.id(), remoteMemberId, LocalDate.parse(req.eventDate()));
        }
        ctx.status(HttpStatus.CREATED).json(new StatusResponse("PENDING"));
    }

    private void updateFederationRegistrationStatus(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(StatusUpdateRequest.class);
        var reg = eventFederationService.findRegistrationById(id).orElseThrow(NotFoundResponse::new);
        var event = eventService.findById(reg.eventId()).orElseThrow(NotFoundResponse::new);
        UserSession session = UserSession.from(ctx);
        if (event.stationId() != session.stationId()) throw new ForbiddenResponse();
        eventFederationService.updateRegistrationStatus(id, req.status());
        ctx.json(new MessageResponse("Status updated"));
    }

    private void federatedMyRegistrations(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.json(List.of());
            return;
        }
        var memberUids = new ArrayList<UUID>();
        memberUids.add(session.member().uid());
        var managed = stationMemberRepository.findManaged(session.member().id());
        for (var m : managed) {
            if (m.uid() != null) memberUids.add(m.uid());
        }
        ctx.json(eventFederationService.findMyRegistrations(session.stationId(), memberUids));
    }

    private void federatedWithdraw(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var partner = resolvePartner(ctx, session.stationId());
        int eventId = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(FederatedRegBody.class);
        UUID remoteMemberId =
                req.memberId() != null ? req.memberId() : session.member().uid();

        if (partner.isRemote()) {
            eventFederationService.withdrawFederatedRegistration(
                    partner.remoteHost(),
                    partner.partnerStationId(),
                    eventId,
                    remoteMemberId,
                    req.eventDate(),
                    station.id(),
                    station.federationPrivateKey());
        } else {
            eventFederationService.withdrawRegistration(
                    eventId, partner.id(), remoteMemberId, LocalDate.parse(req.eventDate()));
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void remoteListEvents(Context ctx) {
        var partner = requireFederationPartner(ctx);
        var eventIds = eventFederationService.findSharedEventIds(partner.id(), partner.stationId());
        var events = eventIds.stream()
                .map(id -> eventService.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .map(this::toRemoteEvent)
                .toList();
        ctx.json(events);
    }

    private void remoteGetEvent(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int eventId = ctx.pathParamAsClass("id", Integer.class).get();
        var eventIds = eventFederationService.findSharedEventIds(partner.id(), partner.stationId());
        if (!eventIds.contains(eventId)) {
            throw new NotFoundResponse();
        }
        var event = eventService.findById(eventId).orElseThrow(NotFoundResponse::new);
        var fields = eventFieldService.findByEvent(eventId).stream()
                .filter(EventField::isPublic)
                .toList();
        ctx.json(new RemoteEventDetail(toRemoteEvent(event), fields));
    }

    private void remoteRegister(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int eventId = ctx.pathParamAsClass("id", Integer.class).get();
        var eventIds = eventFederationService.findSharedEventIds(partner.id(), partner.stationId());
        if (!eventIds.contains(eventId)) {
            throw new NotFoundResponse();
        }
        var req = ctx.bodyAsClass(RemoteRegistrationRequest.class);
        var reg =
                eventFederationService.registerFederated(eventId, partner.id(), req.remoteMemberId(), req.eventDate());
        ctx.status(HttpStatus.CREATED).json(reg);
    }

    private void remoteWithdraw(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int eventId = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(RemoteRegistrationRequest.class);
        eventFederationService.withdrawRegistration(eventId, partner.id(), req.remoteMemberId(), req.eventDate());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void remoteListRegistrations(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int eventId = ctx.pathParamAsClass("id", Integer.class).get();
        var eventIds = eventFederationService.findSharedEventIds(partner.id(), partner.stationId());
        if (!eventIds.contains(eventId)) {
            throw new NotFoundResponse();
        }
        var registrations = eventFederationService.findRegistrationsByPartner(partner.id()).stream()
                .filter(r -> r.eventId() == eventId)
                .toList();
        ctx.json(registrations);
    }

    private void remoteListMemberRegistrations(Context ctx) {
        requireFederationPartner(ctx);
        var memberUid = UUID.fromString(ctx.pathParam("memberUid"));
        var registrations = eventFederationService.findRegistrationsByRemoteMember(memberUid);
        ctx.json(registrations.stream()
                .map(r -> new RemoteMemberRegistration(
                        r.eventId(),
                        r.remoteMemberId().toString(),
                        r.eventDate().toString(),
                        r.status(),
                        r.partnerId()))
                .toList());
    }

    private void remoteOnRegistrationStatus(Context ctx) {
        requireFederationPartner(ctx);
        ctx.json(new StatusResponse("ok"));
    }

    private FederationPartner resolvePartner(Context ctx, int stationId) {
        var partnerUid = UUID.fromString(ctx.pathParam("stationuid"));
        return federationRepository
                .findPartnerByStationAndRemoteUid(stationId, partnerUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown partner"));
    }

    private FederationPartner requireFederationPartner(Context ctx) {
        var session = FederationSession.from(ctx);
        if (session == null) {
            throw new ForbiddenResponse("Missing or invalid federation signature");
        }
        return session.partner();
    }

    private String partnerStationName(FederationPartner partner) {
        return FederationDisplayNames.partnerName(stationRepository, partner, "?");
    }

    private RemoteEvent toRemoteEvent(StationEvent e) {
        return new RemoteEvent(
                e.id(),
                e.name(),
                e.description() != null ? e.description() : "",
                e.eventType(),
                e.dayOfWeek() != null ? e.dayOfWeek() : 0,
                e.startTime() != null ? e.startTime().toString() : "",
                e.endTime() != null ? e.endTime().toString() : "",
                e.requiresRegistration(),
                true);
    }

    private void remoteListComments(Context ctx) {
        requireFederationPartner(ctx);
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        ctx.json(eventFederationService.listComments(eventId));
    }

    // -- Federation sharing --

    private void remoteCreateComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        var req = ctx.bodyAsClass(RemoteCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        // Older peers will omit eventDate entirely; Jackson maps that to null. New peers
        // include an ISO date for date-scoped comments on recurring events.
        LocalDate eventDate = null;
        if (req.eventDate() != null && !req.eventDate().isBlank()) {
            try {
                eventDate = LocalDate.parse(req.eventDate());
            } catch (Exception e) {
                throw new BadRequestResponse("eventDate must be ISO yyyy-MM-dd");
            }
        }
        ctx.status(HttpStatus.CREATED)
                .json(eventFederationService.createRemoteComment(
                        partner,
                        eventId,
                        req.remoteMemberUid(),
                        req.displayName(),
                        req.parentId(),
                        req.content(),
                        eventDate));
    }

    private void remoteUpdateComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        var req = ctx.bodyAsClass(RemoteCommentUpdateRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        ctx.json(eventFederationService.updateRemoteComment(partner, commentId, req.remoteMemberUid(), req.content()));
    }

    private void remoteDeleteComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        var req = ctx.bodyAsClass(RemoteCommentDeleteRequest.class);
        if (eventFederationService.deleteRemoteComment(partner, commentId, req.remoteMemberUid())) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    private void getReminders(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        requireOwnedEvent(id, session);
        ctx.json(eventService.findReminderDays(id));
    }

    private void setReminders(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        requireOwnedEvent(id, session);
        var req = ctx.bodyAsClass(SetRemindersRequest.class);
        eventService.setReminders(id, req.daysBefore() != null ? req.daysBefore() : List.of());
        ctx.json(eventService.findReminderDays(id));
    }

    private void federatedListComments(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var partnerUid = UUID.fromString(ctx.pathParam("stationuid"));
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        var result = eventFederationService.listFederatedComments(session.stationId(), partnerUid, eventId);
        switch (result) {
            case EventFederationService.FederatedCommentResult.ListResult r -> ctx.json(r.comments());
            case EventFederationService.FederatedCommentResult.SingleResult r -> ctx.json(r.comment());
        }
    }

    // -- Federated endpoints (user-facing, aggregates from partners with parallel fetch) --

    private void federatedCreateComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var partnerUid = UUID.fromString(ctx.pathParam("stationuid"));
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        var req = ctx.bodyAsClass(EventCommentRoutes.CreateCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        var result = eventFederationService.createFederatedComment(
                session.stationId(),
                partnerUid,
                eventId,
                session.member().uid(),
                session.account().fullName().trim(),
                req.parentId(),
                req.content(),
                req.eventDate());
        switch (result) {
            case EventFederationService.FederatedCommentResult.SingleResult r ->
                ctx.status(HttpStatus.CREATED).json(r.comment());
            case EventFederationService.FederatedCommentResult.ListResult r ->
                ctx.status(HttpStatus.CREATED).json(r.comments());
        }
    }

    private void federatedUpdateComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var partnerUid = UUID.fromString(ctx.pathParam("stationuid"));
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        var req = ctx.bodyAsClass(EventCommentRoutes.UpdateCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        var result = eventFederationService.updateFederatedComment(
                session.stationId(), partnerUid, commentId, session.member().uid(), req.content());
        switch (result) {
            case EventFederationService.FederatedCommentResult.SingleResult r -> ctx.json(r.comment());
            case EventFederationService.FederatedCommentResult.ListResult r -> ctx.json(r.comments());
        }
    }

    private void federatedDeleteComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var partnerUid = UUID.fromString(ctx.pathParam("stationuid"));
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        eventFederationService.deleteFederatedComment(
                session.stationId(), partnerUid, commentId, session.member().uid());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record RegistrationResponse(
            int id,
            int eventId,
            int memberId,
            String memberName,
            MemberIdentity memberIdentity,
            LocalDate eventDate,
            RegistrationStatus status,
            Instant createdAt,
            String createdByName) {}

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
            List<StationUserType> restrictedUserTypes,
            List<Integer> restrictedGroupIds,
            List<Integer> restrictedTagIds,
            Boolean isPublic,
            Integer registrationLimit,
            Integer minRegistrations,
            Instant thresholdDate,
            Integer registrationCloseDays) {}

    public record CancelEventRequest(String reason) {}

    // -- Remote endpoints (server-to-server, RSA signature auth) --

    public record BreakRequest(String name, LocalDate startDate, LocalDate endDate) {}

    public record CategoryRequest(String name, int position, Integer maxShownEvents, Boolean isPublic, String color) {}

    public record ReorderCategoriesRequest(List<Integer> orderedIds) {}

    @OpenApiName("EventRegisterRequest")
    public record RegisterRequest(String eventDate, Integer memberId) {}

    public record StatusUpdateRequest(RegistrationStatus status) {}

    public record EventRestrictions(
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds,
            RestrictionMode mode) {}

    public record FieldDefaultEntry(int fieldId, String source, String value) {}

    // -- Federation helpers --

    public record AbsentMemberResponse(
            int memberId,
            String memberName,
            MemberIdentity memberIdentity,
            LocalDate absentFrom,
            LocalDate absentUntil,
            String reason) {}

    public record EventExportRequest(
            List<Integer> categoryIds, List<ExportColumnRequest> columns, String from, String to) {}

    public record ExportColumnRequest(String type, String key, String fieldName, String label) {}

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
            List<StationUserType> restrictedUserTypes,
            List<Integer> restrictedGroupIds,
            List<Integer> restrictedTagIds) {}

    public record BatchFieldEntryDto(
            String name,
            EventFieldType fieldType,
            EventFieldConfig config,
            Boolean overview,
            Integer attendanceFieldId) {}

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

    // -- Remote event comment endpoints (server-to-server) --

    public record EnrichedFederationRegistration(
            EventFederationRegistration registration, MemberIdentity memberIdentity) {}

    public record SetFederationShareRequest(ShareScope scope, List<Integer> partnerIds) {}

    public record FederationShareResponse(boolean shared, ShareScope scope, List<Integer> partnerIds) {}

    public record FederatedEventDetail(Object event, List<EventField> publicFields) {}

    public record StatusResponse(String status) {}

    public record RemoteMemberRegistration(
            int eventId, String remoteMemberId, String eventDate, RegistrationStatus status, int partnerId) {}

    public record RemoteEvent(
            int id,
            String name,
            String description,
            StationEvent.EventType eventType,
            int dayOfWeek,
            String startTime,
            String endTime,
            boolean requiresRegistration,
            boolean requiresConfirmation) {}

    public record RemoteEventDetail(RemoteEvent event, List<EventField> publicFields) {}

    public record FederatedRegBody(String eventDate, UUID memberId) {}

    public record RemoteRegistrationRequest(UUID remoteMemberId, LocalDate eventDate) {}

    // -- Federated event comment proxy endpoints (user-facing) --

    public record RemoteCommentRequest(
            UUID remoteMemberUid, String displayName, Integer parentId, String content, String eventDate) {}

    public record RemoteCommentUpdateRequest(UUID remoteMemberUid, String content) {}

    public record RemoteCommentDeleteRequest(UUID remoteMemberUid) {}

    public record SetRemindersRequest(List<Integer> daysBefore) {}
}
