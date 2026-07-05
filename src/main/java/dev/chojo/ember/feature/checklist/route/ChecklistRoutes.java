/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.checklist.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.checklist.entity.Checklist;
import dev.chojo.ember.feature.checklist.entity.ChecklistCell;
import dev.chojo.ember.feature.checklist.entity.ChecklistCellNoteHistory;
import dev.chojo.ember.feature.checklist.entity.ChecklistColumn;
import dev.chojo.ember.feature.checklist.entity.ChecklistEntry;
import dev.chojo.ember.feature.checklist.entity.ChecklistSummary;
import dev.chojo.ember.feature.checklist.service.ChecklistExportService;
import dev.chojo.ember.feature.checklist.service.ChecklistService;
import dev.chojo.ember.feature.checklist.service.ChecklistService.ColumnSpec;
import dev.chojo.ember.feature.checklist.service.ChecklistService.FilterSpec;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.restriction.Restriction;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * HTTP routes for the checklist feature. Read endpoints require CHECKLIST_READ; every
 * write endpoint requires CHECKLIST_MANAGE.
 */
@Singleton
public class ChecklistRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(ChecklistRoutes.class);

    private final ChecklistService checklistService;
    private final ChecklistExportService exportService;
    private final MemberNameResolver memberNameResolver;
    private final StationMemberRepository memberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final UserTagRepository userTagRepository;

    @Inject
    public ChecklistRoutes(
            ChecklistService checklistService,
            ChecklistExportService exportService,
            MemberNameResolver memberNameResolver,
            StationMemberRepository memberRepository,
            MemberGroupRepository memberGroupRepository,
            UserTagRepository userTagRepository) {
        this.checklistService = checklistService;
        this.exportService = exportService;
        this.memberNameResolver = memberNameResolver;
        this.memberRepository = memberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.userTagRepository = userTagRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/checklist", this::list, StationPermission.CHECKLIST_READ);
        routes.post(prefix + "/checklist", this::create, StationPermission.CHECKLIST_MANAGE);
        routes.get(prefix + "/checklist/{id}", this::get, StationPermission.CHECKLIST_READ);
        routes.patch(prefix + "/checklist/{id}", this::update, StationPermission.CHECKLIST_MANAGE);
        routes.delete(prefix + "/checklist/{id}", this::delete, StationPermission.CHECKLIST_MANAGE);

        routes.post(prefix + "/checklist/{id}/refresh", this::refresh, StationPermission.CHECKLIST_MANAGE);
        routes.post(prefix + "/checklist/{id}/column", this::addColumn, StationPermission.CHECKLIST_MANAGE);
        routes.patch(
                prefix + "/checklist/{id}/column/{columnId}", this::updateColumn, StationPermission.CHECKLIST_MANAGE);
        routes.delete(
                prefix + "/checklist/{id}/column/{columnId}", this::deleteColumn, StationPermission.CHECKLIST_MANAGE);
        routes.put(
                prefix + "/checklist/{id}/columns/reorder", this::reorderColumns, StationPermission.CHECKLIST_MANAGE);

        routes.post(prefix + "/checklist/{id}/entry", this::addMembers, StationPermission.CHECKLIST_MANAGE);
        routes.delete(
                prefix + "/checklist/{id}/entry/{entryId}", this::deleteEntry, StationPermission.CHECKLIST_MANAGE);

        routes.put(
                prefix + "/checklist/{id}/entry/{entryId}/column/{columnId}",
                this::writeCell,
                StationPermission.CHECKLIST_MANAGE);
        routes.get(
                prefix + "/checklist/{id}/entry/{entryId}/column/{columnId}/note-history",
                this::noteHistory,
                StationPermission.CHECKLIST_READ);
        routes.post(
                prefix + "/checklist/{id}/column/{columnId}/bulk",
                this::bulkSetColumn,
                StationPermission.CHECKLIST_MANAGE);

        routes.get(prefix + "/checklist/{id}/export.csv", this::exportCsv, StationPermission.CHECKLIST_READ);
        routes.get(prefix + "/checklist/{id}/export.pdf", this::exportPdf, StationPermission.CHECKLIST_READ);
    }

    @OpenApi(
            path = "/api/v1/checklist",
            methods = HttpMethod.GET,
            summary = "List checklists for the current station",
            tags = {"Checklist"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = ChecklistSummaryResponse[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var summaries = checklistService.findSummaries(session.stationId());
        ctx.json(summaries.stream().map(this::toSummaryResponse).toList());
    }

    @OpenApi(
            path = "/api/v1/checklist",
            methods = HttpMethod.POST,
            summary = "Create a checklist with columns and materialise the member set",
            tags = {"Checklist"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateRequest.class)),
            responses =
                    @OpenApiResponse(status = "201", content = @OpenApiContent(from = ChecklistDetailResponse.class)))
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreateRequest.class);
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestResponse("Name is required");
        }
        if (request.columns() == null || request.columns().isEmpty()) {
            throw new BadRequestResponse("At least one column is required");
        }
        var columnSpecs = request.columns().stream()
                .map(c -> new ColumnSpec(requireLabel(c.label()), c.description() == null ? "" : c.description()))
                .toList();
        RestrictionMode mode = resolveMode(request.restriction());
        FilterSpec filterSpec = toFilterSpec(request.restriction());
        var checklist = checklistService.create(
                session.stationId(),
                request.name(),
                request.description() == null ? "" : request.description(),
                mode,
                columnSpecs,
                filterSpec,
                session.member().id());
        ctx.status(HttpStatus.CREATED).json(buildDetail(checklist));
    }

    @OpenApi(
            path = "/api/v1/checklist/{id}",
            methods = HttpMethod.GET,
            summary = "Fetch a checklist with columns, alive entries, cells, and filter",
            tags = {"Checklist"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = ChecklistDetailResponse.class)))
    private void get(Context ctx) {
        var checklist = loadOwned(ctx);
        ctx.json(buildDetail(checklist));
    }

    @OpenApi(
            path = "/api/v1/checklist/{id}",
            methods = HttpMethod.PATCH,
            summary = "Update a checklist's metadata and/or restriction filter (does not refresh)",
            tags = {"Checklist"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateRequest.class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = ChecklistDetailResponse.class)))
    private void update(Context ctx) {
        var checklist = loadOwned(ctx);
        var request = ctx.bodyAsClass(UpdateRequest.class);
        String name = request.name() != null ? request.name() : checklist.name();
        if (name.isBlank()) throw new BadRequestResponse("Name cannot be blank");
        String description = request.description() != null ? request.description() : checklist.description();
        RestrictionMode mode = request.restriction() != null ? resolveMode(request.restriction()) : checklist.mode();
        FilterSpec filterSpec = request.restriction() != null ? toFilterSpec(request.restriction()) : null;
        var updated = checklistService.update(checklist.id(), name, description, mode, filterSpec);
        ctx.json(buildDetail(updated));
    }

    @OpenApi(
            path = "/api/v1/checklist/{id}",
            methods = HttpMethod.DELETE,
            summary = "Cascade-delete a checklist",
            tags = {"Checklist"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void delete(Context ctx) {
        var checklist = loadOwned(ctx);
        checklistService.delete(checklist.id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/checklist/{id}/refresh",
            methods = HttpMethod.POST,
            summary = "Additively pull in any newly-matching members",
            tags = {"Checklist"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RefreshResponse.class)))
    private void refresh(Context ctx) {
        var checklist = loadOwned(ctx);
        var result = checklistService.refresh(checklist.id());
        ctx.json(new RefreshResponse(result.added(), result.alreadyPresent()));
    }

    @OpenApi(
            path = "/api/v1/checklist/{id}/column",
            methods = HttpMethod.POST,
            summary = "Append a column to a checklist",
            tags = {"Checklist"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ColumnCreateRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = ColumnResponse.class)))
    private void addColumn(Context ctx) {
        var checklist = loadOwned(ctx);
        var request = ctx.bodyAsClass(ColumnCreateRequest.class);
        var column = checklistService.addColumn(
                checklist.id(), requireLabel(request.label()), request.description(), request.position());
        ctx.status(HttpStatus.CREATED).json(toColumnResponse(column));
    }

    @OpenApi(
            path = "/api/v1/checklist/{id}/column/{columnId}",
            methods = HttpMethod.PATCH,
            summary = "Update a checklist column",
            tags = {"Checklist"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "columnId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ColumnUpdateRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ColumnResponse.class)))
    private void updateColumn(Context ctx) {
        var checklist = loadOwned(ctx);
        var column = loadColumn(ctx, checklist);
        var request = ctx.bodyAsClass(ColumnUpdateRequest.class);
        String label = request.label() != null ? requireLabel(request.label()) : column.label();
        String description = request.description() != null ? request.description() : column.description();
        int position = request.position() != null ? request.position() : column.position();
        var updated = checklistService.updateColumn(column.id(), label, description, position);
        ctx.json(toColumnResponse(updated));
    }

    @OpenApi(
            path = "/api/v1/checklist/{id}/column/{columnId}",
            methods = HttpMethod.DELETE,
            summary = "Drop a column and all its cells",
            tags = {"Checklist"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "columnId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteColumn(Context ctx) {
        var checklist = loadOwned(ctx);
        var column = loadColumn(ctx, checklist);
        checklistService.deleteColumn(column.id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/checklist/{id}/columns/reorder",
            methods = HttpMethod.PUT,
            summary = "Rewrite the position of every column of the checklist",
            tags = {"Checklist"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ReorderColumnsRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void reorderColumns(Context ctx) {
        var checklist = loadOwned(ctx);
        var request = ctx.bodyAsClass(ReorderColumnsRequest.class);
        if (request.orderedIds() == null || request.orderedIds().isEmpty()) {
            throw new BadRequestResponse("orderedIds is required");
        }
        try {
            checklistService.reorderColumns(checklist.id(), request.orderedIds());
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/checklist/{id}/entry",
            methods = HttpMethod.POST,
            summary = "Manually place members on a checklist or restore previously removed ones",
            tags = {"Checklist"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AddMembersRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AddMembersResponse.class)))
    private void addMembers(Context ctx) {
        var checklist = loadOwned(ctx);
        var request = ctx.bodyAsClass(AddMembersRequest.class);
        if (request.memberIds() == null || request.memberIds().isEmpty()) {
            throw new BadRequestResponse("memberIds is required");
        }
        var validIds = filterToStation(request.memberIds(), checklist.stationId());
        var result = checklistService.addMembers(checklist.id(), validIds);
        ctx.json(new AddMembersResponse(result.added(), result.restored(), result.skipped()));
    }

    @OpenApi(
            path = "/api/v1/checklist/{id}/entry/{entryId}",
            methods = HttpMethod.DELETE,
            summary = "Soft-delete a member's row (refresh will not bring it back)",
            tags = {"Checklist"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "entryId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteEntry(Context ctx) {
        var checklist = loadOwned(ctx);
        var entry = loadEntry(ctx, checklist);
        checklistService.softDeleteEntry(entry.id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/checklist/{id}/entry/{entryId}/column/{columnId}",
            methods = HttpMethod.PUT,
            summary = "Upsert a single cell (boolean state plus optional note)",
            tags = {"Checklist"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "entryId", type = Integer.class, required = true),
                @OpenApiParam(name = "columnId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CellWriteRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CellResponse.class)))
    private void writeCell(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var checklist = loadOwned(ctx);
        var entry = loadEntry(ctx, checklist);
        var column = loadColumn(ctx, checklist);
        var request = ctx.bodyAsClass(CellWriteRequest.class);
        var result = checklistService.writeCell(
                entry.id(),
                column.id(),
                request.checked(),
                request.note(),
                session.member().id());
        ctx.json(toCellResponse(result.cell()));
    }

    @OpenApi(
            path = "/api/v1/checklist/{id}/entry/{entryId}/column/{columnId}/note-history",
            methods = HttpMethod.GET,
            summary = "Fetch the note-history log for a single cell",
            tags = {"Checklist"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "entryId", type = Integer.class, required = true),
                @OpenApiParam(name = "columnId", type = Integer.class, required = true)
            },
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = NoteHistoryEntryResponse[].class)))
    private void noteHistory(Context ctx) {
        var checklist = loadOwned(ctx);
        var entry = loadEntry(ctx, checklist);
        var column = loadColumn(ctx, checklist);
        var rows = checklistService.findNoteHistory(entry.id(), column.id());
        ctx.json(rows.stream().map(this::toNoteHistoryResponse).toList());
    }

    @OpenApi(
            path = "/api/v1/checklist/{id}/column/{columnId}/bulk",
            methods = HttpMethod.POST,
            summary = "Bulk set the boolean state on one column across the listed entries",
            tags = {"Checklist"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "columnId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BulkSetRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BulkSetResponse.class)))
    private void bulkSetColumn(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var checklist = loadOwned(ctx);
        var column = loadColumn(ctx, checklist);
        var request = ctx.bodyAsClass(BulkSetRequest.class);
        if (request.entryIds() == null) {
            throw new BadRequestResponse("entryIds is required");
        }
        var validEntryIds = filterEntryIds(request.entryIds(), checklist.id());
        int updated = checklistService.bulkSetColumn(
                column.id(), validEntryIds, request.checked(), session.member().id());
        ctx.json(new BulkSetResponse(updated));
    }

    @OpenApi(
            path = "/api/v1/checklist/{id}/export.csv",
            methods = HttpMethod.GET,
            summary = "Download the matrix as a CSV file",
            tags = {"Checklist"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void exportCsv(Context ctx) {
        var checklist = loadOwned(ctx);
        String csv = exportService.exportCsv(checklist.id());
        ctx.contentType("text/csv");
        ctx.header(
                "Content-Disposition",
                "attachment; filename=\"" + filename(checklist.name(), checklist.id()) + ".csv\"");
        ctx.result(csv);
    }

    @OpenApi(
            path = "/api/v1/checklist/{id}/export.pdf",
            methods = HttpMethod.GET,
            summary = "Download the matrix as a printable PDF",
            tags = {"Checklist"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void exportPdf(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var checklist = loadOwned(ctx);
        var account = session.account();
        String generatedBy = (account.firstName() + " " + account.lastName()).trim();
        try {
            byte[] pdf = exportService.exportPdf(checklist.id(), generatedBy);
            ctx.contentType("application/pdf");
            ctx.header(
                    "Content-Disposition",
                    "attachment; filename=\"" + filename(checklist.name(), checklist.id()) + ".pdf\"");
            ctx.result(pdf);
        } catch (IOException e) {
            log.error("Failed to render checklist PDF for {}", checklist.id(), e);
            throw new InternalServerErrorResponse("Failed to render PDF");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalServerErrorResponse("PDF rendering interrupted");
        }
    }

    private Checklist loadOwned(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        var checklist = checklistService.findById(id).orElseThrow(() -> new NotFoundResponse("Checklist not found"));
        if (checklist.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Checklist belongs to another station");
        }
        return checklist;
    }

    private ChecklistColumn loadColumn(Context ctx, Checklist checklist) {
        int columnId = pathInt(ctx, "columnId");
        var column = checklistService.findColumn(columnId).orElseThrow(() -> new NotFoundResponse("Column not found"));
        if (column.checklistId() != checklist.id()) {
            throw new ForbiddenResponse("Column does not belong to this checklist");
        }
        return column;
    }

    private ChecklistEntry loadEntry(Context ctx, Checklist checklist) {
        int entryId = pathInt(ctx, "entryId");
        var entry = checklistService.findEntry(entryId).orElseThrow(() -> new NotFoundResponse("Entry not found"));
        if (entry.checklistId() != checklist.id()) {
            throw new ForbiddenResponse("Entry does not belong to this checklist");
        }
        return entry;
    }

    private List<Integer> filterToStation(List<Integer> memberIds, int stationId) {
        var deduped = new HashSet<Integer>();
        var stationMemberIds = new HashSet<Integer>();
        for (var member : memberRepository.findByStation(stationId)) {
            stationMemberIds.add(member.id());
        }
        var out = new ArrayList<Integer>();
        for (int id : memberIds) {
            if (deduped.add(id) && stationMemberIds.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private List<Integer> filterEntryIds(List<Integer> entryIds, int checklistId) {
        var checklistEntryIds = new HashSet<Integer>();
        for (var entry : checklistService.findEntries(checklistId, true)) {
            checklistEntryIds.add(entry.id());
        }
        var deduped = new HashSet<Integer>();
        var out = new ArrayList<Integer>();
        for (int id : entryIds) {
            if (deduped.add(id) && checklistEntryIds.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private ChecklistDetailResponse buildDetail(Checklist checklist) {
        var columns = checklistService.findColumns(checklist.id());
        var aliveEntries = checklistService.findEntries(checklist.id(), false);
        var cells = checklistService.findCells(checklist.id());
        var filter = checklistService.findFilterRows(checklist.id());
        var restrictionSet = checklistService.findRestrictionSet(checklist);
        var groupCache = new HashMap<Integer, List<Integer>>();
        var tagCache = new HashMap<Integer, List<Integer>>();
        var members = new HashMap<Integer, StationMember>();
        for (var m : memberRepository.findByStation(checklist.stationId(), true)) {
            members.put(m.id(), m);
        }
        var entryResponses = aliveEntries.stream()
                .map(entry -> {
                    var member = members.get(entry.memberId());
                    String name = memberNameResolver.resolveLocal(entry.memberId());
                    boolean inFilter = false;
                    if (member != null) {
                        var groupIds = groupCache.computeIfAbsent(
                                member.id(), id -> memberGroupRepository.findGroupsForMember(id).stream()
                                        .map(MemberGroup::id)
                                        .toList());
                        var tagIds = tagCache.computeIfAbsent(
                                member.id(), id -> userTagRepository.findTagsForMember(id).stream()
                                        .map(UserTag::id)
                                        .toList());
                        inFilter = !member.former()
                                && restrictionSet.matches(member.userType(), groupIds, tagIds, member.id());
                    }
                    return new EntryResponse(
                            entry.id(),
                            entry.memberId(),
                            name != null ? name : ("#" + entry.memberId()),
                            entry.addedAt(),
                            entry.deletedAt(),
                            inFilter);
                })
                .toList();
        return new ChecklistDetailResponse(
                checklist.id(),
                checklist.name(),
                checklist.description(),
                checklist.mode().name(),
                checklist.createdAt(),
                checklist.createdBy(),
                checklist.lastRefreshedAt(),
                columns.stream().map(this::toColumnResponse).toList(),
                entryResponses,
                cells.stream().map(this::toCellResponse).toList(),
                toRestrictionResponse(filter, checklist.mode()));
    }

    private ChecklistSummaryResponse toSummaryResponse(ChecklistSummary summary) {
        return new ChecklistSummaryResponse(
                summary.id(),
                summary.name(),
                summary.description(),
                summary.memberCount(),
                summary.columnCount(),
                summary.lastRefreshedAt(),
                summary.createdAt());
    }

    private ColumnResponse toColumnResponse(ChecklistColumn column) {
        return new ColumnResponse(column.id(), column.position(), column.label(), column.description());
    }

    private CellResponse toCellResponse(ChecklistCell cell) {
        return new CellResponse(
                cell.id(),
                cell.entryId(),
                cell.columnId(),
                cell.checked(),
                cell.note(),
                cell.updatedAt(),
                cell.updatedBy());
    }

    private NoteHistoryEntryResponse toNoteHistoryResponse(ChecklistCellNoteHistory history) {
        Integer changedBy = history.changedBy();
        String changedByName = changedBy != null ? memberNameResolver.resolveLocal(changedBy) : null;
        return new NoteHistoryEntryResponse(
                history.id(), history.oldNote(), history.newNote(), changedBy, changedByName, history.changedAt());
    }

    private RestrictionResponse toRestrictionResponse(List<Restriction> filter, RestrictionMode mode) {
        var userTypes = filter.stream()
                .map(Restriction::userType)
                .filter(Objects::nonNull)
                .map(Enum::name)
                .toList();
        var groupIds = filter.stream()
                .map(Restriction::groupId)
                .filter(Objects::nonNull)
                .toList();
        var tagIds =
                filter.stream().map(Restriction::tagId).filter(Objects::nonNull).toList();
        var memberIds = filter.stream()
                .map(Restriction::memberId)
                .filter(Objects::nonNull)
                .toList();
        return new RestrictionResponse(userTypes, groupIds, tagIds, memberIds, mode.name());
    }

    private static FilterSpec toFilterSpec(RestrictionRequest req) {
        if (req == null) return FilterSpec.empty();
        var userTypes = req.userTypes() == null
                ? List.<StationUserType>of()
                : req.userTypes().stream().map(StationUserType::valueOf).toList();
        var groupIds = req.groupIds() != null ? req.groupIds() : List.<Integer>of();
        var tagIds = req.tagIds() != null ? req.tagIds() : List.<Integer>of();
        var memberIds = req.memberIds() != null ? req.memberIds() : List.<Integer>of();
        return new FilterSpec(userTypes, groupIds, tagIds, memberIds);
    }

    private static RestrictionMode resolveMode(RestrictionRequest req) {
        if (req == null || req.mode() == null) return RestrictionMode.AND;
        try {
            return RestrictionMode.valueOf(req.mode());
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Invalid mode: " + req.mode());
        }
    }

    private static String requireLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new BadRequestResponse("Column label is required");
        }
        return label.trim();
    }

    private static String filename(String name, int id) {
        String sanitised =
                name == null ? "" : name.replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("_+", "_");
        if (sanitised.isBlank()) sanitised = "checklist";
        if (sanitised.length() > 40) sanitised = sanitised.substring(0, 40);
        return sanitised + "-" + id;
    }

    /**
     * Compact summary returned by the list endpoint.
     */
    public record ChecklistSummaryResponse(
            int id,
            String name,
            String description,
            int memberCount,
            int columnCount,
            Instant lastRefreshedAt,
            Instant createdAt) {}

    /**
     * Full detail payload returned by the detail and create endpoints.
     */
    public record ChecklistDetailResponse(
            int id,
            String name,
            String description,
            String mode,
            Instant createdAt,
            Integer createdBy,
            Instant lastRefreshedAt,
            List<ColumnResponse> columns,
            List<EntryResponse> entries,
            List<CellResponse> cells,
            RestrictionResponse restriction) {}

    public record ColumnResponse(int id, int position, String label, String description) {}

    public record EntryResponse(
            int id, int memberId, String memberName, Instant addedAt, Instant deletedAt, boolean inFilter) {}

    public record CellResponse(
            int id, int entryId, int columnId, boolean checked, String note, Instant updatedAt, Integer updatedBy) {}

    public record NoteHistoryEntryResponse(
            int id, String oldNote, String newNote, Integer changedBy, String changedByName, Instant changedAt) {}

    public record RestrictionResponse(
            List<String> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds,
            String mode) {}

    public record CreateRequest(
            String name, String description, List<ColumnSpecRequest> columns, RestrictionRequest restriction) {}

    public record UpdateRequest(String name, String description, RestrictionRequest restriction) {}

    public record ColumnSpecRequest(String label, String description) {}

    public record ColumnCreateRequest(String label, String description, Integer position) {}

    public record ColumnUpdateRequest(String label, String description, Integer position) {}

    public record ReorderColumnsRequest(List<Integer> orderedIds) {}

    public record RestrictionRequest(
            List<String> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds,
            String mode) {}

    public record AddMembersRequest(List<Integer> memberIds) {}

    public record AddMembersResponse(int added, int restored, int skipped) {}

    public record RefreshResponse(int added, int alreadyPresent) {}

    public record CellWriteRequest(boolean checked, String note) {}

    public record BulkSetRequest(List<Integer> entryIds, boolean checked) {}

    public record BulkSetResponse(int updated) {}
}
