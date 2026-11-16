/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.events.service.EventGroupSignUpTable;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.entity.NameParts;
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
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The sign-ups of an event group as a file, for whoever handles registrations: a spreadsheet to work
 * with, or a sheet to print with a column per part to tick. Both are drawn from the same table, so
 * they cannot disagree about who signed up or what may be shown.
 */
@Singleton
public class EventGroupSignUpExportRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(EventGroupSignUpExportRoutes.class);
    private static final String EXPORT = "/events/groups/{eventGroupId}/sign-ups/export";

    private final EventGroupService eventGroups;
    private final EventGroupSignUpTable signUpTable;
    private final MemberTableRenderer renderer;
    private final StationService stations;

    @Inject
    public EventGroupSignUpExportRoutes(
            EventGroupService eventGroups,
            EventGroupSignUpTable signUpTable,
            MemberTableRenderer renderer,
            StationService stations) {
        this.eventGroups = eventGroups;
        this.signUpTable = signUpTable;
        this.renderer = renderer;
        this.stations = stations;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + EXPORT + ".csv", this::exportCsv, StationPermission.EVENT_REGISTRATION, StationModule.EVENTS);
        routes.get(
                prefix + EXPORT + ".pdf", this::exportPdf, StationPermission.EVENT_REGISTRATION, StationModule.EVENTS);
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/sign-ups/export.csv",
            methods = HttpMethod.GET,
            summary = "The sign-ups of an event group as a spreadsheet, with a column per part",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "separator"),
            responses = @OpenApiResponse(status = "200"))
    private void exportCsv(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var station = stations.findById(session.stationId())
                .orElseThrow(EventRefusal.STATION_NOT_HERE_FOR_REGISTRATION_CSV::raise);
        var eventGroup = eventGroups.requireEventGroup(session.stationId(), pathInt(ctx, "eventGroupId"));
        var table = tableOf(session, station, eventGroup);
        ctx.contentType("text/csv");
        ctx.header("Content-Disposition", fileName(station, eventGroup, "csv"));
        ctx.result(renderer.toCsv(table, station, CsvWriter.Separator.of(ctx.queryParam("separator"))));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/sign-ups/export.pdf",
            methods = HttpMethod.GET,
            summary = "The sign-ups of an event group as a sheet to print, with a column per part",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void exportPdf(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var station = stations.findById(session.stationId())
                .orElseThrow(EventRefusal.STATION_NOT_HERE_FOR_REGISTRATION_SHEET::raise);
        var eventGroup = eventGroups.requireEventGroup(session.stationId(), pathInt(ctx, "eventGroupId"));
        var table = tableOf(session, station, eventGroup);
        try {
            var pdf = renderer.toPdf(
                    table,
                    station,
                    eventGroup.details().name(),
                    DocumentWord.REGISTRATIONS.in(StationFormat.languageOf(station)),
                    NameParts.of(session.user().account()).official());
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", fileName(station, eventGroup, "pdf"));
            ctx.result(pdf);
        } catch (Exception e) {
            log.error("Failed to render the sign-ups of event group {}", eventGroup.id(), e);
            throw EventRefusal.REGISTRATION_SHEET_NOT_DRAWN.raise();
        }
    }

    /** Questions kept for organisers go to whoever edits appointments only, as on an appointment. */
    private MemberTable tableOf(StationSession session, Station station, EventGroup eventGroup) {
        return signUpTable.table(
                station,
                eventGroup.id(),
                session.user().permissions(),
                session.hasPermission(StationPermission.EVENT_EDIT));
    }

    private static String fileName(Station station, EventGroup eventGroup, String extension) {
        return SafeContentDisposition.build(
                SafeContentDisposition.Disposition.ATTACHMENT,
                DocumentName.of(
                        extension,
                        DocumentWord.REGISTRATIONS.in(StationFormat.languageOf(station)),
                        DocumentName.part(eventGroup.details().name())));
    }
}
