/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.ProcedureRefusal;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.procedure.entity.Procedure;
import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import dev.chojo.ember.feature.procedure.entity.ProcedureStatus;
import dev.chojo.ember.feature.procedure.entity.ProcedureStepKind;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplate;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplateItem;
import dev.chojo.ember.feature.procedure.service.DocumentStepTemplates.StepDocument;
import dev.chojo.ember.feature.procedure.service.ProcedureDraft;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.procedure.service.StepDocumentState;
import dev.chojo.ember.feature.procedure.service.StepDraft;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
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
    /** How many procedures one request starts at most, one per member. */
    static final int MAX_MEMBERS_AT_ONCE = 100;

    private final ProcedureService procedureService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final GuardianPolicy guardians;

    @Inject
    public ProcedureRoutes(
            ProcedureService procedureService, MemberIdentityFactory memberIdentityFactory, GuardianPolicy guardians) {
        this.procedureService = procedureService;
        this.memberIdentityFactory = memberIdentityFactory;
        this.guardians = guardians;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
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

        routes.get(prefix + "/procedures", this::listProcedures, StationPermission.USER);
        routes.get(
                prefix + "/procedures/for-event/{eid}",
                this::listProceduresForOccurrence,
                StationPermission.PROCEDURE_EDIT);
        routes.post(prefix + "/procedures", this::createProcedure, StationPermission.PROCEDURE_EDIT);
        routes.post(
                prefix + "/procedures/for-members", this::createProceduresForMembers, StationPermission.PROCEDURE_EDIT);
        routes.get(prefix + "/procedures/about-me", this::listProceduresAboutMe, StationPermission.USER);
        routes.get(prefix + "/procedures/{rid}", this::getProcedure, StationPermission.USER);
        routes.put(prefix + "/procedures/{rid}", this::updateProcedure, StationPermission.PROCEDURE_EDIT);
        routes.delete(prefix + "/procedures/{rid}", this::deleteProcedure, StationPermission.PROCEDURE_EDIT);
        routes.post(prefix + "/procedures/{rid}/resolve", this::resolveProcedure, StationPermission.PROCEDURE_EDIT);
        routes.post(prefix + "/procedures/{rid}/reopen", this::reopenProcedure, StationPermission.PROCEDURE_EDIT);

        routes.put(prefix + "/procedures/{rid}/party", this::setParty, StationPermission.PROCEDURE_EDIT);
        routes.put(prefix + "/procedures/{rid}/about", this::setAbout, StationPermission.PROCEDURE_EDIT);

        routes.post(prefix + "/procedures/{rid}/items", this::addItem, StationPermission.PROCEDURE_EDIT);
        routes.put(prefix + "/procedures/{rid}/items/{iid}", this::editItem, StationPermission.PROCEDURE_EDIT);
        routes.delete(prefix + "/procedures/{rid}/items/{iid}", this::deleteItem, StationPermission.PROCEDURE_EDIT);
        routes.patch(prefix + "/procedures/{rid}/items/{iid}", this::patchItem, StationPermission.USER);
        routes.post(prefix + "/procedures/{rid}/items/{iid}/ask", this::askAgain, StationPermission.PROCEDURE_EDIT);
        routes.put(
                prefix + "/procedures/{rid}/dependencies",
                this::setProcedureDependencies,
                StationPermission.PROCEDURE_EDIT);
    }

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
        ctx.json(new ProcedureTemplateDetail(
                template,
                items,
                deps,
                procedureService.templatePartyOf(tid),
                procedureService.stepDocuments(
                        template.stationId(),
                        items.stream()
                                .map(ProcedureTemplateItem::documentTemplateId)
                                .toList())));
    }

    @OpenApi(
            path = "/api/v1/procedure-templates",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProcedureTemplateRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProcedureTemplate.class)))
    private void createTemplate(Context ctx) {
        var session = StationSession.from(ctx);
        var req = ctx.bodyAsClass(ProcedureTemplateRequest.class);
        if (req.name() == null || req.name().isBlank()) throw ProcedureRefusal.PROCEDURE_TEMPLATE_NEEDS_A_NAME.raise();
        ctx.json(procedureService.createTemplate(
                session.stationId(),
                req.name(),
                req.description(),
                session.member().id(),
                req.party()));
    }

    @OpenApi(
            path = "/api/v1/procedure-templates/{tid}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProcedureTemplateRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProcedureTemplate.class)))
    private void updateTemplate(Context ctx) {
        int tid = pathInt(ctx, "tid");
        var template =
                requireOwnedOrNotFound(ctx, tid, procedureService::findTemplateById, ProcedureTemplate::stationId);
        var req = ctx.bodyAsClass(ProcedureTemplateRequest.class);
        if (req.name() == null || req.name().isBlank())
            throw ProcedureRefusal.PROCEDURE_TEMPLATE_RENAME_NEEDS_A_NAME.raise();
        ctx.json(procedureService
                .updateTemplate(template.stationId(), tid, req.name(), req.description(), req.party())
                .orElseThrow(ProcedureRefusal.PROCEDURE_TEMPLATE_NOT_HERE_TO_CHANGE::raise));
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
        var template =
                requireOwnedOrNotFound(ctx, tid, procedureService::findTemplateById, ProcedureTemplate::stationId);
        var req = ctx.bodyAsClass(ProcedureItemRequest.class);
        if (req.title() == null || req.title().isBlank())
            throw ProcedureRefusal.PROCEDURE_TEMPLATE_STEP_NEEDS_A_TITLE.raise();
        ctx.json(procedureService.addTemplateStep(template, req.draft(), generatesDocuments(ctx)));
    }

    @OpenApi(
            path = "/api/v1/procedure-templates/{tid}/items/{iid}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProcedureItemRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void updateTemplateItem(Context ctx) {
        var step = requireTemplateStep(ctx);
        var req = ctx.bodyAsClass(ProcedureItemRequest.class);
        if (!procedureService.editTemplateStep(
                StationSession.from(ctx).stationId(), step, req.draft(), generatesDocuments(ctx))) {
            throw ProcedureRefusal.PROCEDURE_TEMPLATE_STEP_NOT_HERE_TO_CHANGE.raise();
        }
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/procedure-templates/{tid}/items/{iid}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteTemplateItem(Context ctx) {
        int iid = requireTemplateStep(ctx).id();
        if (!procedureService.deleteTemplateItem(iid))
            throw ProcedureRefusal.PROCEDURE_TEMPLATE_STEP_NOT_HERE_TO_DELETE.raise();
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
        procedureService.setItemDependencies(
                rid, deps, StationSession.from(ctx).member().id());
        ctx.status(204);
    }

    private static List<int[]> dependencyPairs(DependencyRequest request) {
        return Objects.requireNonNullElse(request.dependencies(), List.<DependencyEntry>of()).stream()
                .map(d -> new int[] {d.itemId(), d.dependsOnItemId()})
                .toList();
    }

    @OpenApi(
            path = "/api/v1/procedures",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Procedure[].class)))
    private void listProcedures(Context ctx) {
        var session = StationSession.from(ctx);
        var status = statusParam(ctx);
        String assigneeParam = ctx.queryParam("assignee");

        boolean canEdit = session.hasPermission(StationPermission.PROCEDURE_EDIT);
        if ("me".equals(assigneeParam) || !canEdit) {
            boolean publicOnly = !session.hasPermission(StationPermission.PROCEDURE_READ);
            ctx.json(procedureService.findProceduresRunBy(
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
        if (date == null || date.isBlank()) throw ProcedureRefusal.PROCEDURE_OCCURRENCE_DAY_MISSING.raise();
        LocalDate eventDate;
        try {
            eventDate = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw ProcedureRefusal.PROCEDURE_OCCURRENCE_DAY_NOT_A_DATE.raise();
        }
        ctx.json(procedureService.findProceduresByOccurrence(session.stationId(), eventId, eventDate));
    }

    @OpenApi(
            path = "/api/v1/procedures/about-me",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Procedure[].class)))
    private void listProceduresAboutMe(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(procedureService.findPublicAbout(
                session.stationId(), guardians.household(session.user()), statusParam(ctx)));
    }

    /** The state a list is narrowed to, refused where it names none there is; null for every state. */
    private static @Nullable ProcedureStatus statusParam(Context ctx) {
        String raw = ctx.queryParam("status");
        if (raw == null) return null;
        try {
            return ProcedureStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw ProcedureRefusal.PROCEDURE_STATUS_UNKNOWN.raise();
        }
    }

    /** Whether the reader is the member a procedure is about, or looks after them. */
    private boolean readsAbout(StationSession session, Procedure procedure) {
        var aboutMemberId = procedure.aboutMemberId();
        return aboutMemberId != null && guardians.mayActFor(session.user(), aboutMemberId);
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProcedureDetail.class)))
    private void getProcedure(Context ctx) {
        var session = StationSession.from(ctx);
        int rid = pathInt(ctx, "rid");
        var procedure = requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);

        boolean edits = session.hasPermission(StationPermission.PROCEDURE_EDIT);
        boolean runs = edits || procedureService.runBy(rid, session.member().id());
        boolean readsAbout = readsAbout(session, procedure);
        if (!edits) {
            if (!procedure.isPublic()) throw ProcedureRefusal.PROCEDURE_NOT_PUBLIC.raise();
            if (!runs && !readsAbout) throw ProcedureRefusal.PROCEDURE_NOT_YOURS.raise();
        }

        var items = procedureService.findItems(rid);
        var deps = procedureService.findItemDependencies(rid);
        var party = procedureService.partyOf(rid);
        var named = party.toSelection().memberIds().stream()
                .map(memberIdentityFactory::fromMemberId)
                .filter(Objects::nonNull)
                .toList();

        if (!edits) items = items.stream().filter(ProcedureItem::isPublic).toList();
        if (!runs) items = items.stream().map(ProcedureItem::withoutNote).toList();

        var aboutMemberId = procedure.aboutMemberId();
        var about = aboutMemberId == null ? null : memberIdentityFactory.fromMemberId(aboutMemberId);
        ctx.json(new ProcedureDetail(
                procedure,
                items,
                deps,
                party,
                named,
                about,
                procedureService.documentStates(items),
                runs,
                readsAbout,
                procedureService.stepDocuments(
                        procedure.stationId(),
                        items.stream().map(ProcedureItem::documentTemplateId).toList())));
    }

    @OpenApi(
            path = "/api/v1/procedures",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateProcedureRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Procedure.class)))
    private void createProcedure(Context ctx) {
        var session = StationSession.from(ctx);
        var req = ctx.bodyAsClass(CreateProcedureRequest.class);
        if (req.name() == null || req.name().isBlank()) throw ProcedureRefusal.PROCEDURE_NEEDS_A_NAME.raise();
        if (req.aboutMemberId() == null) throw ProcedureRefusal.PROCEDURE_ABOUT_MISSING.raise();
        requireTemplateToStart(session, req.templateId());
        var draft = new ProcedureDraft(
                session.stationId(),
                req.templateId(),
                req.name(),
                req.description(),
                req.isPublic(),
                session.member().id(),
                req.dueAt(),
                req.party(),
                req.eventDate() != null ? req.eventId() : null,
                req.eventId() != null ? req.eventDate() : null);
        ctx.json(procedureService.createProcedure(draft, req.aboutMemberId()));
    }

    /**
     * Starts one procedure from a template for each of the given members, each about that member and named
     * after them, all with the same managing party. Every member is checked before any procedure is written,
     * so a member a document step's template is not meant for refuses the whole request.
     */
    @OpenApi(
            path = "/api/v1/procedures/for-members",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateProceduresRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Procedure[].class)))
    private void createProceduresForMembers(Context ctx) {
        var session = StationSession.from(ctx);
        var req = ctx.bodyAsClass(CreateProceduresRequest.class);
        if (req.name() == null || req.name().isBlank()) throw ProcedureRefusal.PROCEDURE_NEEDS_A_NAME.raise();
        var asked = req.memberIds();
        var memberIds = asked == null
                ? List.<Integer>of()
                : asked.stream().filter(Objects::nonNull).distinct().toList();
        if (memberIds.isEmpty()) throw ProcedureRefusal.PROCEDURE_ABOUT_MISSING.raise();
        if (memberIds.size() > MAX_MEMBERS_AT_ONCE) throw ProcedureRefusal.PROCEDURE_TOO_MANY_AT_ONCE.raise();
        requireTemplateToStart(session, req.templateId());
        var named = memberIds.stream()
                .map(memberId -> new ProcedureService.NamedMember(memberId, nameFor(req.name(), memberId)))
                .toList();
        var draft = new ProcedureDraft(
                session.stationId(),
                req.templateId(),
                req.name().trim(),
                req.description(),
                req.isPublic(),
                session.member().id(),
                req.dueAt(),
                req.party(),
                req.eventDate() != null ? req.eventId() : null,
                req.eventId() != null ? req.eventDate() : null);
        ctx.json(procedureService.createForMembers(draft, named));
    }

    /** The name of one member's procedure: the name given, followed by the member's name. */
    private String nameFor(String name, int memberId) {
        var identity = memberIdentityFactory.fromMemberId(memberId);
        return ProcedureService.namedAfter(name, identity == null ? null : identity.name());
    }

    /** Refuses a template of another station, and one asking to sign for somebody who generates no documents. */
    private void requireTemplateToStart(StationSession session, @Nullable Integer templateId) {
        if (templateId == null) return;
        procedureService
                .findTemplateById(templateId)
                .filter(template -> template.stationId() == session.stationId())
                .orElseThrow(ProcedureRefusal.PROCEDURE_TEMPLATE_NOT_HERE_TO_START::raise);
        if (!session.hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER) && procedureService.asksToSign(templateId)) {
            throw ProcedureRefusal.PROCEDURE_STEP_DOCUMENT_NOT_YOURS.raise();
        }
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
            throw ProcedureRefusal.PROCEDURE_NOT_HERE_TO_CHANGE.raise();
        }
        ctx.json(procedureService
                .findProcedureById(rid)
                .orElseThrow(ProcedureRefusal.PROCEDURE_NOT_HERE_AFTER_CHANGE::raise));
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteProcedure(Context ctx) {
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        procedureService.deleteProcedure(rid, StationSession.from(ctx).member().id());
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
            throw ProcedureRefusal.PROCEDURE_ALREADY_DONE.raise();
        }
        ctx.json(procedureService
                .findProcedureById(rid)
                .orElseThrow(ProcedureRefusal.PROCEDURE_NOT_HERE_AFTER_RESOLVING::raise));
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
            throw ProcedureRefusal.PROCEDURE_ALREADY_OPEN.raise();
        }
        ctx.json(procedureService
                .findProcedureById(rid)
                .orElseThrow(ProcedureRefusal.PROCEDURE_NOT_HERE_AFTER_REOPENING::raise));
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}/about",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AboutRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Procedure.class)))
    private void setAbout(Context ctx) {
        var session = StationSession.from(ctx);
        int rid = pathInt(ctx, "rid");
        var procedure = requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        var req = ctx.bodyAsClass(AboutRequest.class);
        var memberId = req == null ? null : req.memberId();
        if (memberId == null) throw ProcedureRefusal.PROCEDURE_ABOUT_MISSING.raise();
        procedureService.setAbout(
                procedure,
                memberId,
                session.member().id(),
                session.hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER),
                !Boolean.FALSE.equals(req.ask()));
        ctx.json(procedureService
                .findProcedureById(rid)
                .orElseThrow(ProcedureRefusal.PROCEDURE_NOT_HERE_AFTER_CHANGE::raise));
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}/party",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RestrictionAudience.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RestrictionAudience.class)))
    private void setParty(Context ctx) {
        var session = StationSession.from(ctx);
        int rid = pathInt(ctx, "rid");
        requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        var party = ctx.bodyAsClass(RestrictionAudience.class);
        if (party == null) throw ProcedureRefusal.PROCEDURE_PARTY_MISSING.raise();
        procedureService.setParty(rid, party, session.member().id());
        ctx.json(procedureService.partyOf(rid));
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}/items",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProcedureItemRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProcedureItem.class)))
    private void addItem(Context ctx) {
        int rid = pathInt(ctx, "rid");
        var procedure = requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        var req = ctx.bodyAsClass(ProcedureItemRequest.class);
        if (req.title() == null || req.title().isBlank()) throw ProcedureRefusal.PROCEDURE_STEP_NEEDS_A_TITLE.raise();
        ctx.json(procedureService.addStep(procedure, req.draft(), generatesDocuments(ctx)));
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}/items/{iid}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProcedureItemRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void editItem(Context ctx) {
        var owned = requireProcedureStep(ctx);
        var req = ctx.bodyAsClass(ProcedureItemRequest.class);
        if (!procedureService.editStep(owned.procedure(), owned.step(), req.draft(), generatesDocuments(ctx))) {
            throw ProcedureRefusal.PROCEDURE_STEP_NOT_HERE_TO_CHANGE.raise();
        }
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}/items/{iid}/ask",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "204"))
    private void askAgain(Context ctx) {
        var step = requireProcedureStep(ctx).step();
        procedureService.askAgain(
                step.procedureId(), step.id(), StationSession.from(ctx).member().id());
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}/items/{iid}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteItem(Context ctx) {
        var step = requireProcedureStep(ctx).step();
        if (!procedureService.deleteItem(step, StationSession.from(ctx).member().id())) {
            throw ProcedureRefusal.PROCEDURE_STEP_NOT_HERE_TO_DELETE.raise();
        }
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/procedures/{rid}/items/{iid}",
            methods = HttpMethod.PATCH,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PatchItemRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProcedureItem[].class)))
    private void patchItem(Context ctx) {
        var session = StationSession.from(ctx);
        var owned = requireProcedureStep(ctx);
        var item = owned.step();
        int rid = item.procedureId();
        int iid = item.id();
        var req = ctx.bodyAsClass(PatchItemRequest.class);
        boolean hasEdit = session.hasPermission(StationPermission.PROCEDURE_EDIT);
        String note = req.note();
        if (note != null && !hasEdit) throw ProcedureRefusal.PROCEDURE_STEP_NOTE_NOT_YOURS.raise();

        if (req.checked() != null) {
            if (item.asksToSign()) {
                throw ProcedureRefusal.PROCEDURE_STEP_SETTLED_BY_SIGNATURES.raise();
            }
            if (req.checked()) {
                if (!hasEdit) requireTickableByParty(session, owned);
                if (!procedureService.checkItem(iid, session.member().id())) {
                    throw ProcedureRefusal.PROCEDURE_STEP_NOT_READY_TO_TICK.raise();
                }
            } else {
                if (!hasEdit) {
                    throw ProcedureRefusal.PROCEDURE_STEP_NOT_YOURS_TO_UNTICK.raise();
                }
                procedureService.uncheckItem(iid);
            }
        }
        if (note != null) {
            procedureService.updateItemNote(iid, note);
        }
        ctx.json(procedureService.findItems(rid));
    }

    /**
     * Refuses a tick by a member who does not edit procedures, unless the procedure is public, the step is
     * public and marked for the party, and the member belongs to the party.
     */
    private void requireTickableByParty(StationSession session, OwnedStep owned) {
        var item = owned.step();
        if (!owned.procedure().isPublic()) throw ProcedureRefusal.PROCEDURE_NOT_PUBLIC.raise();
        if (!item.isPublic() || !item.userAssigned()) throw ProcedureRefusal.PROCEDURE_STEP_NOT_YOURS_TO_TICK.raise();
        if (!procedureService.runBy(item.procedureId(), session.member().id())) {
            throw ProcedureRefusal.PROCEDURE_NOT_HANDED_TO_YOU.raise();
        }
    }

    /**
     * The step a request names under a procedure of the caller's station.
     *
     * @return the procedure and the step, refused as not here when the procedure is another station's or the
     *         step is another procedure's
     */
    private OwnedStep requireProcedureStep(Context ctx) {
        int rid = pathInt(ctx, "rid");
        var procedure = requireOwnedOrNotFound(ctx, rid, procedureService::findProcedureById, Procedure::stationId);
        var step = procedureService
                .findItemIn(rid, pathInt(ctx, "iid"))
                .orElseThrow(ProcedureRefusal.PROCEDURE_STEP_NOT_IN_PROCEDURE::raise);
        return new OwnedStep(procedure, step);
    }

    /**
     * A step a request names, with the procedure of the caller's station it belongs to.
     *
     * @param procedure the procedure
     * @param step      the step
     */
    private record OwnedStep(Procedure procedure, ProcedureItem step) {}

    /**
     * The step a request names under a procedure template of the caller's station.
     *
     * @return the step, refused as not here when the template is another station's or the step is
     *         another template's
     */
    private ProcedureTemplateItem requireTemplateStep(Context ctx) {
        int tid = pathInt(ctx, "tid");
        requireOwnedOrNotFound(ctx, tid, procedureService::findTemplateById, ProcedureTemplate::stationId);
        return procedureService
                .findTemplateItemIn(tid, pathInt(ctx, "iid"))
                .orElseThrow(ProcedureRefusal.PROCEDURE_TEMPLATE_STEP_NOT_IN_TEMPLATE::raise);
    }

    /**
     * A procedure template as it is created or renamed.
     *
     * @param description what the template is for, or {@code null} for nothing
     * @param party       the managing party every procedure started from it begins with, or {@code null} to
     *                    leave it as it is
     */
    public record ProcedureTemplateRequest(
            String name,
            @Nullable String description,
            @Nullable RestrictionAudience party) {}

    /**
     * A template with its steps and the order they depend on each other in.
     *
     * @param dependencies pairs of step ids: the step first, the step it waits for second
     * @param party        the managing party every procedure started from it begins with
     * @param documents    the document templates its steps ask to sign
     */
    public record ProcedureTemplateDetail(
            ProcedureTemplate template,
            List<ProcedureTemplateItem> items,
            List<int[]> dependencies,
            RestrictionAudience party,
            List<StepDocument> documents) {}

    /** Whether the caller may generate documents for members, which a step asking to sign one needs. */
    private static boolean generatesDocuments(Context ctx) {
        return StationSession.from(ctx).hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER);
    }

    /**
     * A step of a template or a procedure as it is added or changed.
     *
     * @param description        what the step asks for, or {@code null} for nothing
     * @param kind               a step ticked by hand or a document to sign; {@code null} leaves a step as it
     *                           is and adds a new one ticked by hand
     * @param documentTemplateId the document template a document step asks to sign
     */
    public record ProcedureItemRequest(
            String title,
            @Nullable String description,
            boolean isPublic,
            boolean userAssigned,
            int position,
            @Nullable ProcedureStepKind kind,
            @Nullable Integer documentTemplateId) {
        /** @return the step as the procedure service takes it */
        StepDraft draft() {
            return new StepDraft(title, description, isPublic, userAssigned, position, kind, documentTemplateId);
        }
    }

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
     * @param party       the managing party, or {@code null} for the template's, or none without one
     * @param eventId     the appointment the procedure is being prepared for, or {@code null}
     * @param eventDate     the one occurrence of that appointment. Both are recorded only when both are
     *                      given: an appointment without a date names every occurrence it has ever had
     * @param aboutMemberId the member the procedure is about, whose documents its steps ask to sign; a new
     *                      procedure has to name one
     */
    public record CreateProcedureRequest(
            @Nullable Integer templateId,
            String name,
            @Nullable String description,
            boolean isPublic,
            @Nullable Instant dueAt,
            @Nullable RestrictionAudience party,
            @Nullable Integer eventId,
            @Nullable LocalDate eventDate,
            @Nullable Integer aboutMemberId) {}

    /**
     * One procedure per member from the same template, each about that member.
     *
     * @param templateId  the template whose steps every procedure starts with, or {@code null} for none
     * @param name        the name every procedure starts with, followed by its member's name
     * @param description what the procedures are about, or {@code null} for nothing
     * @param dueAt       when they are due, or {@code null} for no date
     * @param party       the managing party of all of them, or {@code null} for the template's
     * @param eventId     the appointment they are prepared for, or {@code null}
     * @param eventDate   the one occurrence of that appointment; both are recorded only when both are given
     * @param memberIds   the members, one procedure each
     */
    public record CreateProceduresRequest(
            @Nullable Integer templateId,
            String name,
            @Nullable String description,
            boolean isPublic,
            @Nullable Instant dueAt,
            @Nullable RestrictionAudience party,
            @Nullable Integer eventId,
            @Nullable LocalDate eventDate,
            @Nullable List<Integer> memberIds) {}

    /**
     * @param memberId the member a procedure is to be about
     * @param ask      whether to ask for the document steps that are unlocked right away; {@code false}
     *                 for a caller that sets the dependencies right after, which asks then
     */
    public record AboutRequest(
            @Nullable Integer memberId, @Nullable Boolean ask) {}

    /**
     * @param description what the procedure is about, or {@code null} for nothing
     * @param dueAt       when it is due, or {@code null} for no date
     */
    public record UpdateProcedureRequest(
            String name,
            @Nullable String description,
            boolean isPublic,
            @Nullable Instant dueAt) {}

    /**
     * What a member changes on one step: its tick, its note, or both.
     *
     * @param checked the tick, or {@code null} to leave it
     * @param note    the note, or {@code null} to leave it
     */
    public record PatchItemRequest(
            @Nullable Boolean checked, @Nullable String note) {}

    /**
     * A procedure with its steps, the order they depend on each other in and its managing party.
     *
     * @param party the managing party; one naming nobody where only those who edit procedures run it
     * @param named          the members the party names one by one, as their names are shown
     * @param about          the member the procedure is about, as their name is shown, or null for nobody
     * @param documentStates where the documents of its document steps stand with their signatures
     * @param mayTick        whether the reader runs the procedure and so ticks steps; the member it is
     *                       about and their guardians only read it, without the notes of those who run it
     * @param readsAbout     whether the reader is the member it is about or looks after them, who sign its
     *                       documents
     * @param documents      the document templates its steps ask to sign
     */
    public record ProcedureDetail(
            Procedure procedure,
            List<ProcedureItem> items,
            List<int[]> dependencies,
            RestrictionAudience party,
            List<MemberIdentity> named,
            @Nullable MemberIdentity about,
            List<StepDocumentState> documentStates,
            boolean mayTick,
            boolean readsAbout,
            List<StepDocument> documents) {}
}
