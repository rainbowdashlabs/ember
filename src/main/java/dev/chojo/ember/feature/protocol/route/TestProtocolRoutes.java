/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.federation.service.FederatedContentService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import dev.chojo.ember.feature.protocol.service.TestProtocolPdfService;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Singleton
public class TestProtocolRoutes implements Routes {

    private final TestProtocolService service;
    private final TestProtocolPdfService pdfService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final FederatedContentService federatedContentService;
    private final StationRepository stationRepository;

    @Inject
    public TestProtocolRoutes(
            TestProtocolService service,
            TestProtocolPdfService pdfService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            FederatedContentService federatedContentService,
            StationRepository stationRepository) {
        this.service = service;
        this.pdfService = pdfService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.federatedContentService = federatedContentService;
        this.stationRepository = stationRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Protocol list (static paths first, before {id} params)
        routes.get(prefix + "/protocols", this::listProtocols, Roles.PROTOCOL_MANAGEMENT);
        routes.post(prefix + "/protocols", this::createProtocol, Roles.PROTOCOL_MANAGEMENT);

        // Runs (static "runs" path must be registered before "/protocols/{id}")
        routes.get(prefix + "/protocols/runs", this::listRuns, Roles.PROTOCOL_TESTER);
        routes.get(prefix + "/protocols/runs/{id}", this::getRun, Roles.PROTOCOL_TESTER);
        routes.put(prefix + "/protocols/runs/{id}", this::updateRun, Roles.PROTOCOL_MANAGEMENT);
        routes.delete(prefix + "/protocols/runs/{id}", this::deleteRun, Roles.PROTOCOL_MANAGEMENT);
        routes.post(prefix + "/protocols/runs/{id}/close", this::closeRun, Roles.PROTOCOL_MANAGEMENT);

        // Sections (static "sections" path before {id})
        routes.put(prefix + "/protocols/sections/{id}", this::updateSection, Roles.PROTOCOL_MANAGEMENT);
        routes.delete(prefix + "/protocols/sections/{id}", this::deleteSection, Roles.PROTOCOL_MANAGEMENT);

        // Items (static "items" path before {id})
        routes.put(prefix + "/protocols/items/{id}", this::updateItem, Roles.PROTOCOL_MANAGEMENT);
        routes.delete(prefix + "/protocols/items/{id}", this::deleteItem, Roles.PROTOCOL_MANAGEMENT);

        // Protocol CRUD with {id} param (after all static sub-paths)
        routes.get(prefix + "/protocols/{id}", this::getProtocol, Roles.PROTOCOL_TESTER);
        routes.put(prefix + "/protocols/{id}", this::updateProtocol, Roles.PROTOCOL_MANAGEMENT);
        routes.delete(prefix + "/protocols/{id}", this::deleteProtocol, Roles.PROTOCOL_MANAGEMENT);
        routes.post(prefix + "/protocols/{id}/sections", this::createSection, Roles.PROTOCOL_MANAGEMENT);
        routes.post(prefix + "/protocols/{id}/runs", this::createRun, Roles.PROTOCOL_MANAGEMENT);

        // Section items (needs section {id})
        routes.post(prefix + "/protocols/sections/{id}/items", this::createItem, Roles.PROTOCOL_MANAGEMENT);
        routes.get(
                prefix + "/protocols/runs/{runId}/members/{memberId}/export",
                this::exportMemberPdf,
                Roles.PROTOCOL_TESTER);
        routes.get(prefix + "/protocols/runs/{id}/evaluation", this::getEvaluation, Roles.PROTOCOL_TESTER);
        routes.get(prefix + "/protocols/runs/{id}/evaluation/export", this::exportEvaluationPdf, Roles.PROTOCOL_TESTER);
        routes.get(prefix + "/protocols/runs/{id}/export-all", this::exportAllZip, Roles.PROTOCOL_TESTER);

        // Grading
        routes.post(
                prefix + "/protocols/runs/{runId}/members/{memberId}/lock", this::lockMember, Roles.PROTOCOL_TESTER);
        routes.post(
                prefix + "/protocols/runs/{runId}/members/{memberId}/unlock",
                this::unlockMember,
                Roles.PROTOCOL_TESTER);
        routes.get(
                prefix + "/protocols/runs/{runId}/members/{memberId}/checks", this::getChecks, Roles.PROTOCOL_TESTER);
        routes.put(
                prefix + "/protocols/runs/{runId}/members/{memberId}/checks", this::saveChecks, Roles.PROTOCOL_TESTER);
        routes.post(
                prefix + "/protocols/runs/{runId}/members/{memberId}/complete",
                this::completeMember,
                Roles.PROTOCOL_TESTER);
        routes.get(
                prefix + "/protocols/runs/{runId}/members/{memberId}/sections-done",
                this::getSectionsDone,
                Roles.PROTOCOL_TESTER);
        routes.post(
                prefix + "/protocols/runs/{runId}/members/{memberId}/sections/{sectionId}/toggle-done",
                this::toggleSectionDone,
                Roles.PROTOCOL_TESTER);
    }

