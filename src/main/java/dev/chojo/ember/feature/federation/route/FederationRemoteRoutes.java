/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.FederationSigningService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unauthenticated HTTP endpoints for cross-instance federation.
 * All requests are verified via RSA signature instead of session tokens.
 */
@Singleton
public class FederationRemoteRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(FederationRemoteRoutes.class);

    private final FederationService federationService;
    private final FederationSigningService signingService;
    private final FederationRepository repository;
    private final EventFederationService eventFederationService;

    @Inject
    public FederationRemoteRoutes(
            FederationService federationService,
            FederationSigningService signingService,
            FederationRepository repository,
            EventFederationService eventFederationService) {
        this.federationService = federationService;
        this.signingService = signingService;
        this.repository = repository;
        this.eventFederationService = eventFederationService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String base = prefix + "/remote";

        // Handshake (no signature required - uses embedded public key)
        routes.post(base + "/handshake", this::handshake);

        // Webhook registration (signature required)
        routes.post(base + "/webhook/register", this::registerWebhook);

        // Webhook receivers (signature required)
        routes.post(base + "/webhook/member-name-changed", this::onMemberNameChanged);

        // Sync polling (signature required)
        routes.get(base + "/sync/metadata", this::syncMetadata);

        // Host change announcement (signature required)
        routes.post(base + "/announce", this::announceHostChange);

        // Version ping (signature required) — returns current version, also updates caller's version via header
        routes.get(base + "/federation/ping", this::versionPing);
    }

    // -- Handshake --

    private void handshake(Context ctx) {
        var req = ctx.bodyAsClass(HandshakeRequest.class);
        if (req.stationId() <= 0 || req.publicKey() == null || req.publicKey().isBlank()) {
            throw new BadRequestResponse("stationId and publicKey are required");
        }

        // Verify the incoming signature using the embedded public key
        var remotePublicKey = signingService.decodePublicKey(req.publicKey());
        if (req.signature() != null && !req.signature().isBlank()) {
            String bodyForVerification = req.stationId() + ":" + req.federationVersion() + ":" + req.publicKey();
            boolean valid = signingService.verify(bodyForVerification, req.signature(), remotePublicKey, Instant.now());
            if (!valid) {
                throw new ForbiddenResponse("Invalid handshake signature");
            }
        }

        ctx.json(new HandshakeResponse(
                0, // Our station ID would come from config; 0 as placeholder for remote
                FederationService.FEDERATION_VERSION,
                federationService.getSupportedCapabilities(),
                "")); // Public key returned on partner creation
    }

    // -- Signature Verification --

    /**
     * Returns the verified federation partner from the centrally resolved session.
     * The signature is verified by {@link dev.chojo.ember.api.AccessManager} before this handler runs.
     */
    private FederationPartner requireFederationPartner(Context ctx) {
        var session = FederationSession.from(ctx);
        if (session == null) {
            throw new ForbiddenResponse("Missing or invalid federation signature");
        }
        return session.partner();
    }

    // -- Webhook Registration --

    private void registerWebhook(Context ctx) {
        var partner = requireFederationPartner(ctx);
        var req = ctx.bodyAsClass(WebhookRegisterRequest.class);
        if (req.webhookUrl() == null || req.webhookUrl().isBlank()) {
            throw new BadRequestResponse("webhookUrl is required");
        }

        repository.setWebhookUrl(partner.id(), req.webhookUrl());
        ctx.status(HttpStatus.OK).json(Map.of("status", "registered", "webhookUrl", req.webhookUrl()));
    }

    // -- Sync Polling --

    private void syncMetadata(Context ctx) {
        var partner = requireFederationPartner(ctx);
        String sinceParam = ctx.queryParam("since");
        if (sinceParam == null || sinceParam.isBlank()) {
            throw new BadRequestResponse("since parameter is required");
        }

        Instant since;
        try {
            since = Instant.parse(sinceParam);
        } catch (Exception e) {
            log.warn("Invalid since timestamp for sync metadata: {}", sinceParam, e);
            throw new BadRequestResponse("Invalid since timestamp");
        }

        var changes = repository.findChangesSince(partner.stationId(), since);
        repository.updateLastSyncAt(partner.id());
        ctx.json(changes);
    }

    // -- Host Change Announcement --

    /**
     * Called by a station that has moved to a new host. Updates the remote_host
     * on all partner records pointing to the announcing station.
     * Notifies managers of the host change.
     */
    private void announceHostChange(Context ctx) {
        var partner = requireFederationPartner(ctx);
        var req = ctx.bodyAsClass(AnnounceRequest.class);
        if (req.newHost() == null || req.newHost().isBlank()) {
            throw new BadRequestResponse("newHost is required");
        }

        // The announcing station is the one identified by the federation signature header (UUID)
        UUID remoteStationUid = UUID.fromString(ctx.header("X-Federation-Station-Id"));
        federationService.updateRemoteHost(remoteStationUid, req.newHost());

        log.info("Federation: Station {} announced host change to {}", remoteStationUid, req.newHost());

        ctx.json(Map.of("status", "ok"));
    }

    private void onMemberNameChanged(Context ctx) {
        var partner = requireFederationPartner(ctx);
        var req = ctx.bodyAsClass(MemberNameChangedWebhook.class);
        eventFederationService.invalidateName(partner.id(), req.remoteMemberId());
        ctx.json(Map.of("status", "ok"));
    }

    // -- Version Ping --

    private void versionPing(Context ctx) {
        requireFederationPartner(ctx);
        ctx.json(new VersionPingResponse(FederationService.FEDERATION_VERSION));
    }

    // -- Request/Response Records --

    public record VersionPingResponse(String version) {}

    public record MemberNameChangedWebhook(UUID remoteMemberId) {}

    public record AnnounceRequest(String newHost) {}

    public record HandshakeRequest(
            int stationId, String federationVersion, List<String> capabilities, String publicKey, String signature) {}

    public record HandshakeResponse(
            int stationId, String federationVersion, List<CapabilityType> capabilities, String publicKey) {}

    public record WebhookRegisterRequest(String webhookUrl) {}
}
