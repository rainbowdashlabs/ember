/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederatedContentService;
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
    private final FederatedContentService contentService;
    private final FederationSigningService signingService;
    private final FederationWebhookService webhookService;
    private final FederationRepository repository;
    private final LendingService lendingService;
    private final KnowledgeBaseService kbService;
    private final QuizService quizService;
    private final TestProtocolService protocolService;

    @Inject
    public FederationRemoteRoutes(
            FederationService federationService,
            FederatedContentService contentService,
            FederationSigningService signingService,
            FederationWebhookService webhookService,
            FederationRepository repository,
            LendingService lendingService,
            KnowledgeBaseService kbService,
            QuizService quizService,
            TestProtocolService protocolService) {
        this.federationService = federationService;
        this.contentService = contentService;
        this.signingService = signingService;
        this.webhookService = webhookService;
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
                federationService.getFederationVersion(),
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
            throw new BadRequestResponse("Invalid X-Federation-Station-Id");
        }

        Instant timestamp;
        try {
            timestamp = Instant.parse(timestampHeader);
        } catch (Exception e) {
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

        return p;
    }

    // -- KB Endpoints --

    private void browseKb(Context ctx) {
        var partner = verifySignature(ctx);
        var items = contentService.browseSharedKb(partner.stationId());
        ctx.json(items.stream()
                .filter(i -> i.file() != null)
                .map(i -> Map.of(
                        "id", i.file().id(),
                        "name", i.file().name(),
                        "description", i.file().description(),
                        "fileType", i.file().fileType().name(),
                        "updatedAt", i.file().updatedAt().toString()))
                .toList());
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
        var items = contentService.browseSharedQuiz(partner.stationId());
        ctx.json(items.stream()
                .map(i -> Map.of(
                        "id", i.catalog().id(),
                        "name", i.catalog().name(),
                        "description", i.catalog().description(),
                        "updatedAt", i.catalog().updatedAt().toString()))
                .toList());
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
        var items = contentService.browseSharedProtocols(partner.stationId());
        ctx.json(items.stream()
                .map(i -> Map.of(
                        "id", i.protocol().id(),
                        "name", i.protocol().name(),
                        "description", i.protocol().description(),
                        "updatedAt", i.protocol().updatedAt().toString()))
                .toList());
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
            throw new BadRequestResponse("Invalid since timestamp");
        }

        var changes = repository.findChangesSince(partner.stationId(), since);
        repository.updateLastSyncAt(partner.id());
        ctx.json(changes);
    }

    // -- Request/Response Records --

    public record HandshakeRequest(
            int stationId, int federationVersion, List<String> capabilities, String publicKey, String signature) {}

    public record HandshakeResponse(
            int stationId, int federationVersion, List<String> capabilities, String publicKey) {}

    public record WebhookRegisterRequest(String webhookUrl) {}
}
