/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldConfig;
import dev.chojo.ember.feature.events.entity.MemberRegistrationStats;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository.FieldEntry;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.StationMemberService;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;
import static dev.chojo.ember.feature.events.route.EventOwnership.requireOwnedEvent;

/**
 * Local routes for taking part in an event: signing up, declining, withdrawing, the manager-side
 * accept/deny decision with its fairness ranking, and the absence list an event date is planned
 * against.
 */
@Singleton
public class EventRegistrationRoutes implements Routes {
    private final EventCrudService crudService;
    private final EventRegistrationService registrationService;
    private final EventRestrictionService restrictionService;
    private final StationMemberService stationMemberService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final AttendanceService attendanceService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final EventRegistrationFieldService registrationFieldService;

    @Inject
    public EventRegistrationRoutes(
            EventCrudService crudService,
            EventRegistrationService registrationService,
            EventRestrictionService restrictionService,
            StationMemberService stationMemberService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            AttendanceService attendanceService,
            MemberIdentityFactory memberIdentityFactory,
            EventRegistrationFieldService registrationFieldService) {
        this.crudService = crudService;
        this.registrationService = registrationService;
        this.restrictionService = restrictionService;
        this.stationMemberService = stationMemberService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.attendanceService = attendanceService;
        this.memberIdentityFactory = memberIdentityFactory;
        this.registrationFieldService = registrationFieldService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
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

        routes.get(
                prefix + "/events/{eventId}/registration-stats",
                this::getRegistrationStats,
                StationPermission.EVENT_REGISTRATION);
        routes.get(
                prefix + "/events/{eventId}/registration-fields", this::listRegistrationFields, StationPermission.USER);
        routes.put(
                prefix + "/events/{eventId}/registration-fields",
                this::setRegistrationFields,
                StationPermission.EVENT_EDIT);
        routes.put(
                prefix + "/events/registrations/{id}/fields", this::updateRegistrationFields, StationPermission.USER);

        routes.get(prefix + "/events/{eventId}/registrations", this::listRegistrations, StationPermission.USER);
        routes.post(prefix + "/events/{eventId}/register", this::register, StationPermission.USER);
        routes.post(prefix + "/events/{eventId}/decline", this::decline, StationPermission.USER);

        routes.get(
                prefix + "/events/{id}/absences",
                this::listAbsencesForDate,
                StationPermission.EVENT_EDIT,
                StationPermission.ATTENDANCE_EDIT);
    }

    private String resolveCreatedByName(Integer createdBy) {
        if (createdBy == null) return null;
        return stationMemberRepository
                .findById(createdBy)
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse(null);
    }

    /**
     * Resolves a member's display name and identity, empty and {@code null} respectively when unknown.
     */
    private MemberDisplay resolveMemberDisplay(int memberId) {
        var member = stationMemberRepository.findById(memberId);
        String name = member.flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse("");
        MemberIdentity identity = member.map(m -> memberIdentityFactory.local(m.stationId(), memberId))
                .orElse(null);
        return new MemberDisplay(name, identity);
    }

    /**
     * Maps a registration to its response, resolving the member's display name, identity, and creator.
     */
    private RegistrationResponse toRegistrationResponse(Context ctx, EventRegistration r) {
        var hidden = registrationFieldService.hiddenFieldIds(r.eventId(), manages(ctx));
        return toRegistrationResponse(
                r,
                registrationFieldService.findValues(r.id()).stream()
                        .filter(v -> !hidden.contains(v.fieldId()))
                        .map(v -> new FieldValueEntry(v.fieldId(), v.value()))
                        .toList());
    }

    /**
     * Whether the caller runs the event. Manager-only answers are filtered here rather than hidden
     * in the interface, so a direct call cannot read them either.
     */
    private static boolean manages(Context ctx) {
        return UserSession.from(ctx).hasPermission(StationPermission.EVENT_EDIT);
    }

    private RegistrationResponse toRegistrationResponse(EventRegistration r, List<FieldValueEntry> fields) {
        var display = resolveMemberDisplay(r.memberId());
        String createdByName = resolveCreatedByName(r.createdBy());
        return new RegistrationResponse(
                r.id(),
                r.eventId(),
                r.memberId(),
                display.name(),
                display.identity(),
                r.eventDate(),
                r.status(),
                r.createdAt(),
                createdByName,
                fields,
                crudService.findById(r.eventId()).map(StationEvent::name).orElse(null));
    }

