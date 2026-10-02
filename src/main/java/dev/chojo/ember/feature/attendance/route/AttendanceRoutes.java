/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.AttendanceRefusal;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldValueEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceReportPreset;
import dev.chojo.ember.feature.attendance.entity.AttendanceSession;
import dev.chojo.ember.feature.attendance.entity.AttendanceSessionField;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplate;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplateField;
import dev.chojo.ember.feature.attendance.entity.SessionAudience;
import dev.chojo.ember.feature.attendance.entity.SessionSummary;
import dev.chojo.ember.feature.attendance.entity.TemplateGroup;
import dev.chojo.ember.feature.attendance.service.AttendanceExportService;
import dev.chojo.ember.feature.attendance.service.AttendanceReportService;
import dev.chojo.ember.feature.attendance.service.AttendanceReportService.ReportData;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.attendance.service.MemberCheckNotesService;
import dev.chojo.ember.feature.members.entity.MemberAbsence;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.util.CsvWriter;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * HTTP route definitions for the attendance feature, handling templates, sessions, entries,
 * absences, reports, and PDF exports.
 */
@Singleton
public class AttendanceRoutes implements Routes {
    private final AttendanceService attendanceService;
    private final MemberCheckNotesService memberCheckNotesService;
    private final AttendanceExportService exportService;
    private final AttendanceReportService reportService;
    private final StationMemberService memberService;
    private final MemberNameResolver memberNames;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public AttendanceRoutes(
            AttendanceService attendanceService,
            MemberCheckNotesService memberCheckNotesService,
            AttendanceExportService exportService,
            AttendanceReportService reportService,
            StationMemberService memberService,
            MemberNameResolver memberNames,
            MemberIdentityFactory memberIdentityFactory) {
        this.attendanceService = attendanceService;
        this.memberCheckNotesService = memberCheckNotesService;
        this.exportService = exportService;
        this.reportService = reportService;
        this.memberService = memberService;
        this.memberNames = memberNames;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/attendance/templates",
                this::listTemplates,
                StationPermission.ATTENDANCE_READ,
                StationPermission.ATTENDANCE_CONFIGURE,
                StationPermission.EVENT_EDIT);
        routes.get(
                prefix + "/attendance/templates/detail",
                this::listTemplateDetails,
                StationPermission.ATTENDANCE_READ,
                StationPermission.ATTENDANCE_CONFIGURE,
                StationPermission.EVENT_EDIT);
        routes.post(prefix + "/attendance/templates", this::createTemplate, StationPermission.ATTENDANCE_CONFIGURE);
        routes.get(
                prefix + "/attendance/templates/{id}",
                this::getTemplate,
                StationPermission.ATTENDANCE_READ,
                StationPermission.ATTENDANCE_CONFIGURE);
        routes.put(prefix + "/attendance/templates/{id}", this::updateTemplate, StationPermission.ATTENDANCE_CONFIGURE);
        routes.delete(
                prefix + "/attendance/templates/{id}", this::deleteTemplate, StationPermission.ATTENDANCE_CONFIGURE);

        routes.put(
                prefix + "/attendance/templates/{templateId}/groups",
                this::setTemplateGroups,
                StationPermission.ATTENDANCE_CONFIGURE);
        routes.put(
                prefix + "/attendance/templates/{templateId}/user-types",
                this::setTemplateUserTypes,
                StationPermission.ATTENDANCE_CONFIGURE);

        routes.get(
                prefix + "/attendance/templates/{templateId}/fields",
                this::listTemplateFields,
                StationPermission.ATTENDANCE_READ,
                StationPermission.ATTENDANCE_CONFIGURE,
                StationPermission.EVENT_EDIT);
        routes.post(
                prefix + "/attendance/templates/{templateId}/fields",
                this::createTemplateField,
                StationPermission.ATTENDANCE_CONFIGURE);
        routes.put(
                prefix + "/attendance/templates/{templateId}/fields/{fieldId}",
                this::updateTemplateField,
                StationPermission.ATTENDANCE_CONFIGURE);
        routes.delete(
                prefix + "/attendance/templates/{templateId}/fields/{fieldId}",
                this::deleteTemplateField,
                StationPermission.ATTENDANCE_CONFIGURE);

        routes.get(prefix + "/attendance/sessions", this::listSessionSummaries, StationPermission.ATTENDANCE_READ);
        routes.get(
                prefix + "/attendance/templates/{templateId}/sessions",
                this::listSessions,
                StationPermission.ATTENDANCE_READ);
        routes.post(
                prefix + "/attendance/templates/{templateId}/sessions",
                this::createSession,
                StationPermission.ATTENDANCE_EDIT);
        routes.get(
                prefix + "/attendance/events/{eventId}/session",
                this::getSessionForEvent,
                StationPermission.ATTENDANCE_READ);
        routes.get(prefix + "/attendance/sessions/{id}", this::getSession, StationPermission.ATTENDANCE_READ);
        routes.put(prefix + "/attendance/sessions/{id}", this::updateSession, StationPermission.ATTENDANCE_EDIT);
        routes.delete(prefix + "/attendance/sessions/{id}", this::deleteSession, StationPermission.ATTENDANCE_EDIT);
        routes.post(
                prefix + "/attendance/sessions/{id}/unlock", this::unlockSession, StationPermission.ATTENDANCE_MANAGER);
        routes.post(prefix + "/attendance/sessions/{id}/lock", this::lockSession, StationPermission.ATTENDANCE_MANAGER);
        routes.get(
                prefix + "/attendance/sessions/{id}/member-notes",
                this::listMemberNotes,
                StationPermission.ATTENDANCE_READ);

        routes.get(
                prefix + "/attendance/sessions/{sessionId}/fields",
                this::listSessionFields,
                StationPermission.ATTENDANCE_READ);
        routes.put(
                prefix + "/attendance/sessions/{sessionId}/fields",
                this::setSessionFields,
                StationPermission.ATTENDANCE_MANAGER);

