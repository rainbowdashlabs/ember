/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.entity.FederationChangeLog;
import dev.chojo.ember.feature.federation.entity.FederationContract;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.FederationSigningService;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
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
import java.util.UUID;

/**
 * Unauthenticated HTTP endpoints for cross-instance federation.
 * All requests are verified via RSA signature instead of session tokens.
 */
@Singleton
public class RemoteFederationRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(RemoteFederationRoutes.class);

    public static final FederationEndpoint HANDSHAKE = FederationEndpoint.post(
                    FederationSurface.CORE, "/remote/handshake", HandshakeRequest.class, HandshakeResponse.class)
            .exempt();
    public static final FederationEndpoint WEBHOOK_REGISTER = FederationEndpoint.post(
            FederationSurface.CORE,
            "/remote/webhook/register",
            WebhookRegisterRequest.class,
            WebhookRegisterResponse.class);
    public static final FederationEndpoint MEMBER_NAME_CHANGED = FederationEndpoint.post(
            FederationSurface.CORE,
            "/remote/webhook/member-name-changed",
            MemberNameChangedWebhook.class,
            StatusResponse.class);
    public static final FederationEndpoint SYNC_METADATA =
            FederationEndpoint.getList(FederationSurface.CORE, "/remote/sync/metadata", FederationChangeLog.class);
    /**
     * Version-exempt like the handshake and the ping: a station that moves host and rolls
     * the core contract in the same release must still be able to announce its new address,
     * or the partner keeps pinging the dead host and the vector can never heal.
     */
    public static final FederationEndpoint ANNOUNCE = FederationEndpoint.post(
                    FederationSurface.CORE, "/remote/announce", AnnounceRequest.class, StatusResponse.class)
            .exempt();

    public static final FederationEndpoint VERSION_PING = FederationEndpoint.get(
                    FederationSurface.CORE, "/remote/federation/ping", VersionPingResponse.class)
            .exempt();

    public static final List<FederationEndpoint> CONTRACT =
            List.of(HANDSHAKE, WEBHOOK_REGISTER, MEMBER_NAME_CHANGED, SYNC_METADATA, ANNOUNCE, VERSION_PING);

    private final FederationService federationService;
    private final FederationSigningService signingService;
    private final FederationRepository repository;
    private final EventFederationService eventFederationService;
    private final RemoteUrlValidator urlValidator;
    private final StationReadOnlyGuard readOnlyGuard;

    @Inject
    public RemoteFederationRoutes(
            FederationService federationService,
            FederationSigningService signingService,
            FederationRepository repository,
            EventFederationService eventFederationService,
            RemoteUrlValidator urlValidator,
            StationReadOnlyGuard readOnlyGuard) {
        this.federationService = federationService;
        this.signingService = signingService;
        this.repository = repository;
        this.eventFederationService = eventFederationService;
        this.urlValidator = urlValidator;
        this.readOnlyGuard = readOnlyGuard;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, binder -> binder.handle(HANDSHAKE, this::handshake)
                .handle(WEBHOOK_REGISTER, this::registerWebhook)
                .handle(MEMBER_NAME_CHANGED, this::onMemberNameChanged)
                .handle(SYNC_METADATA, this::syncMetadata)
                .handle(ANNOUNCE, this::announceHostChange)
                .handle(VERSION_PING, this::versionPing));
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
            String core = req.contract() != null ? req.contract().core() : "";
            String payload = req.stationId() + ":" + core + ":" + req.publicKey();
            boolean valid = signingService.verifyEnrollmentPayload(payload, req.signature(), remotePublicKey);
            if (!valid) {
                throw new ForbiddenResponse("Invalid handshake signature");
            }
        }

        ctx.json(new HandshakeResponse(
                0, // Our station ID would come from config; 0 as placeholder for remote
                FederationContractVersions.current(),
                "")); // Public key returned on partner creation
    }

    // -- Webhook Registration --

    private void registerWebhook(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        readOnlyGuard.requireWritable(partner.stationId());
        var req = ctx.bodyAsClass(WebhookRegisterRequest.class);
        if (req.webhookUrl() == null || req.webhookUrl().isBlank()) {
            throw new BadRequestResponse("webhookUrl is required");
        }
        if (!urlValidator.isAllowed(req.webhookUrl())) {
            throw new BadRequestResponse(RemoteUrlValidator.rejectReason());
        }

        repository.setWebhookUrl(partner.id(), req.webhookUrl());
        ctx.status(HttpStatus.OK).json(new WebhookRegisterResponse("registered", req.webhookUrl()));
    }

    // -- Sync Polling --

    private void syncMetadata(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        readOnlyGuard.requireWritable(partner.stationId());
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
        var partner = FederationSession.requirePartner(ctx);
        readOnlyGuard.requireWritable(partner.stationId());
        var req = ctx.bodyAsClass(AnnounceRequest.class);
        if (req.newHost() == null || req.newHost().isBlank()) {
            throw new BadRequestResponse("newHost is required");
        }
        if (!urlValidator.isAllowed(req.newHost())) {
            throw new BadRequestResponse(RemoteUrlValidator.rejectReason());
        }

        // The announcing station is the one identified by the federation signature header (UUID)
        UUID remoteStationUid = UUID.fromString(ctx.header("X-Federation-Station-Id"));
        federationService.updateRemoteHost(remoteStationUid, req.newHost());

        log.info("Federation: Station {} announced host change to {}", remoteStationUid, req.newHost());

        ctx.json(new StatusResponse("ok"));
    }

    private void onMemberNameChanged(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        readOnlyGuard.requireWritable(partner.stationId());
        var req = ctx.bodyAsClass(MemberNameChangedWebhook.class);
        eventFederationService.invalidateName(partner.id(), req.remoteMemberId());
        ctx.json(new StatusResponse("ok"));
    }

    // -- Version Ping --

    private void versionPing(Context ctx) {
        FederationSession.requirePartner(ctx);
        ctx.json(new VersionPingResponse(FederationContractVersions.current()));
    }

    // -- Request/Response Records --

    public record VersionPingResponse(FederationContract contract) {}

    public record MemberNameChangedWebhook(UUID remoteMemberId) {}

    public record AnnounceRequest(String newHost) {}

    public record HandshakeRequest(int stationId, FederationContract contract, String publicKey, String signature) {}

    public record HandshakeResponse(int stationId, FederationContract contract, String publicKey) {}

    public record WebhookRegisterRequest(String webhookUrl) {}

    public record WebhookRegisterResponse(String status, String webhookUrl) {}

    public record StatusResponse(String status) {}
}
