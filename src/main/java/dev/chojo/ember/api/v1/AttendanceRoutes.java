/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.v1;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.entity.AttendanceEntry;
import dev.chojo.ember.entity.AttendanceReportPreset;
import dev.chojo.ember.entity.AttendanceSession;
import dev.chojo.ember.entity.AttendanceSessionField;
import dev.chojo.ember.entity.AttendanceTemplate;
import dev.chojo.ember.entity.AttendanceTemplateField;
import dev.chojo.ember.entity.MemberAbsence;
import dev.chojo.ember.repository.AttendanceRepository.TemplateGroup;
import dev.chojo.ember.service.AttendanceExportService;
import dev.chojo.ember.service.AttendanceReportService;
import dev.chojo.ember.service.AttendanceReportService.ReportData;
import dev.chojo.ember.service.AttendanceService;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Singleton
public class AttendanceRoutes implements Routes {
    private final AttendanceService attendanceService;
    private final AttendanceExportService exportService;
    private final AttendanceReportService reportService;

    @Inject
    public AttendanceRoutes(
            AttendanceService attendanceService,
            AttendanceExportService exportService,
            AttendanceReportService reportService) {
        this.attendanceService = attendanceService;
        this.exportService = exportService;
        this.reportService = reportService;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // -- Templates --

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/attendance/templates", this::listTemplates, Roles.ATTENDENCE_MANAGEMENT);
        routes.post(prefix + "/attendance/templates", this::createTemplate, Roles.ATTENDENCE_MANAGEMENT);
        routes.get(prefix + "/attendance/templates/{id}", this::getTemplate, Roles.ATTENDENCE_MANAGEMENT);
        routes.put(prefix + "/attendance/templates/{id}", this::updateTemplate, Roles.ATTENDENCE_MANAGEMENT);
        routes.delete(prefix + "/attendance/templates/{id}", this::deleteTemplate, Roles.ATTENDENCE_MANAGEMENT);

        routes.put(
                prefix + "/attendance/templates/{templateId}/groups",
                this::setTemplateGroups,
                Roles.ATTENDENCE_MANAGEMENT);

        routes.get(
                prefix + "/attendance/templates/{templateId}/fields",
                this::listTemplateFields,
                Roles.ATTENDENCE_MANAGEMENT);
        routes.post(
                prefix + "/attendance/templates/{templateId}/fields",
                this::createTemplateField,
                Roles.ATTENDENCE_MANAGEMENT);
        routes.put(
                prefix + "/attendance/templates/{templateId}/fields/{fieldId}",
                this::updateTemplateField,
                Roles.ATTENDENCE_MANAGEMENT);
        routes.delete(
                prefix + "/attendance/templates/{templateId}/fields/{fieldId}",
                this::deleteTemplateField,
                Roles.ATTENDENCE_MANAGEMENT);

        routes.get(prefix + "/attendance/sessions", this::listSessionSummaries, Roles.ATTENDENCE_MANAGEMENT);
        routes.get(
                prefix + "/attendance/templates/{templateId}/sessions",
                this::listSessions,
                Roles.ATTENDENCE_MANAGEMENT);
        routes.post(
                prefix + "/attendance/templates/{templateId}/sessions",
                this::createSession,
                Roles.ATTENDENCE_MANAGEMENT);
        routes.get(prefix + "/attendance/sessions/{id}", this::getSession, Roles.ATTENDENCE_MANAGEMENT);
        routes.put(prefix + "/attendance/sessions/{id}", this::updateSession, Roles.ATTENDENCE_MANAGEMENT);
        routes.delete(prefix + "/attendance/sessions/{id}", this::deleteSession, Roles.ATTENDENCE_MANAGEMENT);

        routes.get(
                prefix + "/attendance/sessions/{sessionId}/fields",
                this::listSessionFields,
                Roles.ATTENDENCE_MANAGEMENT);
        routes.put(
                prefix + "/attendance/sessions/{sessionId}/fields",
                this::setSessionFields,
                Roles.ATTENDENCE_MANAGEMENT);

        routes.get(prefix + "/attendance/sessions/{sessionId}/entries", this::listEntries, Roles.ATTENDENCE_MANAGEMENT);
        routes.post(
                prefix + "/attendance/sessions/{sessionId}/entries", this::createEntry, Roles.ATTENDENCE_MANAGEMENT);
        routes.post(prefix + "/attendance/entries/{id}/check-in", this::checkIn, Roles.ATTENDENCE_MANAGEMENT);
        routes.post(prefix + "/attendance/entries/{id}/check-out", this::checkOut, Roles.ATTENDENCE_MANAGEMENT);
        routes.put(prefix + "/attendance/entries/{id}/status", this::updateEntryStatus, Roles.ATTENDENCE_MANAGEMENT);
        routes.post(prefix + "/attendance/entries/{id}/reset-times", this::resetTimes, Roles.ATTENDENCE_MANAGEMENT);
        routes.delete(prefix + "/attendance/entries/{id}", this::deleteEntry, Roles.ATTENDENCE_MANAGEMENT);
        routes.post(
                prefix + "/attendance/sessions/{sessionId}/sync-event",
                this::syncFromEvent,
                Roles.ATTENDENCE_MANAGEMENT);
        routes.get(prefix + "/attendance/sessions/{sessionId}/export", this::exportPdf, Roles.ATTENDENCE_MANAGEMENT);

        // Report export
        routes.get(prefix + "/attendance/report/preview", this::reportPreview, Roles.ATTENDENCE_EXPORT_MANAGER);
        routes.get(prefix + "/attendance/report/export", this::reportExport, Roles.ATTENDENCE_EXPORT_MANAGER);

        // Saved report presets
        routes.get(prefix + "/attendance/report/presets", this::listPresets, Roles.ATTENDENCE_EXPORT_MANAGER);
        routes.post(prefix + "/attendance/report/presets", this::createPreset, Roles.ATTENDENCE_EXPORT_MANAGER);
        routes.delete(prefix + "/attendance/report/presets/{id}", this::deletePreset, Roles.ATTENDENCE_EXPORT_MANAGER);

        routes.get(prefix + "/attendance/absences", this::listActiveAbsences, Roles.ATTENDENCE_MANAGEMENT);
        routes.get(
                prefix + "/attendance/absences/member/{memberId}",
                this::listMemberAbsences,
                Roles.ATTENDENCE_MANAGEMENT);
        routes.post(prefix + "/attendance/absences", this::createAbsence, Roles.ATTENDENCE_MANAGEMENT);
        routes.delete(prefix + "/attendance/absences/{id}", this::deleteAbsence, Roles.ATTENDENCE_MANAGEMENT);

        // Self-service absence management
        routes.get(prefix + "/profile/absences", this::listMyAbsences, Roles.USER);
        routes.post(prefix + "/profile/absences", this::createMyAbsence, Roles.USER);
        routes.delete(prefix + "/profile/absences/{id}", this::deleteMyAbsence, Roles.USER);
    }

