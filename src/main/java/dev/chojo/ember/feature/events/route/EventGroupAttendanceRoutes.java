/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.service.EventGroupAttendanceService;
import dev.chojo.ember.feature.events.service.EventGroupAttendanceService.AttendanceRow;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetResult;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.util.CsvWriter;
import dev.chojo.ember.util.DocumentName;
import dev.chojo.ember.util.DocumentWord;
import dev.chojo.ember.util.SafeContentDisposition;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * How the participants of an event group attended its parts, for whoever handles registrations: on the
 * event group page and as a spreadsheet.
 */
@Singleton
public class EventGroupAttendanceRoutes implements Routes {
    private static final String EVENT_GROUP = "/events/groups/{eventGroupId}";

    private final EventGroupAttendanceService attendance;
    private final EventGroupService eventGroups;
    private final MemberIdentityFactory identities;
    private final MemberNameResolver names;
    private final StationService stations;

    @Inject
    public EventGroupAttendanceRoutes(
            EventGroupAttendanceService attendance,
            EventGroupService eventGroups,
            MemberIdentityFactory identities,
            MemberNameResolver names,
            StationService stations) {
        this.attendance = attendance;
        this.eventGroups = eventGroups;
        this.identities = identities;
        this.names = names;
        this.stations = stations;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + EVENT_GROUP + "/attendance",
                this::summary,
                StationPermission.EVENT_REGISTRATION,
                StationModule.EVENTS);
        routes.get(
                prefix + EVENT_GROUP + "/attendance/export.csv",
                this::exportCsv,
                StationPermission.EVENT_REGISTRATION,
                StationModule.EVENTS);
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/attendance",
            methods = HttpMethod.GET,
            summary = "How the accepted participants of an event group attended its parts",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupAttendance[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void summary(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(attendance.summary(session.stationId(), pathInt(ctx, "eventGroupId")).stream()
                .map(row -> new EventGroupAttendance(
                        row.memberId(),
                        identities.local(session.stationId(), row.memberId()),
                        row.sets(),
                        row.complete()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/attendance/export.csv",
            methods = HttpMethod.GET,
            summary = "The attendance of an event group's participants as a spreadsheet",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "separator"),
            responses = @OpenApiResponse(status = "200"))
    private void exportCsv(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var eventGroup = eventGroups.requireEventGroup(session.stationId(), pathInt(ctx, "eventGroupId"));
        var station = stations.findById(session.stationId())
                .orElseThrow(EventRefusal.STATION_NOT_HERE_FOR_REGISTRATION_CSV::raise);
        String language = StationFormat.languageOf(station);
        var rows = attendance.summary(session.stationId(), eventGroup.id());
        var headers = new ArrayList<String>();
        headers.add(DocumentWord.MEMBER.in(language));
        eventGroups.findSets(eventGroup.id()).forEach(set -> headers.add(set.name() + " (" + set.minimum() + ")"));
        headers.add(DocumentWord.COMPLETE.in(language));
        var lines = rows.stream().map(row -> lineOf(row, language)).toList();
        ctx.contentType("text/csv");
        ctx.header(
                "Content-Disposition",
                SafeContentDisposition.build(
                        SafeContentDisposition.Disposition.ATTACHMENT,
                        DocumentName.of(
                                "csv",
                                DocumentWord.EVENT_GROUP_ATTENDANCE.in(language),
                                DocumentName.part(eventGroup.name()))));
        ctx.result(CsvWriter.write(headers, lines, CsvWriter.Separator.of(ctx.queryParam("separator"))));
    }

    private List<String> lineOf(AttendanceRow row, String language) {
        var line = new ArrayList<String>();
        line.add(names.called(row.memberId()));
        for (SetResult set : row.sets()) {
            line.add(set.chosen() + "/" + set.required());
        }
        line.add((row.complete() ? DocumentWord.YES : DocumentWord.NO).in(language));
        return line;
    }

    /**
     * One participant's attendance as the event group page shows it.
     *
     * @param memberId the participant
     * @param member   the participant as they are shown
     * @param sets     how the parts they attended meet every set
     * @param complete whether every set is met
     */
    public record EventGroupAttendance(int memberId, MemberIdentity member, List<SetResult> sets, boolean complete) {}
}
