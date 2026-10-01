/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.procedure.entity.Procedure;
import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import dev.chojo.ember.feature.procedure.entity.ProcedureStatus;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplate;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplateItem;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

@Singleton
public class ProcedureRoutes implements Routes {
    private final ProcedureService procedureService;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public ProcedureRoutes(ProcedureService procedureService, MemberIdentityFactory memberIdentityFactory) {
        this.procedureService = procedureService;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Templates
        routes.get(prefix + "/procedure-templates", this::listTemplates, StationPermission.PROCEDURE_MANAGER);
        routes.post(prefix + "/procedure-templates", this::createTemplate, StationPermission.PROCEDURE_MANAGER);
        routes.get(prefix + "/procedure-templates/{tid}", this::getTemplate, StationPermission.PROCEDURE_MANAGER);
        routes.put(prefix + "/procedure-templates/{tid}", this::updateTemplate, StationPermission.PROCEDURE_MANAGER);
        routes.delete(
                prefix + "/procedure-templates/{tid}", this::archiveTemplate, StationPermission.PROCEDURE_MANAGER);
        routes.post(
                prefix + "/procedure-templates/{tid}/items",
                this::createTemplateItem,
                StationPermission.PROCEDURE_MANAGER);
        routes.put(
                prefix + "/procedure-templates/{tid}/items/{iid}",
                this::updateTemplateItem,
                StationPermission.PROCEDURE_MANAGER);
        routes.delete(
                prefix + "/procedure-templates/{tid}/items/{iid}",
                this::deleteTemplateItem,
                StationPermission.PROCEDURE_MANAGER);
        routes.put(
                prefix + "/procedure-templates/{tid}/dependencies",
                this::setTemplateDependencies,
                StationPermission.PROCEDURE_MANAGER);

        // Procedures (USER can list/view their assigned procedures; PROCEDURE_READ sees all)
        routes.get(prefix + "/procedures", this::listProcedures, StationPermission.USER);
        routes.get(
                prefix + "/procedures/for-event/{eid}",
                this::listProceduresForOccurrence,
                StationPermission.PROCEDURE_EDIT);
        routes.post(prefix + "/procedures", this::createProcedure, StationPermission.PROCEDURE_EDIT);
        routes.get(prefix + "/procedures/{rid}", this::getProcedure, StationPermission.USER);
        routes.put(prefix + "/procedures/{rid}", this::updateProcedure, StationPermission.PROCEDURE_EDIT);
        routes.delete(prefix + "/procedures/{rid}", this::deleteProcedure, StationPermission.PROCEDURE_EDIT);
        routes.post(prefix + "/procedures/{rid}/resolve", this::resolveProcedure, StationPermission.PROCEDURE_EDIT);
        routes.post(prefix + "/procedures/{rid}/reopen", this::reopenProcedure, StationPermission.PROCEDURE_EDIT);

        // Assignees
        routes.post(prefix + "/procedures/{rid}/assignees", this::addAssignees, StationPermission.PROCEDURE_EDIT);
        routes.delete(
                prefix + "/procedures/{rid}/assignees/{mid}", this::removeAssignee, StationPermission.PROCEDURE_EDIT);

        // Items
        routes.post(prefix + "/procedures/{rid}/items", this::addItem, StationPermission.PROCEDURE_EDIT);
        routes.put(prefix + "/procedures/{rid}/items/{iid}", this::editItem, StationPermission.PROCEDURE_EDIT);
        routes.delete(prefix + "/procedures/{rid}/items/{iid}", this::deleteItem, StationPermission.PROCEDURE_EDIT);
        routes.patch(prefix + "/procedures/{rid}/items/{iid}", this::patchItem, StationPermission.USER);
        routes.put(
                prefix + "/procedures/{rid}/dependencies",
                this::setProcedureDependencies,
                StationPermission.PROCEDURE_EDIT);
    }

    // ── Template endpoints ──

