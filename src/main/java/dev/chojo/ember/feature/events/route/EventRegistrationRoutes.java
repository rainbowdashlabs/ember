/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldConfig;
import dev.chojo.ember.feature.events.entity.MemberRegistrationStats;
import dev.chojo.ember.feature.events.entity.RegistrationCount;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventMemberTableService;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.events.service.RegistrationAnswerReminder;
import dev.chojo.ember.feature.events.service.RegistrationRowLookups;
import dev.chojo.ember.feature.members.entity.MemberAbsence;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.entity.MemberTableCellType;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.MemberTableRenderer;
import dev.chojo.ember.feature.members.service.MemberTableService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.util.CsvWriter;
import dev.chojo.ember.util.DocumentName;
import dev.chojo.ember.util.DocumentWord;
import dev.chojo.ember.util.SafeContentDisposition;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;
import static dev.chojo.ember.feature.events.service.EventOwnership.requireOwnedEvent;

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
    private final MemberNameResolver memberNameResolver;
    private final GuardianPolicy guardianPolicy;
    private final RegistrationRowLookups.Reader rowReader;
    private final AttendanceService attendanceService;
    private final EventRegistrationFieldService registrationFieldService;
    private static final Logger log = LoggerFactory.getLogger(EventRegistrationRoutes.class);
    private static final DateTimeFormatter DAY_STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final RegistrationAnswerReminder answerReminder;
    private final EventMemberTableService eventMemberTableService;
    private final MemberTableService memberTableService;
    private final MemberTableRenderer memberTableRenderer;
    private final StationService stationService;
    private final OccurrenceCalendar occurrenceCalendar;
    private final EventVisibility visibility;

    @Inject
    public EventRegistrationRoutes(
            EventCrudService crudService,
            EventRegistrationService registrationService,
            EventRestrictionService restrictionService,
            MemberNameResolver memberNameResolver,
            GuardianPolicy guardianPolicy,
            RegistrationRowLookups.Reader rowReader,
            AttendanceService attendanceService,
            EventRegistrationFieldService registrationFieldService,
            RegistrationAnswerReminder answerReminder,
            EventMemberTableService eventMemberTableService,
            MemberTableService memberTableService,
            MemberTableRenderer memberTableRenderer,
            StationService stationService,
            OccurrenceCalendar occurrenceCalendar,
            EventVisibility visibility) {
        this.occurrenceCalendar = occurrenceCalendar;
        this.crudService = crudService;
        this.visibility = visibility;
        this.stationService = stationService;
        this.registrationService = registrationService;
        this.restrictionService = restrictionService;
        this.memberNameResolver = memberNameResolver;
        this.guardianPolicy = guardianPolicy;
        this.rowReader = rowReader;
        this.attendanceService = attendanceService;
        this.registrationFieldService = registrationFieldService;
        this.answerReminder = answerReminder;
        this.eventMemberTableService = eventMemberTableService;
        this.memberTableService = memberTableService;
        this.memberTableRenderer = memberTableRenderer;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events/registrations/mine", this::listMyRegistrations, StationPermission.USER);
        routes.get(
                prefix + "/events/registrations/pending",
                this::listPendingRegistrations,
                StationPermission.EVENT_REGISTRATION);
        routes.get(prefix + "/events/registrations/counts", this::listRegistrationCounts, StationPermission.USER);
        routes.get(prefix + "/events/registrations/awaiting", this::listAwaitingAnswer, StationPermission.USER);
        routes.put(
                prefix + "/events/registrations/{id}/status",
                this::updateRegistrationStatus,
                StationPermission.EVENT_REGISTRATION);
        routes.delete(prefix + "/events/registrations/{id}", this::withdrawRegistration, StationPermission.USER);
        routes.post(prefix + "/events/registrations/{id}/undo", this::undoWithdrawal, StationPermission.USER);
        routes.put(prefix + "/events/registrations/{id}/answer", this::changeAnswer, StationPermission.USER);

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

        routes.get(
                prefix + "/events/{eventId}/registration-table/columns",
                this::tableColumns,
                StationPermission.EVENT_REGISTRATION);
        routes.post(
                prefix + "/events/{eventId}/registration-table", this::drawTable, StationPermission.EVENT_REGISTRATION);
        routes.post(
                prefix + "/events/{eventId}/registration-table/export.csv",
                this::exportTableCsv,
                StationPermission.EVENT_REGISTRATION);
        routes.post(
                prefix + "/events/{eventId}/registration-table/export.pdf",
                this::exportTablePdf,
                StationPermission.EVENT_REGISTRATION);

        routes.get(prefix + "/events/{eventId}/registrations", this::listRegistrations, StationPermission.USER);
        routes.post(prefix + "/events/{eventId}/register", this::register, StationPermission.USER);
        routes.post(prefix + "/events/{eventId}/decline", this::decline, StationPermission.USER);

        routes.get(
                prefix + "/events/{id}/absences",
                this::listAbsencesForDate,
                StationPermission.EVENT_EDIT,
                StationPermission.ATTENDANCE_EDIT);
    }

    /** The shared reads for a list about these members, see {@link RegistrationRowLookups}. */
    private RegistrationRowLookups lookupsFor(Collection<Integer> memberIds) {
        return rowReader.forMembers(memberIds);
    }

    /**
     * Maps a registration to its response, resolving the member's display name, identity, and creator.
     */
    private RegistrationResponse toRegistrationResponse(Context ctx, EventRegistration r) {
        var answers = registrationFieldService.findValues(r.id());
        var hidden = registrationFieldService.hiddenFieldIds(r.eventId(), readsAnswersOf(ctx, r));
        return toRegistrationResponse(
                lookupsFor(List.of(r.memberId())),
                r,
                answers.stream()
                        .filter(v -> !hidden.contains(v.fieldId()))
                        .map(v -> new FieldValueEntry(v.fieldId(), v.value()))
                        .toList(),
                EventRegistrationFieldService.owesAnswer(
                        r.status(), registrationFieldService.requiredFieldIds(r.eventId()), answers));
    }

    /**
     * Whether this reader may read the answers an appointment keeps for its organisers.
     *
     * <p>Whoever runs the appointment collected them, and the household they are about gave them.
     * Everybody else is answered without them, which is filtered here rather than in the browser so
     * that a direct call cannot read them either.
     */
    private boolean readsAnswersOf(Context ctx, EventRegistration registration) {
        var session = UserSession.from(ctx);
        if (session.hasPermission(StationPermission.EVENT_EDIT)) return true;
        return guardianPolicy.mayActFor(session, registration.memberId());
    }

    private static RegistrationResponse toRegistrationResponse(
            RegistrationRowLookups lookups, EventRegistration r, List<FieldValueEntry> fields, boolean answersMissing) {
        var display = lookups.member(r.memberId());
        return new RegistrationResponse(
                r.id(),
                r.eventId(),
                r.memberId(),
                display.name(),
                display.identity(),
                r.eventDate(),
                r.status(),
                r.createdAt(),
                lookups.createdByName(r.createdBy()),
                fields,
                lookups.eventName(r.eventId()),
                answersMissing,
                r.fromField(),
                r.fromField() ? lookups.namingField(r) : null);
    }

    /**
     * Maps a whole list of registrations without reading anything once per row.
     *
     * <p>Every answer is read in one query and the members in one more. Each appointment, its hidden
     * and required questions and the questions it asked on a date are read once for the list rather
     * than once per registration. Names and identities come from the member caches, which read a
     * member they have not seen before once and answer every later row from memory.
     */
    private List<RegistrationResponse> toRegistrationResponses(Context ctx, List<EventRegistration> registrations) {
        var answers = registrationFieldService.findValuesByRegistration(
                registrations.stream().map(EventRegistration::id).toList());
        var lookups = lookupsFor(
                registrations.stream().map(EventRegistration::memberId).toList());
        var session = UserSession.from(ctx);
        boolean runsTheEvents = session.hasPermission(StationPermission.EVENT_EDIT);
        var household = runsTheEvents ? Set.<Integer>of() : Set.copyOf(guardianPolicy.household(session));
        var hiddenByEvent = new HashMap<Integer, Set<Integer>>();
        var requiredByEvent = new HashMap<Integer, Set<Integer>>();
        return registrations.stream()
                .map(r -> {
                    boolean reads = runsTheEvents || household.contains(r.memberId());
                    var hidden = reads
                            ? Set.<Integer>of()
                            : hiddenByEvent.computeIfAbsent(
                                    r.eventId(), eventId -> registrationFieldService.hiddenFieldIds(eventId, false));
                    var required =
                            requiredByEvent.computeIfAbsent(r.eventId(), registrationFieldService::requiredFieldIds);
                    var carried = answers.getOrDefault(r.id(), List.of());
                    return toRegistrationResponse(
                            lookups,
                            r,
                            carried.stream()
                                    .filter(v -> !hidden.contains(v.fieldId()))
                                    .map(v -> new FieldValueEntry(v.fieldId(), v.value()))
                                    .toList(),
                            EventRegistrationFieldService.owesAnswer(r.status(), required, carried));
                })
                .toList();
    }

    /**
     * Resolves and authorises the member id a register or decline call targets, defaulting to the caller.
     */
    private int resolveTargetMemberId(UserSession session, RegisterRequest req) {
        if (session.member() == null) throw Refusal.NOT_A_MEMBER_ON_REGISTRATION.raise();
        if (req.memberId() == null) return session.member().id();
        int memberId = req.memberId();
        if (!guardianPolicy.mayActFor(session, memberId) && !session.hasPermission(StationPermission.EVENT_MANAGER)) {
            throw Refusal.MEMBER_NOT_YOURS_TO_REGISTER.raise();
        }
        return memberId;
    }

    @OpenApi(
            path = "/api/v1/events/registrations/mine",
            methods = HttpMethod.GET,
            summary = "List my registrations",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventRegistration[].class)))
    @OpenApi(
            path = "/api/v1/events/registrations/awaiting",
            methods = HttpMethod.GET,
            summary = "Events still waiting on an answer from the reader or anyone they answer for",
            tags = {"Events"},
            description = "One entry per event, naming everyone in the household who still owes an "
                    + "answer, soonest deadline first. Only events open to that person are listed, and "
                    + "only while their registration is still open.",
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AwaitingAnswer[].class)))
    private void listAwaitingAnswer(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.json(Collections.emptyList());
            return;
        }

        var household = guardianPolicy.household(session);

        var byEvent = new LinkedHashMap<Integer, AwaitingAnswer>();
        for (var row : registrationService.findAwaitingAnswer(household)) {
            if (!restrictionService.canRegister(row.eventId(), row.memberId(), session.permissions())) {
                continue;
            }
            var entry = byEvent.computeIfAbsent(
                    row.eventId(),
                    id -> new AwaitingAnswer(
                            id,
                            row.name(),
                            row.startTime(),
                            row.registrationDeadline(),
                            row.categoryId(),
                            new ArrayList<>()));
            entry.members().add(new AwaitingMember(row.memberId(), memberNameResolver.called(row.memberId())));
        }
        ctx.json(List.copyOf(byEvent.values()));
    }

    /** An event still waiting on an answer, and everyone in the household who owes one. */
    public record AwaitingAnswer(
            int eventId,
            String name,
            Instant startTime,
            Instant registrationDeadline,
            Integer categoryId,
            List<AwaitingMember> members) {}

    /** Somebody who still owes an answer, named so a guardian can tell their children apart. */
    public record AwaitingMember(int memberId, String name) {}

    private void listMyRegistrations(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.json(Collections.emptyList());
            return;
        }
        var registrations = new ArrayList<EventRegistration>();
        for (int memberId : guardianPolicy.household(session)) {
            registrations.addAll(registrationService.findByMember(memberId));
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
        var event = visibility.requireVisibleEvent(session, eventId);
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
        String name = memberNameResolver.called(s.memberId());
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
        visibility.requireVisibleEvent(session, eventId);
        ctx.json(registrationFieldService.findByEvent(eventId).stream()
                .map(f -> new RegistrationFieldResponse(f.id(), f.name(), f.fieldType(), f.config(), f.overview()))
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
        answerReminder.replaceQuestions(
                eventId,
                fields.stream()
                        .map(f -> new RegistrationFieldDraft(
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
        var registration = registrationService
                .findById(registrationId)
                .orElseThrow(Refusal.EVENT_REGISTRATION_NOT_HERE_ON_FIELD_CHANGE::raise);
        requireOwnedEvent(crudService, registration.eventId(), session);
        requireAnswerAuthor(session, registration);

        var req = ctx.bodyAsClass(RegistrationFieldsRequest.class);
        registrationFieldService.replaceAnswers(
                registration.eventId(), registrationId, answersOf(req.fields()), readsAnswersOf(ctx, registration));
        ctx.json(toRegistrationResponse(ctx, registration));
    }

    /**
     * Answers belong to the member who registered. They may change them, as may whoever manages
     * that member and anyone allowed to decide registrations.
     *
     * <p>Whoever runs the appointment may change them too. They are the one the answers were
     * collected for: a shirt size typed wrong or a lift offered to somebody else is theirs to put
     * right, and asking the member to correct it while the list is being read from is how a wrong
     * answer stays wrong.
     */
    private void requireAnswerAuthor(UserSession session, EventRegistration registration) {
        if (session.member() == null) throw Refusal.NOT_A_MEMBER_ON_ANSWER_CHANGE.raise();
        if (session.hasPermission(StationPermission.EVENT_EDIT)) return;
        if (session.hasPermission(StationPermission.EVENT_REGISTRATION)) return;
        if (!guardianPolicy.mayActFor(session, registration.memberId())) {
            throw Refusal.REGISTRATION_ANSWERS_NOT_YOURS.raise();
        }
    }

    /**
     * The columns somebody may put on this appointment's table: the station's own questions, and the
     * appointment's.
     */
    private void tableColumns(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int eventId = pathInt(ctx, "eventId");
        visibility.requireVisibleEvent(session, eventId);
        var questions = eventMemberTableService.offerableQuestions(eventId, readsHiddenAnswers(session));
        ctx.json(new TableColumnsResponse(
                memberTableService.offerableColumns(session.stationId(), session.permissions()),
                questions.entrySet().stream()
                        .map(entry -> new QuestionColumn(
                                entry.getKey(),
                                entry.getValue().label(),
                                entry.getValue().type()))
                        .toList()));
    }

    private void drawTable(Context ctx) {
        ctx.json(tableOf(ctx));
    }

    private void exportTableCsv(Context ctx) {
        var session = UserSession.from(ctx);
        var event = requireOwnedEvent(crudService, pathInt(ctx, "eventId"), session);
        var station = stationService
                .findById(session.stationId())
                .orElseThrow(Refusal.STATION_NOT_HERE_FOR_REGISTRATION_CSV::raise);
        ctx.contentType("text/csv");
        ctx.header("Content-Disposition", registrationsName(station, event.name(), ctx, "csv"));
        ctx.result(
                memberTableRenderer.toCsv(tableOf(ctx), station, CsvWriter.Separator.of(ctx.queryParam("separator"))));
    }

    /** A registration list belongs to one appointment on one day, and says both. */
    private String registrationsName(Station station, String eventName, Context ctx, String extension) {
        String language = StationFormat.languageOf(station);
        String filename = DocumentName.of(
                extension,
                DocumentWord.REGISTRATIONS.in(language),
                DocumentName.part(eventName),
                tableDate(ctx).format(DAY_STAMP));
        return SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, filename);
    }

    private void exportTablePdf(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int eventId = pathInt(ctx, "eventId");
        var event = requireOwnedEvent(crudService, eventId, session);
        var station = stationService
                .findById(session.stationId())
                .orElseThrow(Refusal.STATION_NOT_HERE_FOR_REGISTRATION_SHEET::raise);
        var table = tableOf(ctx);
        try {
            var pdf = memberTableRenderer.toPdf(
                    table,
                    station,
                    event.name(),
                    tableDate(ctx).format(DAY_STAMP),
                    NameParts.of(session.account()).official());
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", registrationsName(station, event.name(), ctx, "pdf"));
            ctx.result(pdf);
        } catch (Exception e) {
            log.error("Failed to render the registration table of event {}", eventId, e);
            throw Refusal.REGISTRATION_SHEET_NOT_DRAWN.raise();
        }
    }

    private MemberTable tableOf(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int eventId = pathInt(ctx, "eventId");
        var event = visibility.requireVisibleEvent(session, eventId);
        var station = stationService
                .findById(event.stationId())
                .orElseThrow(Refusal.STATION_NOT_HERE_FOR_REGISTRATION_TABLE::raise);
        var req = ctx.bodyAsClass(RegistrationTableRequest.class);
        var columns = req.columns() == null
                ? List.<MemberTableColumn>of()
                : req.columns().stream().filter(MemberTableColumn::isWellFormed).toList();
        return eventMemberTableService.table(
                station, eventId, tableDate(ctx), columns, session.permissions(), readsHiddenAnswers(session));
    }

    /**
     * Which day the table is about. A registration belongs to one occurrence, so a table without a
     * day would be a table of every occurrence at once.
     */
    private LocalDate tableDate(Context ctx) {
        var req = ctx.bodyAsClass(RegistrationTableRequest.class);
        if (req.date() == null || req.date().isBlank()) {
            throw Refusal.REGISTRATION_TABLE_NEEDS_A_DAY.raise();
        }
        return LocalDate.parse(req.date());
    }

    private boolean readsHiddenAnswers(UserSession session) {
        return session.hasPermission(StationPermission.EVENT_EDIT);
    }

    private void listRegistrations(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int eventId = pathInt(ctx, "eventId");
        visibility.requireVisibleEvent(session, eventId);
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
            throw Refusal.EVENT_TAKES_NO_REGISTRATIONS.raise();
        }

        // Whoever runs the event is not answering it, they are keeping its list: somebody who rang up
        // after the deadline is still somebody who is coming, and the list has to be able to say so.
        boolean runsTheEvent = session.hasPermission(StationPermission.EVENT_MANAGER);
        if (!runsTheEvent
                && event.registrationDeadline() != null
                && Instant.now().isAfter(event.registrationDeadline())) {
            throw Refusal.REGISTRATION_CLOSED.raise();
        }

        int memberId = resolveTargetMemberId(session, req);

        boolean isManagerRegistration =
                req.memberId() != null && req.memberId() != session.member().id() && runsTheEvent;
        if (!isManagerRegistration && !restrictionService.canRegister(eventId, memberId, session.permissions())) {
            throw Refusal.MEMBER_NOT_INVITED_TO_EVENT.raise();
        }

        var answers = registrationFieldService.resolveAnswers(eventId, answersOf(req.fields()));

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

        var event = visibility.requireVisibleEvent(session, eventId);
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
        ctx.json(visibility.keepVisible(
                session, registrationService.findCountsByStation(session.stationId()), RegistrationCount::eventId));
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
            throw Refusal.REGISTRATION_DECISION_UNKNOWN.raise();
        }
        var registration = registrationService
                .findById(id)
                .orElseThrow(Refusal.EVENT_REGISTRATION_NOT_HERE_ON_STATUS_CHANGE::raise);
        requireOwnedOrNotFound(ctx, registration.eventId(), crudService::findById, StationEvent::stationId);
        if (!registrationService.updateStatus(id, req.status())) {
            throw Refusal.EVENT_REGISTRATION_STATUS_NOT_CHANGED.raise();
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
    @OpenApi(
            path = "/api/v1/events/registrations/{id}/answer",
            methods = HttpMethod.PUT,
            summary = "Change whether somebody is coming",
            description = "While registration is open a member changes their own answer, and so does "
                    + "whoever looks after them. Once it has closed only the people who run the event can, "
                    + "because the list has been counted on by then. Coming back after declining is a "
                    + "fresh answer rather than the old place restored: an event that confirms its list "
                    + "confirms this one too, so nobody keeps a place they gave up. The few minutes "
                    + "straight after giving one up are the exception, and have their own route: a "
                    + "misclick put right at once is not a change of mind.",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AnswerRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void changeAnswer(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        var req = ctx.bodyAsClass(AnswerRequest.class);
        var registration =
                registrationService.findById(id).orElseThrow(Refusal.EVENT_REGISTRATION_NOT_HERE_ON_ANSWER::raise);
        var event = requireOwnedEvent(crudService, registration.eventId(), session);
        occurrenceCalendar.dateToAnswerFor(event, registration.eventDate());

        boolean manages = guardianPolicy.mayActFor(session, registration.memberId());
        boolean runsTheEvent = session.hasPermission(StationPermission.EVENT_MANAGER)
                || session.hasPermission(StationPermission.EVENT_REGISTRATION);
        if (!manages && !runsTheEvent) {
            throw Refusal.ANSWER_NOT_YOURS_TO_GIVE.raise();
        }

        boolean closed = event.registrationDeadline() != null && Instant.now().isAfter(event.registrationDeadline());
        if (closed && !runsTheEvent) {
            throw Refusal.REGISTRATION_CLOSED_ON_ANSWER_CHANGE.raise();
        }

        if (!req.attending()) {
            if (!registrationService.refuse(id)) throw Refusal.EVENT_REGISTRATION_NOT_REFUSED.raise();
            ctx.status(HttpStatus.NO_CONTENT);
            return;
        }
        var status = event.requiresConfirmation() ? RegistrationStatus.PENDING : RegistrationStatus.ACCEPTED;
        if (!registrationService.updateStatus(id, status)) {
            throw Refusal.EVENT_REGISTRATION_NOT_CONFIRMED.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /** Whether somebody is coming. */
    public record AnswerRequest(boolean attending) {}

    private void withdrawRegistration(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        var reg =
                registrationService.findById(id).orElseThrow(Refusal.EVENT_REGISTRATION_NOT_HERE_ON_WITHDRAWAL::raise);
        requireOwnedEvent(crudService, reg.eventId(), session);
        requireMayAnswerFor(session, reg.memberId());

        if (!registrationService.withdraw(id)) {
            throw Refusal.EVENT_REGISTRATION_NOT_WITHDRAWN.raise();
        }
        ctx.json(new WithdrawalResponse(Instant.now().plus(EventRegistrationService.UNDO_WINDOW)));
    }

    /**
     * Taking a withdrawal back, for as long as it may be taken back.
     *
     * <p>Whoever could give the answer can take it back, which is the same question asked at the same
     * door. Past the window this is a plain refusal: there is nothing here to put back, and the
     * member registers again the ordinary way if the appointment still takes answers.
     */
    private void undoWithdrawal(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        var reg = registrationService.findById(id).orElseThrow(Refusal.EVENT_REGISTRATION_NOT_HERE_ON_UNDO::raise);
        requireOwnedEvent(crudService, reg.eventId(), session);
        requireMayAnswerFor(session, reg.memberId());

        if (!registrationService.undoWithdrawal(id)) {
            throw Refusal.WITHDRAWAL_NO_LONGER_UNDONE.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Whether this session may answer for that member: themselves, somebody in their care, or
     * anybody where they keep the list.
     */
    private void requireMayAnswerFor(UserSession session, int memberId) {
        if (!guardianPolicy.mayActFor(session, memberId)
                && !session.hasPermission(StationPermission.EVENT_MANAGER)
                && !session.hasPermission(StationPermission.EVENT_REGISTRATION)) {
            throw Refusal.WITHDRAWAL_NOT_YOURS_TO_ANSWER.raise();
        }
    }

    /**
     * @param undoUntil the moment the withdrawal stops being something that can be taken back, so the
     *         page can offer it for exactly as long as the server would accept it
     */
    public record WithdrawalResponse(Instant undoUntil) {}

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
        var lookups = lookupsFor(absences.stream().map(MemberAbsence::memberId).toList());
        ctx.json(absences.stream()
                .map(a -> {
                    var display = lookups.member(a.memberId());
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

    /** The day a registration or a decline is filed against, refused where the appointment is not on it. */
    private LocalDate resolveEventDate(RegisterRequest req, StationEvent event) {
        LocalDate requested = req.eventDate() == null ? null : LocalDate.parse(req.eventDate());
        return occurrenceCalendar.dateToAnswerFor(event, requested);
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
            String eventName,
            /**
             * Whether the appointment asks something this registration has not answered, which is
             * what a question added after somebody registered leaves behind.
             */
            boolean answersMissing,
            /**
             * Whether this place is held because a question of the appointment names the member,
             * which is a place nobody can give back from the list.
             */
            boolean fromField,
            /** The question that names them, where one does. */
            String fieldName) {}

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
     * What may go on this appointment's table.
     *
     * @param member    what the station knows about a person, already cut to this reader
     * @param questions what this appointment asked, minus anything kept from the room
     */
    public record TableColumnsResponse(List<MemberTable.MemberTableHeader> member, List<QuestionColumn> questions) {}

    /** One of an appointment's own questions, offered as a column. */
    public record QuestionColumn(int fieldId, String label, MemberTableCellType type) {}

    /**
     * @param date    the day the table is about, because a registration belongs to one
     * @param columns the columns asked for, which may name more than this reader may read
     */
    public record RegistrationTableRequest(String date, List<MemberTableColumn> columns) {}
}
