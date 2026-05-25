/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.FederationSigningService;
import dev.chojo.ember.feature.federation.service.FederationWebhookService;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import dev.chojo.ember.feature.quiz.service.QuizService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
    private final LendingService lendingService;
    private final KnowledgeBaseService kbService;
    private final QuizService quizService;
    private final TestProtocolService protocolService;

    @Inject
    public FederationRemoteRoutes(
            FederationService federationService,
            FederationSigningService signingService,
            FederationWebhookService webhookService,
            FederationRepository repository,
            LendingService lendingService,
            KnowledgeBaseService kbService,
            QuizService quizService,
            TestProtocolService protocolService) {
        this.federationService = federationService;
        this.signingService = signingService;
        this.repository = repository;
        this.lendingService = lendingService;
        this.kbService = kbService;
        this.quizService = quizService;
        this.protocolService = protocolService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String base = prefix + "/federation/remote";

        // Handshake (no signature required - uses embedded public key)
        routes.post(base + "/handshake", this::handshake);

        // KB endpoints (signature required)
        routes.get(base + "/kb/browse", this::browseKb);
        routes.get(base + "/kb/file/{id}", this::getKbFile);
        routes.get(base + "/kb/file/{id}/content", this::getKbFileContent);

        // Quiz endpoints (signature required)
        routes.get(base + "/quiz/catalogs", this::browseCatalogs);
        routes.get(base + "/quiz/catalog/{id}", this::getCatalog);

        // Protocol endpoints (signature required)
        routes.get(base + "/protocols", this::browseProtocols);
        routes.get(base + "/protocols/{id}", this::getProtocol);

        // Lending endpoints (signature required)
        routes.get(base + "/lending/messages/{requestId}", this::getLendingMessages);

        // Webhook registration (signature required)
        routes.post(base + "/webhook/register", this::registerWebhook);

        // Sync polling (signature required)
        routes.get(base + "/sync/metadata", this::syncMetadata);

        // Host change announcement (signature required)
        routes.post(base + "/announce", this::announceHostChange);
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

    // -- Signature Verification Middleware --

    /**
     * Verifies the federation signature headers on the given request context.
     * Returns the verified partner record.
     */
    private FederationPartner verifySignature(Context ctx) {
        String stationIdHeader = ctx.header("X-Federation-Station-Id");
        String signature = ctx.header("X-Federation-Signature");
        String timestampHeader = ctx.header("X-Federation-Timestamp");

        if (stationIdHeader == null || signature == null || timestampHeader == null) {
            throw new ForbiddenResponse("Missing federation signature headers");
        }

        int remoteStationId;
        try {
            remoteStationId = Integer.parseInt(stationIdHeader);
        } catch (NumberFormatException e) {
            log.warn("Invalid X-Federation-Station-Id header value: {}", stationIdHeader, e);
            throw new BadRequestResponse("Invalid X-Federation-Station-Id");
        }

        Instant timestamp;
        try {
            timestamp = Instant.parse(timestampHeader);
        } catch (Exception e) {
            log.warn("Invalid X-Federation-Timestamp header value: {}", timestampHeader, e);
            throw new BadRequestResponse("Invalid X-Federation-Timestamp");
        }

        // Look up the partner by remote station ID
        var partner = repository.findPartnerByRemoteStationId(remoteStationId);
        if (partner.isEmpty()) {
            throw new ForbiddenResponse("Unknown federation partner");
        }

        var p = partner.get();
        if (p.status() != FederationPartner.FederationStatus.ACTIVE) {
            throw new ForbiddenResponse("Federation partnership is not active");
        }

        if (p.partnerPublicKey() == null || p.partnerPublicKey().isBlank()) {
            throw new ForbiddenResponse("Partner public key not configured");
        }

        // Verify signature
        var publicKey = signingService.decodePublicKey(p.partnerPublicKey());
        String body = ctx.body().isEmpty() ? ctx.queryString() != null ? ctx.queryString() : "" : ctx.body();
        boolean valid = signingService.verify(body, signature, publicKey, timestamp);
        if (!valid) {
            throw new ForbiddenResponse("Invalid federation signature");
        }

        // Track remote federation version
        String versionHeader = ctx.header("X-Federation-Version");
        if (versionHeader != null) {
            try {
                int remoteVersion = Integer.parseInt(versionHeader);
                if (remoteVersion != p.federationVersion()) {
                    repository.updateFederationVersion(p.id(), remoteVersion);
                    if (remoteVersion != FederationService.FEDERATION_VERSION) {
                        log.warn(
                                "Federation partner {} (station {}) is running version {} (we are version {})",
                                p.id(),
                                p.partnerStationId(),
                                remoteVersion,
                                FederationService.FEDERATION_VERSION);
                    }
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return p;
    }

    // -- KB Endpoints --

    private void browseKb(Context ctx) {
        var partner = verifySignature(ctx);
        // Only return files shared by THIS station — never re-share federated content
        var shares = repository.findKbShares(partner.stationId());
        var result = shares.stream()
                .filter(s -> s.fileId() != null)
                .flatMap(s -> kbService.findFile(s.fileId()).stream())
                .filter(file -> file.stationId() == partner.stationId())
                .map(file -> Map.<String, Object>of(
                        "id", file.id(),
                        "name", file.name(),
                        "description", file.description() != null ? file.description() : "",
                        "fileType", file.fileType().name(),
                        "updatedAt", file.updatedAt().toString()))
                .toList();
        ctx.json(result);
    }

    private void getKbFile(Context ctx) {
        var partner = verifySignature(ctx);
        int fileId = ctx.pathParamAsClass("id", Integer.class).get();

        // Verify the file is shared with this partner
        var file = kbService.findFile(fileId).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != partner.stationId()) {
            throw new ForbiddenResponse("File not shared with this partner");
        }

        ctx.json(file);
    }

    private void getKbFileContent(Context ctx) {
        var partner = verifySignature(ctx);
        int fileId = ctx.pathParamAsClass("id", Integer.class).get();

        var file = kbService.findFile(fileId).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != partner.stationId()) {
            throw new ForbiddenResponse("File not shared with this partner");
        }

        var content = kbService.getMarkdownContent(fileId).orElse("");
        ctx.json(Map.of("fileId", fileId, "content", content));
    }

    // -- Quiz Endpoints --

    private void browseCatalogs(Context ctx) {
        var partner = verifySignature(ctx);
        // Only return catalogs shared by THIS station — never re-share federated content
        var shares = repository.findQuizShares(partner.stationId());
        var result = shares.stream()
                .filter(s -> s.catalogId() != null)
                .flatMap(s -> quizService.findCatalog(s.catalogId()).stream())
                .filter(catalog -> catalog.stationId() == partner.stationId())
                .map(catalog -> Map.<String, Object>of(
                        "id", catalog.id(),
                        "name", catalog.name(),
                        "description", catalog.description(),
                        "updatedAt", catalog.updatedAt().toString()))
                .toList();
        ctx.json(result);
    }

    private void getCatalog(Context ctx) {
        var partner = verifySignature(ctx);
        int catalogId = ctx.pathParamAsClass("id", Integer.class).get();

        var catalog = quizService.findCatalog(catalogId).orElseThrow(NotFoundResponse::new);
        if (catalog.stationId() != partner.stationId()) {
            throw new ForbiddenResponse("Catalog not shared with this partner");
        }

        var categories = quizService.findCategories(catalog.stationId());
        var questions = quizService.findQuestions(catalog.id());
        ctx.json(Map.of("catalog", catalog, "categories", categories, "questions", questions));
    }

    // -- Protocol Endpoints --

    private void browseProtocols(Context ctx) {
        var partner = verifySignature(ctx);
        // Only return protocols shared by THIS station — never re-share federated content
        var shares = repository.findProtocolShares(partner.stationId());
        var result = shares.stream()
                .filter(s -> s.protocolId() != null)
                .flatMap(s -> protocolService.findProtocol(s.protocolId()).stream())
                .filter(proto -> proto.stationId() == partner.stationId())
                .map(proto -> Map.<String, Object>of(
                        "id", proto.id(),
                        "name", proto.name(),
                        "description", proto.description(),
                        "updatedAt", proto.updatedAt().toString()))
                .toList();
        ctx.json(result);
    }

    private void getProtocol(Context ctx) {
        var partner = verifySignature(ctx);
        int protocolId = ctx.pathParamAsClass("id", Integer.class).get();

        var protocol = protocolService.findProtocol(protocolId).orElseThrow(NotFoundResponse::new);
        if (protocol.stationId() != partner.stationId()) {
            throw new ForbiddenResponse("Protocol not shared with this partner");
        }

        var sections = protocolService.findSections(protocolId);
        var items = protocolService.findAllItemsByProtocol(protocolId);
        ctx.json(Map.of("protocol", protocol, "sections", sections, "items", items));
    }

    // -- Lending Endpoints --

    private void getLendingMessages(Context ctx) {
        var partner = verifySignature(ctx);
        int requestId = ctx.pathParamAsClass("requestId", Integer.class).get();

        // Return only messages sent by the authenticated partner's station
        var messages = lendingService.getLocalMessages(requestId, partner.stationId());
        ctx.json(messages);
    }

    // -- Webhook Registration --

    private void registerWebhook(Context ctx) {
        var partner = verifySignature(ctx);
        var req = ctx.bodyAsClass(WebhookRegisterRequest.class);
        if (req.webhookUrl() == null || req.webhookUrl().isBlank()) {
            throw new BadRequestResponse("webhookUrl is required");
        }

        repository.setWebhookUrl(partner.id(), req.webhookUrl());
        ctx.status(HttpStatus.OK).json(Map.of("status", "registered", "webhookUrl", req.webhookUrl()));
    }

    // -- Sync Polling --

    private void syncMetadata(Context ctx) {
        var partner = verifySignature(ctx);
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
        var partner = verifySignature(ctx);
        var req = ctx.bodyAsClass(AnnounceRequest.class);
        if (req.newHost() == null || req.newHost().isBlank()) {
            throw new BadRequestResponse("newHost is required");
        }

        // Computed but not yet used — kept for future audit logging
        int announcingStationId =
                partner.stationId() == partner.partnerStationId() ? partner.stationId() : partner.partnerStationId();
        // The announcing station is the one identified by the federation signature header
        // Uses internal int IDs in the federation protocol — UUID migration requires protocol version bump
        int remoteStationId = Integer.parseInt(ctx.header("X-Federation-Station-Id"));
        federationService.updateRemoteHost(remoteStationId, req.newHost());

        log.info("Federation: Station {} announced host change to {}", remoteStationId, req.newHost());

        ctx.json(Map.of("status", "ok"));
    }

    // -- Request/Response Records --

    public record AnnounceRequest(String newHost) {}

    // Uses internal int IDs in the federation protocol — UUID migration requires protocol version bump
    public record HandshakeRequest(
            int stationId, int federationVersion, List<String> capabilities, String publicKey, String signature) {}

    public record HandshakeResponse(
            int stationId, int federationVersion, List<String> capabilities, String publicKey) {}

    public record WebhookRegisterRequest(String webhookUrl) {}
}
