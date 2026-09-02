/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.FederationHeaders;
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
import dev.chojo.ember.feature.federation.service.FederationEnrollmentService;
import dev.chojo.ember.feature.federation.service.FederationService;
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
    private final FederationEnrollmentService enrollmentService;
    private final FederationRepository repository;
    private final EventFederationService eventFederationService;
    private final RemoteUrlValidator urlValidator;
    private final StationReadOnlyGuard readOnlyGuard;

    @Inject
    public RemoteFederationRoutes(
            FederationService federationService,
            FederationEnrollmentService enrollmentService,
            FederationRepository repository,
            EventFederationService eventFederationService,
            RemoteUrlValidator urlValidator,
            StationReadOnlyGuard readOnlyGuard) {
        this.federationService = federationService;
        this.enrollmentService = enrollmentService;
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

    /**
     * Answers a station on another instance that is redeeming an invite code issued here.
     *
     * <p>The endpoint carries no partner session, because the partnership it creates does not exist
     * yet: what stands in for one is the enrollment signature inside the body. Everything the
     * exchange decides happens in {@link FederationEnrollmentService}; this handler only turns the
     * outcome into a status code the calling instance can act on.
     */
    private void handshake(Context ctx) {
        switch (enrollmentService.acceptHandshake(ctx.bodyAsClass(HandshakeRequest.class))) {
            case FederationEnrollmentService.Handshake.Accepted accepted -> ctx.json(accepted.response());
            case FederationEnrollmentService.Handshake.Rejected rejected -> reject(ctx, rejected.reason());
        }
    }

    private void reject(Context ctx, FederationEnrollmentService.HandshakeRejection reason) {
        var local = FederationContractVersions.current();
        switch (reason) {
            case INVALID_REQUEST -> throw new BadRequestResponse("The handshake is missing fields it needs");
            case BAD_SIGNATURE -> throw new ForbiddenResponse("Invalid handshake signature");
            case CONTRACT_MISMATCH ->
                ctx.status(HttpStatus.CONFLICT)
                        .json(new FederationContractBinder.MismatchResponse(
                                FederationContractBinder.CORE_MISMATCH,
                                null,
                                local.core(),
                                ctx.header(FederationHeaders.HEADER_CORE)));
            case HOST_REFUSED ->
                ctx.status(HttpStatus.UNPROCESSABLE_CONTENT)
                        .json(new StatusResponse(RemoteUrlValidator.rejectReason()));
            case UNKNOWN_STATION -> ctx.status(HttpStatus.NOT_FOUND).json(new StatusResponse("No such station here"));
            case SPENT_TOKEN -> ctx.status(HttpStatus.GONE).json(new StatusResponse("This code has already been used"));
        }
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

    /**
     * The entering station's side of the handshake.
     *
     * @param stationUid       the entering station, as the instance it lives on knows it
     * @param targetStationUid the station the invite code names, on the instance being called
     * @param stationName      the entering station's name, which the called instance cannot look up
     * @param baseUrl          where the entering instance is reached
     * @param contract         the entering instance's contract vector
     * @param publicKey        the entering station's federation public key
     * @param token            the invite token, which is redeemed by the instance that issued it
     * @param signature        an enrollment signature over all of the above
     */
    public record HandshakeRequest(
            UUID stationUid,
            UUID targetStationUid,
            String stationName,
            String baseUrl,
            FederationContract contract,
            String publicKey,
            String token,
            String signature) {}

    /**
     * The issuing station's side of the handshake, sent once the token has been redeemed.
     *
     * @param stationUid  the issuing station
     * @param stationName the issuing station's name
     * @param baseUrl     where the issuing instance is reached
     * @param contract    the issuing instance's contract vector
     * @param publicKey   the issuing station's federation public key
     */
    public record HandshakeResponse(
            UUID stationUid, String stationName, String baseUrl, FederationContract contract, String publicKey) {}

    public record WebhookRegisterRequest(String webhookUrl) {}

    public record WebhookRegisterResponse(String status, String webhookUrl) {}

    public record StatusResponse(String status) {}
}
