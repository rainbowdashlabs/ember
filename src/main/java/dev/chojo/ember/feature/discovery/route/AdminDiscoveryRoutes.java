/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.discovery.entity.BlocklistKind;
import dev.chojo.ember.feature.discovery.entity.CachedDiscoveryStation;
import dev.chojo.ember.feature.discovery.entity.DiscoveryBlocklistEntry;
import dev.chojo.ember.feature.discovery.entity.DiscoveryPeer;
import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import dev.chojo.ember.feature.discovery.entity.PeerSource;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryInfoResponse;
import dev.chojo.ember.feature.discovery.repository.DiscoveryBlocklistRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryStationCacheRepository;
import dev.chojo.ember.feature.discovery.service.DiscoveryHttpClient;
import dev.chojo.ember.feature.discovery.service.DiscoveryKeyService;
import dev.chojo.ember.feature.discovery.service.DiscoveryPingService;
import dev.chojo.ember.feature.discovery.service.DiscoveryReputationService;
import dev.chojo.ember.feature.discovery.service.DiscoverySettingsService;
import dev.chojo.ember.feature.discovery.service.DiscoveryStationFetcher;
import dev.chojo.ember.feature.discovery.service.FederationPartnerSeeder;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Admin-facing discovery routes mounted under {@code /api/v1/admin/discovery}. Required
 * permission is {@link InstancePermission#ADMINISTRATOR}.
 *
 * <p>Authenticated user-facing discovery (browsing the cached peers' station cards) lives in
 * {@code /api/v1/discovery/stations} and is intentionally permissionless beyond a valid
 * session — discovery surfaces are public by design (the same data is available
 * anonymously).
 */
@Singleton
public class AdminDiscoveryRoutes implements Routes {

    private final DiscoveryPeerRepository peerRepository;
    private final DiscoveryStationCacheRepository cacheRepository;
    private final DiscoveryBlocklistRepository blocklistRepository;
    private final DiscoveryReputationService reputationService;
    private final DiscoverySettingsService settingsService;
    private final DiscoveryPingService pingService;
    private final DiscoveryStationFetcher stationFetcher;
    private final FederationPartnerSeeder federationPartnerSeeder;
    private final FederationRepository federationRepository;
    private final DiscoveryHttpClient httpClient;
    private final DiscoveryKeyService keyService;

    @Inject
    public AdminDiscoveryRoutes(
            DiscoveryPeerRepository peerRepository,
            DiscoveryStationCacheRepository cacheRepository,
            DiscoveryBlocklistRepository blocklistRepository,
            DiscoveryReputationService reputationService,
            DiscoverySettingsService settingsService,
            DiscoveryPingService pingService,
            DiscoveryStationFetcher stationFetcher,
            FederationPartnerSeeder federationPartnerSeeder,
            FederationRepository federationRepository,
            DiscoveryHttpClient httpClient,
            DiscoveryKeyService keyService) {
        this.peerRepository = peerRepository;
        this.cacheRepository = cacheRepository;
        this.blocklistRepository = blocklistRepository;
        this.reputationService = reputationService;
        this.settingsService = settingsService;
        this.pingService = pingService;
        this.stationFetcher = stationFetcher;
        this.federationPartnerSeeder = federationPartnerSeeder;
        this.federationRepository = federationRepository;
        this.httpClient = httpClient;
        this.keyService = keyService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/discovery/identity", this::getIdentity, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/discovery/settings", this::getSettings, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/discovery/settings",
                this::updateSettings,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(prefix + "/admin/discovery/peers", this::listPeers, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/discovery/peers/probe", this::probePeer, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/discovery/peers",
                this::addPeer,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.FEDERATION);
        routes.delete(
                prefix + "/admin/discovery/peers/{publicKey}",
                this::deletePeer,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.FEDERATION);
        routes.post(
                prefix + "/admin/discovery/peers/{publicKey}/upvote",
                this::upvotePeer,
                InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/discovery/peers/{publicKey}/downvote",
                this::downvotePeer,
                InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/discovery/peers/{publicKey}/block",
                this::blockPeer,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.FEDERATION);
        routes.post(
                prefix + "/admin/discovery/peers/{publicKey}/unblock",
                this::unblockPeer,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.FEDERATION);
        routes.post(
                prefix + "/admin/discovery/peers/{publicKey}/ping",
                this::pingPeerNow,
                InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/discovery/discover-now", this::discoverNow, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/discovery/seed",
                this::seedFederation,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.FEDERATION);
        routes.get(prefix + "/admin/discovery/blocklist", this::listBlocklist, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/discovery/blocklist",
                this::addToBlocklist,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.FEDERATION);
        routes.delete(
                prefix + "/admin/discovery/blocklist/{value}",
                this::removeFromBlocklist,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.FEDERATION);

        // Authenticated user-facing endpoint for the /station/discovery page.
        routes.get(prefix + "/discovery/stations", this::listCachedStations);

        // Editor-facing picker for the PARTNER_STATIONS cell (concept §4.5). Auth-gated by
        // PAGE_EDIT so it never escapes the editor surface — public scrapers cannot use it to
        // enumerate the federation network.
        routes.get(prefix + "/federation/stations/search", this::searchStationPicker, StationPermission.PAGE_EDIT);
    }

    // -- Identity & settings --

    private void getIdentity(Context ctx) {
        ctx.json(
                new IdentityResponse(keyService.instanceId(), keyService.publicKeyBase64(), pingService.selfBaseUrl()));
    }

    private void getSettings(Context ctx) {
        ctx.json(new SettingsResponse(
                settingsService.isEnabled(),
                settingsService.maxDepth(),
                settingsService.pingIntervalMinutes(),
                DiscoverySettingsService.MAX_DEPTH));
    }

    private void updateSettings(Context ctx) {
        var body = ctx.bodyAsClass(SettingsRequest.class);
        if (body.enabled() != null) settingsService.setEnabled(body.enabled());
        if (body.maxDepth() != null) settingsService.setMaxDepth(body.maxDepth());
        if (body.pingIntervalMinutes() != null) settingsService.setPingIntervalMinutes(body.pingIntervalMinutes());
        getSettings(ctx);
    }

    // -- Peer registry --

    private void listPeers(Context ctx) {
        var peers = peerRepository.findAll();
        List<PeerResponse> responses = new ArrayList<>(peers.size());
        for (var p : peers) responses.add(toResponse(p));
        ctx.json(responses);
    }

    private void probePeer(Context ctx) {
        var body = ctx.bodyAsClass(ProbeRequest.class);
        if (body.baseUrl() == null || body.baseUrl().isBlank()) {
            throw new BadRequestResponse("baseUrl required");
        }
        var info = httpClient.get(body.baseUrl(), "/api/v1/public/discovery/info", DiscoveryInfoResponse.class);
        if (info == null) {
            throw new NotFoundResponse("Peer did not respond with discovery info");
        }
        ctx.json(info);
    }

    private void addPeer(Context ctx) {
        var body = ctx.bodyAsClass(AddPeerRequest.class);
        if (body.baseUrl() == null || body.baseUrl().isBlank()) {
            throw new BadRequestResponse("baseUrl required");
        }
        var info = httpClient.get(body.baseUrl(), "/api/v1/public/discovery/info", DiscoveryInfoResponse.class);
        if (info == null || info.publicKey() == null) {
            throw new BadRequestResponse("Peer did not return a usable discovery identity");
        }
        if (body.expectedPublicKey() != null
                && !body.expectedPublicKey().isBlank()
                && !body.expectedPublicKey().equals(info.publicKey())) {
            throw new BadRequestResponse("expectedPublicKey did not match the peer's actual key");
        }
        if (!info.discoveryEnabled()) {
            throw new BadRequestResponse("Peer reports discoveryEnabled=false");
        }
        var peer = peerRepository.upsert(info.publicKey(), info.baseUrl(), info.instanceId(), PeerSource.MANUAL, null);
        // Fire-and-forget ping so the peer's neighborhood starts streaming in immediately.
        try {
            pingService.sendPing(peer);
        } catch (Exception ignored) {
            // Probing failures don't roll back the insert.
        }
        ctx.json(toResponse(peer));
    }

    private void deletePeer(Context ctx) {
        String key = ctx.pathParam("publicKey");
        boolean removed = peerRepository.delete(key);
        ctx.json(new ChangedResponse(removed));
    }

    private void upvotePeer(Context ctx) {
        String key = ctx.pathParam("publicKey");
        peerRepository.findByPublicKey(key).orElseThrow(() -> new NotFoundResponse("Peer not found"));
        reputationService.upvote(key);
        ctx.json(toResponse(
                peerRepository.findByPublicKey(key).orElseThrow(() -> new NotFoundResponse("Peer disappeared"))));
    }

    private void downvotePeer(Context ctx) {
        String key = ctx.pathParam("publicKey");
        peerRepository.findByPublicKey(key).orElseThrow(() -> new NotFoundResponse("Peer not found"));
        reputationService.downvote(key);
        ctx.json(toResponse(
                peerRepository.findByPublicKey(key).orElseThrow(() -> new NotFoundResponse("Peer disappeared"))));
    }

    private void blockPeer(Context ctx) {
        String key = ctx.pathParam("publicKey");
        peerRepository.findByPublicKey(key).orElseThrow(() -> new NotFoundResponse("Peer not found"));
        peerRepository.setBlocked(key, true);
        ctx.json(toResponse(
                peerRepository.findByPublicKey(key).orElseThrow(() -> new NotFoundResponse("Peer disappeared"))));
    }

    private void unblockPeer(Context ctx) {
        String key = ctx.pathParam("publicKey");
        peerRepository.findByPublicKey(key).orElseThrow(() -> new NotFoundResponse("Peer not found"));
        peerRepository.setBlocked(key, false);
        ctx.json(toResponse(
                peerRepository.findByPublicKey(key).orElseThrow(() -> new NotFoundResponse("Peer disappeared"))));
    }

    private void pingPeerNow(Context ctx) {
        String key = ctx.pathParam("publicKey");
        var peer = peerRepository.findByPublicKey(key).orElseThrow(() -> new NotFoundResponse("Peer not found"));
        pingService.sendPing(peer);
        ctx.json(new MessageResponse("Ping dispatched"));
    }

    private void discoverNow(Context ctx) {
        int pinged = 0;
        for (var peer : peerRepository.findUsable()) {
            pingService.sendPing(peer);
            pinged++;
        }
        int fetched = stationFetcher.refreshAll();
        ctx.json(new DiscoverNowResponse(pinged, fetched));
    }

    private void seedFederation(Context ctx) {
        int added = federationPartnerSeeder.seedFromFederationPartners();
        ctx.json(new ChangedCountResponse(added));
    }

    // -- Blocklist --

    private void listBlocklist(Context ctx) {
        List<DiscoveryBlocklistEntry> entries = blocklistRepository.findAll();
        List<BlocklistResponse> responses = new ArrayList<>(entries.size());
        for (var e : entries) {
            responses.add(new BlocklistResponse(e.value(), e.kind(), e.note(), e.createdAt()));
        }
        ctx.json(responses);
    }

    private void addToBlocklist(Context ctx) {
        var body = ctx.bodyAsClass(BlocklistRequest.class);
        if (body.value() == null || body.value().isBlank() || body.kind() == null) {
            throw new BadRequestResponse("value and kind required");
        }
        blocklistRepository.add(body.kind(), body.value(), body.note());
        ctx.json(new MessageResponse("Added to blocklist"));
    }

    private void removeFromBlocklist(Context ctx) {
        String value = ctx.pathParam("value");
        boolean removed = blocklistRepository.remove(value);
        ctx.json(new ChangedResponse(removed));
    }

    // -- Cached stations (authenticated) --

    private void listCachedStations(Context ctx) {
        List<CachedDiscoveryStation> cached = cacheRepository.findAll();
        List<DiscoveredStationResponse> responses = new ArrayList<>(cached.size());
        for (var c : cached) {
            DiscoveryStationCard card = c.card();
            responses.add(new DiscoveredStationResponse(
                    card.stationUid(),
                    card.name(),
                    card.slogan(),
                    card.logoUrl(),
                    card.country(),
                    card.region(),
                    card.city(),
                    card.contactUrl(),
                    card.tags(),
                    card.memberCount(),
                    card.publishedAt(),
                    card.addressLine(),
                    card.latitude(),
                    card.longitude(),
                    c.instancePublicKey(),
                    c.fetchedAt()));
        }
        ctx.json(responses);
    }

    private void searchStationPicker(Context ctx) {
        var session = UserSession.from(ctx);
        String q = ctx.queryParam("q");
        int requested = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        int limit = Math.clamp(requested, 1, 50);

        LinkedHashMap<String, StationPickerResult> byUid = new LinkedHashMap<>();

        for (var partner : federationRepository.findActivePartnerSummaries(session.stationId())) {
            if (matchesQuery(partner.name(), q)) {
                byUid.put(
                        partner.uid().toString(),
                        new StationPickerResult(partner.uid().toString(), partner.name(), null, null, null, true));
            }
        }

        for (var c : cacheRepository.searchForPicker(q, limit)) {
            var card = c.card();
            byUid.putIfAbsent(
                    card.stationUid(),
                    new StationPickerResult(
                            card.stationUid(), card.name(), card.city(), card.country(), card.logoUrl(), true));
        }

        List<StationPickerResult> results = new ArrayList<>(byUid.values());
        if (results.size() > limit) {
            results = results.subList(0, limit);
        }
        ctx.json(results);
    }

    private static boolean matchesQuery(String name, String query) {
        if (query == null || query.isBlank()) return true;
        if (name == null) return false;
        return name.toLowerCase().contains(query.trim().toLowerCase());
    }

    /**
     * Lightweight picker result row. {@code selectable} mirrors whether the cell should let
     * the editor pick this station — the discovery cache only contains stations that already
     * opted-in to public discovery, so this is always {@code true} today. The flag is part of
     * the contract so the frontend stays stable when (and if) we later widen the picker to
     * include federation partners that aren't discoverable.
     */
    public record StationPickerResult(
            String stationUid, String name, String city, String country, String logoUrl, boolean selectable) {}

    private static PeerResponse toResponse(DiscoveryPeer p) {
        return new PeerResponse(
                p.publicKey(),
                p.baseUrl(),
                p.instanceId(),
                p.firstSeenAt(),
                p.lastSeenAt(),
                p.lastPingedAt(),
                p.lastReachedAt(),
                p.reachable(),
                p.source(),
                p.introducedBy(),
                p.reputation(),
                p.blocked());
    }

    // -- DTOs --

    public record IdentityResponse(String instanceId, String publicKey, String baseUrl) {}

    public record SettingsResponse(boolean enabled, int maxDepth, int pingIntervalMinutes, int hardMaxDepth) {}

    public record SettingsRequest(Boolean enabled, Integer maxDepth, Integer pingIntervalMinutes) {}

    public record ProbeRequest(String baseUrl) {}

    public record AddPeerRequest(String baseUrl, String expectedPublicKey) {}

    public record PeerResponse(
            String publicKey,
            String baseUrl,
            String instanceId,
            Instant firstSeenAt,
            Instant lastSeenAt,
            Instant lastPingedAt,
            Instant lastReachedAt,
            boolean reachable,
            PeerSource source,
            String introducedBy,
            int reputation,
            boolean blocked) {}

    public record DiscoverNowResponse(int pingsDispatched, int stationsFetched) {}

    public record BlocklistRequest(String value, BlocklistKind kind, String note) {}

    public record BlocklistResponse(String value, BlocklistKind kind, String note, Instant createdAt) {}

    public record DiscoveredStationResponse(
            String stationUid,
            String name,
            String slogan,
            String logoUrl,
            String country,
            String region,
            String city,
            String contactUrl,
            List<String> tags,
            String memberCount,
            Instant publishedAt,
            String addressLine,
            BigDecimal latitude,
            BigDecimal longitude,
            String instancePublicKey,
            Instant fetchedAt) {}

    public record ChangedResponse(boolean changed) {}

    public record ChangedCountResponse(int changed) {}

    public record MessageResponse(String message) {}
}