    @OpenApi(
            path = "/api/v1/procedure-templates",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProcedureTemplate[].class)))
    private void listTemplates(Context ctx) {
        var session = StationSession.from(ctx);
        boolean includeArchived = "true".equals(ctx.queryParam("archived"));
        ctx.json(procedureService.findTemplatesByStation(session.stationId(), includeArchived));
    }

    @OpenApi(
            path = "/api/v1/procedure-templates/{tid}",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProcedureTemplateDetail.class)))
    private void getTemplate(Context ctx) {
        int tid = pathInt(ctx, "tid");
        var template =
                requireOwnedOrNotFound(ctx, tid, procedureService::findTemplateById, ProcedureTemplate::stationId);
        var items = procedureService.findTemplateItems(tid);
        var deps = procedureService.findTemplateItemDependencies(tid);
        ctx.json(new ProcedureTemplateDetail(template, items, deps));
    }

    @OpenApi(
            path = "/api/v1/procedure-templates",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProcedureTemplateRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProcedureTemplate.class)))
    private void createTemplate(Context ctx) {
        var session = StationSession.from(ctx);
        var req = ctx.bodyAsClass(ProcedureTemplateRequest.class);
        if (req.name() == null || req.name().isBlank()) throw Refusal.PROCEDURE_TEMPLATE_NEEDS_A_NAME.raise();
        ctx.json(procedureService.createTemplate(
                session.stationId(),
                req.name(),
                req.description(),
                session.member().id()));
    }

    @OpenApi(
            path = "/api/v1/procedure-templates/{tid}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProcedureTemplateRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProcedureTemplate.class)))
    private void updateTemplate(Context ctx) {
        int tid = pathInt(ctx, "tid");
        requireOwnedOrNotFound(ctx, tid, procedureService::findTemplateById, ProcedureTemplate::stationId);
        var req = ctx.bodyAsClass(ProcedureTemplateRequest.class);
        if (req.name() == null || req.name().isBlank()) throw Refusal.PROCEDURE_TEMPLATE_RENAME_NEEDS_A_NAME.raise();
        procedureService.updateTemplate(tid, req.name(), req.description()).ifPresentOrElse(ctx::json, () -> {
            throw Refusal.PROCEDURE_TEMPLATE_NOT_HERE_TO_CHANGE.raise();
        });
    }

    @OpenApi(
            path = "/api/v1/procedure-templates/{tid}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void archiveTemplate(Context ctx) {
        int tid = pathInt(ctx, "tid");
        requireOwnedOrNotFound(ctx, tid, procedureService::findTemplateById, ProcedureTemplate::stationId);
        procedureService.archiveTemplate(tid);
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/procedure-templates/{tid}/items",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProcedureItemRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProcedureTemplateItem.class)))
    private void createTemplateItem(Context ctx) {
        int tid = pathInt(ctx, "tid");
        requireOwnedOrNotFound(ctx, tid, procedureService::findTemplateById, ProcedureTemplate::stationId);
        var req = ctx.bodyAsClass(ProcedureItemRequest.class);
        if (req.title() == null || req.title().isBlank()) throw Refusal.PROCEDURE_TEMPLATE_STEP_NEEDS_A_TITLE.raise();
        ctx.json(procedureService.createTemplateItem(
                tid, req.title(), req.description(), req.isPublic(), req.userAssigned(), req.position()));
    }

    @OpenApi(
            path = "/api/v1/procedure-templates/{tid}/items/{iid}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProcedureItemRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void updateTemplateItem(Context ctx) {
        requireOwnedOrNotFound(
                ctx, pathInt(ctx, "tid"), procedureService::findTemplateById, ProcedureTemplate::stationId);
        int iid = pathInt(ctx, "iid");
        var req = ctx.bodyAsClass(ProcedureItemRequest.class);
        if (!procedureService.updateTemplateItem(
                iid, req.title(), req.description(), req.isPublic(), req.userAssigned(), req.position())) {
            throw Refusal.PROCEDURE_TEMPLATE_STEP_NOT_HERE_TO_CHANGE.raise();
        }
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/procedure-templates/{tid}/items/{iid}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteTemplateItem(Context ctx) {
        requireOwnedOrNotFound(
                ctx, pathInt(ctx, "tid"), procedureService::findTemplateById, ProcedureTemplate::stationId);
        int iid = pathInt(ctx, "iid");
        if (!procedureService.deleteTemplateItem(iid)) throw Refusal.PROCEDURE_TEMPLATE_STEP_NOT_HERE_TO_DELETE.raise();
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/procedure-templates/{tid}/dependencies",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DependencyRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setTemplateDependencies(Context ctx) {
        int tid = pathInt(ctx, "tid");
        requireOwnedOrNotFound(ctx, tid, procedureService::findTemplateById, ProcedureTemplate::stationId);
        var deps = dependencyPairs(ctx.bodyAsClass(DependencyRequest.class));
        procedureService.setTemplateItemDependencies(tid, deps);
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}/dependencies",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DependencyRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setProcedureDependencies(Context ctx) {
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        var deps = dependencyPairs(ctx.bodyAsClass(DependencyRequest.class));
        procedureService.setItemDependencies(rid, deps);
        ctx.status(204);
    }

    private static List<int[]> dependencyPairs(DependencyRequest request) {
        return Objects.requireNonNullElse(request.dependencies(), List.<DependencyEntry>of()).stream()
                .map(d -> new int[] {d.itemId(), d.dependsOnItemId()})
                .toList();
    }

    // ── Procedure endpoints ──

    @OpenApi(
            path = "/api/v1/procedures",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Procedure[].class)))
    private void listProcedures(Context ctx) {
        var session = StationSession.from(ctx);
        String statusParam = ctx.queryParam("status");
        ProcedureStatus status = statusParam != null ? ProcedureStatus.valueOf(statusParam) : null;
        String assigneeParam = ctx.queryParam("assignee");

        boolean canEdit = session.hasPermission(StationPermission.PROCEDURE_EDIT);
        if ("me".equals(assigneeParam) || !canEdit) {
            // Non-EDIT users only see public procedures they're assigned to
            boolean publicOnly = !session.hasPermission(StationPermission.PROCEDURE_READ);
            ctx.json(procedureService.findProceduresByAssignee(
                    session.stationId(), session.member().id(), status, publicOnly));
        } else {
            ctx.json(procedureService.findProceduresByStation(session.stationId(), status));
        }
    }

    /**
     * What has already been prepared for one date of one appointment, so a second press of the
     * button that made it offers the list rather than a copy of it.
     */
    @OpenApi(
            path = "/api/v1/procedures/for-event/{eid}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Procedure[].class)))
    private void listProceduresForOccurrence(Context ctx) {
        var session = StationSession.from(ctx);
        int eventId = pathInt(ctx, "eid");
        String date = ctx.queryParam("date");
        if (date == null || date.isBlank()) throw Refusal.PROCEDURE_OCCURRENCE_DAY_MISSING.raise();
        LocalDate eventDate;
        try {
            eventDate = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw Refusal.PROCEDURE_OCCURRENCE_DAY_NOT_A_DATE.raise();
        }
        ctx.json(procedureService.findProceduresByOccurrence(session.stationId(), eventId, eventDate));
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProcedureDetail.class)))
    private void getProcedure(Context ctx) {
        var session = StationSession.from(ctx);
        int rid = pathInt(ctx, "rid");
        var procedure = requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);

        if (!session.hasPermission(StationPermission.PROCEDURE_EDIT)) {
            if (!procedure.isPublic()) throw Refusal.PROCEDURE_NOT_PUBLIC.raise();
            var assignees = procedureService.findAssigneeIds(rid);
            if (!assignees.contains(session.member().id())) throw Refusal.PROCEDURE_NOT_YOURS.raise();
        }

        var items = procedureService.findItems(rid);
        var deps = procedureService.findItemDependencies(rid);
        var assigneeIds = procedureService.findAssigneeIds(rid);
        var assignees =
                assigneeIds.stream().map(memberIdentityFactory::fromMemberId).toList();

        // Filter private items for non-EDIT users
        if (!session.hasPermission(StationPermission.PROCEDURE_EDIT)) {
            items = items.stream().filter(ProcedureItem::isPublic).toList();
        }

        ctx.json(new ProcedureDetail(procedure, items, deps, assigneeIds, assignees));
    }

    @OpenApi(
            path = "/api/v1/procedures",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateProcedureRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Procedure.class)))
    private void createProcedure(Context ctx) {
        var session = StationSession.from(ctx);
        var req = ctx.bodyAsClass(CreateProcedureRequest.class);
        if (req.name() == null || req.name().isBlank()) throw Refusal.PROCEDURE_NEEDS_A_NAME.raise();
        var procedure = procedureService.createProcedure(
                session.stationId(),
                req.templateId(),
                req.name(),
                req.description(),
                req.isPublic(),
                session.member().id(),
                req.dueAt(),
                Objects.requireNonNullElse(req.assigneeIds(), List.of()),
                req.eventDate() != null ? req.eventId() : null,
                req.eventId() != null ? req.eventDate() : null);
        ctx.json(procedure);
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateProcedureRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Procedure.class)))
    private void updateProcedure(Context ctx) {
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        var req = ctx.bodyAsClass(UpdateProcedureRequest.class);
        if (!procedureService.updateProcedure(rid, req.name(), req.description(), req.isPublic(), req.dueAt())) {
            throw Refusal.PROCEDURE_NOT_HERE_TO_CHANGE.raise();
        }
        ctx.json(procedureService.findProcedureById(rid).orElseThrow(Refusal.PROCEDURE_NOT_HERE_AFTER_CHANGE::raise));
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteProcedure(Context ctx) {
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        procedureService.deleteProcedure(rid);
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}/resolve",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Procedure.class)))
    private void resolveProcedure(Context ctx) {
        var session = StationSession.from(ctx);
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        if (!procedureService.resolveProcedure(rid, session.member().id())) {
            throw Refusal.PROCEDURE_ALREADY_DONE.raise();
        }
        ctx.json(
                procedureService.findProcedureById(rid).orElseThrow(Refusal.PROCEDURE_NOT_HERE_AFTER_RESOLVING::raise));
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}/reopen",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Procedure.class)))
    private void reopenProcedure(Context ctx) {
        var session = StationSession.from(ctx);
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        if (!procedureService.reopenProcedure(rid, session.member().id())) {
            throw Refusal.PROCEDURE_ALREADY_OPEN.raise();
        }
        ctx.json(
                procedureService.findProcedureById(rid).orElseThrow(Refusal.PROCEDURE_NOT_HERE_AFTER_REOPENING::raise));
    }

    // ── Assignee endpoints ──

    @OpenApi(
            path = "/api/v1/procedures/{rid}/assignees",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AssigneeRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Integer[].class)))
    private void addAssignees(Context ctx) {
        var session = StationSession.from(ctx);
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        var req = ctx.bodyAsClass(AssigneeRequest.class);
        if (req.memberIds() == null || req.memberIds().isEmpty()) throw Refusal.PROCEDURE_NAMES_NOBODY.raise();
        procedureService.addAssignees(rid, req.memberIds(), session.member().id());
        ctx.json(procedureService.findAssigneeIds(rid));
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}/assignees/{mid}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Integer[].class)))
    private void removeAssignee(Context ctx) {
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        int mid = pathInt(ctx, "mid");
        procedureService.removeAssignee(rid, mid);
        ctx.json(procedureService.findAssigneeIds(rid));
    }

    // ── Item endpoints ──

    @OpenApi(
            path = "/api/v1/procedures/{rid}/items",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProcedureItemRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProcedureItem.class)))
    private void addItem(Context ctx) {
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        var req = ctx.bodyAsClass(ProcedureItemRequest.class);
        if (req.title() == null || req.title().isBlank()) throw Refusal.PROCEDURE_STEP_NEEDS_A_TITLE.raise();
        ctx.json(procedureService.createItem(
                rid, req.title(), req.description(), req.isPublic(), req.userAssigned(), req.position()));
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}/items/{iid}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProcedureItemRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void editItem(Context ctx) {
        requireOwnedOrNotFound(ctx, pathInt(ctx, "rid"), procedureService::findProcedureById, Procedure::stationId);
        int iid = pathInt(ctx, "iid");
        var req = ctx.bodyAsClass(ProcedureItemRequest.class);
        if (!procedureService.updateItem(
                iid, req.title(), req.description(), req.isPublic(), req.userAssigned(), req.position())) {
            throw Refusal.PROCEDURE_STEP_NOT_HERE_TO_CHANGE.raise();
        }
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}/items/{iid}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteItem(Context ctx) {
        requireOwnedOrNotFound(ctx, pathInt(ctx, "rid"), procedureService::findProcedureById, Procedure::stationId);
        int iid = pathInt(ctx, "iid");
        if (!procedureService.deleteItem(iid)) throw Refusal.PROCEDURE_STEP_NOT_HERE_TO_DELETE.raise();
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}/items/{iid}",
            methods = HttpMethod.PATCH,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PatchItemRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProcedureItem[].class)))
    private void patchItem(Context ctx) {
        var session = StationSession.from(ctx);
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        int iid = pathInt(ctx, "iid");
        var req = ctx.bodyAsClass(PatchItemRequest.class);

        if (req.checked() != null) {
            boolean hasEdit = session.hasPermission(StationPermission.PROCEDURE_EDIT);
            if (req.checked()) {
                // Non-EDIT users can only check user-assigned items they're assigned to
                if (!hasEdit) {
                    var item = procedureService
                            .findItemById(iid)
                            .orElseThrow(Refusal.PROCEDURE_STEP_NOT_HERE_TO_TICK::raise);
                    if (!item.userAssigned()) {
                        throw Refusal.PROCEDURE_STEP_NOT_YOURS_TO_TICK.raise();
                    }
                    var assignees = procedureService.findAssigneeIds(rid);
                    if (!assignees.contains(session.member().id())) {
                        throw Refusal.PROCEDURE_NOT_HANDED_TO_YOU.raise();
                    }
                }
                if (!procedureService.checkItem(iid, session.member().id())) {
                    throw Refusal.PROCEDURE_STEP_NOT_READY_TO_TICK.raise();
                }
            } else {
                if (!hasEdit) {
                    throw Refusal.PROCEDURE_STEP_NOT_YOURS_TO_UNTICK.raise();
                }
                procedureService.uncheckItem(iid);
            }
        }
        String note = req.note();
        if (note != null) {
            procedureService.updateItemNote(iid, note);
        }
        ctx.json(procedureService.findItems(rid));
    }

    // ── Request/Response records ──

    /**
     * A procedure template as it is created or renamed.
     *
     * @param description what the template is for, or {@code null} for nothing
     */
    public record ProcedureTemplateRequest(
            String name, @Nullable String description) {}

    /**
     * A template with its steps and the order they depend on each other in.
     *
     * @param dependencies pairs of step ids: the step first, the step it waits for second
     */
    public record ProcedureTemplateDetail(
            ProcedureTemplate template, List<ProcedureTemplateItem> items, List<int[]> dependencies) {}

    /**
     * A step of a template or a procedure as it is added or changed.
     *
     * @param description what the step asks for, or {@code null} for nothing
     */
    public record ProcedureItemRequest(
            String title, @Nullable String description, boolean isPublic, boolean userAssigned, int position) {}

    public record DependencyEntry(int itemId, int dependsOnItemId) {}

    /**
     * Every dependency between the steps, replacing the ones there were.
     *
     * @param dependencies the dependencies, or {@code null} for none
     */
    public record DependencyRequest(@Nullable List<DependencyEntry> dependencies) {}

    /**
     * @param templateId  the template whose steps the procedure starts with, or {@code null} for none
     * @param description what the procedure is about, or {@code null} for nothing
     * @param dueAt       when it is due, or {@code null} for no date
     * @param assigneeIds the members it is handed to, or {@code null} for nobody yet
     * @param eventId     the appointment the procedure is being prepared for, or {@code null}
     * @param eventDate   the one occurrence of that appointment. Both are recorded only when both are
     *                    given: an appointment without a date names every occurrence it has ever had
     */
    public record CreateProcedureRequest(
            @Nullable Integer templateId,
            String name,
            @Nullable String description,
            boolean isPublic,
            @Nullable Instant dueAt,
            @Nullable List<Integer> assigneeIds,
            @Nullable Integer eventId,
            @Nullable LocalDate eventDate) {}

    /**
     * @param description what the procedure is about, or {@code null} for nothing
     * @param dueAt       when it is due, or {@code null} for no date
     */
    public record UpdateProcedureRequest(
            String name,
            @Nullable String description,
            boolean isPublic,
            @Nullable Instant dueAt) {}

    public record AssigneeRequest(List<Integer> memberIds) {}

    /**
     * What a member changes on one step: its tick, its note, or both.
     *
     * @param checked the tick, or {@code null} to leave it
     * @param note    the note, or {@code null} to leave it
     */
    public record PatchItemRequest(
            @Nullable Boolean checked, @Nullable String note) {}

    public record ProcedureDetail(
            Procedure procedure,
            List<ProcedureItem> items,
            List<int[]> dependencies,
            List<Integer> assigneeIds,
            List<MemberIdentity> assignees) {}
}
