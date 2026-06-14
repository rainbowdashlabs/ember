/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
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

import java.util.List;
import java.util.UUID;

@Singleton
public class FederationRoutes implements Routes {

    private final FederationService service;
    private final StationRepository stationRepository;

    @Inject
    public FederationRoutes(FederationService service, StationRepository stationRepository) {
        this.service = service;
        this.stationRepository = stationRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Partner management
        routes.get(
                prefix + "/federation/partners",
                this::listPartners,
                StationPermission.STATION_FEDERATION,
                StationPermission.BOARD_FEDERATE,
                StationPermission.EVENTS_FEDERATE,
                StationPermission.NEWS_FEDERATE,
                StationPermission.KNOWLEDGE_FEDERATE);
        routes.post(prefix + "/federation/invite", this::createInvite, StationPermission.STATION_FEDERATION);
        routes.post(prefix + "/federation/accept", this::acceptInvite, StationPermission.STATION_FEDERATION);
        routes.get(prefix + "/federation/partners/{id}", this::getPartner, StationPermission.STATION_FEDERATION);
        routes.get(prefix + "/federation/requests", this::listPendingRequests, StationPermission.STATION_FEDERATION);
        routes.post(
                prefix + "/federation/requests/{id}/accept",
                this::acceptPairRequest,
                StationPermission.STATION_FEDERATION);
        routes.post(
                prefix + "/federation/requests/{id}/decline",
                this::declinePairRequest,
                StationPermission.STATION_FEDERATION);
        routes.post(
                prefix + "/federation/partners/{id}/suspend",
                this::suspendPartner,
                StationPermission.STATION_FEDERATION);
        routes.post(
                prefix + "/federation/partners/{id}/resume", this::resumePartner, StationPermission.STATION_FEDERATION);
        routes.delete(prefix + "/federation/partners/{id}", this::endFederation, StationPermission.STATION_FEDERATION);

        // Capabilities
        routes.get(
                prefix + "/federation/partners/{id}/capabilities",
                this::getCapabilities,
                StationPermission.STATION_FEDERATION);
        routes.put(
                prefix + "/federation/partners/{id}/capabilities",
                this::setCapabilities,
                StationPermission.STATION_FEDERATION);

        // Sharing management
        routes.get(prefix + "/federation/shares/kb", this::listKbShares, StationPermission.STATION_FEDERATION);
        routes.post(prefix + "/federation/shares/kb", this::createKbShare, StationPermission.STATION_FEDERATION);
        routes.delete(prefix + "/federation/shares/kb/{id}", this::deleteKbShare, StationPermission.STATION_FEDERATION);
        routes.get(prefix + "/federation/shares/quiz", this::listQuizShares, StationPermission.STATION_FEDERATION);
        routes.post(prefix + "/federation/shares/quiz", this::createQuizShare, StationPermission.STATION_FEDERATION);
        routes.delete(
                prefix + "/federation/shares/quiz/{id}", this::deleteQuizShare, StationPermission.STATION_FEDERATION);
        routes.get(
                prefix + "/federation/shares/protocol", this::listProtocolShares, StationPermission.STATION_FEDERATION);
        routes.post(
                prefix + "/federation/shares/protocol",
                this::createProtocolShare,
                StationPermission.STATION_FEDERATION);
        routes.delete(
                prefix + "/federation/shares/protocol/{id}",
                this::deleteProtocolShare,
                StationPermission.STATION_FEDERATION);

        // Version/capabilities info
        routes.get(prefix + "/federation/info", this::getInfo, StationPermission.STATION_FEDERATION);
    }

    // -- Partner Management --

    private void listPartners(Context ctx) {
        var session = UserSession.from(ctx);
        var partners = service.findPartners(session.stationId());
        // Enrich with station names
        ctx.json(partners.stream()
                .map(p -> {
                    String partnerName = stationRepository
                            .findByUid(p.partnerStationId())
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
        ctx.json(new InviteResponse(code));
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
        if (existingPartners.stream().anyMatch(p -> p.partnerStationId().equals(targetStation.uid()))) {
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
        UUID sessionStationUid = stationRepository
                .findById(session.stationId())
                .map(Station::uid)
                .orElse(null);
        if (!partner.partnerStationId().equals(sessionStationUid)) {
            throw new ForbiddenResponse("This request is not for your station");
        }
        var result = service.acceptPairRequest(requestId);
        ctx.json(result);
    }

    private void declinePairRequest(Context ctx) {
        var session = UserSession.from(ctx);
        int requestId = ctx.pathParamAsClass("id", Integer.class).get();
        var partner = service.findPartner(requestId).orElseThrow(NotFoundResponse::new);
        UUID sessionStationUid = stationRepository
                .findById(session.stationId())
                .map(Station::uid)
                .orElse(null);
        if (!partner.partnerStationId().equals(sessionStationUid)) {
            throw new ForbiddenResponse("This request is not for your station");
        }
        service.declinePairRequest(requestId);
        ctx.json(new MessageResponse("Request declined"));
    }

    private void getPartner(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var partner = service.findPartner(id).orElseThrow(NotFoundResponse::new);
        String partnerName = stationRepository
                .findByUid(partner.partnerStationId())
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

    // -- Info --

    private void getInfo(Context ctx) {
        ctx.json(new FederationInfoResponse(FederationService.FEDERATION_VERSION, service.getSupportedCapabilities()));
    }

    // -- Records --

    public record AcceptRequest(String inviteCode) {}

    public record CapabilityRequest(CapabilityType capability, Direction direction, boolean enabled) {}

    public record KbShareRequest(Integer fileId, Integer folderId, ShareScope shareScope) {}

    public record QuizShareRequest(int catalogId, ShareScope shareScope) {}

    public record ProtocolShareRequest(int protocolId, ShareScope shareScope) {}

    public record PartnerResponse(FederationPartner partner, String partnerStationName) {}

    public record PairRequestResponse(int id, String stationName, String createdAt) {}

    public record InviteResponse(String inviteCode) {}

    public record MessageResponse(String message) {}

    public record FederationInfoResponse(String federationVersion, List<CapabilityType> supportedCapabilities) {}
}
