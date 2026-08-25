/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.procedure.entity.Procedure;
import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import dev.chojo.ember.feature.procedure.entity.ProcedureStatus;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplate;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplateItem;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

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

    private void listTemplates(Context ctx) {
        var session = UserSession.from(ctx);
        boolean includeArchived = "true".equals(ctx.queryParam("archived"));
        ctx.json(procedureService.findTemplatesByStation(session.stationId(), includeArchived));
    }

    private void getTemplate(Context ctx) {
        int tid = pathInt(ctx, "tid");
        var template =
                requireOwnedOrNotFound(ctx, tid, procedureService::findTemplateById, ProcedureTemplate::stationId);
        var items = procedureService.findTemplateItems(tid);
        var deps = procedureService.findTemplateItemDependencies(tid);
        ctx.json(new TemplateDetail(template, items, deps));
    }

    private void createTemplate(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(TemplateRequest.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        ctx.json(procedureService.createTemplate(
                session.stationId(),
                req.name(),
                req.description(),
                session.member().id()));
    }

    private void updateTemplate(Context ctx) {
        int tid = pathInt(ctx, "tid");
        requireOwnedOrNotFound(ctx, tid, procedureService::findTemplateById, ProcedureTemplate::stationId);
        var req = ctx.bodyAsClass(TemplateRequest.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        procedureService.updateTemplate(tid, req.name(), req.description()).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void archiveTemplate(Context ctx) {
        int tid = pathInt(ctx, "tid");
        requireOwnedOrNotFound(ctx, tid, procedureService::findTemplateById, ProcedureTemplate::stationId);
        procedureService.archiveTemplate(tid);
        ctx.status(204);
    }

    private void createTemplateItem(Context ctx) {
        int tid = pathInt(ctx, "tid");
        requireOwnedOrNotFound(ctx, tid, procedureService::findTemplateById, ProcedureTemplate::stationId);
        var req = ctx.bodyAsClass(ItemRequest.class);
        if (req.title() == null || req.title().isBlank()) throw new BadRequestResponse("title is required");
        ctx.json(procedureService.createTemplateItem(
                tid, req.title(), req.description(), req.isPublic(), req.userAssigned(), req.position()));
    }

    private void updateTemplateItem(Context ctx) {
        requireOwnedOrNotFound(
                ctx, pathInt(ctx, "tid"), procedureService::findTemplateById, ProcedureTemplate::stationId);
        int iid = pathInt(ctx, "iid");
        var req = ctx.bodyAsClass(ItemRequest.class);
        if (!procedureService.updateTemplateItem(
                iid, req.title(), req.description(), req.isPublic(), req.userAssigned(), req.position())) {
            throw new NotFoundResponse();
        }
        ctx.status(204);
    }

    private void deleteTemplateItem(Context ctx) {
        requireOwnedOrNotFound(
                ctx, pathInt(ctx, "tid"), procedureService::findTemplateById, ProcedureTemplate::stationId);
        int iid = pathInt(ctx, "iid");
        if (!procedureService.deleteTemplateItem(iid)) throw new NotFoundResponse();
        ctx.status(204);
    }

    private void setTemplateDependencies(Context ctx) {
        int tid = pathInt(ctx, "tid");
        requireOwnedOrNotFound(ctx, tid, procedureService::findTemplateById, ProcedureTemplate::stationId);
        var req = ctx.bodyAsClass(DependencyRequest.class);
        var deps = req.dependencies() != null
                ? req.dependencies().stream()
                        .map(d -> new int[] {d.itemId(), d.dependsOnItemId()})
                        .toList()
                : List.<int[]>of();
        procedureService.setTemplateItemDependencies(tid, deps);
        ctx.status(204);
    }

    private void setProcedureDependencies(Context ctx) {
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        var req = ctx.bodyAsClass(DependencyRequest.class);
        var deps = req.dependencies() != null
                ? req.dependencies().stream()
                        .map(d -> new int[] {d.itemId(), d.dependsOnItemId()})
                        .toList()
                : List.<int[]>of();
        procedureService.setItemDependencies(rid, deps);
        ctx.status(204);
    }

    // ── Procedure endpoints ──

    private void listProcedures(Context ctx) {
        var session = UserSession.from(ctx);
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

    private void getProcedure(Context ctx) {
        var session = UserSession.from(ctx);
        int rid = pathInt(ctx, "rid");
        var procedure = requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);

        // Non-EDIT users can only see public procedures they're assigned to
        if (!session.hasPermission(StationPermission.PROCEDURE_EDIT)) {
            if (!procedure.isPublic()) throw new ForbiddenResponse();
            var assignees = procedureService.findAssigneeIds(rid);
            if (!assignees.contains(session.member().id())) throw new ForbiddenResponse();
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

    private void createProcedure(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CreateProcedureRequest.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        var procedure = procedureService.createProcedure(
                session.stationId(),
                req.templateId(),
                req.name(),
                req.description(),
                req.isPublic(),
                session.member().id(),
                req.dueAt(),
                req.assigneeIds() != null ? req.assigneeIds() : List.of());
        ctx.json(procedure);
    }

    private void updateProcedure(Context ctx) {
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        var req = ctx.bodyAsClass(UpdateProcedureRequest.class);
        if (!procedureService.updateProcedure(rid, req.name(), req.description(), req.isPublic(), req.dueAt())) {
            throw new NotFoundResponse();
        }
        ctx.json(procedureService.findProcedureById(rid).orElseThrow());
    }

    private void deleteProcedure(Context ctx) {
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        procedureService.deleteProcedure(rid);
        ctx.status(204);
    }

    private void resolveProcedure(Context ctx) {
        var session = UserSession.from(ctx);
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        if (!procedureService.resolveProcedure(rid, session.member().id())) {
            throw new BadRequestResponse("Already resolved");
        }
        ctx.json(procedureService.findProcedureById(rid).orElseThrow());
    }

    private void reopenProcedure(Context ctx) {
        var session = UserSession.from(ctx);
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        if (!procedureService.reopenProcedure(rid, session.member().id())) {
            throw new BadRequestResponse("Already open");
        }
        ctx.json(procedureService.findProcedureById(rid).orElseThrow());
    }

    // ── Assignee endpoints ──

    private void addAssignees(Context ctx) {
        var session = UserSession.from(ctx);
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        var req = ctx.bodyAsClass(AssigneeRequest.class);
        if (req.memberIds() == null || req.memberIds().isEmpty()) throw new BadRequestResponse("memberIds required");
        procedureService.addAssignees(rid, req.memberIds(), session.member().id());
        ctx.json(procedureService.findAssigneeIds(rid));
    }

    private void removeAssignee(Context ctx) {
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        int mid = pathInt(ctx, "mid");
        procedureService.removeAssignee(rid, mid);
        ctx.json(procedureService.findAssigneeIds(rid));
    }

    // ── Item endpoints ──

    private void addItem(Context ctx) {
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        var req = ctx.bodyAsClass(ItemRequest.class);
        if (req.title() == null || req.title().isBlank()) throw new BadRequestResponse("title is required");
        ctx.json(procedureService.createItem(
                rid, req.title(), req.description(), req.isPublic(), req.userAssigned(), req.position()));
    }

    private void editItem(Context ctx) {
        requireOwnedOrNotFound(ctx, pathInt(ctx, "rid"), procedureService::findProcedureById, Procedure::stationId);
        int iid = pathInt(ctx, "iid");
        var req = ctx.bodyAsClass(ItemRequest.class);
        if (!procedureService.updateItem(
                iid, req.title(), req.description(), req.isPublic(), req.userAssigned(), req.position())) {
            throw new NotFoundResponse();
        }
        ctx.status(204);
    }

    private void deleteItem(Context ctx) {
        requireOwnedOrNotFound(ctx, pathInt(ctx, "rid"), procedureService::findProcedureById, Procedure::stationId);
        int iid = pathInt(ctx, "iid");
        if (!procedureService.deleteItem(iid)) throw new NotFoundResponse();
        ctx.status(204);
    }

    private void patchItem(Context ctx) {
        var session = UserSession.from(ctx);
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        int iid = pathInt(ctx, "iid");
        var req = ctx.bodyAsClass(PatchItemRequest.class);

        if (req.checked() != null) {
            boolean hasEdit = session.hasPermission(StationPermission.PROCEDURE_EDIT);
            if (req.checked()) {
                // Non-EDIT users can only check user-assigned items they're assigned to
                if (!hasEdit) {
                    var item = procedureService.findItemById(iid).orElseThrow(NotFoundResponse::new);
                    if (!item.userAssigned()) {
                        throw new ForbiddenResponse("Only PROCEDURE_EDIT can check non-user-assigned items");
                    }
                    var assignees = procedureService.findAssigneeIds(rid);
                    if (!assignees.contains(session.member().id())) {
                        throw new ForbiddenResponse("Not assigned to this procedure");
                    }
                }
                if (!procedureService.checkItem(iid, session.member().id())) {
                    throw new BadRequestResponse("Cannot check item - dependencies not met or already checked");
                }
            } else {
                if (!hasEdit) {
                    throw new ForbiddenResponse("Only PROCEDURE_EDIT can uncheck items");
                }
                procedureService.uncheckItem(iid);
            }
        }
        if (req.note() != null) {
            procedureService.updateItemNote(iid, req.note());
        }
        ctx.json(procedureService.findItems(rid));
    }

    // ── Request/Response records ──

    public record TemplateRequest(String name, String description) {}

    public record TemplateDetail(
            ProcedureTemplate template, List<ProcedureTemplateItem> items, List<int[]> dependencies) {}

    public record ItemRequest(String title, String description, boolean isPublic, boolean userAssigned, int position) {}

    public record DependencyEntry(int itemId, int dependsOnItemId) {}

    public record DependencyRequest(List<DependencyEntry> dependencies) {}

    public record CreateProcedureRequest(
            Integer templateId,
            String name,
            String description,
            boolean isPublic,
            Instant dueAt,
            List<Integer> assigneeIds) {}

    public record UpdateProcedureRequest(String name, String description, boolean isPublic, Instant dueAt) {}

    public record AssigneeRequest(List<Integer> memberIds) {}

    public record PatchItemRequest(Boolean checked, String note) {}

    public record ProcedureDetail(
            Procedure procedure,
            List<ProcedureItem> items,
            List<int[]> dependencies,
            List<Integer> assigneeIds,
            List<MemberIdentity> assignees) {}
}