    // -- Protocol CRUD --

    private void listProtocols(Context ctx) {
        var session = UserSession.from(ctx);
        var q = ctx.queryParam("q");
        var federatedParam = ctx.queryParam("federated");
        boolean includeFederated = federatedParam == null || !"false".equalsIgnoreCase(federatedParam);

        List<dev.chojo.ember.feature.protocol.entity.TestProtocol> local;
        if (q != null && !q.isBlank()) {
            local = service.searchProtocols(session.stationId(), q.trim());
        } else {
            local = service.findProtocols(session.stationId());
        }

        if (!includeFederated) {
            ctx.json(new ProtocolListResponse(local, List.of()));
            return;
        }

        var shared = federatedContentService.browseSharedProtocols(session.stationId());
        var sharedItems = shared.stream()
                .map(i -> {
                    String stationName = stationRepository
                            .findById(i.sourceStationId())
                            .map(s -> s.name())
                            .orElse("Unknown");
                    return new SharedProtocolEntry(i.protocol(), stationName, i.sourceStationId());
                })
                .toList();

        ctx.json(new ProtocolListResponse(local, sharedItems));
    }

    private void createProtocol(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(ProtocolRequest.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        ctx.status(HttpStatus.CREATED)
                .json(service.createProtocol(
                        session.stationId(),
                        req.name().trim(),
                        req.description() != null ? req.description() : "",
                        req.passThreshold()));
    }

    private void getProtocol(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var protocol = service.findProtocol(id).orElseThrow(NotFoundResponse::new);
        var sections = service.findSections(id);
        var allItems = service.findAllItemsByProtocol(id);
        ctx.json(new ProtocolDetailResponse(protocol, sections, allItems));
    }

    private void updateProtocol(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(ProtocolRequest.class);
        if (!service.updateProtocol(
                id, req.name(), req.description() != null ? req.description() : "", req.passThreshold())) {
            throw new NotFoundResponse();
        }
        ctx.json(service.findProtocol(id).orElseThrow());
    }

    private void deleteProtocol(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        service.deleteProtocol(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Sections --

    private void createSection(Context ctx) {
        int protocolId = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(SectionRequest.class);
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

    private void updateSection(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(SectionRequest.class);
        service.updateSection(
                id,
                req.name(),
                req.description() != null ? req.description() : "",
                req.maxPoints(),
                req.passThreshold(),
                req.position() != null ? req.position() : 0);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void deleteSection(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        service.deleteSection(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Items --

    private void createItem(Context ctx) {
        int sectionId = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(ItemRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(service.createItem(
                        sectionId,
                        req.label(),
                        req.description() != null ? req.description() : "",
                        req.points() != null ? req.points() : 1.0,
                        req.position() != null ? req.position() : 0));
    }

    private void updateItem(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(ItemRequest.class);
        service.updateItem(
                id,
                req.label(),
                req.description() != null ? req.description() : "",
                req.points() != null ? req.points() : 1.0,
                req.position() != null ? req.position() : 0);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void deleteItem(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        service.deleteItem(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Runs --

    private void listRuns(Context ctx) {
        var session = UserSession.from(ctx);
        var runs = service.findRuns(session.stationId());
        ctx.json(runs);
    }

    private void createRun(Context ctx) {
        var session = UserSession.from(ctx);
        int protocolId = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(RunRequest.class);
        var run = service.createRun(
                protocolId,
                session.stationId(),
                req.name(),
                req.testDate() != null ? req.testDate() : LocalDate.now(),
                session.member().id());
        // Add members
        if (req.memberIds() != null) {
            service.addRunMembers(run.id(), req.memberIds());
        }
        ctx.status(HttpStatus.CREATED).json(run);
    }

    private void getRun(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var run = service.findRun(id).orElseThrow(NotFoundResponse::new);
        var members = service.findRunMembers(id);
        var topSections = service.findSections(run.protocolId()).stream()
                .filter(s -> s.parentId() == null)
                .count();
        var membersWithProgress = members.stream()
                .map(m -> new RunMemberWithProgress(m, service.countDoneSections(m.id()), (int) topSections))
                .toList();
        ctx.json(new RunDetailResponse(run, membersWithProgress));
    }

    private void updateRun(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(RunRequest.class);
        service.updateRun(id, req.name(), req.testDate() != null ? req.testDate() : LocalDate.now());
        ctx.json(service.findRun(id).orElseThrow());
    }

    private void closeRun(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        service.closeRun(id);
        ctx.json(service.findRun(id).orElseThrow());
    }

    private void deleteRun(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        service.deleteRun(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Grading --

    private void lockMember(Context ctx) {
        var session = UserSession.from(ctx);
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        if (!service.lockMember(runId, memberId, session.member().id())) {
            throw new BadRequestResponse("Member is already locked by another tester");
        }
        ctx.json(service.findRunMember(runId, memberId).orElseThrow());
    }

    private void unlockMember(Context ctx) {
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        service.unlockMember(runId, memberId);
        ctx.json(service.findRunMember(runId, memberId).orElseThrow());
    }

    private void getChecks(Context ctx) {
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        ctx.json(service.findChecks(runId, memberId));
    }

    private void saveChecks(Context ctx) {
        var session = UserSession.from(ctx);
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        var req = ctx.bodyAsClass(ChecksRequest.class);
        var run = service.findRun(runId).orElseThrow(NotFoundResponse::new);
        service.saveChecks(runId, memberId, req.checks(), session.member().id(), run.protocolId());
        ctx.json(service.findChecks(runId, memberId));
    }

    private void completeMember(Context ctx) {
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        var run = service.findRun(runId).orElseThrow(NotFoundResponse::new);
        service.completeMember(runId, memberId, run.protocolId());
        ctx.json(service.findRunMember(runId, memberId).orElseThrow());
    }

    private void getSectionsDone(Context ctx) {
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        ctx.json(service.findDoneSections(runId, memberId));
    }

    private void toggleSectionDone(Context ctx) {
        var session = UserSession.from(ctx);
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        int sectionId = ctx.pathParamAsClass("sectionId", Integer.class).get();
        service.toggleSectionDone(runId, memberId, sectionId, session.member().id());
        ctx.json(service.findDoneSections(runId, memberId));
    }

    private void getEvaluation(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var run = service.findRun(id).orElseThrow(NotFoundResponse::new);
        var proto = service.findProtocol(run.protocolId()).orElseThrow(NotFoundResponse::new);
        var sections = service.findSections(run.protocolId());
        var allItems = service.findAllItemsByProtocol(run.protocolId());
        var members = service.findRunMembers(id);

        // Build per-section scores per member
        var itemsBySectionId = allItems.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        dev.chojo.ember.feature.protocol.entity.TestProtocolItem::sectionId));

        var memberScores = new java.util.ArrayList<EvalMemberData>();
        for (var rm : members) {
            var checks = service.findChecks(id, rm.memberId());
            var checkedIds = checks.stream()
                    .filter(dev.chojo.ember.feature.protocol.entity.TestProtocolRunCheck::checked)
                    .map(dev.chojo.ember.feature.protocol.entity.TestProtocolRunCheck::itemId)
                    .collect(java.util.stream.Collectors.toSet());

            var sectionScores = new java.util.HashMap<Integer, Double>();
            for (var section : sections) {
                var sItems = itemsBySectionId.getOrDefault(section.id(), List.of());
                var score = sItems.stream()
                        .filter(i -> checkedIds.contains(i.id()))
                        .map(dev.chojo.ember.feature.protocol.entity.TestProtocolItem::points)
                        .reduce(0.0, Double::sum);
                sectionScores.put(section.id(), score);
            }
            memberScores.add(new EvalMemberData(rm.memberId(), rm.totalScore(), sectionScores));
        }

        // Section max points
        var sectionMaxPoints = new java.util.HashMap<Integer, Double>();
        for (var section : sections) {
            var sItems = itemsBySectionId.getOrDefault(section.id(), List.of());
            sectionMaxPoints.put(
                    section.id(),
                    sItems.stream()
                            .map(dev.chojo.ember.feature.protocol.entity.TestProtocolItem::points)
                            .reduce(0.0, Double::sum));
        }

        ctx.json(new EvaluationResponse(
                proto.name(), run.testDate(), sections, sectionMaxPoints, memberScores, proto.passThreshold()));
    }

    private void exportAllZip(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var run = service.findRun(id).orElseThrow(NotFoundResponse::new);
        var proto = service.findProtocol(run.protocolId()).orElseThrow(NotFoundResponse::new);
        var members = service.findRunMembers(id);

        try {
            var baos = new java.io.ByteArrayOutputStream();
            var zos = new java.util.zip.ZipOutputStream(baos);

            // Table PDF
            byte[] tablePdf = pdfService.exportEvaluationTable(id, proto.name(), run.testDate());
            zos.putNextEntry(new java.util.zip.ZipEntry("Auswertung.pdf"));
            zos.write(tablePdf);
            zos.closeEntry();

            // Per-member PDFs
            for (var rm : members) {
                String memberName = resolveMemberNameForFile(rm.memberId());
                byte[] pdf = pdfService.exportRunMember(id, rm.memberId(), proto.name(), run.testDate());
                zos.putNextEntry(new java.util.zip.ZipEntry(memberName + ".pdf"));
                zos.write(pdf);
                zos.closeEntry();
            }

            zos.finish();
            zos.close();

            ctx.contentType("application/zip");
            ctx.header(
                    "Content-Disposition",
                    "attachment; filename=\"" + proto.name().replaceAll("[^a-zA-ZäöüÄÖÜß0-9 _-]", "") + ".zip\"");
            ctx.result(baos.toByteArray());
        } catch (Exception e) {
            throw new io.javalin.http.InternalServerErrorResponse("Export failed: " + e.getMessage());
        }
    }

    private String resolveMemberNameForFile(int memberId) {
        return stationMemberRepository
                .findById(memberId)
                .map(m -> {
                    String name = m.displayName();
                    if (name == null || name.isBlank()) {
                        if (m.accountId() != null) {
                            name = accountRepository
                                    .findById(m.accountId())
                                    .map(a -> a.firstName() + " " + a.lastName())
                                    .orElse("Member_" + memberId);
                        } else {
                            name = "Member_" + memberId;
                        }
                    }
                    return name.replaceAll("[^a-zA-ZäöüÄÖÜß0-9 _-]", "");
                })
                .orElse("Member_" + memberId);
    }

    private void exportEvaluationPdf(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var run = service.findRun(id).orElseThrow(NotFoundResponse::new);
        var proto = service.findProtocol(run.protocolId()).orElseThrow(NotFoundResponse::new);
        byte[] pdf = pdfService.exportEvaluationTable(id, proto.name(), run.testDate());
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", "inline; filename=\"evaluation.pdf\"");
        ctx.result(pdf);
    }

    private void exportMemberPdf(Context ctx) {
        int runId = ctx.pathParamAsClass("runId", Integer.class).get();
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        var run = service.findRun(runId).orElseThrow(NotFoundResponse::new);
        var proto = service.findProtocol(run.protocolId()).orElseThrow(NotFoundResponse::new);
        byte[] pdf = pdfService.exportRunMember(runId, memberId, proto.name(), run.testDate());
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", "inline; filename=\"protocol.pdf\"");
        ctx.result(pdf);
    }

    // -- Records --

    public record ProtocolRequest(String name, String description, Integer passThreshold) {}

    public record SectionRequest(
            Integer parentId,
            String name,
            String description,
            Integer maxPoints,
            Integer passThreshold,
            Integer position) {}

    public record ItemRequest(String label, String description, Double points, Integer position) {}

    public record RunRequest(String name, LocalDate testDate, List<Integer> memberIds) {}

    public record ChecksRequest(Map<Integer, Boolean> checks) {}

    public record ProtocolDetailResponse(
            dev.chojo.ember.feature.protocol.entity.TestProtocol protocol,
            List<dev.chojo.ember.feature.protocol.entity.TestProtocolSection> sections,
            List<dev.chojo.ember.feature.protocol.entity.TestProtocolItem> items) {}

    public record RunMemberWithProgress(
            dev.chojo.ember.feature.protocol.entity.TestProtocolRunMember member,
            int sectionsDone,
            int sectionsTotal) {}

    public record RunDetailResponse(TestProtocolRun run, List<RunMemberWithProgress> members) {}

    public record EvalMemberData(int memberId, Double totalScore, java.util.Map<Integer, Double> sectionScores) {}

    public record EvaluationResponse(
            String protocolName,
            java.time.LocalDate testDate,
            List<dev.chojo.ember.feature.protocol.entity.TestProtocolSection> sections,
            java.util.Map<Integer, Double> sectionMaxPoints,
            List<EvalMemberData> members,
            Integer passThreshold) {}

    public record SharedProtocolEntry(
            dev.chojo.ember.feature.protocol.entity.TestProtocol protocol, String stationName, int sourceStationId) {}

    public record ProtocolListResponse(
            List<dev.chojo.ember.feature.protocol.entity.TestProtocol> protocols, List<SharedProtocolEntry> shared) {}
}