    /**
     * Maps a whole list of registrations, reading every answer in one query rather than one per
     * row.
     */
    private List<RegistrationResponse> toRegistrationResponses(Context ctx, List<EventRegistration> registrations) {
        var answers = registrationFieldService.findValuesByRegistration(
                registrations.stream().map(EventRegistration::id).toList());
        boolean manages = manages(ctx);
        var hiddenByEvent = new HashMap<Integer, Set<Integer>>();
        return registrations.stream()
                .map(r -> {
                    var hidden = hiddenByEvent.computeIfAbsent(
                            r.eventId(), eventId -> registrationFieldService.hiddenFieldIds(eventId, manages));
                    return toRegistrationResponse(
                            r,
                            answers.getOrDefault(r.id(), List.of()).stream()
                                    .filter(v -> !hidden.contains(v.fieldId()))
                                    .map(v -> new FieldValueEntry(v.fieldId(), v.value()))
                                    .toList());
                })
                .toList();
    }

    /**
     * Resolves and authorises the member id a register or decline call targets, defaulting to the caller.
     */
    private int resolveTargetMemberId(UserSession session, RegisterRequest req) {
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
        return memberId;
    }

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
        var registrations = new ArrayList<>(
                registrationService.findByMember(session.member().id()));
        if (session.hasPermission(StationPermission.MEMBER_GUARDIAN)) {
            for (var managed : stationMemberService.findManaged(session.member().id())) {
                registrations.addAll(registrationService.findByMember(managed.id()));
            }
        }
        ctx.json(toRegistrationResponses(ctx, registrations));
    }

    @OpenApi(
            path = "/api/v1/events/registrations/pending",
            methods = HttpMethod.GET,
            summary = "List pending registrations",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventRegistration[].class)))
    private void listPendingRegistrations(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var regs = registrationService.findPendingByStation(session.stationId());
        ctx.json(toRegistrationResponses(ctx, regs));
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/registration-stats",
            methods = HttpMethod.GET,
            summary = "Registration history and fairness ranking for an event's members",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            queryParams = {
                @OpenApiParam(name = "categoryId", type = Integer.class),
                @OpenApiParam(name = "months", type = Integer.class)
            },
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = RegistrationStatsResponse[].class)))
    private void getRegistrationStats(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int eventId = pathInt(ctx, "eventId");
        var event = requireOwnedEvent(crudService, eventId, session);
        String catParam = ctx.queryParam("categoryId");
        // Both sides of the choice stay boxed. An int on one of them promotes the other, which
        // unboxes the category of an event that has none and answers 500 for asking.
        Integer categoryId = catParam != null ? Integer.valueOf(catParam) : event.categoryId();
        String monthsParam = ctx.queryParam("months");
        int months = monthsParam != null ? Integer.parseInt(monthsParam) : 12;

        var stats = registrationService.findStatsByEvent(eventId, categoryId, months);
        ctx.json(stats.stream().map(this::toRegistrationStats).toList());
    }

    /**
     * Scores a member's registration history so the registration screen can rank who should be
     * prioritised next: the fairness score rises with denials and falls with acceptances, and the
     * priority band summarises the accept rate.
     */
    private RegistrationStatsResponse toRegistrationStats(MemberRegistrationStats s) {
        String name = resolveCreatedByName(s.memberId());
        int decisions = s.accepted() + s.denied();
        double acceptRate = decisions > 0 ? (double) s.accepted() / decisions : 1.0;
        String priority;
        if (decisions == 0) priority = "NONE";
        else if (acceptRate < 0.5) priority = "HIGH";
        else if (acceptRate < 0.75) priority = "MEDIUM";
        else priority = "LOW";
        double denialRatio = decisions > 0 ? (double) s.denied() / decisions : 0;
        double fairnessScore = Math.round((denialRatio * 50 + s.denied() * 5 - s.accepted() * 2 + 50) * 10) / 10.0;
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
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/registration-fields",
            methods = HttpMethod.GET,
            summary = "List the questions an event asks of everyone registering",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = RegistrationFieldResponse[].class)))
    private void listRegistrationFields(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int eventId = pathInt(ctx, "eventId");
        requireOwnedEvent(crudService, eventId, session);
        ctx.json(
                registrationFieldService
                        .findVisibleByEvent(eventId, session.hasPermission(StationPermission.EVENT_EDIT))
                        .stream()
                        .map(f -> new RegistrationFieldResponse(
                                f.id(), f.name(), f.fieldType(), f.config(), f.overview()))
                        .toList());
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/registration-fields",
            methods = HttpMethod.PUT,
            summary = "Replace the questions an event asks of everyone registering",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            requestBody =
                    @OpenApiRequestBody(content = @OpenApiContent(from = RegistrationFieldDefinitionsRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void setRegistrationFields(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int eventId = pathInt(ctx, "eventId");
        requireOwnedEvent(crudService, eventId, session);
        var req = ctx.bodyAsClass(RegistrationFieldDefinitionsRequest.class);
        var fields = req.fields() == null ? List.<RegistrationFieldDefinition>of() : req.fields();
        registrationFieldService.replaceFields(
                eventId,
                fields.stream()
                        .map(f -> new FieldEntry(
                                f.name(),
                                f.fieldType(),
                                f.config() != null ? f.config() : EventRegistrationFieldConfig.empty(),
                                f.overview()))
                        .toList());
        ctx.json(new MessageResponse("Registration fields updated"));
    }

    @OpenApi(
            path = "/api/v1/events/registrations/{id}/fields",
            methods = HttpMethod.PUT,
            summary = "Change the answers of a registration",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RegistrationFieldsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = RegistrationResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateRegistrationFields(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int registrationId = pathInt(ctx, "id");
        var registration = registrationService.findById(registrationId).orElseThrow(NotFoundResponse::new);
        requireOwnedEvent(crudService, registration.eventId(), session);
        requireAnswerAuthor(session, registration);

        var req = ctx.bodyAsClass(RegistrationFieldsRequest.class);
        registrationFieldService.replaceAnswers(
                registration.eventId(), registrationId, answersOf(req.fields()), manages(ctx));
        ctx.json(toRegistrationResponse(ctx, registration));
    }

    /**
     * Answers belong to the member who registered. They may change them, as may whoever manages
     * that member and anyone allowed to decide registrations.
     */
    private void requireAnswerAuthor(UserSession session, EventRegistration registration) {
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        if (registration.memberId() == session.member().id()) return;
        if (session.hasPermission(StationPermission.EVENT_REGISTRATION)) return;
        boolean manages = stationMemberService.findManaged(session.member().id()).stream()
                .anyMatch(m -> m.id() == registration.memberId());
        if (!manages) throw new ForbiddenResponse("You do not manage this member");
    }

    private void listRegistrations(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int eventId = pathInt(ctx, "eventId");
        requireOwnedEvent(crudService, eventId, session);
        String dateStr = ctx.queryParam("date");
        var regs = dateStr != null
                ? registrationService.findByEventAndDate(eventId, LocalDate.parse(dateStr))
                : registrationService.findByEvent(eventId);
        ctx.json(toRegistrationResponses(ctx, regs));
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
        int eventId = pathInt(ctx, "eventId");
        var req = ctx.bodyAsClass(RegisterRequest.class);

        var event = requireOwnedEvent(crudService, eventId, session);
        LocalDate date = resolveEventDate(req, event);
        if (!event.requiresRegistration()) {
            throw new BadRequestResponse("Event does not require registration");
        }
        if (event.registrationDeadline() != null && Instant.now().isAfter(event.registrationDeadline())) {
            throw new BadRequestResponse("Registration deadline has passed");
        }

        int memberId = resolveTargetMemberId(session, req);

        boolean isManagerRegistration = req.memberId() != null
                && req.memberId() != session.member().id()
                && session.hasPermission(StationPermission.EVENT_MANAGER);
        if (!isManagerRegistration && !restrictionService.isMemberEligible(eventId, memberId, session.permissions())) {
            throw new BadRequestResponse("Member is not eligible for this event");
        }

        var answers = registrationFieldService.resolveAnswers(
                eventId, answersOf(req.fields()), session.hasPermission(StationPermission.EVENT_EDIT));

        boolean autoAccept = !event.requiresConfirmation();
        Integer createdBy = memberId != session.member().id() ? session.member().id() : null;
        var registration = registrationService.register(eventId, memberId, date, autoAccept, createdBy);
        registrationFieldService.persistAnswers(registration.id(), answers);
        ctx.status(HttpStatus.CREATED).json(toRegistrationResponse(ctx, registration));
    }

    /**
     * Reads the submitted answers into a map keyed by question, keeping the last entry when a
     * question is sent twice.
     */
    private static Map<Integer, String> answersOf(List<FieldValueEntry> fields) {
        if (fields == null) return Map.of();
        var answers = new LinkedHashMap<Integer, String>();
        for (var field : fields) {
            answers.put(field.fieldId(), field.value());
        }
        return answers;
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
        int eventId = pathInt(ctx, "eventId");
        var req = ctx.bodyAsClass(RegisterRequest.class);

        var event = requireOwnedEvent(crudService, eventId, session);
        LocalDate date = resolveEventDate(req, event);

        int memberId = resolveTargetMemberId(session, req);

        Integer createdBy = memberId != session.member().id() ? session.member().id() : null;
        ctx.status(HttpStatus.CREATED).json(registrationService.decline(eventId, memberId, date, createdBy));
    }

    @OpenApi(
            path = "/api/v1/events/registrations/counts",
            methods = HttpMethod.GET,
            summary = "List registration counts per event",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200"))
    private void listRegistrationCounts(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(registrationService.findCountsByStation(session.stationId()));
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
        int id = pathInt(ctx, "id");
        var req = ctx.bodyAsClass(StatusUpdateRequest.class);
        if (req.status() != RegistrationStatus.ACCEPTED && req.status() != RegistrationStatus.DENIED) {
            throw new BadRequestResponse("status must be ACCEPTED or DENIED");
        }
        var registration = registrationService.findById(id).orElseThrow(NotFoundResponse::new);
        requireOwnedOrNotFound(ctx, registration.eventId(), crudService::findById, StationEvent::stationId);
        if (!registrationService.updateStatus(id, req.status())) {
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
        int id = pathInt(ctx, "id");
        var reg = registrationService.findById(id).orElseThrow(NotFoundResponse::new);

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

        if (!registrationService.withdraw(id)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
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
                    var display = resolveMemberDisplay(a.memberId());
                    return new AbsentMemberResponse(
                            a.memberId(),
                            display.name(),
                            display.identity(),
                            a.absentFrom(),
                            a.absentUntil(),
                            a.reason());
                })
                .toList());
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

    public record RegistrationResponse(
            int id,
            int eventId,
            int memberId,
            String memberName,
            MemberIdentity memberIdentity,
            LocalDate eventDate,
            RegistrationStatus status,
            Instant createdAt,
            String createdByName,
            List<FieldValueEntry> fields,
            /**
             * What the appointment is called. Carried on the registration because the reader cannot always
             * look it up: an appointment made by the association above the station lives on the
             * association's own station, so a station-side list matching the id against its own events
             * finds nothing and shows somebody a registration for something it cannot name.
             */
            String eventName) {}

    @OpenApiName("EventRegisterRequest")
    public record RegisterRequest(String eventDate, Integer memberId, List<FieldValueEntry> fields) {}

    /**
     * One answer to one registration question. Deliberately the same shape as the attendance and
     * batch field entries, so every custom field payload in the API reads alike.
     */
    @OpenApiName("EventRegistrationFieldValue")
    public record FieldValueEntry(int fieldId, String value) {}

    @OpenApiName("EventRegistrationFieldsRequest")
    public record RegistrationFieldsRequest(List<FieldValueEntry> fields) {}

    @OpenApiName("EventRegistrationFieldDefinition")
    public record RegistrationFieldResponse(
            int id, String name, EventFieldType fieldType, EventRegistrationFieldConfig config, boolean overview) {}

    @OpenApiName("EventRegistrationFieldDefinitionsRequest")
    public record RegistrationFieldDefinitionsRequest(List<RegistrationFieldDefinition> fields) {}

    @OpenApiName("EventRegistrationFieldDefinitionEntry")
    public record RegistrationFieldDefinition(
            String name, EventFieldType fieldType, EventRegistrationFieldConfig config, boolean overview) {}

    public record StatusUpdateRequest(RegistrationStatus status) {}

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

    public record AbsentMemberResponse(
            int memberId,
            String memberName,
            MemberIdentity memberIdentity,
            LocalDate absentFrom,
            LocalDate absentUntil,
            String reason) {}

    /**
     * A member's resolved display name and identity for registration and absence responses.
     */
    private record MemberDisplay(String name, MemberIdentity identity) {}
}