    @OpenApi(
            path = "/api/v1/attendance/templates",
            methods = HttpMethod.GET,
            summary = "List attendance templates for the current station",
            tags = {"Attendance"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceTemplate[].class)))
    private void listTemplates(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(attendanceService.findTemplatesByStation(session.stationId()));
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
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(TemplateRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        attendanceService
                .findTemplateById(id)
                .ifPresentOrElse(
                        template -> {
                            var fields = attendanceService.findTemplateFields(id);
                            var groups = attendanceService.findTemplateGroups(id).stream()
                                    .map(g -> new TemplateGroupEntry(g.groupId(), g.position()))
                                    .toList();
                            ctx.json(new TemplateDetail(
                                    template.id(), template.stationId(), template.name(), fields, groups));
                        },
                        () -> {
                            throw new NotFoundResponse();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(TemplateRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        attendanceService.updateTemplate(id, request.name()).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    // -- Template Groups --

    @OpenApi(
            path = "/api/v1/attendance/templates/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an attendance template",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void deleteTemplate(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (attendanceService.deleteTemplate(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Template Fields --

    @OpenApi(
            path = "/api/v1/attendance/templates/{templateId}/groups",
            methods = HttpMethod.PUT,
            summary = "Set groups for an attendance template (replace all)",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "templateId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetTemplateGroupsRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TemplateGroupEntry[].class)))
    private void setTemplateGroups(Context ctx) {
        int templateId = ctx.pathParamAsClass("templateId", Integer.class).get();
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
            path = "/api/v1/attendance/templates/{templateId}/fields",
            methods = HttpMethod.GET,
            summary = "List fields of an attendance template",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "templateId", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceTemplateField[].class)))
    private void listTemplateFields(Context ctx) {
        int templateId = ctx.pathParamAsClass("templateId", Integer.class).get();
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
        int templateId = ctx.pathParamAsClass("templateId", Integer.class).get();
        var request = ctx.bodyAsClass(TemplateFieldRequest.class);
        if (isBlank(request.name()) || isBlank(request.fieldType())) {
            throw new BadRequestResponse("name and fieldType are required");
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
        int templateId = ctx.pathParamAsClass("templateId", Integer.class).get();
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        var request = ctx.bodyAsClass(TemplateFieldRequest.class);
        if (isBlank(request.name()) || isBlank(request.fieldType())) {
            throw new BadRequestResponse("name and fieldType are required");
        }
        attendanceService
                .updateTemplateField(
                        templateId, fieldId, request.name(), request.fieldType(), request.config(), request.position())
                .ifPresentOrElse(ctx::json, () -> {
                    throw new NotFoundResponse();
                });
    }

    // -- Sessions --

    @OpenApi(
            path = "/api/v1/attendance/templates/{templateId}/fields/{fieldId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a template field",
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
        int templateId = ctx.pathParamAsClass("templateId", Integer.class).get();
        int fieldId = ctx.pathParamAsClass("fieldId", Integer.class).get();
        attendanceService.deleteTemplateField(templateId, fieldId).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/{templateId}/sessions",
            methods = HttpMethod.GET,
            summary = "List sessions of a template",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "templateId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceSession[].class)))
    private void listSessionSummaries(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(attendanceService.findSessionSummaries(session.stationId()));
    }

    private void listSessions(Context ctx) {
        int templateId = ctx.pathParamAsClass("templateId", Integer.class).get();
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
        int templateId = ctx.pathParamAsClass("templateId", Integer.class).get();
        var request = ctx.bodyAsClass(SessionRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(attendanceService.createSession(
                        templateId, request.startTime(), request.endTime(), request.eventId(), request.title()));
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        attendanceService
                .findSessionById(id)
                .ifPresentOrElse(
                        session -> {
                            var fields = attendanceService.findSessionFields(id);
                            var entries = attendanceService.findEntries(id);
                            ctx.json(new SessionDetail(session, fields, entries));
                        },
                        () -> {
                            throw new NotFoundResponse();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(SessionRequest.class);
        attendanceService
                .updateSession(id, request.startTime(), request.endTime(), request.title())
                .ifPresentOrElse(ctx::json, () -> {
                    throw new NotFoundResponse();
                });
    }

    // -- Session Fields --

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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (attendanceService.deleteSession(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
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
        int sessionId = ctx.pathParamAsClass("sessionId", Integer.class).get();
        ctx.json(attendanceService.findSessionFields(sessionId));
    }

    // -- Entries --

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
        int sessionId = ctx.pathParamAsClass("sessionId", Integer.class).get();
        var request = ctx.bodyAsClass(SetSessionFieldsRequest.class);
        List<AttendanceService.FieldValueEntry> entries = request.fields() != null
                ? request.fields().stream()
                        .map(f -> new AttendanceService.FieldValueEntry(f.fieldId(), f.value()))
                        .toList()
                : List.of();
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
        int sessionId = ctx.pathParamAsClass("sessionId", Integer.class).get();
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
        int sessionId = ctx.pathParamAsClass("sessionId", Integer.class).get();
        var request = ctx.bodyAsClass(CreateEntryRequest.class);
        if (request.memberId() == null) {
            throw new BadRequestResponse("memberId is required");
        }
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(TimestampRequest.class);
        Instant time = request.time() != null ? request.time() : Instant.now();
        if (attendanceService.checkIn(id, time)) {
            ctx.json(new TimestampResponse(id, time));
        } else {
            throw new NotFoundResponse();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(TimestampRequest.class);
        Instant time = request.time() != null ? request.time() : Instant.now();
        if (attendanceService.checkOut(id, time)) {
            ctx.json(new TimestampResponse(id, time));
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Entry Status --

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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (attendanceService.deleteEntry(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(StatusRequest.class);
        AttendanceEntry.AttendanceStatus status;
        try {
            status = AttendanceEntry.AttendanceStatus.valueOf(request.status());
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("status must be UNCONFIRMED, PRESENT, ABSENT, or DECLINED");
        }
        if (attendanceService.updateEntryStatus(id, status)) {
            ctx.json(new StatusResponse(id, status.name()));
        } else {
            throw new NotFoundResponse();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (attendanceService.resetTimes(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
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
        int sessionId = ctx.pathParamAsClass("sessionId", Integer.class).get();
        ctx.json(attendanceService.syncFromEvent(sessionId));
    }

    // -- Report --

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
        int sessionId = ctx.pathParamAsClass("sessionId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        String generatedBy =
                (session.account().firstName() + " " + session.account().lastName()).trim();
        var pdf = exportService.exportSessionPdf(sessionId, generatedBy);
        if (pdf.isEmpty()) {
            throw new NotFoundResponse();
        }
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", "attachment; filename=\"attendance-" + sessionId + ".pdf\"");
        ctx.result(pdf.get());
    }

    @OpenApi(
            path = "/api/v1/attendance/report/preview",
            methods = HttpMethod.GET,
            summary = "Preview an attendance report",
            tags = {"Attendance"},
            queryParams = {
                @OpenApiParam(name = "role", type = String.class),
                @OpenApiParam(name = "groupId", type = Integer.class),
                @OpenApiParam(name = "from", type = String.class, required = true),
                @OpenApiParam(name = "to", type = String.class, required = true),
                @OpenApiParam(name = "rounding", type = String.class)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ReportData.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void reportPreview(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String role = ctx.queryParam("role");
        String groupIdStr = ctx.queryParam("groupId");
        Integer groupId = groupIdStr != null && !groupIdStr.isBlank() ? Integer.parseInt(groupIdStr) : null;
        String fromStr = ctx.queryParam("from");
        String toStr = ctx.queryParam("to");
        String rounding = ctx.queryParamAsClass("rounding", String.class).getOrDefault("exact");
        if (fromStr == null || toStr == null) {
            throw new BadRequestResponse("from and to are required");
        }
        if ((role == null || role.isBlank()) && groupId == null) {
            throw new BadRequestResponse("role or groupId is required");
        }
        Instant from = Instant.parse(fromStr);
        Instant to = Instant.parse(toStr);
        ctx.json(reportService.buildReport(session.stationId(), role, groupId, from, to, rounding));
    }

    @OpenApi(
            path = "/api/v1/attendance/report/export",
            methods = HttpMethod.GET,
            summary = "Export an attendance report as PDF",
            tags = {"Attendance"},
            queryParams = {
                @OpenApiParam(name = "role", type = String.class),
                @OpenApiParam(name = "groupId", type = Integer.class),
                @OpenApiParam(name = "from", type = String.class, required = true),
                @OpenApiParam(name = "to", type = String.class, required = true),
                @OpenApiParam(name = "rounding", type = String.class),
                @OpenApiParam(name = "period", type = String.class)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void reportExport(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String role = ctx.queryParam("role");
        String groupIdStr = ctx.queryParam("groupId");
        Integer groupId = groupIdStr != null && !groupIdStr.isBlank() ? Integer.parseInt(groupIdStr) : null;
        String fromStr = ctx.queryParam("from");
        String toStr = ctx.queryParam("to");
        String rounding = ctx.queryParamAsClass("rounding", String.class).getOrDefault("exact");
        if (fromStr == null || toStr == null) {
            throw new BadRequestResponse("from and to are required");
        }
        if ((role == null || role.isBlank()) && groupId == null) {
            throw new BadRequestResponse("role or groupId is required");
        }
        Instant from = Instant.parse(fromStr);
        Instant to = Instant.parse(toStr);
        String generatedBy =
                (session.account().firstName() + " " + session.account().lastName()).trim();
        String period = ctx.queryParamAsClass("period", String.class).getOrDefault("month");
        var pdf = reportService.exportReportPdf(
                session.stationId(), role, groupId, from, to, rounding, generatedBy, "year".equals(period));
        if (pdf.isEmpty()) {
            throw new NotFoundResponse();
        }
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", "attachment; filename=\"attendance-report.pdf\"");
        ctx.result(pdf.get());
    }

    @OpenApi(
            path = "/api/v1/attendance/report/presets",
            methods = HttpMethod.GET,
            summary = "List saved report presets",
            tags = {"Attendance"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceReportPreset[].class)))
    private void listPresets(Context ctx) {
        UserSession session = UserSession.from(ctx);
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
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreatePresetRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        if (isBlank(request.roleName()) && request.groupId() == null) {
            throw new BadRequestResponse("roleName or groupId is required");
        }
        ctx.status(HttpStatus.CREATED)
                .json(reportService.createPreset(
                        session.stationId(),
                        request.name(),
                        request.roleName(),
                        request.groupId(),
                        request.period(),
                        request.rounding()));
    }

    // -- Absences --

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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (reportService.deletePreset(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/attendance/absences",
            methods = HttpMethod.GET,
            summary = "List active absences for the current station",
            tags = {"Attendance"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberAbsence[].class)))
    private void listActiveAbsences(Context ctx) {
        UserSession session = UserSession.from(ctx);
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
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
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
        var request = ctx.bodyAsClass(AbsenceRequest.class);
        if (request.memberId() == null) {
            throw new BadRequestResponse("memberId is required");
        }
        if (isBlank(request.absentFrom()) || isBlank(request.absentUntil())) {
            throw new BadRequestResponse("absentFrom and absentUntil are required");
        }
        LocalDate from = LocalDate.parse(request.absentFrom());
        LocalDate until = LocalDate.parse(request.absentUntil());
        if (until.isBefore(from)) {
            throw new BadRequestResponse("absentUntil must not be before absentFrom");
        }
        ctx.status(HttpStatus.CREATED)
                .json(attendanceService.createAbsence(request.memberId(), from, until, request.reason()));
    }

    // -- Self-service absences --

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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (attendanceService.deleteAbsence(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/profile/absences",
            methods = HttpMethod.GET,
            summary = "List own and managed members' absences",
            tags = {"Attendance"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberAbsence[].class)))
    private void listMyAbsences(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.json(Collections.emptyList());
            return;
        }
        var absences = new ArrayList<>(
                attendanceService.findAbsencesByMember(session.member().id()));
        // Include managed members' absences
        if (session.hasRole(Roles.MEMBER_MANAGER)) {
            for (int mid :
                    attendanceService.findManagedMemberIds(session.member().id())) {
                absences.addAll(attendanceService.findAbsencesByMember(mid));
            }
        }
        ctx.json(absences);
    }

    @OpenApi(
            path = "/api/v1/profile/absences",
            methods = HttpMethod.POST,
            summary = "Create an absence for yourself or managed members",
            tags = {"Attendance"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MyAbsenceRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = MemberAbsence[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createMyAbsence(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            throw new BadRequestResponse("Not a station member");
        }
        var req = ctx.bodyAsClass(MyAbsenceRequest.class);
        if (req.absentFrom() == null
                || req.absentFrom().isBlank()
                || req.absentUntil() == null
                || req.absentUntil().isBlank()) {
            throw new BadRequestResponse("absentFrom and absentUntil are required");
        }
        LocalDate from = LocalDate.parse(req.absentFrom());
        LocalDate until = LocalDate.parse(req.absentUntil());
        if (until.isBefore(from)) {
            throw new BadRequestResponse("absentUntil must not be before absentFrom");
        }

        // Determine which members to create absences for
        var memberIds = new ArrayList<Integer>();
        if (req.memberIds() != null && !req.memberIds().isEmpty()) {
            var managed = session.hasRole(Roles.MEMBER_MANAGER)
                    ? attendanceService.findManagedMemberIds(session.member().id())
                    : Set.<Integer>of();
            for (int mid : req.memberIds()) {
                if (mid == session.member().id() || managed.contains(mid)) {
                    memberIds.add(mid);
                } else {
                    throw new ForbiddenResponse("You do not manage member " + mid);
                }
            }
        } else {
            memberIds.add(session.member().id());
        }

        var created = new ArrayList<MemberAbsence>();
        for (int mid : memberIds) {
            created.add(attendanceService.createAbsence(mid, from, until, req.reason()));
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
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var absence = attendanceService.findAbsenceById(id);
        if (absence.isEmpty()) {
            throw new NotFoundResponse();
        }
        // Allow deleting own or managed members' absences
        int absMemberId = absence.get().memberId();
        boolean isOwn = session.member() != null && session.member().id() == absMemberId;
        boolean manages = session.member() != null
                && session.hasRole(Roles.MEMBER_MANAGER)
                && attendanceService.findManagedMemberIds(session.member().id()).contains(absMemberId);
        if (!isOwn && !manages) {
            throw new ForbiddenResponse("Cannot delete this absence");
        }
        if (!attendanceService.deleteAbsence(id)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Request/Response records --

    public record TemplateRequest(String name) {}

    public record TemplateDetail(
            int id,
            int stationId,
            String name,
            List<AttendanceTemplateField> fields,
            List<TemplateGroupEntry> groups) {}

    public record TemplateGroupEntry(int groupId, int position) {}

    public record SetTemplateGroupsRequest(List<TemplateGroupEntry> groups) {}

    public record TemplateFieldRequest(String name, String fieldType, String config, int position) {}

    public record SessionRequest(Instant startTime, Instant endTime, Integer eventId, String title) {}

    public record SessionDetail(
            AttendanceSession session, List<AttendanceSessionField> fields, List<AttendanceEntry> entries) {}

    @OpenApiName("AttendanceFieldValueEntry")
    public record FieldValueEntry(int fieldId, String value) {}

    public record SetSessionFieldsRequest(List<FieldValueEntry> fields) {}

    public record CreateEntryRequest(Integer memberId, AttendanceEntry.EntrySource source) {}

    public record TimestampRequest(Instant time) {}

    public record TimestampResponse(int entryId, Instant time) {}

    public record StatusRequest(String status) {}

    public record StatusResponse(int entryId, String status) {}

    public record AbsenceRequest(Integer memberId, String absentFrom, String absentUntil, String reason) {}

    @OpenApiName("MyAbsenceRequest")
    public record MyAbsenceRequest(String absentFrom, String absentUntil, String reason, List<Integer> memberIds) {}

    public record CreatePresetRequest(String name, String roleName, Integer groupId, String period, String rounding) {}
}
