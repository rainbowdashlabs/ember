/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.entity.TestProtocolItem;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunCheck;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunMember;
import dev.chojo.ember.feature.protocol.entity.TestProtocolSection;
import dev.chojo.ember.feature.protocol.service.TestProtocolEvaluationService;
import dev.chojo.ember.feature.protocol.service.TestProtocolEvaluationService.EvaluationResponse;
import dev.chojo.ember.feature.protocol.service.TestProtocolGuards;
import dev.chojo.ember.feature.protocol.service.TestProtocolPdfService;
import dev.chojo.ember.feature.protocol.service.TestProtocolRunService;
import dev.chojo.ember.feature.protocol.service.TestProtocolRunService.ProtocolRunRequest;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import dev.chojo.ember.feature.protocol.service.TestProtocolService.SharedProtocolView;
import dev.chojo.ember.util.DocumentName;
import dev.chojo.ember.util.DocumentWord;
import dev.chojo.ember.util.SafeContentDisposition;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * HTTP route definitions for test protocols owned by the current station. The federated consumer
 * endpoints live in {@link FederatedTestProtocolRoutes}, the server-to-server endpoints in
 * {@link RemoteTestProtocolRoutes}.
 */
@Singleton
public class TestProtocolRoutes implements Routes {
    private final TestProtocolService service;
    private final TestProtocolGuards guards;
    private final TestProtocolPdfService pdfService;
    private final TestProtocolRunService runs;
    private final TestProtocolEvaluationService evaluations;

    @Inject
    public TestProtocolRoutes(
            TestProtocolService service,
            TestProtocolGuards guards,
            TestProtocolPdfService pdfService,
            TestProtocolRunService runs,
            TestProtocolEvaluationService evaluations) {
        this.service = service;
        this.guards = guards;
        this.pdfService = pdfService;
        this.runs = runs;
        this.evaluations = evaluations;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/protocols", this::listProtocols, StationPermission.PROTOCOL_TESTER);
        routes.post(prefix + "/protocols", this::createProtocol, StationPermission.PROTOCOL_CONFIGURE);

        routes.get(prefix + "/protocols/runs", this::listRuns, StationPermission.PROTOCOL_TESTER);
        routes.get(prefix + "/protocols/runs/{id}", this::getRun, StationPermission.PROTOCOL_TESTER);
        routes.put(prefix + "/protocols/runs/{id}", this::updateRun, StationPermission.PROTOCOL_CREATE);
        routes.delete(prefix + "/protocols/runs/{id}", this::deleteRun, StationPermission.PROTOCOL_MANAGER);
        routes.post(prefix + "/protocols/runs/{id}/close", this::closeRun, StationPermission.PROTOCOL_CREATE);

        routes.put(prefix + "/protocols/sections/{id}", this::updateSection, StationPermission.PROTOCOL_CONFIGURE);
        routes.delete(prefix + "/protocols/sections/{id}", this::deleteSection, StationPermission.PROTOCOL_CONFIGURE);

        routes.put(prefix + "/protocols/items/{id}", this::updateItem, StationPermission.PROTOCOL_CONFIGURE);
        routes.delete(prefix + "/protocols/items/{id}", this::deleteItem, StationPermission.PROTOCOL_CONFIGURE);

        routes.get(prefix + "/protocols/{id}", this::getProtocol, StationPermission.PROTOCOL_TESTER);
        routes.put(prefix + "/protocols/{id}", this::updateProtocol, StationPermission.PROTOCOL_CONFIGURE);
        routes.delete(prefix + "/protocols/{id}", this::deleteProtocol, StationPermission.PROTOCOL_CONFIGURE);
        routes.post(prefix + "/protocols/{id}/sections", this::createSection, StationPermission.PROTOCOL_CONFIGURE);
        routes.post(prefix + "/protocols/{id}/runs", this::createRun, StationPermission.PROTOCOL_CREATE);

        routes.post(prefix + "/protocols/sections/{id}/items", this::createItem, StationPermission.PROTOCOL_CONFIGURE);
        routes.get(
                prefix + "/protocols/runs/{runId}/members/{memberId}/export",
                this::exportMemberPdf,
                StationPermission.PROTOCOL_TESTER);
        routes.get(prefix + "/protocols/runs/{id}/evaluation", this::getEvaluation, StationPermission.PROTOCOL_TESTER);
        routes.get(
                prefix + "/protocols/runs/{id}/evaluation/export",
                this::exportEvaluationPdf,
                StationPermission.PROTOCOL_TESTER);
        routes.get(prefix + "/protocols/runs/{id}/export-all", this::exportAllZip, StationPermission.PROTOCOL_TESTER);

        routes.post(
                prefix + "/protocols/runs/{runId}/members/{memberId}/lock",
                this::lockMember,
                StationPermission.PROTOCOL_TESTER);
        routes.post(
                prefix + "/protocols/runs/{runId}/members/{memberId}/unlock",
                this::unlockMember,
                StationPermission.PROTOCOL_TESTER);
        routes.get(
                prefix + "/protocols/runs/{runId}/members/{memberId}/checks",
                this::getChecks,
                StationPermission.PROTOCOL_TESTER);
        routes.put(
                prefix + "/protocols/runs/{runId}/members/{memberId}/checks",
                this::saveChecks,
                StationPermission.PROTOCOL_TESTER);
        routes.post(
                prefix + "/protocols/runs/{runId}/members/{memberId}/complete",
                this::completeMember,
                StationPermission.PROTOCOL_TESTER);
        routes.get(
                prefix + "/protocols/runs/{runId}/members/{memberId}/sections-done",
                this::getSectionsDone,
                StationPermission.PROTOCOL_TESTER);
        routes.post(
                prefix + "/protocols/runs/{runId}/members/{memberId}/sections/{sectionId}/toggle-done",
                this::toggleSectionDone,
                StationPermission.PROTOCOL_TESTER);
    }

