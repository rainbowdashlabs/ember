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
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.service.EventGroupAttendanceService;
import dev.chojo.ember.feature.events.service.EventGroupAttendanceService.AttendanceRow;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetResult;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.entity.MemberTable.MemberTableHeader;
import dev.chojo.ember.feature.members.entity.MemberTable.MemberTableRow;
import dev.chojo.ember.feature.members.entity.MemberTableCellType;
import dev.chojo.ember.feature.members.entity.MemberTableColumnKind;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.MemberTableRenderer;
import dev.chojo.ember.feature.station.entity.Station;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * How the participants of an event group attended its parts, for whoever handles registrations: on the
 * event group page, as a spreadsheet and as a sheet to print.
 *
 * <p>Both files are drawn from one table, a column per set beside the participant and whether every
 * set is met, and rendered the way the sign-up export renders its table, so the two can never
 * disagree about who attended what.
 */
@Singleton
public class EventGroupAttendanceRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(EventGroupAttendanceRoutes.class);
    private static final String EVENT_GROUP = "/events/groups/{eventGroupId}";

    private final EventGroupAttendanceService attendance;
    private final EventGroupService eventGroups;
    private final MemberIdentityFactory identities;
    private final MemberNameResolver names;
    private final StationService stations;
    private final MemberTableRenderer renderer;

    @Inject
    public EventGroupAttendanceRoutes(
            EventGroupAttendanceService attendance,
            EventGroupService eventGroups,
            MemberIdentityFactory identities,
            MemberNameResolver names,
            StationService stations,
            MemberTableRenderer renderer) {
        this.attendance = attendance;
        this.eventGroups = eventGroups;
        this.identities = identities;
        this.names = names;
        this.stations = stations;
        this.renderer = renderer;
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
        routes.get(
                prefix + EVENT_GROUP + "/attendance/export.pdf",
                this::exportPdf,
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
        var table = tableOf(session, station, eventGroup);
        ctx.contentType("text/csv");
        ctx.header("Content-Disposition", fileName(station, eventGroup, "csv"));
        ctx.result(renderer.toCsv(table, station, CsvWriter.Separator.of(ctx.queryParam("separator"))));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/attendance/export.pdf",
            methods = HttpMethod.GET,
            summary = "The attendance of an event group's participants as a sheet to print",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void exportPdf(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var eventGroup = eventGroups.requireEventGroup(session.stationId(), pathInt(ctx, "eventGroupId"));
        var station = stations.findById(session.stationId())
                .orElseThrow(EventRefusal.STATION_NOT_HERE_FOR_REGISTRATION_SHEET::raise);
        var table = tableOf(session, station, eventGroup);
        try {
            var pdf = renderer.toPdf(
                    table,
                    station,
                    eventGroup.details().name(),
                    DocumentWord.EVENT_GROUP_ATTENDANCE.in(StationFormat.languageOf(station)),
                    NameParts.of(session.user().account()).official());
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", fileName(station, eventGroup, "pdf"));
            ctx.result(pdf);
        } catch (Exception e) {
            log.error("Failed to render the attendance of event group {}", eventGroup.id(), e);
            throw EventRefusal.REGISTRATION_SHEET_NOT_DRAWN.raise();
        }
    }

    /**
     * The attendance as one table: the participant, the parts attended against the minimum of every set,
     * and whether every set is met. A set's column names its minimum, so a printed sheet reads on its own.
     */
    private MemberTable tableOf(StationSession session, Station station, EventGroup eventGroup) {
        String language = StationFormat.languageOf(station);
        var headers = new ArrayList<MemberTableHeader>();
        headers.add(column(DocumentWord.MEMBER.in(language)));
        eventGroups
                .findSets(eventGroup.id())
                .forEach(set -> headers.add(column(set.name() + " (" + set.minimum() + ")")));
        headers.add(column(DocumentWord.COMPLETE.in(language)));
        var rows = attendance.summary(session.stationId(), eventGroup.id()).stream()
                .map(row -> new MemberTableRow(row.memberId(), lineOf(row, language)))
                .toList();
        return new MemberTable(headers, rows);
    }

    private static MemberTableHeader column(String label) {
        return new MemberTableHeader(label, MemberTableColumnKind.BUILTIN, null, null, MemberTableCellType.TEXT);
    }

    private static String fileName(Station station, EventGroup eventGroup, String extension) {
        return SafeContentDisposition.build(
                SafeContentDisposition.Disposition.ATTACHMENT,
                DocumentName.of(
                        extension,
                        DocumentWord.EVENT_GROUP_ATTENDANCE.in(StationFormat.languageOf(station)),
                        DocumentName.part(eventGroup.details().name())));
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