        routes.get(
                prefix + "/attendance/sessions/{sessionId}/entries",
                this::listEntries,
                StationPermission.ATTENDANCE_READ);
        routes.post(
                prefix + "/attendance/sessions/{sessionId}/entries",
                this::createEntry,
                StationPermission.ATTENDANCE_EDIT);
        routes.post(prefix + "/attendance/entries/{id}/check-in", this::checkIn, StationPermission.ATTENDANCE_EDIT);
        routes.post(prefix + "/attendance/entries/{id}/check-out", this::checkOut, StationPermission.ATTENDANCE_EDIT);
        routes.put(
                prefix + "/attendance/entries/{id}/status", this::updateEntryStatus, StationPermission.ATTENDANCE_EDIT);
        routes.post(
                prefix + "/attendance/entries/{id}/reset-times", this::resetTimes, StationPermission.ATTENDANCE_EDIT);
        routes.delete(prefix + "/attendance/entries/{id}", this::deleteEntry, StationPermission.ATTENDANCE_EDIT);
        routes.post(
                prefix + "/attendance/sessions/{sessionId}/sync-event",
                this::syncFromEvent,
                StationPermission.ATTENDANCE_MANAGER);
        routes.get(
                prefix + "/attendance/sessions/{sessionId}/export",
                this::exportPdf,
                StationPermission.ATTENDANCE_MANAGER);

        routes.get(prefix + "/attendance/report/preview", this::reportPreview, StationPermission.ATTENDANCE_EXPORT);
        routes.get(prefix + "/attendance/report/export", this::reportExport, StationPermission.ATTENDANCE_EXPORT);
        routes.get(
                prefix + "/attendance/report/export.csv", this::reportExportCsv, StationPermission.ATTENDANCE_EXPORT);

        routes.get(prefix + "/attendance/report/presets", this::listPresets, StationPermission.ATTENDANCE_EXPORT);
        routes.post(prefix + "/attendance/report/presets", this::createPreset, StationPermission.ATTENDANCE_EXPORT);
        routes.delete(
                prefix + "/attendance/report/presets/{id}", this::deletePreset, StationPermission.ATTENDANCE_EXPORT);

        routes.get(
                prefix + "/attendance/absences",
                this::listActiveAbsences,
                StationPermission.ATTENDANCE_EDIT,
                StationPermission.MEMBER_EDIT);
        routes.get(
                prefix + "/attendance/absences/member/{memberId}",
                this::listMemberAbsences,
                StationPermission.ATTENDANCE_EDIT,
                StationPermission.MEMBER_EDIT);
        routes.post(
                prefix + "/attendance/absences",
                this::createAbsence,
                StationPermission.ATTENDANCE_MANAGER,
                StationPermission.MEMBER_EDIT);
        routes.delete(
                prefix + "/attendance/absences/{id}",
                this::deleteAbsence,
                StationPermission.ATTENDANCE_MANAGER,
                StationPermission.MEMBER_EDIT);