    @OpenApi(
            path = "/api/v1/protocols",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProtocolListResponse.class)))
    private void listProtocols(Context ctx) {
        var session = UserSession.from(ctx);
        var protocols = service.findProtocols(session.stationId());
        ctx.json(new ProtocolListResponse(protocols, service.browseSharedProtocolViews(session.stationId())));
    }

    @OpenApi(
            path = "/api/v1/protocols",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProtocolRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = TestProtocol.class)))
    private void createProtocol(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(ProtocolRequest.class);
        if (req.name() == null || req.name().isBlank()) throw Refusal.PROTOCOL_NEEDS_A_NAME.raise();
        ctx.status(HttpStatus.CREATED)
                .json(service.createProtocol(
                        session.stationId(),
                        req.name().trim(),
                        req.description() != null ? req.description() : "",
                        req.passThreshold()));
    }

    @OpenApi(
            path = "/api/v1/protocols/{id}",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProtocolDetailResponse.class)))
    private void getProtocol(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var protocol = guards.requireProtocol(ctx, id);
        var sections = service.findSections(id);
        var allItems = service.findAllItemsByProtocol(id);
        ctx.json(new ProtocolDetailResponse(protocol, sections, allItems));
    }

    @OpenApi(
            path = "/api/v1/protocols/{id}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProtocolRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TestProtocol.class)))
    private void updateProtocol(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        guards.requireProtocol(ctx, id);
        var req = ctx.bodyAsClass(ProtocolRequest.class);
        if (!service.updateProtocol(
                id, req.name(), req.description() != null ? req.description() : "", req.passThreshold())) {
            throw Refusal.PROTOCOL_NOT_CHANGED.raise();
        }
        ctx.json(service.findProtocol(id).orElseThrow(Refusal.PROTOCOL_NOT_HERE_AFTER_CHANGE::raise));
    }

    @OpenApi(path = "/api/v1/protocols/{id}", methods = HttpMethod.DELETE, responses = @OpenApiResponse(status = "204"))
    private void deleteProtocol(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        guards.requireProtocol(ctx, id);
        service.deleteProtocol(id, UserSession.from(ctx).stationId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/protocols/{id}/sections",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProtocolSectionRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = TestProtocolSection.class)))
    private void createSection(Context ctx) {
        int protocolId = ctx.pathParamAsClass("id", Integer.class).get();
        guards.requireProtocol(ctx, protocolId);
        var req = ctx.bodyAsClass(ProtocolSectionRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(service.createSection(
                        protocolId,
                        req.parentId(),
                        req.name(),
                        req.description() != null ? req.description() : "",
                        req.maxPoints(),
                        req.passThreshold(),
                        req.position() != null ? req.position() : 0));
    }

    @OpenApi(
            path = "/api/v1/protocols/sections/{id}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProtocolSectionRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void updateSection(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        guards.requireSection(ctx, id);
        var req = ctx.bodyAsClass(ProtocolSectionRequest.class);
        service.updateSection(
                id,
                req.name(),
                req.description() != null ? req.description() : "",
                req.maxPoints(),
                req.passThreshold(),
                req.position() != null ? req.position() : 0);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/protocols/sections/{id}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteSection(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        guards.requireSection(ctx, id);
        service.deleteSection(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/protocols/sections/{id}/items",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProtocolItemRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = TestProtocolItem.class)))
    private void createItem(Context ctx) {
        int sectionId = ctx.pathParamAsClass("id", Integer.class).get();
        guards.requireSection(ctx, sectionId);
        var req = ctx.bodyAsClass(ProtocolItemRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(service.createItem(
                        sectionId,
                        req.label(),
                        req.description() != null ? req.description() : "",
                        req.points() != null ? req.points() : 1.0,
                        req.position() != null ? req.position() : 0));
    }

    @OpenApi(
            path = "/api/v1/protocols/items/{id}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProtocolItemRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void updateItem(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        guards.requireItem(ctx, id);
        var req = ctx.bodyAsClass(ProtocolItemRequest.class);
        service.updateItem(
                id,
                req.label(),
                req.description() != null ? req.description() : "",
                req.points() != null ? req.points() : 1.0,
                req.position() != null ? req.position() : 0);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/protocols/items/{id}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteItem(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        guards.requireItem(ctx, id);
        service.deleteItem(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/protocols/runs",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TestProtocolRun[].class)))
    private void listRuns(Context ctx) {
        var session = UserSession.from(ctx);
        var runs = service.findRuns(session.stationId());
        ctx.json(runs);
    }

    @OpenApi(
            path = "/api/v1/protocols/{id}/runs",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProtocolRunRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = TestProtocolRun.class)))
    private void createRun(Context ctx) {
        var session = UserSession.from(ctx);
        int protocolId = ctx.pathParamAsClass("id", Integer.class).get();
        guards.requireProtocol(ctx, protocolId);
        var run = runs.start(
                protocolId, session.stationId(), session.member().id(), ctx.bodyAsClass(ProtocolRunRequest.class));
        ctx.status(HttpStatus.CREATED).json(run);
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{id}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RunDetailResponse.class)))
    private void getRun(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var run = guards.requireRun(ctx, id);
        var members = service.findRunMembers(id);
        var topSections = service.findSections(run.protocolId()).stream()
                .filter(s -> s.parentId() == null)
                .count();
        var membersWithProgress = members.stream()
                .map(m -> new RunMemberWithProgress(m, service.countDoneSections(m.id()), (int) topSections))
                .toList();
        ctx.json(new RunDetailResponse(run, membersWithProgress));
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{id}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProtocolRunRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TestProtocolRun.class)))
    private void updateRun(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        guards.requireRun(ctx, id);
        var req = ctx.bodyAsClass(ProtocolRunRequest.class);
        service.updateRun(id, req.name(), req.testDate() != null ? req.testDate() : LocalDate.now());
        ctx.json(service.findRun(id).orElseThrow(Refusal.PROTOCOL_RUN_NOT_HERE_AFTER_CHANGE::raise));
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{id}/close",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TestProtocolRun.class)))
    private void closeRun(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        guards.requireRun(ctx, id);
        service.closeRun(id);
        ctx.json(service.findRun(id).orElseThrow(Refusal.PROTOCOL_RUN_NOT_HERE_AFTER_CLOSING::raise));
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{id}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteRun(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        guards.requireRun(ctx, id);
        service.deleteRun(id, UserSession.from(ctx).stationId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{runId}/members/{memberId}/lock",
            methods = HttpMethod.POST,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = TestProtocolRunMember.class)))
    private void lockMember(Context ctx) {
        var session = UserSession.from(ctx);
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        guards.requireRun(ctx, runId);
        if (!service.lockMember(runId, memberId, session.member().id())) {
            throw Refusal.PROTOCOL_MEMBER_HELD_BY_ANOTHER_TESTER.raise();
        }
        ctx.json(service.findRunMember(runId, memberId)
                .orElseThrow(Refusal.PROTOCOL_MEMBER_NOT_HERE_AFTER_LOCKING::raise));
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{runId}/members/{memberId}/unlock",
            methods = HttpMethod.POST,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = TestProtocolRunMember.class)))
    private void unlockMember(Context ctx) {
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        guards.requireRun(ctx, runId);
        service.unlockMember(runId, memberId);
        ctx.json(service.findRunMember(runId, memberId)
                .orElseThrow(Refusal.PROTOCOL_MEMBER_NOT_HERE_AFTER_UNLOCKING::raise));
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{runId}/members/{memberId}/checks",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = TestProtocolRunCheck[].class)))
    private void getChecks(Context ctx) {
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        guards.requireRun(ctx, runId);
        ctx.json(service.findChecks(runId, memberId));
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{runId}/members/{memberId}/checks",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProtocolChecksRequest.class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = TestProtocolRunCheck[].class)))
    private void saveChecks(Context ctx) {
        var session = UserSession.from(ctx);
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        var req = ctx.bodyAsClass(ProtocolChecksRequest.class);
        var run = guards.requireRun(ctx, runId);
        service.saveChecks(runId, memberId, req.checks(), session.member().id(), run.protocolId());
        ctx.json(service.findChecks(runId, memberId));
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{runId}/members/{memberId}/complete",
            methods = HttpMethod.POST,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = TestProtocolRunMember.class)))
    private void completeMember(Context ctx) {
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        var run = guards.requireRun(ctx, runId);
        service.completeMember(runId, memberId, run.protocolId());
        ctx.json(service.findRunMember(runId, memberId)
                .orElseThrow(Refusal.PROTOCOL_MEMBER_NOT_HERE_AFTER_COMPLETION::raise));
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{runId}/members/{memberId}/sections-done",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Integer[].class)))
    private void getSectionsDone(Context ctx) {
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        guards.requireRun(ctx, runId);
        ctx.json(service.findDoneSections(runId, memberId));
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{runId}/members/{memberId}/sections/{sectionId}/toggle-done",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Integer[].class)))
    private void toggleSectionDone(Context ctx) {
        var session = UserSession.from(ctx);
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        int sectionId = ctx.pathParamAsClass("sectionId", Integer.class).get();
        guards.requireRun(ctx, runId);
        guards.requireSection(ctx, sectionId);
        service.toggleSectionDone(runId, memberId, sectionId, session.member().id());
        ctx.json(service.findDoneSections(runId, memberId));
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{id}/evaluation",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EvaluationResponse.class)))
    private void getEvaluation(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(evaluations.evaluate(guards.requireRun(ctx, id)));
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{id}/export-all",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void exportAllZip(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var run = guards.requireRun(ctx, id);
        var proto = service.findProtocol(run.protocolId())
                .orElseThrow(Refusal.PROTOCOL_NOT_HERE_BEHIND_RUN_TO_EXPORT::raise);
        byte[] archive = runs.archive(run, proto.name());
        ctx.contentType("application/zip");
        ctx.header("Content-Disposition", protocolName(proto.name(), "zip", null));
        ctx.result(archive);
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{id}/evaluation/export",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void exportEvaluationPdf(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var run = guards.requireRun(ctx, id);
        var proto = service.findProtocol(run.protocolId())
                .orElseThrow(Refusal.PROTOCOL_NOT_HERE_BEHIND_RUN_FOR_TABLE::raise);
        byte[] pdf = pdfService.exportEvaluationTable(id, proto.name(), run.testDate());
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", protocolName(proto.name(), "pdf", DocumentWord.EVALUATION.in("de")));
        ctx.result(pdf);
    }

    @OpenApi(
            path = "/api/v1/protocols/runs/{runId}/members/{memberId}/export",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void exportMemberPdf(Context ctx) {
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        var run = guards.requireRun(ctx, runId);
        var proto = service.findProtocol(run.protocolId())
                .orElseThrow(Refusal.PROTOCOL_NOT_HERE_BEHIND_RUN_FOR_MEMBER_SHEET::raise);
        byte[] pdf = pdfService.exportRunMember(runId, memberId, proto.name(), run.testDate());
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", protocolName(proto.name(), "pdf", runs.memberFileName(memberId)));
        ctx.result(pdf);
    }

    /**
     * What a protocol export is called: the protocol it belongs to, and whose or which part it is.
     *
     * <p>Written in German because these sheets are rendered from Typst built here rather than from a
     * template chosen by the station's language, so there is only one language to be had. When those
     * two exports join the templates, the language follows the same way as everywhere else.
     */
    private static String protocolName(String protocolName, String extension, String subject) {
        String filename = DocumentName.of(
                extension, DocumentWord.PROTOCOL.in("de"), DocumentName.part(protocolName), DocumentName.part(subject));
        return SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, filename);
    }

    /**
     * The station's own protocols and the ones its federation partners share with it.
     */
    public record ProtocolListResponse(List<TestProtocol> protocols, List<SharedProtocolView> shared) {}

    /**
     * A protocol as it is created or changed.
     *
     * @param description what the protocol is about, or {@code null} for none
     * @param passThreshold the share of points needed to pass, or {@code null} for no threshold
     */
    public record ProtocolRequest(String name, @Nullable String description, @Nullable Integer passThreshold) {}

    /**
     * A section of a protocol as it is created or changed.
     *
     * @param parentId the section it stands in, or {@code null} for a top-level section; ignored on a change
     * @param position where it stands among its siblings, or {@code null} for first
     */
    public record ProtocolSectionRequest(
            @Nullable Integer parentId,
            String name,
            @Nullable String description,
            @Nullable Integer maxPoints,
            @Nullable Integer passThreshold,
            @Nullable Integer position) {}

    /**
     * A checkbox of a section as it is created or changed.
     *
     * @param points what ticking it is worth, or {@code null} for one point
     */
    public record ProtocolItemRequest(
            String label, @Nullable String description, @Nullable Double points, @Nullable Integer position) {}

    /**
     * The ticked state of the checkboxes of one member's sheet, by checkbox id.
     */
    public record ProtocolChecksRequest(Map<Integer, Boolean> checks) {}

    public record ProtocolDetailResponse(
            TestProtocol protocol, List<TestProtocolSection> sections, List<TestProtocolItem> items) {}

    public record RunMemberWithProgress(TestProtocolRunMember member, int sectionsDone, int sectionsTotal) {}

    public record RunDetailResponse(TestProtocolRun run, List<RunMemberWithProgress> members) {}
}
