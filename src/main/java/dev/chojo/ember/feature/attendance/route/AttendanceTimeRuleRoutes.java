/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.AttendanceRefusal;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplate;
import dev.chojo.ember.feature.attendance.entity.TimeRule;
import dev.chojo.ember.feature.attendance.entity.TimeRuleInput;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.attendance.service.AttendanceTimeRuleService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * The preparation and follow-up rules of attendance templates and sheets: who is credited with time
 * before and after an appointment, and how much. A sheet copies its template's rules when it is made
 * and is corrected on its own from then on, down to one person who did not do the work.
 */
public class AttendanceTimeRuleRoutes implements Routes {
    private final AttendanceService attendanceService;
    private final AttendanceTimeRuleService timeRuleService;

    @Inject
    public AttendanceTimeRuleRoutes(AttendanceService attendanceService, AttendanceTimeRuleService timeRuleService) {
        this.attendanceService = attendanceService;
        this.timeRuleService = timeRuleService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/attendance/templates/{templateId}/time-rules",
                this::listTemplateRules,
                StationPermission.ATTENDANCE_READ,
                StationPermission.ATTENDANCE_CONFIGURE);
        routes.put(
                prefix + "/attendance/templates/{templateId}/time-rules",
                this::replaceTemplateRules,
                StationPermission.ATTENDANCE_CONFIGURE);
        routes.get(
                prefix + "/attendance/sessions/{sessionId}/time-rules",
                this::listSessionRules,
                StationPermission.ATTENDANCE_READ);
        routes.put(
                prefix + "/attendance/sessions/{sessionId}/time-rules",
                this::replaceSessionRules,
                StationPermission.ATTENDANCE_EDIT);
        routes.put(
                prefix + "/attendance/entries/{id}/extra-time", this::setExtraTime, StationPermission.ATTENDANCE_EDIT);
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/{templateId}/time-rules",
            methods = HttpMethod.GET,
            summary = "List the preparation and follow-up rules of an attendance template",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "templateId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TimeRule[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void listTemplateRules(Context ctx) {
        int templateId = pathInt(ctx, "templateId");
        requireOwnedOrNotFound(ctx, templateId, attendanceService::findTemplateById, AttendanceTemplate::stationId);
        ctx.json(timeRuleService.templateRules(templateId));
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/{templateId}/time-rules",
            methods = HttpMethod.PUT,
            summary = "Replace the preparation and follow-up rules of an attendance template",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "templateId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetTimeRulesRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TimeRule[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void replaceTemplateRules(Context ctx) {
        int templateId = pathInt(ctx, "templateId");
        var template = requireOwnedOrNotFound(
                ctx, templateId, attendanceService::findActiveTemplateById, AttendanceTemplate::stationId);
        var request = ctx.bodyAsClass(SetTimeRulesRequest.class);
        List<TimeRuleInput> rules = request.rules() == null ? List.of() : request.rules();
        ctx.json(timeRuleService.replaceTemplateRules(template.stationId(), templateId, rules));
    }

    @OpenApi(
            path = "/api/v1/attendance/sessions/{sessionId}/time-rules",
            methods = HttpMethod.GET,
            summary = "List the preparation and follow-up rules of an attendance sheet",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "sessionId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TimeRule[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void listSessionRules(Context ctx) {
        int sessionId = pathInt(ctx, "sessionId");
        requireOwnSheet(ctx, sessionId);
        ctx.json(timeRuleService.sessionRules(sessionId));
    }

    @OpenApi(
            path = "/api/v1/attendance/sessions/{sessionId}/time-rules",
            methods = HttpMethod.PUT,
            summary = "Replace the preparation and follow-up rules of an attendance sheet",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "sessionId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetTimeRulesRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TimeRule[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void replaceSessionRules(Context ctx) {
        int sessionId = pathInt(ctx, "sessionId");
        var template = requireOwnSheet(ctx, sessionId);
        var request = ctx.bodyAsClass(SetTimeRulesRequest.class);
        List<TimeRuleInput> rules = request.rules() == null ? List.of() : request.rules();
        ctx.json(timeRuleService.replaceSessionRules(template.stationId(), sessionId, rules));
    }

    @OpenApi(
            path = "/api/v1/attendance/entries/{id}/extra-time",
            methods = HttpMethod.PUT,
            summary = "Set whether a person on a sheet did the preparation and follow-up its rules credit",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ExtraTimeRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AttendanceEntry.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setExtraTime(Context ctx) {
        int entryId = pathInt(ctx, "id");
        var entry = attendanceService
                .findEntryById(entryId)
                .orElseThrow(AttendanceRefusal.ATTENDANCE_ENTRY_NOT_HERE::raise);
        requireOwnSheet(ctx, entry.sessionId());
        Boolean skip = ctx.bodyAsClass(ExtraTimeRequest.class).skip();
        if (skip == null) throw AttendanceRefusal.ATTENDANCE_EXTRA_TIME_NOT_GIVEN.raise();
        ctx.json(attendanceService
                .setSkipExtraTime(entryId, skip)
                .orElseThrow(AttendanceRefusal.ATTENDANCE_ENTRY_NOT_HERE::raise));
    }

    /**
     * The template of a sheet of the caller's station, which is what says whose the sheet is.
     *
     * @throws io.javalin.http.HttpResponseException {@link AttendanceRefusal#ATTENDANCE_SHEET_NOT_HERE}
     *     where there is no such sheet or it is another station's
     */
    private AttendanceTemplate requireOwnSheet(Context ctx, int sessionId) {
        var sheet = attendanceService
                .findSessionById(sessionId)
                .orElseThrow(AttendanceRefusal.ATTENDANCE_SHEET_NOT_HERE::raise);
        var template = attendanceService
                .findTemplateById(sheet.templateId())
                .orElseThrow(AttendanceRefusal.ATTENDANCE_SHEET_NOT_HERE::raise);
        if (template.stationId() != StationSession.from(ctx).stationId()) {
            throw AttendanceRefusal.ATTENDANCE_SHEET_NOT_HERE.raise();
        }
        return template;
    }

    /**
     * Whether a person did the preparation and follow-up their sheet's rules would credit them with.
     *
     * @param skip true where they did not, so the report counts their presence alone
     */
    public record ExtraTimeRequest(@Nullable Boolean skip) {}

    /**
     * A whole list of preparation and follow-up rules, replacing the one before.
     *
     * @param rules the rules in their order, empty to remove them all
     */
    public record SetTimeRulesRequest(List<TimeRuleInput> rules) {}
}
