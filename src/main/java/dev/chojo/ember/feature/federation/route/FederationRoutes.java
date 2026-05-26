/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.service.FederatedContentService;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class FederationRoutes implements Routes {

    private final FederationService service;
    private final FederatedContentService contentService;
    private final StationRepository stationRepository;

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
        routes.get(prefix + "/federation/partners", this::listPartners, Roles.FEDERATION_MANAGER);
        routes.post(prefix + "/federation/invite", this::createInvite, Roles.FEDERATION_MANAGER);
        routes.post(prefix + "/federation/accept", this::acceptInvite, Roles.FEDERATION_MANAGER);
        routes.get(prefix + "/federation/partners/{id}", this::getPartner, Roles.FEDERATION_MANAGER);
        routes.get(prefix + "/federation/requests", this::listPendingRequests, Roles.FEDERATION_MANAGER);
        routes.post(prefix + "/federation/requests/{id}/accept", this::acceptPairRequest, Roles.FEDERATION_MANAGER);
        routes.post(prefix + "/federation/requests/{id}/decline", this::declinePairRequest, Roles.FEDERATION_MANAGER);
        routes.post(prefix + "/federation/partners/{id}/suspend", this::suspendPartner, Roles.FEDERATION_MANAGER);
        routes.post(prefix + "/federation/partners/{id}/resume", this::resumePartner, Roles.FEDERATION_MANAGER);
        routes.delete(prefix + "/federation/partners/{id}", this::endFederation, Roles.FEDERATION_MANAGER);

        // Capabilities
        routes.get(prefix + "/federation/partners/{id}/capabilities", this::getCapabilities, Roles.FEDERATION_MANAGER);
        routes.put(prefix + "/federation/partners/{id}/capabilities", this::setCapabilities, Roles.FEDERATION_MANAGER);

        // Sharing management
        routes.get(prefix + "/federation/shares/kb", this::listKbShares, Roles.FEDERATION_MANAGER);
        routes.post(prefix + "/federation/shares/kb", this::createKbShare, Roles.FEDERATION_MANAGER);
        routes.delete(prefix + "/federation/shares/kb/{id}", this::deleteKbShare, Roles.FEDERATION_MANAGER);
        routes.get(prefix + "/federation/shares/quiz", this::listQuizShares, Roles.FEDERATION_MANAGER);
        routes.post(prefix + "/federation/shares/quiz", this::createQuizShare, Roles.FEDERATION_MANAGER);
        routes.delete(prefix + "/federation/shares/quiz/{id}", this::deleteQuizShare, Roles.FEDERATION_MANAGER);
        routes.get(prefix + "/federation/shares/protocol", this::listProtocolShares, Roles.FEDERATION_MANAGER);
        routes.post(prefix + "/federation/shares/protocol", this::createProtocolShare, Roles.FEDERATION_MANAGER);
        routes.delete(prefix + "/federation/shares/protocol/{id}", this::deleteProtocolShare, Roles.FEDERATION_MANAGER);

        // Browse shared content (available to all users)
        routes.get(prefix + "/federation/shared/kb", this::browseSharedKb, Roles.USER);
        routes.get(prefix + "/federation/shared/quiz", this::browseSharedQuiz, Roles.USER);
        routes.get(prefix + "/federation/shared/protocols", this::browseSharedProtocols, Roles.USER);

        // Copy shared content to own station
        routes.post(prefix + "/federation/copy/kb/{fileId}", this::copyKbFile, Roles.KNOWLEDGE_MANAGER);
        routes.post(prefix + "/federation/copy/quiz/{catalogId}", this::copyQuizCatalog, Roles.QUIZ_MANAGER);
        routes.post(prefix + "/federation/copy/protocol/{protocolId}", this::copyProtocol, Roles.PROTOCOL_MANAGER);

        // Version/capabilities info
        routes.get(prefix + "/federation/info", this::getInfo, Roles.FEDERATION_MANAGER);
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
                            .map(Station::name)
                            .orElse("Unknown");
                    return new PartnerResponse(p, partnerName);
                })
                .toList());
    }

    private void createInvite(Context ctx) {
        var session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow(NotFoundResponse::new);
        // Station invite — includes token proving consent, auto-activates on accept
        var code = service.generateStationInvite(station.id(), station.uid());
        ctx.json(Map.of("inviteCode", code));
    }

    private void acceptInvite(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(AcceptRequest.class);
        if (req.inviteCode() == null || req.inviteCode().isBlank()) {
            throw new BadRequestResponse("inviteCode is required");
        }

        String code = req.inviteCode().trim();

        // Parse the code to extract station UID, host, and optional token
        var parts = service.parsePairingCode(code);
        if (parts.isEmpty()) {
            throw new BadRequestResponse("Invalid code format");
        }

        // Check if the code is for this instance
        if (!parts.get().host().equalsIgnoreCase(service.getInstanceHost())) {
            throw new BadRequestResponse(
                    "This code is for a different instance: " + parts.get().host());
        }

        // Find the target station by UID
        var targetStation = stationRepository
                .findByUid(parts.get().stationUid())
                .orElseThrow(() -> new BadRequestResponse("Station not found"));
        if (targetStation.id() == session.stationId()) {
            throw new BadRequestResponse("Cannot federate with yourself");
        }

        // Check not already federated
        var existingPartners = service.findPartners(session.stationId());
        if (existingPartners.stream().anyMatch(p -> p.partnerStationId() == targetStation.id())) {
            throw new BadRequestResponse("Already federated with this station");
        }

        if (parts.get().isStationInvite()) {
            // Station invite (with token) — validate token and auto-activate
            if (!service.consumeInviteToken(targetStation.id(), parts.get().token())) {
                throw new BadRequestResponse("Invalid or already used invite code");
            }
            var keyPair = service.generateKeyPair();
            var partner = service.acceptInvite(
                    session.stationId(), targetStation.id(), service.encodePublicKey(keyPair), null, null);
            ctx.status(HttpStatus.CREATED).json(partner);
        } else {
            // Discovery code (no token) — create pending request
            var pendingRequests = service.findPendingRequests(targetStation.id());
            if (pendingRequests.stream().anyMatch(p -> p.stationId() == session.stationId())) {
                throw new BadRequestResponse("Request already pending");
            }
            var partner = service.createPairRequest(session.stationId(), targetStation.id());
            ctx.status(HttpStatus.CREATED).json(partner);
        }
    }

    private void listPendingRequests(Context ctx) {
        var session = UserSession.from(ctx);
        var requests = service.findPendingRequests(session.stationId());
        ctx.json(requests.stream()
                .map(p -> {
                    String requesterName = stationRepository
                            .findById(p.stationId())
                            .map(Station::name)
                            .orElse("Unknown");
                    return new PairRequestResponse(
                            p.id(), requesterName, p.createdAt().toString());
                })
                .toList());
    }

    private void acceptPairRequest(Context ctx) {
        var session = UserSession.from(ctx);
        int requestId = ctx.pathParamAsClass("id", Integer.class).get();
        // Verify the request targets this station
        var partner = service.findPartner(requestId).orElseThrow(NotFoundResponse::new);
        if (partner.partnerStationId() != session.stationId()) {
            throw new ForbiddenResponse("This request is not for your station");
        }
        var result = service.acceptPairRequest(requestId);
        ctx.json(result);
    }

    private void declinePairRequest(Context ctx) {
        var session = UserSession.from(ctx);
        int requestId = ctx.pathParamAsClass("id", Integer.class).get();
        var partner = service.findPartner(requestId).orElseThrow(NotFoundResponse::new);
        if (partner.partnerStationId() != session.stationId()) {
            throw new ForbiddenResponse("This request is not for your station");
        }
        service.declinePairRequest(requestId);
        ctx.json(Map.of("message", "Request declined"));
    }

    private void getPartner(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var partner = service.findPartner(id).orElseThrow(NotFoundResponse::new);
        String partnerName = stationRepository
                .findById(partner.partnerStationId())
                .map(Station::name)
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
                        req.shareScope() != null ? req.shareScope() : ShareScope.ALL_PARTNERS));
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
                        req.shareScope() != null ? req.shareScope() : ShareScope.ALL_PARTNERS));
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
                        req.shareScope() != null ? req.shareScope() : ShareScope.ALL_PARTNERS));
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
                            .map(Station::name)
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
                            .map(Station::name)
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
                            .map(Station::name)
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
                "federationVersion",
                FederationService.FEDERATION_VERSION,
                "supportedCapabilities",
                service.getSupportedCapabilities()));
    }

    // -- Records --

    public record AcceptRequest(String inviteCode) {}

    public record CapabilityRequest(CapabilityType capability, Direction direction, boolean enabled) {}

    public record KbShareRequest(Integer fileId, Integer folderId, ShareScope shareScope) {}

    public record QuizShareRequest(int catalogId, ShareScope shareScope) {}

    public record ProtocolShareRequest(int protocolId, ShareScope shareScope) {}

    public record PartnerResponse(FederationPartner partner, String partnerStationName) {}

    public record PairRequestResponse(int id, String stationName, String createdAt) {}

    public record SharedContentItem(
            int remoteId, String title, String description, String stationName, int stationId, int partnerId) {}
}
