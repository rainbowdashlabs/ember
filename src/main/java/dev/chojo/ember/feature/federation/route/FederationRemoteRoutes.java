/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.FederationSigningService;
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
import java.time.LocalDate;
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
    private final EventFederationService eventFederationService;
    private final EventService eventService;
    private final EventFieldService eventFieldService;

    @Inject
    public FederationRemoteRoutes(
            FederationService federationService,
            FederationSigningService signingService,
            FederationRepository repository,
            LendingService lendingService,
            KnowledgeBaseService kbService,
            QuizService quizService,
            TestProtocolService protocolService,
            EventFederationService eventFederationService,
            EventService eventService,
            EventFieldService eventFieldService) {
        this.federationService = federationService;
        this.signingService = signingService;
        this.repository = repository;
        this.lendingService = lendingService;
        this.kbService = kbService;
        this.quizService = quizService;
        this.protocolService = protocolService;
        this.eventFederationService = eventFederationService;
        this.eventService = eventService;
        this.eventFieldService = eventFieldService;
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

        // Event endpoints (signature required)
        routes.get(base + "/events", this::listFederatedEvents);
        routes.get(base + "/events/{id}", this::getFederatedEvent);
        routes.post(base + "/events/{id}/register", this::registerForFederatedEvent);
        routes.delete(base + "/events/{id}/register", this::withdrawFederatedRegistration);
        routes.get(base + "/events/{id}/registrations", this::listFederatedRegistrations);

        // Lending endpoints (signature required)
        routes.get(base + "/lending/messages/{requestId}", this::getLendingMessages);

        // Webhook registration (signature required)
        routes.post(base + "/webhook/register", this::registerWebhook);

        // Webhook receivers (signature required)
        routes.post(base + "/webhook/event-registration-status", this::onEventRegistrationStatus);
        routes.post(base + "/webhook/member-name-changed", this::onMemberNameChanged);

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

    // -- Federated Events --

    private void listFederatedEvents(Context ctx) {
        var partner = verifySignature(ctx);
        var eventIds = eventFederationService.findSharedEventIds(partner.id(), partner.stationId());
        var events = eventIds.stream()
                .map(id -> eventService.findById(id).orElse(null))
                .filter(e -> e != null)
                .map(e -> Map.of(
                        "id", e.id(),
                        "name", e.name(),
                        "description", e.description() != null ? e.description() : "",
                        "eventType", e.eventType() != null ? e.eventType().name() : "",
                        "dayOfWeek", e.dayOfWeek() != null ? e.dayOfWeek() : 0,
                        "startTime", e.startTime() != null ? e.startTime().toString() : "",
                        "endTime", e.endTime() != null ? e.endTime().toString() : "",
                        "requiresRegistration", e.requiresRegistration(),
                        "requiresConfirmation", true))
                .toList();
        ctx.json(events);
    }

    private void getFederatedEvent(Context ctx) {
        var partner = verifySignature(ctx);
        int eventId = ctx.pathParamAsClass("id", Integer.class).get();
        var eventIds = eventFederationService.findSharedEventIds(partner.id(), partner.stationId());
        if (!eventIds.contains(eventId)) {
            throw new NotFoundResponse();
        }
        var event = eventService.findById(eventId).orElseThrow(NotFoundResponse::new);
        var fields = eventFieldService.findByEvent(eventId).stream()
                .filter(f -> f.isPublic())
                .toList();
        ctx.json(Map.of(
                "id",
                event.id(),
                "name",
                event.name(),
                "description",
                event.description() != null ? event.description() : "",
                "eventType",
                event.eventType() != null ? event.eventType().name() : "",
                "dayOfWeek",
                event.dayOfWeek() != null ? event.dayOfWeek() : 0,
                "startTime",
                event.startTime() != null ? event.startTime().toString() : "",
                "endTime",
                event.endTime() != null ? event.endTime().toString() : "",
                "requiresRegistration",
                event.requiresRegistration(),
                "requiresConfirmation",
                true,
                "publicFields",
                fields));
    }

    private void registerForFederatedEvent(Context ctx) {
        var partner = verifySignature(ctx);
        int eventId = ctx.pathParamAsClass("id", Integer.class).get();
        var eventIds = eventFederationService.findSharedEventIds(partner.id(), partner.stationId());
        if (!eventIds.contains(eventId)) {
            throw new NotFoundResponse();
        }
        var req = ctx.bodyAsClass(FederatedRegistrationRequest.class);
        var reg =
                eventFederationService.registerFederated(eventId, partner.id(), req.remoteMemberId(), req.eventDate());
        ctx.status(HttpStatus.CREATED).json(reg);
    }

    private void withdrawFederatedRegistration(Context ctx) {
        var partner = verifySignature(ctx);
        int eventId = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(FederatedRegistrationRequest.class);
        eventFederationService.withdrawRegistration(eventId, partner.id(), req.remoteMemberId(), req.eventDate());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listFederatedRegistrations(Context ctx) {
        var partner = verifySignature(ctx);
        int eventId = ctx.pathParamAsClass("id", Integer.class).get();
        var eventIds = eventFederationService.findSharedEventIds(partner.id(), partner.stationId());
        if (!eventIds.contains(eventId)) {
            throw new NotFoundResponse();
        }
        var registrations = eventFederationService.findRegistrationsByPartner(partner.id()).stream()
                .filter(r -> r.eventId() == eventId)
                .toList();
        ctx.json(registrations);
    }

    // -- Webhook Receivers --

    private void onEventRegistrationStatus(Context ctx) {
        var partner = verifySignature(ctx);
        var req = ctx.bodyAsClass(EventRegistrationStatusWebhook.class);
        // The owning station notifies us that a registration was accepted/denied.
        // We create a local notification for the member.
        // remoteMemberId is the local member ID as string.
        // This would trigger a local notification via the domain event system.
        ctx.json(Map.of("status", "ok"));
    }

    private void onMemberNameChanged(Context ctx) {
        var partner = verifySignature(ctx);
        var req = ctx.bodyAsClass(MemberNameChangedWebhook.class);
        eventFederationService.invalidateName(partner.id(), req.remoteMemberId());
        ctx.json(Map.of("status", "ok"));
    }

    // -- Request/Response Records --

    public record EventRegistrationStatusWebhook(int eventId, String remoteMemberId, String eventDate, String status) {}

    public record MemberNameChangedWebhook(String remoteMemberId) {}

    public record AnnounceRequest(String newHost) {}

    public record HandshakeRequest(
            int stationId, int federationVersion, List<String> capabilities, String publicKey, String signature) {}

    public record HandshakeResponse(
            int stationId, int federationVersion, List<String> capabilities, String publicKey) {}

    public record WebhookRegisterRequest(String webhookUrl) {}

    public record FederatedRegistrationRequest(String remoteMemberId, LocalDate eventDate) {}
}