        routes.get(prefix + "/profile/absences", this::listMyAbsences, StationPermission.USER);
        routes.post(prefix + "/profile/absences", this::createMyAbsence, StationPermission.USER);
        routes.delete(prefix + "/profile/absences/{id}", this::deleteMyAbsence, StationPermission.USER);
    }

    private void verifySessionOwnership(int sessionId, StationSession userSession) {
        var attSession = attendanceService
                .findSessionById(sessionId)
                .orElseThrow(AttendanceRefusal.ATTENDANCE_SHEET_NOT_HERE::raise);
        var template = attendanceService
                .findTemplateById(attSession.templateId())
                .orElseThrow(AttendanceRefusal.ATTENDANCE_SHEET_NOT_HERE::raise);
        if (template.stationId() != userSession.stationId()) {
            throw AttendanceRefusal.ATTENDANCE_SHEET_NOT_HERE.raise();
        }
    }

    /**
     * Asserts the given template belongs to the caller's station.
     */
    private void verifyTemplateOwnership(int templateId, StationSession userSession) {
        var template = attendanceService
                .findTemplateById(templateId)
                .orElseThrow(AttendanceRefusal.ATTENDANCE_TEMPLATE_NOT_HERE_OR_NOT_YOURS::raise);
        if (template.stationId() != userSession.stationId()) {
            throw AttendanceRefusal.ATTENDANCE_TEMPLATE_NOT_HERE_OR_NOT_YOURS.raise();
        }
    }

    /**
     * Asserts the given entry's session (and thus template) belongs to the caller's station.
     */
    private void verifyEntryOwnership(int entryId, StationSession userSession) {
        var entry = attendanceService
                .findEntryById(entryId)
                .orElseThrow(AttendanceRefusal.ATTENDANCE_ENTRY_NOT_HERE::raise);
        verifySessionOwnership(entry.sessionId(), userSession);
    }

    /**
     * Asserts the given member belongs to the caller's station.
     */
    private void verifyMemberInStation(int memberId, StationSession userSession) {
        var member = memberService.findById(memberId).orElseThrow(AttendanceRefusal.ATTENDANCE_MEMBER_NOT_HERE::raise);
        if (member.stationId() != userSession.stationId()) {
            throw AttendanceRefusal.ATTENDANCE_MEMBER_NOT_HERE.raise();
        }
    }

    /**
     * Resolves the name of the member who created an absence record.
     *
     * @param createdBy the member ID of the creator, or {@code null}
     * @return the name the station calls the creator by, or {@code null} if not resolvable
     */
    private @Nullable String resolveCreatedByName(@Nullable Integer createdBy) {
        return createdBy == null ? null : memberNames.called(createdBy);
    }

    /**
     * Converts a {@link MemberAbsence} entity to an {@link AbsenceResponse} with resolved creator name.
     */
    private AbsenceResponse toAbsenceResponse(MemberAbsence a) {
        return new AbsenceResponse(
                a.id(),
                a.memberId(),
                a.absentFrom(),
                a.absentUntil(),
                a.reason(),
                a.createdAt(),
                resolveCreatedByName(a.createdBy()),
                memberIdentityFactory.fromMemberId(a.memberId()));
    }

    @OpenApi(
            path = "/api/v1/attendance/templates",
            methods = HttpMethod.GET,
            summary = "List attendance templates for the current station",
            tags = {"Attendance"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceTemplate[].class)))
    private void listTemplates(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(attendanceService.findTemplatesByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/detail",
            methods = HttpMethod.GET,
            summary = "List attendance templates with their fields and groups",
            tags = {"Attendance"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TemplateDetail[].class)))
    private void listTemplateDetails(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(attendanceService.findTemplatesByStation(session.stationId()).stream()
                .map(this::detailOf)
                .toList());
    }

    private TemplateDetail detailOf(AttendanceTemplate template) {
        return new TemplateDetail(
                template.id(),
                template.stationId(),
                template.name(),
                attendanceService.findTemplateFields(template.id()),
                attendanceService.findTemplateGroups(template.id()).stream()
                        .map(group -> new TemplateGroupEntry(group.groupId(), group.position()))
                        .toList(),
                attendanceService.findTemplateUserTypes(template.id()));
    }

    @OpenApi(
            path = "/api/v1/attendance/templates",
            methods = HttpMethod.POST,
            summary = "Create an attendance template",
            tags = {"Attendance"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TemplateRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = AttendanceTemplate.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createTemplate(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(TemplateRequest.class);
        if (isBlank(request.name())) {
            throw AttendanceRefusal.ATTENDANCE_TEMPLATE_NEEDS_A_NAME.raise();
        }
        ctx.status(HttpStatus.CREATED).json(attendanceService.createTemplate(session.stationId(), request.name()));
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/{id}",
            methods = HttpMethod.GET,
            summary = "Get an attendance template with its fields",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TemplateDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getTemplate(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyTemplateOwnership(id, session);
        attendanceService.findActiveTemplateById(id).ifPresentOrElse(template -> ctx.json(detailOf(template)), () -> {
            throw AttendanceRefusal.ATTENDANCE_TEMPLATE_GONE_WHILE_READ.raise();
        });
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/{id}",
            methods = HttpMethod.PUT,
            summary = "Update an attendance template",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TemplateRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceTemplate.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateTemplate(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, attendanceService::findActiveTemplateById, AttendanceTemplate::stationId);
        var request = ctx.bodyAsClass(TemplateRequest.class);
        if (isBlank(request.name())) {
            throw AttendanceRefusal.ATTENDANCE_TEMPLATE_RENAME_NEEDS_A_NAME.raise();
        }
        attendanceService.updateTemplate(id, request.name()).ifPresentOrElse(ctx::json, () -> {
            throw AttendanceRefusal.ATTENDANCE_TEMPLATE_NOT_HERE_TO_CHANGE.raise();
        });
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an attendance template, which archives it and keeps its sheets",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteTemplate(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, attendanceService::findActiveTemplateById, AttendanceTemplate::stationId);
        if (attendanceService.archiveTemplate(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw AttendanceRefusal.ATTENDANCE_TEMPLATE_NOT_HERE_TO_DELETE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/{templateId}/groups",
            methods = HttpMethod.PUT,
            summary = "Set groups for an attendance template (replace all)",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "templateId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetTemplateGroupsRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TemplateGroupEntry[].class)))
    private void setTemplateGroups(Context ctx) {
        int templateId = pathInt(ctx, "templateId");
        requireOwnedOrNotFound(
                ctx, templateId, attendanceService::findActiveTemplateById, AttendanceTemplate::stationId);
        var request = ctx.bodyAsClass(SetTemplateGroupsRequest.class);
        var groups = request.groups() != null
                ? request.groups().stream()
                        .map(g -> new TemplateGroup(g.groupId(), g.position()))
                        .toList()
                : List.<TemplateGroup>of();
        attendanceService.setTemplateGroups(templateId, groups);
        var result = attendanceService.findTemplateGroups(templateId).stream()
                .map(g -> new TemplateGroupEntry(g.groupId(), g.position()))
                .toList();
        ctx.json(result);
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/{templateId}/user-types",
            methods = HttpMethod.PUT,
            summary = "Set the user types an attendance template expects besides its groups (replace all)",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "templateId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetTemplateUserTypesRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationUserType[].class)))
    private void setTemplateUserTypes(Context ctx) {
        int templateId = pathInt(ctx, "templateId");
        requireOwnedOrNotFound(
                ctx, templateId, attendanceService::findActiveTemplateById, AttendanceTemplate::stationId);
        var request = ctx.bodyAsClass(SetTemplateUserTypesRequest.class);
        ctx.json(attendanceService.setTemplateUserTypes(templateId, request.userTypes()));
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/{templateId}/fields",
            methods = HttpMethod.GET,
            summary = "List fields of an attendance template",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "templateId", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceTemplateField[].class)))
    private void listTemplateFields(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int templateId = pathInt(ctx, "templateId");
        verifyTemplateOwnership(templateId, session);
        ctx.json(attendanceService.findTemplateFields(templateId));
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/{templateId}/fields",
            methods = HttpMethod.POST,
            summary = "Create a template field",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "templateId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TemplateFieldRequest.class)),
            responses =
                    @OpenApiResponse(status = "201", content = @OpenApiContent(from = AttendanceTemplateField[].class)))
    private void createTemplateField(Context ctx) {
        int templateId = pathInt(ctx, "templateId");
        requireOwnedOrNotFound(
                ctx, templateId, attendanceService::findActiveTemplateById, AttendanceTemplate::stationId);
        var request = ctx.bodyAsClass(TemplateFieldRequest.class);
        if (isBlank(request.name()) || request.fieldType() == null) {
            throw AttendanceRefusal.ATTENDANCE_FIELD_DETAILS_MISSING.raise();
        }
        ctx.status(HttpStatus.CREATED)
                .json(attendanceService.createTemplateField(
                        templateId, request.name(), request.fieldType(), request.config(), request.position()));
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/{templateId}/fields/{fieldId}",
            methods = HttpMethod.PUT,
            summary = "Update a template field",
            tags = {"Attendance"},
            pathParams = {
                @OpenApiParam(name = "templateId", type = Integer.class, required = true),
                @OpenApiParam(name = "fieldId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TemplateFieldRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceTemplateField[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateTemplateField(Context ctx) {
        int templateId = pathInt(ctx, "templateId");
        int fieldId = pathInt(ctx, "fieldId");
        requireOwnedOrNotFound(
                ctx, templateId, attendanceService::findActiveTemplateById, AttendanceTemplate::stationId);
        var request = ctx.bodyAsClass(TemplateFieldRequest.class);
        if (isBlank(request.name()) || request.fieldType() == null) {
            throw AttendanceRefusal.ATTENDANCE_FIELD_CHANGE_DETAILS_MISSING.raise();
        }
        attendanceService
                .updateTemplateField(
                        templateId, fieldId, request.name(), request.fieldType(), request.config(), request.position())
                .ifPresentOrElse(ctx::json, () -> {
                    throw AttendanceRefusal.ATTENDANCE_FIELD_NOT_HERE_TO_CHANGE.raise();
                });
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/{templateId}/fields/{fieldId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a template field, which archives it and keeps its answers on existing sheets",
            tags = {"Attendance"},
            pathParams = {
                @OpenApiParam(name = "templateId", type = Integer.class, required = true),
                @OpenApiParam(name = "fieldId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceTemplateField[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteTemplateField(Context ctx) {
        int templateId = pathInt(ctx, "templateId");
        int fieldId = pathInt(ctx, "fieldId");
        requireOwnedOrNotFound(
                ctx, templateId, attendanceService::findActiveTemplateById, AttendanceTemplate::stationId);
        attendanceService.archiveTemplateField(templateId, fieldId).ifPresentOrElse(ctx::json, () -> {
            throw AttendanceRefusal.ATTENDANCE_FIELD_NOT_HERE_TO_DELETE.raise();
        });
    }

    @OpenApi(
            path = "/api/v1/attendance/sessions",
            methods = HttpMethod.GET,
            summary = "List the station's sessions with their counts",
            tags = {"Attendance"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SessionSummary[].class)))
    private void listSessionSummaries(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(attendanceService.findSessionSummaries(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/{templateId}/sessions",
            methods = HttpMethod.GET,
            summary = "List sessions of a template",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "templateId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceSession[].class)))
    private void listSessions(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int templateId = pathInt(ctx, "templateId");
        verifyTemplateOwnership(templateId, session);
        ctx.json(attendanceService.findSessionsByTemplate(templateId));
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/{templateId}/sessions",
            methods = HttpMethod.POST,
            summary = "Create an attendance session",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "templateId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SessionRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = AttendanceSession.class)))
    private void createSession(Context ctx) {
        int templateId = pathInt(ctx, "templateId");
        requireOwnedOrNotFound(ctx, templateId, attendanceService::findTemplateById, AttendanceTemplate::stationId);
        var request = ctx.bodyAsClass(SessionRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(attendanceService.createSession(
                        templateId,
                        request.startTime(),
                        request.endTime(),
                        request.eventId(),
                        request.title(),
                        request.countedMinutes(),
                        request.audience(),
                        request.eventDate()));
    }

    /**
     * The sheet an appointment has on one of its days, so a page showing that day can offer to open
     * it rather than to take it again. A day nobody has taken yet answers with no content.
     */
    @OpenApi(
            path = "/api/v1/attendance/events/{eventId}/session",
            methods = HttpMethod.GET,
            summary = "The attendance sheet of an appointment on one day",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            queryParams =
                    @OpenApiParam(name = "date", description = "The day to look on, the station's today by default"),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceSession.class)),
                @OpenApiResponse(status = "204")
            })
    private void getSessionForEvent(Context ctx) {
        StationSession userSession = StationSession.from(ctx);
        int eventId = pathInt(ctx, "eventId");
        String date = ctx.queryParam("date");
        LocalDate day;
        try {
            day = date == null || date.isBlank() ? null : LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw AttendanceRefusal.ATTENDANCE_DAY_NOT_A_DATE.raise(date);
        }
        var found = attendanceService.findSessionForEvent(eventId, day);
        if (found.isEmpty()) {
            ctx.status(204);
            return;
        }
        verifySessionOwnership(found.get().id(), userSession);
        ctx.json(found.get());
    }

    @OpenApi(
            path = "/api/v1/attendance/sessions/{id}",
            methods = HttpMethod.GET,
            summary = "Get an attendance session with its fields and entries",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = SessionDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getSession(Context ctx) {
        StationSession userSession = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifySessionOwnership(id, userSession);
        attendanceService
                .findSessionById(id)
                .ifPresentOrElse(
                        session -> {
                            var fields = attendanceService.findSessionFields(id);
                            var entries = attendanceService.findEntries(id);
                            ctx.json(new SessionDetail(
                                    session,
                                    attendanceService.findSheetFields(id),
                                    fields,
                                    entries,
                                    !attendanceService.isSessionOpen(id),
                                    attendanceService.audienceOf(session)));
                        },
                        () -> {
                            throw AttendanceRefusal.ATTENDANCE_SHEET_GONE_WHILE_READ.raise();
                        });
    }

    @OpenApi(
            path = "/api/v1/attendance/sessions/{id}/member-notes",
            methods = HttpMethod.GET,
            summary = "What is outstanding for the members on this sheet",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = MemberCheckNotesService.MemberNotes[].class)))
    private void listMemberNotes(Context ctx) {
        StationSession userSession = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifySessionOwnership(id, userSession);
        ctx.json(memberCheckNotesService
                .findForStation(userSession.stationId(), userSession.user().permissions())
                .values());
    }

    /**
     * Reopens a closed sheet. The one thing an ordinary taker may not do, because the point of
     * closing is that the appointment stops being everybody's to change.
     */
    @OpenApi(
            path = "/api/v1/attendance/sessions/{id}/unlock",
            methods = HttpMethod.POST,
            summary = "Reopen a closed attendance session",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceSession.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void unlockSession(Context ctx) {
        StationSession userSession = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifySessionOwnership(id, userSession);
        attendanceService.unlockSession(id).ifPresentOrElse(ctx::json, () -> {
            throw AttendanceRefusal.ATTENDANCE_SHEET_NOT_HERE_TO_REOPEN.raise();
        });
    }

    @OpenApi(
            path = "/api/v1/attendance/sessions/{id}/lock",
            methods = HttpMethod.POST,
            summary = "Close an attendance session",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceSession.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void lockSession(Context ctx) {
        StationSession userSession = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifySessionOwnership(id, userSession);
        attendanceService.lockSession(id).ifPresentOrElse(ctx::json, () -> {
            throw AttendanceRefusal.ATTENDANCE_SHEET_NOT_HERE_TO_CLOSE.raise();
        });
    }

    @OpenApi(
            path = "/api/v1/attendance/sessions/{id}",
            methods = HttpMethod.PUT,
            summary = "Update an attendance session",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SessionRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceSession.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateSession(Context ctx) {
        int id = pathInt(ctx, "id");
        verifySessionOwnership(id, StationSession.from(ctx));
        var request = ctx.bodyAsClass(SessionRequest.class);
        attendanceService
                .updateSession(id, request.startTime(), request.endTime(), request.title(), request.countedMinutes())
                .ifPresentOrElse(ctx::json, () -> {
                    throw AttendanceRefusal.ATTENDANCE_SHEET_NOT_HERE_TO_CHANGE.raise();
                });
    }

    /**
     * Throws a sheet away. Whoever may take an attendance may do so: a sheet opened for the wrong
     * appointment is a mistake made while taking it, undone by the same person on the spot.
     */
    @OpenApi(
            path = "/api/v1/attendance/sessions/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an attendance session",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteSession(Context ctx) {
        int id = pathInt(ctx, "id");
        verifySessionOwnership(id, StationSession.from(ctx));
        if (attendanceService.deleteSession(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw AttendanceRefusal.ATTENDANCE_SHEET_NOT_HERE_TO_DELETE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/attendance/sessions/{sessionId}/fields",
            methods = HttpMethod.GET,
            summary = "Get session field values",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "sessionId", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceSessionField[].class)))
    private void listSessionFields(Context ctx) {
        StationSession userSession = StationSession.from(ctx);
        int sessionId = pathInt(ctx, "sessionId");
        verifySessionOwnership(sessionId, userSession);
        ctx.json(attendanceService.findSessionFields(sessionId));
    }

    @OpenApi(
            path = "/api/v1/attendance/sessions/{sessionId}/fields",
            methods = HttpMethod.PUT,
            summary = "Set session field values (batch upsert)",
            description = "Provide all field values for the session. Each entry is upserted.",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "sessionId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetSessionFieldsRequest.class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceSessionField[].class)))
    private void setSessionFields(Context ctx) {
        int sessionId = pathInt(ctx, "sessionId");
        verifySessionOwnership(sessionId, StationSession.from(ctx));
        var request = ctx.bodyAsClass(SetSessionFieldsRequest.class);
        List<AttendanceFieldValueEntry> entries = request.fields() != null ? request.fields() : List.of();
        ctx.json(attendanceService.setSessionFields(sessionId, entries));
    }

    @OpenApi(
            path = "/api/v1/attendance/sessions/{sessionId}/entries",
            methods = HttpMethod.GET,
            summary = "List entries for a session",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "sessionId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceEntry[].class)))
    private void listEntries(Context ctx) {
        StationSession userSession = StationSession.from(ctx);
        int sessionId = pathInt(ctx, "sessionId");
        verifySessionOwnership(sessionId, userSession);
        ctx.json(attendanceService.findEntries(sessionId));
    }

    @OpenApi(
            path = "/api/v1/attendance/sessions/{sessionId}/entries",
            methods = HttpMethod.POST,
            summary = "Create an attendance entry for a member",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "sessionId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateEntryRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = AttendanceEntry[].class)))
    private void createEntry(Context ctx) {
        int sessionId = pathInt(ctx, "sessionId");
        var session = StationSession.from(ctx);
        verifySessionOwnership(sessionId, session);
        var request = ctx.bodyAsClass(CreateEntryRequest.class);
        if (request.memberId() == null) {
            throw AttendanceRefusal.ATTENDANCE_ENTRY_NAMES_NO_MEMBER.raise();
        }
        verifyMemberInStation(request.memberId(), session);
        var source = request.source() != null ? request.source() : AttendanceEntry.EntrySource.EXTRA;
        ctx.status(HttpStatus.CREATED).json(attendanceService.createEntry(sessionId, request.memberId(), source));
    }

    @OpenApi(
            path = "/api/v1/attendance/entries/{id}/check-in",
            methods = HttpMethod.POST,
            summary = "Record check-in time for an entry",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TimestampRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TimestampResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void checkIn(Context ctx) {
        var entryTime = resolveEntryTime(ctx);
        if (attendanceService.checkIn(entryTime.id(), entryTime.time())) {
            ctx.json(new TimestampResponse(entryTime.id(), entryTime.time()));
        } else {
            throw AttendanceRefusal.ATTENDANCE_CHECK_IN_ENTRY_NOT_HERE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/attendance/entries/{id}/check-out",
            methods = HttpMethod.POST,
            summary = "Record check-out time for an entry",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TimestampRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TimestampResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void checkOut(Context ctx) {
        var entryTime = resolveEntryTime(ctx);
        if (attendanceService.checkOut(entryTime.id(), entryTime.time())) {
            ctx.json(new TimestampResponse(entryTime.id(), entryTime.time()));
        } else {
            throw AttendanceRefusal.ATTENDANCE_CHECK_OUT_ENTRY_NOT_HERE.raise();
        }
    }

    /**
     * Resolves the owned entry id and the effective timestamp shared by check-in and check-out.
     */
    private EntryTime resolveEntryTime(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyEntryOwnership(id, session);
        var request = ctx.bodyAsClass(TimestampRequest.class);
        Instant time = request.time() != null ? request.time() : Instant.now();
        return new EntryTime(id, time);
    }

    @OpenApi(
            path = "/api/v1/attendance/entries/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an attendance entry",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteEntry(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyEntryOwnership(id, session);
        if (attendanceService.deleteEntry(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw AttendanceRefusal.ATTENDANCE_ENTRY_NOT_HERE_TO_DELETE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/attendance/entries/{id}/status",
            methods = HttpMethod.PUT,
            summary = "Update the status of an attendance entry",
            description = "Set the entry status to 'present' or 'absent'.",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StatusRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = StatusResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateEntryStatus(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyEntryOwnership(id, session);
        var request = ctx.bodyAsClass(StatusRequest.class);
        AttendanceEntry.AttendanceStatus status = request.status();
        if (status == null) {
            throw AttendanceRefusal.ATTENDANCE_STATUS_NOT_GIVEN.raise();
        }
        if (attendanceService.updateEntryStatus(id, status)) {
            ctx.json(new StatusResponse(id, status));
        } else {
            throw AttendanceRefusal.ATTENDANCE_STATUS_ENTRY_NOT_HERE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/attendance/entries/{id}/reset-times",
            methods = HttpMethod.POST,
            summary = "Reset check-in and check-out times for an entry",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void resetTimes(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        verifyEntryOwnership(id, session);
        if (attendanceService.resetTimes(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw AttendanceRefusal.ATTENDANCE_RESET_TIMES_ENTRY_NOT_HERE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/attendance/sessions/{sessionId}/sync-event",
            methods = HttpMethod.POST,
            summary = "Sync attendance entries from a linked event",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "sessionId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceEntry[].class)))
    private void syncFromEvent(Context ctx) {
        int sessionId = pathInt(ctx, "sessionId");
        verifySessionOwnership(sessionId, StationSession.from(ctx));
        ctx.json(attendanceService.syncFromEvent(sessionId));
    }

    @OpenApi(
            path = "/api/v1/attendance/sessions/{sessionId}/export",
            methods = HttpMethod.GET,
            summary = "Export an attendance session as PDF",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "sessionId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void exportPdf(Context ctx) {
        int sessionId = pathInt(ctx, "sessionId");
        StationSession session = StationSession.from(ctx);
        verifySessionOwnership(sessionId, session);
        String generatedBy = NameParts.of(session.user().account()).official();
        var pdf = exportService.exportSessionPdf(sessionId, generatedBy, sheetOptions(ctx));
        if (pdf.isEmpty()) {
            throw AttendanceRefusal.ATTENDANCE_SHEET_PDF_NOT_MADE.raise();
        }
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", pdf.get().contentDisposition());
        ctx.result(pdf.get().bytes());
    }

    /**
     * How the sheet was asked for, read off the address.
     *
     * <p>They travel in the query rather than in a body because the browser downloads the answer
     * itself, and an address with nothing in it is the export the product has always produced.
     */
    private AttendanceExportService.SheetOptions sheetOptions(Context ctx) {
        boolean signature = "true".equals(ctx.queryParam("signature"));
        String title = ctx.queryParam("title");
        int blankRows = ctx.queryParamAsClass("blankRows", Integer.class).getOrDefault(0);
        String instanceUrl = ctx.queryParam("instanceUrl");
        return new AttendanceExportService.SheetOptions(
                signature, title, blankRows, instanceUrl == null ? null : "true".equals(instanceUrl));
    }

    @OpenApi(
            path = "/api/v1/attendance/report/preview",
            methods = HttpMethod.GET,
            summary = "Preview an attendance report",
            tags = {"Attendance"},
            queryParams = {
                @OpenApiParam(name = "userTypes"),
                @OpenApiParam(name = "groupIds"),
                @OpenApiParam(name = "from", required = true),
                @OpenApiParam(name = "to", required = true),
                @OpenApiParam(name = "rounding")
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ReportData.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void reportPreview(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var query = parseReportQuery(ctx);
        ctx.json(reportService.buildReport(
                session.stationId(), query.userTypes(), query.groupIds(), query.from(), query.to(), query.rounding()));
    }

    /**
     * Parses and validates the shared report filter query parameters used by preview and export.
     */
    private ReportQuery parseReportQuery(Context ctx) {
        var userTypes = ctx.queryParams("userTypes").stream()
                .filter(s -> !s.isBlank())
                .map(StationUserType::valueOf)
                .toList();
        var groupIds = ctx.queryParams("groupIds").stream()
                .filter(s -> !s.isBlank())
                .map(Integer::parseInt)
                .toList();
        String fromStr = ctx.queryParam("from");
        String toStr = ctx.queryParam("to");
        String rounding = ctx.queryParamAsClass("rounding", String.class).getOrDefault("exact");
        if (fromStr == null || toStr == null) {
            throw AttendanceRefusal.ATTENDANCE_REPORT_SPAN_MISSING.raise();
        }
        if (userTypes.isEmpty() && groupIds.isEmpty()) {
            throw AttendanceRefusal.ATTENDANCE_REPORT_AUDIENCE_MISSING.raise();
        }
        Instant from = Instant.parse(fromStr);
        Instant to = Instant.parse(toStr);
        return new ReportQuery(userTypes, groupIds, from, to, rounding);
    }

    @OpenApi(
            path = "/api/v1/attendance/report/export.csv",
            methods = HttpMethod.GET,
            summary = "Export an attendance report as CSV",
            tags = {"Attendance"},
            responses = @OpenApiResponse(status = "200"))
    private void reportExportCsv(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var query = parseReportQuery(ctx);
        String period = ctx.queryParamAsClass("period", String.class).getOrDefault("month");
        var csv = reportService.exportReportCsv(
                session.stationId(),
                query.userTypes(),
                query.groupIds(),
                query.from(),
                query.to(),
                query.rounding(),
                period,
                CsvWriter.Separator.of(ctx.queryParam("separator")));
        if (csv.isEmpty()) {
            throw AttendanceRefusal.ATTENDANCE_REPORT_TABLE_EMPTY.raise();
        }
        ctx.contentType("text/csv");
        ctx.header("Content-Disposition", csv.get().contentDisposition());
        ctx.result(csv.get().bytes());
    }

    @OpenApi(
            path = "/api/v1/attendance/report/export",
            methods = HttpMethod.GET,
            summary = "Export an attendance report as PDF",
            tags = {"Attendance"},
            queryParams = {
                @OpenApiParam(name = "userTypes"),
                @OpenApiParam(name = "groupIds"),
                @OpenApiParam(name = "from", required = true),
                @OpenApiParam(name = "to", required = true),
                @OpenApiParam(name = "rounding"),
                @OpenApiParam(name = "period")
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void reportExport(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var query = parseReportQuery(ctx);
        String generatedBy = NameParts.of(session.user().account()).official();
        String period = ctx.queryParamAsClass("period", String.class).getOrDefault("month");
        var pdf = reportService.exportReportPdf(
                session.stationId(),
                query.userTypes(),
                query.groupIds(),
                query.from(),
                query.to(),
                query.rounding(),
                generatedBy,
                period);
        if (pdf.isEmpty()) {
            throw AttendanceRefusal.ATTENDANCE_REPORT_PDF_EMPTY.raise();
        }
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", pdf.get().contentDisposition());
        ctx.result(pdf.get().bytes());
    }

    @OpenApi(
            path = "/api/v1/attendance/report/presets",
            methods = HttpMethod.GET,
            summary = "List saved report presets",
            tags = {"Attendance"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceReportPreset[].class)))
    private void listPresets(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(reportService.findPresets(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/attendance/report/presets",
            methods = HttpMethod.POST,
            summary = "Create a report preset",
            tags = {"Attendance"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreatePresetRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = AttendanceReportPreset.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createPreset(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(CreatePresetRequest.class);
        if (isBlank(request.name())) {
            throw AttendanceRefusal.ATTENDANCE_REPORT_PRESET_NEEDS_A_NAME.raise();
        }
        ctx.status(HttpStatus.CREATED)
                .json(reportService.createPreset(
                        session.stationId(),
                        request.name(),
                        request.userTypes(),
                        request.groupIds(),
                        request.period(),
                        request.rounding()));
    }

    @OpenApi(
            path = "/api/v1/attendance/report/presets/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a report preset",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deletePreset(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        if (reportService.findPresets(session.stationId()).stream().noneMatch(p -> p.id() == id)) {
            throw AttendanceRefusal.ATTENDANCE_REPORT_PRESET_NOT_YOURS.raise();
        }
        if (reportService.deletePreset(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw AttendanceRefusal.ATTENDANCE_REPORT_PRESET_NOT_HERE_TO_DELETE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/attendance/absences",
            methods = HttpMethod.GET,
            summary = "List active absences for the current station",
            tags = {"Attendance"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberAbsence[].class)))
    private void listActiveAbsences(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(attendanceService.findActiveAbsencesByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/attendance/absences/member/{memberId}",
            methods = HttpMethod.GET,
            summary = "List absences for a specific member",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberAbsence[].class)))
    private void listMemberAbsences(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int memberId = pathInt(ctx, "memberId");
        verifyMemberInStation(memberId, session);
        ctx.json(attendanceService.findAbsencesByMember(memberId));
    }

    @OpenApi(
            path = "/api/v1/attendance/absences",
            methods = HttpMethod.POST,
            summary = "Mark a member as absent until a date",
            description =
                    "Creates an absence record. The member will automatically be marked as absent on all attendance sessions until the given date.",
            tags = {"Attendance"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AbsenceRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = MemberAbsence.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createAbsence(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(AbsenceRequest.class);
        if (request.memberId() == null) {
            throw AttendanceRefusal.ABSENCE_MEMBER_NOT_NAMED.raise();
        }
        verifyMemberInStation(request.memberId(), session);
        if (request.absentFrom() == null || request.absentUntil() == null) {
            throw AttendanceRefusal.ABSENCE_SPAN_MISSING.raise();
        }
        if (request.absentUntil().isBefore(request.absentFrom())) {
            throw AttendanceRefusal.ABSENCE_ENDS_BEFORE_IT_STARTS.raise();
        }
        ctx.status(HttpStatus.CREATED)
                .json(attendanceService.createAbsence(
                        request.memberId(), request.absentFrom(), request.absentUntil(), request.reason(), null));
    }

    @OpenApi(
            path = "/api/v1/attendance/absences/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an absence record",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteAbsence(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        var absence = attendanceService.findAbsenceById(id).orElseThrow(AttendanceRefusal.ABSENCE_NOT_HERE::raise);
        verifyMemberInStation(absence.memberId(), session);
        if (attendanceService.deleteAbsence(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw AttendanceRefusal.ABSENCE_NOT_HERE_TO_DELETE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/profile/absences",
            methods = HttpMethod.GET,
            summary = "List own and managed members' absences",
            tags = {"Attendance"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AbsenceResponse[].class)))
    private void listMyAbsences(Context ctx) {
        var atStation = StationSession.optional(UserSession.from(ctx));
        if (atStation.isEmpty()) {
            ctx.json(Collections.emptyList());
            return;
        }
        StationSession session = atStation.get();
        var absences = new ArrayList<>(
                attendanceService.findAbsencesByMember(session.member().id()));
        if (session.hasPermission(StationPermission.MEMBER_GUARDIAN)) {
            for (int mid :
                    attendanceService.findManagedMemberIds(session.member().id())) {
                absences.addAll(attendanceService.findAbsencesByMember(mid));
            }
        }
        ctx.json(absences.stream().map(this::toAbsenceResponse).toList());
    }

    @OpenApi(
            path = "/api/v1/profile/absences",
            methods = HttpMethod.POST,
            summary = "Create an absence for yourself or managed members",
            tags = {"Attendance"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MyAbsenceRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = AbsenceResponse[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createMyAbsence(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var req = ctx.bodyAsClass(MyAbsenceRequest.class);
        if (req.absentFrom() == null || req.absentUntil() == null) {
            throw AttendanceRefusal.MY_ABSENCE_SPAN_MISSING.raise();
        }
        LocalDate from = req.absentFrom();
        LocalDate until = req.absentUntil();
        if (until.isBefore(from)) {
            throw AttendanceRefusal.MY_ABSENCE_ENDS_BEFORE_IT_STARTS.raise();
        }

        var memberIds = new ArrayList<Integer>();
        List<Integer> requestedMemberIds = req.memberIds();
        if (requestedMemberIds != null && !requestedMemberIds.isEmpty()) {
            var managed = session.hasPermission(StationPermission.MEMBER_GUARDIAN)
                    ? attendanceService.findManagedMemberIds(session.member().id())
                    : Set.<Integer>of();
            for (int mid : requestedMemberIds) {
                if (mid == session.member().id() || managed.contains(mid)) {
                    memberIds.add(mid);
                } else {
                    throw AttendanceRefusal.ABSENCE_MEMBER_NOT_YOURS.raise();
                }
            }
        } else {
            memberIds.add(session.member().id());
        }

        var created = new ArrayList<AbsenceResponse>();
        for (int mid : memberIds) {
            Integer createdBy = mid != session.member().id() ? session.member().id() : null;
            created.add(toAbsenceResponse(attendanceService.createAbsence(mid, from, until, req.reason(), createdBy)));
        }
        ctx.status(HttpStatus.CREATED).json(created);
    }

    @OpenApi(
            path = "/api/v1/profile/absences/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an absence for yourself or managed members",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteMyAbsence(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        var absence = attendanceService.findAbsenceById(id);
        if (absence.isEmpty()) {
            throw AttendanceRefusal.MY_ABSENCE_NOT_HERE.raise();
        }
        int absMemberId = absence.get().memberId();
        boolean isOwn = session.member().id() == absMemberId;
        boolean manages = session.hasPermission(StationPermission.MEMBER_GUARDIAN)
                && attendanceService.findManagedMemberIds(session.member().id()).contains(absMemberId);
        if (!isOwn && !manages) {
            throw AttendanceRefusal.MY_ABSENCE_NOT_YOURS_TO_DELETE.raise();
        }
        if (!attendanceService.deleteAbsence(id)) {
            throw AttendanceRefusal.MY_ABSENCE_NOT_HERE_TO_DELETE.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Request body for creating or updating an attendance template.
     */
    public record TemplateRequest(String name) {}

    /**
     * Detailed template response including fields, group associations and user types.
     *
     * @param userTypes the user types whose members the template's sheets expect besides the members
     *     of its groups
     */
    public record TemplateDetail(
            int id,
            int stationId,
            String name,
            List<AttendanceTemplateField> fields,
            List<TemplateGroupEntry> groups,
            Set<StationUserType> userTypes) {}

    /**
     * Request body for replacing the user types of a template.
     *
     * @param userTypes the user types to expect, empty to expect nobody by type
     */
    public record SetTemplateUserTypesRequest(List<StationUserType> userTypes) {}

    /**
     * A group association entry with position for ordering.
     */
    public record TemplateGroupEntry(int groupId, int position) {}

    /**
     * Request body for replacing all group associations of a template.
     */
    public record SetTemplateGroupsRequest(List<TemplateGroupEntry> groups) {}

    /**
     * Request body for creating or updating a template field.
     */
    public record TemplateFieldRequest(String name, FieldType fieldType, AttendanceFieldConfig config, int position) {}

    /**
     * Request body for creating or updating an attendance session.
     *
     * @param countedMinutes what a whole presence at the sheet counts as when hours are added up,
     *     null where the sheet's own times decide
     * @param audience whom to enter on this one sheet, kept with it; null where the template's own
     *     user types and groups decide
     * @param eventDate which day of a repeating appointment this sheet is for, null where the sheet
     *     stands on its own or the times are given outright
     */
    public record SessionRequest(
            Instant startTime,
            Instant endTime,
            @Nullable Integer eventId,
            @Nullable String title,
            @Nullable Integer countedMinutes,
            @Nullable SessionAudience audience,
            @Nullable LocalDate eventDate) {}

    /**
     * Detailed session response including fields and attendance entries.
     *
     * @param templateFields the fields the sheet shows: its template's fields in use, and the deleted
     *     ones it answered before they went, so the answer still reads under its field's name
     * @param fields the sheet's answers, by field
     * @param locked whether the sheet refuses writes, decided here so the rule and the configured
     *     span are not written down a second time in the browser
     * @param audience whom the sheet expects: what it was started with, or its template's user types
     *     and groups where it was started with nothing
     */
    public record SessionDetail(
            AttendanceSession session,
            List<AttendanceTemplateField> templateFields,
            List<AttendanceSessionField> fields,
            List<AttendanceEntry> entries,
            boolean locked,
            SessionAudience audience) {}

    /**
     * Request body for batch-upserting session field values.
     */
    public record SetSessionFieldsRequest(List<AttendanceFieldValueEntry> fields) {}

    /**
     * Request body for creating an attendance entry.
     */
    public record CreateEntryRequest(Integer memberId, AttendanceEntry.EntrySource source) {}

    /**
     * Request body for check-in or check-out with an optional timestamp.
     */
    public record TimestampRequest(Instant time) {}

    /**
     * The owned entry id and effective timestamp resolved for a check-in or check-out.
     */
    private record EntryTime(int id, Instant time) {}

    /**
     * Parsed and validated report filter query parameters shared by preview and export.
     */
    private record ReportQuery(
            List<StationUserType> userTypes, List<Integer> groupIds, Instant from, Instant to, String rounding) {}

    /**
     * Response confirming a check-in or check-out timestamp was recorded.
     */
    public record TimestampResponse(int entryId, Instant time) {}

    /**
     * Request body for updating an attendance entry's status.
     */
    public record StatusRequest(AttendanceEntry.AttendanceStatus status) {}

    /**
     * Response confirming an attendance entry's status was updated.
     */
    public record StatusResponse(int entryId, AttendanceEntry.AttendanceStatus status) {}

    /**
     * Request body for creating an absence by a manager.
     */
    public record AbsenceRequest(
            Integer memberId,
            LocalDate absentFrom,
            LocalDate absentUntil,
            @Nullable String reason) {}

    /**
     * Request body for self-service absence creation, optionally targeting managed members.
     */
    public record MyAbsenceRequest(
            LocalDate absentFrom,
            LocalDate absentUntil,
            @Nullable String reason,
            @Nullable List<Integer> memberIds) {}

    /**
     * Request body for creating a report preset. An unknown user type is refused as a bad request.
     */
    public record CreatePresetRequest(
            String name, List<StationUserType> userTypes, List<Integer> groupIds, String period, String rounding) {}

    /**
     * Absence response enriched with the creator's display name.
     */
    public record AbsenceResponse(
            int id,
            int memberId,
            LocalDate absentFrom,
            LocalDate absentUntil,
            @Nullable String reason,
            Instant createdAt,
            @Nullable String createdByName,
            @Nullable MemberIdentity memberIdentity) {}
}
