/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationPermission;
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

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * The preparation and follow-up rules of attendance templates: who is credited with time before and
 * after an appointment, and how much. Sheets copy them when they are made.
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
    }

    @OpenApi(
            path = "/api/v1/attendance/templates/{templateId}/time-rules",
            methods = HttpMethod.GET,
            summary = "List the preparation and follow-up rules of an attendance template",
            tags = {"Attendance"},
            pathParams = @OpenApiParam(name = "templateId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TimeRule[].class)))
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
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TimeRule[].class)))
    private void replaceTemplateRules(Context ctx) {
        int templateId = pathInt(ctx, "templateId");
        var template = requireOwnedOrNotFound(
                ctx, templateId, attendanceService::findActiveTemplateById, AttendanceTemplate::stationId);
        var request = ctx.bodyAsClass(SetTimeRulesRequest.class);
        List<TimeRuleInput> rules = request.rules() == null ? List.of() : request.rules();
        ctx.json(timeRuleService.replaceTemplateRules(template.stationId(), templateId, rules));
    }

    /**
     * A whole list of preparation and follow-up rules, replacing the one before.
     *
     * @param rules the rules in their order, empty to remove them all
     */
    public record SetTimeRulesRequest(List<TimeRuleInput> rules) {}
}
