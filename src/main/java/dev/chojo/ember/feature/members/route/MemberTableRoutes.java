/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.entity.MemberTable.MemberTableHeader;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.entity.MemberTablePeople;
import dev.chojo.ember.feature.members.entity.MemberTablePreset;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.service.MemberTablePresetService;
import dev.chojo.ember.feature.members.service.MemberTableRenderer;
import dev.chojo.ember.feature.members.service.MemberTableService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.util.CsvWriter;
import dev.chojo.ember.util.DocumentName;
import dev.chojo.ember.util.DocumentPeriod;
import dev.chojo.ember.util.DocumentWord;
import dev.chojo.ember.util.SafeContentDisposition;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * The register as a table of chosen columns, and the selections a station has saved.
 *
 * <p>The people are named by whoever is asking, because they are looking at a list they were already
 * allowed to see. The columns are not: what a reader may read is worked out here from their own
 * permissions, so naming a question they may not see gets them a table without it rather than a
 * refusal or a column of blanks.
 */
@Singleton
public class MemberTableRoutes implements Routes {
    private final MemberTableService tableService;
    private final MemberTableRenderer renderer;
    private final MemberTablePresetService presets;
    private final StationService stationService;

    @Inject
    public MemberTableRoutes(
            MemberTableService tableService,
            MemberTableRenderer renderer,
            MemberTablePresetService presets,
            StationService stationService) {
        this.tableService = tableService;
        this.renderer = renderer;
        this.presets = presets;
        this.stationService = stationService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/member-table/columns", this::offerableColumns, StationPermission.MEMBER_READ);
        routes.get(prefix + "/member-table/presets", this::listPresets, StationPermission.MEMBER_READ);
        routes.put(prefix + "/member-table/presets", this::savePreset, StationPermission.MEMBER_FIELDS);
        routes.delete(prefix + "/member-table/presets/{id}", this::deletePreset, StationPermission.MEMBER_FIELDS);
        routes.post(prefix + "/member-table", this::draw, StationPermission.MEMBER_READ);
        routes.post(prefix + "/member-table/export.csv", this::exportCsv, StationPermission.MEMBER_EXPORT);
        routes.post(prefix + "/member-table/export.pdf", this::exportPdf, StationPermission.MEMBER_EXPORT);
    }

    @OpenApi(
            path = "/api/v1/member-table/columns",
            methods = HttpMethod.GET,
            summary = "List the columns this reader may draw a member table with",
            tags = {"Member Table"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberTableHeader[].class)))
    private void offerableColumns(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(tableService.offerableColumns(session.stationId(), session.permissions()));
    }

    @OpenApi(
            path = "/api/v1/member-table/presets",
            methods = HttpMethod.GET,
            summary = "List the saved column selections of the station",
            tags = {"Member Table"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberTablePreset[].class)))
    private void listPresets(Context ctx) {
        ctx.json(presets.list(UserSession.from(ctx).stationId()));
    }

    @OpenApi(
            path = "/api/v1/member-table/presets",
            methods = HttpMethod.PUT,
            summary = "Save a column selection under a name",
            tags = {"Member Table"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SavePresetRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberTablePreset.class)))
    private void savePreset(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(SavePresetRequest.class);
        ctx.json(presets.save(session.stationId(), req.name(), req.columns()));
    }

    @OpenApi(
            path = "/api/v1/member-table/presets/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a saved column selection",
            tags = {"Member Table"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {@OpenApiResponse(status = "204"), @OpenApiResponse(status = "404")})
    private void deletePreset(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, presets::findById, MemberTablePreset::stationId);
        presets.delete(id, UserSession.from(ctx).stationId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/member-table",
            methods = HttpMethod.POST,
            summary = "Draw a member table of chosen people and columns",
            tags = {"Member Table"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MemberTableRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberTable.class)))
    private void draw(Context ctx) {
        ctx.json(tableOf(ctx));
    }

    @OpenApi(
            path = "/api/v1/member-table/export.csv",
            methods = HttpMethod.POST,
            summary = "Export a member table as CSV",
            tags = {"Member Table"},
            queryParams = @OpenApiParam(name = "separator"),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MemberTableRequest.class)),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = String.class, mimeType = "text/csv")))
    private void exportCsv(Context ctx) {
        var station = stationOf(ctx);
        ctx.contentType("text/csv");
        ctx.header("Content-Disposition", memberListName(station, "csv"));
        ctx.result(renderer.toCsv(tableOf(ctx), station, CsvWriter.Separator.of(ctx.queryParam("separator"))));
    }

    @OpenApi(
            path = "/api/v1/member-table/export.pdf",
            methods = HttpMethod.POST,
            summary = "Export a member table as PDF",
            tags = {"Member Table"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MemberTableRequest.class)),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = byte[].class, mimeType = "application/pdf")))
    private void exportPdf(Context ctx) {
        var station = stationOf(ctx);
        var table = tableOf(ctx);
        try {
            var pdf = renderer.toPdf(table, station, "Mitgliederliste", "", generatedBy(ctx));
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", memberListName(station, "pdf"));
            ctx.result(pdf);
        } catch (Exception ignored) {
            throw Refusal.MEMBER_TABLE_NOT_A_SHEET.raise();
        }
    }

    /** A member list is a snapshot, so the day it was taken is what tells two of them apart. */
    private static String memberListName(Station station, String extension) {
        String language = StationFormat.languageOf(station);
        String filename = DocumentName.of(
                extension,
                DocumentWord.MEMBERS.in(language),
                DocumentPeriod.day(Instant.now(), StationFormat.timezoneOf(station)));
        return SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, filename);
    }

    private MemberTable tableOf(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(MemberTableRequest.class);
        var people = MemberTablePeople.of(req.memberIds() == null ? List.of() : req.memberIds());
        return tableService.build(
                stationOf(ctx),
                people,
                MemberTablePresetService.wellFormed(req.columns()),
                session.permissions(),
                Map.of());
    }

    private Station stationOf(Context ctx) {
        return stationService
                .findById(UserSession.from(ctx).stationId())
                .orElseThrow(Refusal.STATION_NOT_HERE_FOR_MEMBER_TABLE::raise);
    }

    private String generatedBy(Context ctx) {
        var account = UserSession.from(ctx).account();
        return account == null ? "" : NameParts.of(account).official();
    }

    /**
     * @param memberIds  who the table is about, named by the screen that is already showing them
     * @param columns    the columns asked for, which may name more than this reader may read
     */
    public record MemberTableRequest(List<Integer> memberIds, List<MemberTableColumn> columns) {}

    /** A selection to save under a name, writing over one saved under that name before. */
    public record SavePresetRequest(String name, List<MemberTableColumn> columns) {}
}
