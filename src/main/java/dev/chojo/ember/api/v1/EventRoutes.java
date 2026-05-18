/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.v1;

import dev.chojo.ember.api.AccessManager;
import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.entity.EventBreak;
import dev.chojo.ember.entity.EventCategory;
import dev.chojo.ember.entity.EventField;
import dev.chojo.ember.entity.EventFieldDefault;
import dev.chojo.ember.entity.EventFieldValue;
import dev.chojo.ember.entity.EventRegistration;
import dev.chojo.ember.entity.MemberGroup;
import dev.chojo.ember.entity.NotificationData;
import dev.chojo.ember.entity.NotificationType;
import dev.chojo.ember.entity.StationEvent;
import dev.chojo.ember.entity.StationMember;
import dev.chojo.ember.repository.AccountRepository;
import dev.chojo.ember.repository.StationMemberRepository;
import dev.chojo.ember.service.EventFieldService;
import dev.chojo.ember.service.EventService;
import dev.chojo.ember.service.MemberGroupService;
import dev.chojo.ember.service.NotificationService;
import dev.chojo.ember.service.StationMemberService;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class EventRoutes implements Routes {
    private final EventService eventService;
    private final EventFieldService eventFieldService;
    private final StationMemberService stationMemberService;
    private final AccessManager accessManager;
    private final MemberGroupService memberGroupService;
    private final NotificationService notificationService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;

    @Inject
    public EventRoutes(
            EventService eventService,
            EventFieldService eventFieldService,
            StationMemberService stationMemberService,
            AccessManager accessManager,
            MemberGroupService memberGroupService,
            NotificationService notificationService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository) {
        this.eventService = eventService;
        this.eventFieldService = eventFieldService;
        this.stationMemberService = stationMemberService;
        this.accessManager = accessManager;
        this.memberGroupService = memberGroupService;
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events", this::list, Roles.USER);
        routes.get(prefix + "/events/today", this::listToday, Roles.USER);
        routes.post(prefix + "/events", this::create, Roles.EVENT_MANAGEMENT);

        routes.get(prefix + "/events/categories", this::listCategories, Roles.USER);
        routes.post(prefix + "/events/categories", this::createCategory, Roles.EVENT_MANAGEMENT);
        routes.put(prefix + "/events/categories/{id}", this::updateCategory, Roles.EVENT_MANAGEMENT);
        routes.delete(prefix + "/events/categories/{id}", this::deleteCategory, Roles.EVENT_MANAGEMENT);

        routes.get(prefix + "/events/breaks", this::listBreaks, Roles.USER);
        routes.post(prefix + "/events/breaks", this::createBreak, Roles.EVENT_MANAGEMENT);
        routes.put(prefix + "/events/breaks/{id}", this::updateBreak, Roles.EVENT_MANAGEMENT);
        routes.delete(prefix + "/events/breaks/{id}", this::deleteBreak, Roles.EVENT_MANAGEMENT);

        routes.get(prefix + "/events/registrations/mine", this::listMyRegistrations, Roles.USER);
        routes.get(prefix + "/events/registrations/pending", this::listPendingRegistrations, Roles.EVENT_MANAGEMENT);
        routes.get(prefix + "/events/registrations/counts", this::listRegistrationCounts, Roles.USER);
        routes.put(
                prefix + "/events/registrations/{id}/status", this::updateRegistrationStatus, Roles.EVENT_MANAGEMENT);
        routes.delete(prefix + "/events/registrations/{id}", this::withdrawRegistration, Roles.USER);

        routes.get(prefix + "/events/restrictions", this::listAllRestrictions, Roles.USER);
        routes.get(prefix + "/events/eligible-members", this::listEligibleMembers, Roles.USER);

        routes.get(prefix + "/events/{eventId}/registrations", this::listRegistrations, Roles.USER);
        routes.post(prefix + "/events/{eventId}/register", this::register, Roles.USER);
        routes.post(prefix + "/events/{eventId}/decline", this::decline, Roles.USER);

        routes.get(prefix + "/events/{id}", this::get, Roles.USER);
        routes.put(prefix + "/events/{id}", this::update, Roles.EVENT_MANAGEMENT);
        routes.delete(prefix + "/events/{id}", this::delete, Roles.EVENT_MANAGEMENT);

        routes.get(prefix + "/events/{id}/restrictions", this::getRestrictions, Roles.USER);
        routes.put(prefix + "/events/{id}/restrictions", this::setRestrictions, Roles.EVENT_MANAGEMENT);

        routes.get(prefix + "/events/{id}/field-defaults", this::getFieldDefaults, Roles.USER);
        routes.put(prefix + "/events/{id}/field-defaults", this::setFieldDefaults, Roles.EVENT_MANAGEMENT);

        routes.get(prefix + "/events/fields", this::listFields, Roles.USER);
        routes.post(prefix + "/events/fields", this::createField, Roles.EVENT_MANAGEMENT);
        routes.get(prefix + "/events/fields/{fieldId}", this::getField, Roles.USER);
        routes.put(prefix + "/events/fields/{fieldId}", this::updateField, Roles.EVENT_MANAGEMENT);
        routes.delete(prefix + "/events/fields/{fieldId}", this::deleteField, Roles.EVENT_MANAGEMENT);

        routes.get(prefix + "/events/{id}/fields", this::getFieldValues, Roles.USER);
        routes.put(prefix + "/events/{id}/fields", this::setFieldValues, Roles.EVENT_MANAGEMENT);
    }

    private Set<Roles> resolveRolesForMember(UserSession session, int memberId) {
        if (session.member() != null && session.member().id() == memberId) {
            return session.roles();
        }
        return accessManager.resolveExpandedMemberRoles(memberId);
    }

    private List<Integer> resolveGroupIdsForMember(int memberId) {
        return memberGroupService.findGroupsForMember(memberId).stream()
                .map(MemberGroup::id)
                .toList();
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
        ctx.json(eventService.findByStation(session.stationId()));
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
        eventService.setRestrictions(event.id(), req.restrictedRoleIds(), req.restrictedGroupIds());

        // Notify station members about new event
        String eventDescription = "";
        if (req.description() != null && !req.description().isBlank()) {
            eventDescription =
                    req.description().length() > 80 ? req.description().substring(0, 80) + "..." : req.description();
        }
        notificationService.notifyStation(
                session.stationId(),
                NotificationType.NEW_EVENT,
                NotificationData.of(
                        "notification.newEvent",
                        Map.of("title", req.name(), "eventDescription", eventDescription),
                        new NotificationData.NotificationLink("events-upcoming")));

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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        eventService.findById(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
                        req.categoryId())
                .ifPresentOrElse(
                        event -> {
                            eventService.setRestrictions(id, req.restrictedRoleIds(), req.restrictedGroupIds());
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
        if (session.hasRole(Roles.MEMBER_MANAGER)) {
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

    public record RegistrationResponse(
            int id,
            int eventId,
            int memberId,
            String memberName,
            java.time.LocalDate eventDate,
            String status,
            java.time.Instant createdAt,
            String createdByName) {}

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
    private void listRegistrations(Context ctx) {
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        String dateStr = ctx.queryParam("date");
        LocalDate date = dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now();
        ctx.json(eventService.findRegistrations(eventId, date));
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
                if (!manages && !session.hasRole(Roles.EVENT_MANAGEMENT)) {
                    throw new ForbiddenResponse("You do not manage this member");
                }
            }
        } else {
            memberId = session.member().id();
        }

        // Check eligibility using expanded roles
        var memberRoles = resolveRolesForMember(session, memberId);
        var memberGroupIds = resolveGroupIdsForMember(memberId);
        if (!eventService.isMemberEligible(eventId, memberRoles, memberGroupIds)) {
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
                if (!manages && !session.hasRole(Roles.EVENT_MANAGEMENT)) {
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
        if (!eventService.updateRegistrationStatus(id, status)) {
            throw new NotFoundResponse();
        }
        var event = eventService.findById(registration.eventId()).orElse(null);
        String eventName = event != null ? event.name() : "?";
        String eventDescription = "";
        if (event != null && event.description() != null && !event.description().isBlank()) {
            eventDescription = event.description().length() > 80
                    ? event.description().substring(0, 80) + "..."
                    : event.description();
        }
        var data = NotificationData.of(
                "notification.eventRegistrationStatus",
                Map.of("eventName", eventName, "status", req.status(), "eventDescription", eventDescription),
                new NotificationData.NotificationLink("events-registrations"));
        notificationService.notify(registration.memberId(), NotificationType.EVENT_REGISTRATION_STATUS, data);
        var eventMgmtIds =
                stationMemberRepository.findMembersWithRole(session.stationId(), Roles.EVENT_MANAGEMENT).stream()
                        .map(StationMember::id)
                        .toList();
        notificationService.notifyMembersIfAbsent(
                eventMgmtIds,
                NotificationType.EVENT_REGISTRATION_STATUS,
                data,
                session.member().id());
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

        // Allow if it's the user's own registration, they manage the member, or they have EVENT_MANAGEMENT
        int regMemberId = reg.memberId();
        boolean isOwn = session.member() != null && session.member().id() == regMemberId;
        boolean manages = session.member() != null
                && session.hasRole(Roles.MEMBER_MANAGER)
                && stationMemberService.findManaged(session.member().id()).stream()
                        .anyMatch(m -> m.id() == regMemberId);
        if (!isOwn && !manages && !session.hasRole(Roles.EVENT_MANAGEMENT)) {
            throw new ForbiddenResponse("You cannot withdraw this registration");
        }

        if (!eventService.withdrawRegistration(id)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Categories --

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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(CategoryRequest.class);
        if (!eventService.updateCategory(id, req.name(), req.position())) {
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
    private void deleteCategory(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (eventService.deleteCategory(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Eligible Members --

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
        if (session.hasRole(Roles.MEMBER_MANAGER)) {
            stationMemberService.findManaged(session.member().id()).forEach(m -> memberIds.add(m.id()));
        }

        // Pre-resolve roles and groups for each member
        var memberRolesMap = new HashMap<Integer, Set<Roles>>();
        var memberGroupsMap = new HashMap<Integer, List<Integer>>();
        for (int mid : memberIds) {
            memberRolesMap.put(mid, resolveRolesForMember(session, mid));
            memberGroupsMap.put(mid, resolveGroupIdsForMember(mid));
        }

        var allEvents = eventService.findByStation(session.stationId());
        var result = new HashMap<Integer, List<Integer>>();

        for (var event : allEvents) {
            var roleRes = eventService.findRoleRestrictions(event.id());
            var groupRes = eventService.findGroupRestrictions(event.id());
            if (roleRes.isEmpty() && groupRes.isEmpty()) continue;

            var eligible = new ArrayList<Integer>();
            for (int mid : memberIds) {
                if (eventService.isMemberEligible(event.id(), memberRolesMap.get(mid), memberGroupsMap.get(mid))) {
                    eligible.add(mid);
                }
            }
            result.put(event.id(), eligible);
        }
        ctx.json(result);
    }

    // -- Restrictions --

    @OpenApi(
            path = "/api/v1/events/{id}/restrictions",
            methods = HttpMethod.GET,
            summary = "Get event restrictions",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventRestrictions.class)))
    private void getRestrictions(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(new EventRestrictions(eventService.findRoleRestrictions(id), eventService.findGroupRestrictions(id)));
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(EventRestrictions.class);
        eventService.setRestrictions(id, req.roleIds(), req.groupIds());
        ctx.json(req);
    }

    // -- Field Defaults --

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

    @OpenApi(
            path = "/api/v1/events/{id}/field-defaults",
            methods = HttpMethod.PUT,
            summary = "Set event field defaults",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FieldDefaultEntry[].class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventFieldDefault[].class)))
    private void setFieldDefaults(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AllEventRestrictions.class)))
    private void listAllRestrictions(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(new AllEventRestrictions(
                eventService.findAllRoleRestrictionsByStation(session.stationId()),
                eventService.findAllGroupRestrictionsByStation(session.stationId())));
    }

    // -- Records --

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
            List<Integer> restrictedGroupIds) {}

    public record BreakRequest(String name, String startDate, String endDate) {}

    public record CategoryRequest(String name, int position) {}

    @OpenApiName("EventRegisterRequest")
    public record RegisterRequest(String eventDate, Integer memberId) {}

    public record StatusUpdateRequest(String status) {}

    public record EventRestrictions(List<Integer> roleIds, List<Integer> groupIds) {}

    public record AllEventRestrictions(
            Map<Integer, List<Integer>> roleRestrictions, Map<Integer, List<Integer>> groupRestrictions) {}

    public record FieldDefaultEntry(int fieldId, String source, String value) {}

    // -- Event Fields --

    @OpenApi(
            path = "/api/v1/events/fields",
            methods = HttpMethod.GET,
            summary = "List event field definitions",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventField[].class)))
    private void listFields(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(eventFieldService.findByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/events/fields",
            methods = HttpMethod.POST,
            summary = "Create an event field",
            tags = {"Events"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventFieldRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = EventField.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createField(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(EventFieldRequest.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        if (req.fieldType() == null || req.fieldType().isBlank()) throw new BadRequestResponse("fieldType is required");
        ctx.status(HttpStatus.CREATED)
                .json(eventFieldService.create(
                        session.stationId(),
                        req.name(),
                        req.fieldType(),
                        req.config() != null ? req.config() : "{}",
                        req.position()));
    }

    @OpenApi(
            path = "/api/v1/events/fields/{fieldId}",
            methods = HttpMethod.GET,
            summary = "Get an event field",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "fieldId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventField.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getField(Context ctx) {
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        eventFieldService.findById(fieldId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/events/fields/{fieldId}",
            methods = HttpMethod.PUT,
            summary = "Update an event field",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "fieldId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventFieldRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventField.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateField(Context ctx) {
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        var req = ctx.bodyAsClass(EventFieldRequest.class);
        if (!eventFieldService.update(
                fieldId, req.name(), req.fieldType(), req.config() != null ? req.config() : "{}", req.position())) {
            throw new NotFoundResponse();
        }
        eventFieldService.findById(fieldId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/events/fields/{fieldId}",
            methods = HttpMethod.DELETE,
            summary = "Delete an event field",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "fieldId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteField(Context ctx) {
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        if (eventFieldService.delete(fieldId)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/events/{id}/fields",
            methods = HttpMethod.GET,
            summary = "Get event field values",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventFieldValue[].class)))
    private void getFieldValues(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(eventFieldService.findValues(id));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/fields",
            methods = HttpMethod.PUT,
            summary = "Set event field values",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetEventFieldValuesRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventFieldValue[].class)))
    private void setFieldValues(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(SetEventFieldValuesRequest.class);
        eventFieldService.setValues(
                id,
                req.values().stream()
                        .map(e -> new EventFieldService.FieldValueEntry(e.fieldId(), e.value()))
                        .toList());
        ctx.json(eventFieldService.findValues(id));
    }

    public record EventFieldRequest(String name, String fieldType, String config, int position) {}

    @OpenApiName("SetEventFieldValuesRequest")
    public record SetEventFieldValuesRequest(List<EventFieldValueEntry> values) {}

    @OpenApiName("EventFieldValueEntry")
    public record EventFieldValueEntry(int fieldId, String value) {}
}
