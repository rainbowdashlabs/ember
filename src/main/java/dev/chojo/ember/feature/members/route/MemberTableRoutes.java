/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.entity.MemberTablePeople;
import dev.chojo.ember.feature.members.entity.MemberTablePreset;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.repository.MemberTablePresetRepository;
import dev.chojo.ember.feature.members.service.MemberTableRenderer;
import dev.chojo.ember.feature.members.service.MemberTableService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.CsvWriter;
import dev.chojo.ember.util.DocumentName;
import dev.chojo.ember.util.DocumentPeriod;
import dev.chojo.ember.util.DocumentWord;
import dev.chojo.ember.util.SafeContentDisposition;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
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
    private final MemberTablePresetRepository presetRepository;
    private final StationRepository stationRepository;

    @Inject
    public MemberTableRoutes(
            MemberTableService tableService,
            MemberTableRenderer renderer,
            MemberTablePresetRepository presetRepository,
            StationRepository stationRepository) {
        this.tableService = tableService;
        this.renderer = renderer;
        this.presetRepository = presetRepository;
        this.stationRepository = stationRepository;
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

    private void offerableColumns(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(tableService.offerableColumns(session.stationId(), session.permissions()));
    }

    private void listPresets(Context ctx) {
        ctx.json(presetRepository.findByStation(UserSession.from(ctx).stationId()));
    }

    private void savePreset(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(SavePresetRequest.class);
        if (req.name() == null || req.name().isBlank()) {
            throw new BadRequestResponse("A saved selection needs a name");
        }
        ctx.json(presetRepository.save(session.stationId(), req.name().trim(), columnsOf(req.columns())));
    }

    private void deletePreset(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, presetRepository::findById, MemberTablePreset::stationId);
        presetRepository.delete(id, UserSession.from(ctx).stationId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void draw(Context ctx) {
        ctx.json(tableOf(ctx));
    }

    private void exportCsv(Context ctx) {
        var station = stationOf(ctx);
        ctx.contentType("text/csv");
        ctx.header("Content-Disposition", memberListName(station, "csv"));
        ctx.result(renderer.toCsv(tableOf(ctx), station, CsvWriter.Separator.of(ctx.queryParam("separator"))));
    }

    private void exportPdf(Context ctx) {
        var station = stationOf(ctx);
        var table = tableOf(ctx);
        try {
            var pdf = renderer.toPdf(table, station, "Mitgliederliste", "", generatedBy(ctx));
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", memberListName(station, "pdf"));
            ctx.result(pdf);
        } catch (Exception e) {
            throw new BadRequestResponse("This list cannot be turned into a sheet");
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
        return tableService.build(stationOf(ctx), people, columnsOf(req.columns()), session.permissions(), Map.of());
    }

    private Station stationOf(Context ctx) {
        return stationRepository.findById(UserSession.from(ctx).stationId()).orElseThrow(NotFoundResponse::new);
    }

    private String generatedBy(Context ctx) {
        var account = UserSession.from(ctx).account();
        return account == null ? "" : NameParts.of(account).official();
    }

    private List<MemberTableColumn> columnsOf(List<MemberTableColumn> columns) {
        if (columns == null) return List.of();
        return columns.stream().filter(MemberTableColumn::isWellFormed).toList();
    }

    /**
     * @param memberIds  who the table is about, named by the screen that is already showing them
     * @param columns    the columns asked for, which may name more than this reader may read
     */
    public record MemberTableRequest(List<Integer> memberIds, List<MemberTableColumn> columns) {}

    /** A selection to save under a name, writing over one saved under that name before. */
    public record SavePresetRequest(String name, List<MemberTableColumn> columns) {}
}
