/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.federation.service.FederatedContentService;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class FederationRoutes implements Routes {

    private final FederationService service;
    private final FederatedContentService contentService;
    private final StationRepository stationRepository;

    // In-memory pending invites (invite code -> {stationId, publicKey})
    private final Map<String, PendingInvite> pendingInvites = new ConcurrentHashMap<>();

    private record PendingInvite(int stationId, String publicKey) {}

    @Inject
    public FederationRoutes(
            FederationService service, FederatedContentService contentService, StationRepository stationRepository) {
        this.service = service;
        this.contentService = contentService;
        this.stationRepository = stationRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Partner management
        routes.get(prefix + "/federation/partners", this::listPartners, Roles.FEDERATION_MANAGEMENT);
        routes.post(prefix + "/federation/invite", this::createInvite, Roles.FEDERATION_MANAGEMENT);
        routes.post(prefix + "/federation/accept", this::acceptInvite, Roles.FEDERATION_MANAGEMENT);
        routes.get(prefix + "/federation/partners/{id}", this::getPartner, Roles.FEDERATION_MANAGEMENT);
        routes.post(prefix + "/federation/partners/{id}/suspend", this::suspendPartner, Roles.FEDERATION_MANAGEMENT);
        routes.post(prefix + "/federation/partners/{id}/resume", this::resumePartner, Roles.FEDERATION_MANAGEMENT);
        routes.delete(prefix + "/federation/partners/{id}", this::endFederation, Roles.FEDERATION_MANAGEMENT);

        // Capabilities
        routes.get(
                prefix + "/federation/partners/{id}/capabilities", this::getCapabilities, Roles.FEDERATION_MANAGEMENT);
        routes.put(
                prefix + "/federation/partners/{id}/capabilities", this::setCapabilities, Roles.FEDERATION_MANAGEMENT);

        // Sharing management
        routes.get(prefix + "/federation/shares/kb", this::listKbShares, Roles.FEDERATION_MANAGEMENT);
        routes.post(prefix + "/federation/shares/kb", this::createKbShare, Roles.FEDERATION_MANAGEMENT);
        routes.delete(prefix + "/federation/shares/kb/{id}", this::deleteKbShare, Roles.FEDERATION_MANAGEMENT);
        routes.get(prefix + "/federation/shares/quiz", this::listQuizShares, Roles.FEDERATION_MANAGEMENT);
        routes.post(prefix + "/federation/shares/quiz", this::createQuizShare, Roles.FEDERATION_MANAGEMENT);
        routes.delete(prefix + "/federation/shares/quiz/{id}", this::deleteQuizShare, Roles.FEDERATION_MANAGEMENT);
        routes.get(prefix + "/federation/shares/protocol", this::listProtocolShares, Roles.FEDERATION_MANAGEMENT);
        routes.post(prefix + "/federation/shares/protocol", this::createProtocolShare, Roles.FEDERATION_MANAGEMENT);
        routes.delete(
                prefix + "/federation/shares/protocol/{id}", this::deleteProtocolShare, Roles.FEDERATION_MANAGEMENT);

        // Browse shared content (available to all users)
        routes.get(prefix + "/federation/shared/kb", this::browseSharedKb, Roles.USER);
        routes.get(prefix + "/federation/shared/quiz", this::browseSharedQuiz, Roles.USER);
        routes.get(prefix + "/federation/shared/protocols", this::browseSharedProtocols, Roles.USER);

        // Copy shared content to own station
        routes.post(prefix + "/federation/copy/kb/{fileId}", this::copyKbFile, Roles.KNOWLEDGE_MANAGEMENT);
        routes.post(prefix + "/federation/copy/quiz/{catalogId}", this::copyQuizCatalog, Roles.QUIZ_MANAGEMENT);
        routes.post(prefix + "/federation/copy/protocol/{protocolId}", this::copyProtocol, Roles.PROTOCOL_MANAGEMENT);

        // Version/capabilities info
        routes.get(prefix + "/federation/info", this::getInfo, Roles.FEDERATION_MANAGEMENT);
    }

    // -- Partner Management --

    private void listPartners(Context ctx) {
        var session = UserSession.from(ctx);
        var partners = service.findPartners(session.stationId());
        // Enrich with station names
        ctx.json(partners.stream()
                .map(p -> {
                    String partnerName = stationRepository
                            .findById(p.stationId() == session.stationId() ? p.partnerStationId() : p.stationId())
                            .map(s -> s.name())
                            .orElse("Unknown");
                    return new PartnerResponse(p, partnerName);
                })
                .toList());
    }

    private void createInvite(Context ctx) {
        var session = UserSession.from(ctx);
        var invite = service.createInvite(session.stationId());
        pendingInvites.put(invite.inviteCode(), new PendingInvite(session.stationId(), invite.publicKey()));
        ctx.json(Map.of("inviteCode", invite.inviteCode()));
    }

    private void acceptInvite(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(AcceptRequest.class);
        if (req.inviteCode() == null || req.inviteCode().isBlank()) {
            throw new BadRequestResponse("inviteCode is required");
        }

        var pending = pendingInvites.remove(req.inviteCode().trim().toUpperCase());
        if (pending == null) {
            throw new BadRequestResponse("Invalid or expired invite code");
        }
        if (pending.stationId() == session.stationId()) {
            throw new BadRequestResponse("Cannot federate with yourself");
        }

        var partner = service.acceptInvite(session.stationId(), pending.stationId(), pending.publicKey());
        ctx.status(HttpStatus.CREATED).json(partner);
    }

    private void getPartner(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var partner = service.findPartner(id).orElseThrow(NotFoundResponse::new);
        String partnerName = stationRepository
                .findById(partner.partnerStationId())
                .map(s -> s.name())
                .orElse("Unknown");
        ctx.json(new PartnerResponse(partner, partnerName));
    }

    private void suspendPartner(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        service.suspendPartner(id);
        ctx.json(service.findPartner(id).orElseThrow());
    }

    private void resumePartner(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        service.resumePartner(id);
        ctx.json(service.findPartner(id).orElseThrow());
    }

    private void endFederation(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        service.endFederation(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Capabilities --

    private void getCapabilities(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(service.findCapabilities(id));
    }

    private void setCapabilities(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(CapabilityRequest[].class);
        for (var cap : req) {
            service.setCapability(id, cap.capability(), cap.direction(), cap.enabled());
        }
        ctx.json(service.findCapabilities(id));
    }

    // -- Sharing --

    private void listKbShares(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(service.findKbShares(session.stationId()));
    }

    private void createKbShare(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(KbShareRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(service.createKbShare(
                        session.stationId(),
                        req.fileId(),
                        req.folderId(),
                        req.shareScope() != null ? req.shareScope() : "ALL_PARTNERS"));
    }

    private void deleteKbShare(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        service.deleteKbShare(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listQuizShares(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(service.findQuizShares(session.stationId()));
    }

    private void createQuizShare(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(QuizShareRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(service.createQuizShare(
                        session.stationId(),
                        req.catalogId(),
                        req.shareScope() != null ? req.shareScope() : "ALL_PARTNERS"));
    }

    private void deleteQuizShare(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        service.deleteQuizShare(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listProtocolShares(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(service.findProtocolShares(session.stationId()));
    }

    private void createProtocolShare(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(ProtocolShareRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(service.createProtocolShare(
                        session.stationId(),
                        req.protocolId(),
                        req.shareScope() != null ? req.shareScope() : "ALL_PARTNERS"));
    }

    private void deleteProtocolShare(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        service.deleteProtocolShare(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Browse Shared Content (uses same-instance service layer) --

    private void browseSharedKb(Context ctx) {
        var session = UserSession.from(ctx);
        var items = contentService.browseSharedKb(session.stationId());
        ctx.json(items.stream()
                .filter(i -> i.file() != null)
                .map(i -> {
                    String name = stationRepository
                            .findById(i.sourceStationId())
                            .map(s -> s.name())
                            .orElse("Unknown");
                    return new SharedContentItem(
                            i.file().id(),
                            i.file().name(),
                            i.file().description(),
                            name,
                            i.sourceStationId(),
                            i.partnerId());
                })
                .toList());
    }

    private void browseSharedQuiz(Context ctx) {
        var session = UserSession.from(ctx);
        var items = contentService.browseSharedQuiz(session.stationId());
        ctx.json(items.stream()
                .map(i -> {
                    String name = stationRepository
                            .findById(i.sourceStationId())
                            .map(s -> s.name())
                            .orElse("Unknown");
                    return new SharedContentItem(
                            i.catalog().id(),
                            i.catalog().name(),
                            i.catalog().description(),
                            name,
                            i.sourceStationId(),
                            i.partnerId());
                })
                .toList());
    }

    private void browseSharedProtocols(Context ctx) {
        var session = UserSession.from(ctx);
        var items = contentService.browseSharedProtocols(session.stationId());
        ctx.json(items.stream()
                .map(i -> {
                    String name = stationRepository
                            .findById(i.sourceStationId())
                            .map(s -> s.name())
                            .orElse("Unknown");
                    return new SharedContentItem(
                            i.protocol().id(),
                            i.protocol().name(),
                            i.protocol().description(),
                            name,
                            i.sourceStationId(),
                            i.partnerId());
                })
                .toList());
    }

    // -- Copy Shared Content --

    private void copyKbFile(Context ctx) {
        var session = UserSession.from(ctx);
        int fileId = ctx.pathParamAsClass("fileId", Integer.class).get();
        var copied = contentService.copyKbFile(
                fileId, session.stationId(), session.member().id());
        ctx.status(HttpStatus.CREATED).json(copied);
    }

    private void copyQuizCatalog(Context ctx) {
        var session = UserSession.from(ctx);
        int catalogId = ctx.pathParamAsClass("catalogId", Integer.class).get();
        var copied = contentService.copyQuizCatalog(catalogId, session.stationId());
        ctx.status(HttpStatus.CREATED).json(copied);
    }

    private void copyProtocol(Context ctx) {
        var session = UserSession.from(ctx);
        int protocolId = ctx.pathParamAsClass("protocolId", Integer.class).get();
        var copied = contentService.copyProtocol(protocolId, session.stationId());
        ctx.status(HttpStatus.CREATED).json(copied);
    }

    // -- Info --

    private void getInfo(Context ctx) {
        ctx.json(Map.of(
                "federationVersion", service.getFederationVersion(),
                "supportedCapabilities", service.getSupportedCapabilities()));
    }

    // -- Records --

    public record AcceptRequest(String inviteCode) {}

    public record CapabilityRequest(String capability, String direction, boolean enabled) {}

    public record KbShareRequest(Integer fileId, Integer folderId, String shareScope) {}

    public record QuizShareRequest(int catalogId, String shareScope) {}

    public record ProtocolShareRequest(int protocolId, String shareScope) {}

    public record PartnerResponse(
            dev.chojo.ember.feature.federation.entity.FederationPartner partner, String partnerStationName) {}

    public record SharedContentItem(
            int remoteId, String title, String description, String stationName, int stationId, int partnerId) {}
}
