/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.auth.signing.DatabaseReplayStore;
import dev.chojo.ember.auth.signing.SignedRequests;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.feature.discovery.entity.BlocklistKind;
import dev.chojo.ember.feature.discovery.entity.DiscoveryPeer;
import dev.chojo.ember.feature.discovery.entity.PeerSource;
import dev.chojo.ember.feature.discovery.entity.PingDirection;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryCallbackMessage;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryIdentity;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryPingMessage;
import dev.chojo.ember.feature.discovery.protocol.PeerAnnouncement;
import dev.chojo.ember.feature.discovery.repository.DiscoveryBlocklistRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPingRepository;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.util.RandomTokens;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the instance-gossip layer: outbound pings, inbound ping handling, and
 * callback validation.
 *
 * <p>The protocol is intentionally fire-and-forget on the wire - every {@code POST
 * /discovery/ping} returns {@code 204} immediately, and the actual peer list comes back via a
 * delayed callback.
 *
 * <p>Fan-out is <em>not yet implemented</em>; the {@code depth} field is sent and
 * recorded but receivers currently always answer with what they already know. The protocol
 * is forward-compatible: enabling fan-out is a local code change.
 */
@Singleton
public class DiscoveryPingService {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryPingService.class);
    private static final Duration CALLBACK_WINDOW = Duration.ofSeconds(60);
    private static final Duration MAX_DRIFT = Duration.ofMinutes(5);
    private static final Duration NONCE_TTL = Duration.ofMinutes(30);
    private static final Duration CALLBACK_DELAY = Duration.ofMillis(100);
    private static final String PING_PATH = "/api/v1/discovery/ping";
    private static final String CALLBACK_PATH = "/api/v1/discovery/peers";
    private static final String REPLAY_SCOPE = "discovery";

    private final DiscoveryKeyService keyService;
    private final DiscoverySigningService signingService;
    private final DiscoveryHttpClient httpClient;
    private final DiscoveryPeerRepository peerRepository;
    private final DiscoveryPingRepository pingRepository;
    private final DatabaseReplayStore replayStore;
    private final DiscoveryBlocklistRepository blocklistRepository;
    private final DiscoveryReputationService reputationService;
    private final DiscoverySettingsService settingsService;
    private final RemoteUrlValidator urlValidator;
    private final Conf conf;
    private final TaskScheduler scheduler;

    @Inject
    public DiscoveryPingService(
            DiscoveryKeyService keyService,
            DiscoverySigningService signingService,
            DiscoveryHttpClient httpClient,
            DiscoveryPeerRepository peerRepository,
            DiscoveryPingRepository pingRepository,
            DatabaseReplayStore replayStore,
            DiscoveryBlocklistRepository blocklistRepository,
            DiscoveryReputationService reputationService,
            DiscoverySettingsService settingsService,
            RemoteUrlValidator urlValidator,
            Conf conf,
            TaskScheduler scheduler) {
        this.scheduler = scheduler;
        this.keyService = keyService;
        this.signingService = signingService;
        this.httpClient = httpClient;
        this.peerRepository = peerRepository;
        this.pingRepository = pingRepository;
        this.replayStore = replayStore;
        this.blocklistRepository = blocklistRepository;
        this.reputationService = reputationService;
        this.settingsService = settingsService;
        this.urlValidator = urlValidator;
        this.conf = conf;
    }

    private static String stripCallbackSuffix(String callbackUrl) {
        int idx = callbackUrl.indexOf("/api/v1/");
        if (idx < 0) return callbackUrl;
        return callbackUrl.substring(0, idx);
    }

    private static String randomNonce() {
        return RandomTokens.urlSafe(32);
    }

    /**
     * Sends a discovery ping to the given peer, recording the outbound nonce so the matching
     * callback can be validated. No-op if discovery is disabled or the peer is blocked.
     */
    public void sendPing(DiscoveryPeer peer) {
        if (!settingsService.isEnabled()) return;
        if (peer.blocked()) return;
        if (blocklistRepository.contains(BlocklistKind.PUBLIC_KEY, peer.publicKey())) return;
        if (blocklistRepository.contains(BlocklistKind.BASE_URL, peer.baseUrl())) return;

        String nonce = randomNonce();
        Instant issuedAt = Instant.now();
        var message = new DiscoveryPingMessage(
                selfIdentity(), nonce, issuedAt, selfBaseUrl() + CALLBACK_PATH, settingsService.maxDepth());

        pingRepository.record(nonce, PingDirection.OUT, peer.publicKey(), issuedAt, issuedAt.plus(CALLBACK_WINDOW));
        peerRepository.markPinged(peer.publicKey(), issuedAt);

        boolean ok = httpClient.signedPost(peer.baseUrl(), PING_PATH, message);
        if (!ok) {
            log.debug("Discovery ping to {} failed", peer.baseUrl());
            peerRepository.markUnreachable(peer.publicKey());
            reputationService.recordTimeout(peer.publicKey());
        }
    }

    /**
     * Inbound ping handler. Validates signature, replay-protects the nonce, and schedules
     * the asynchronous callback. The callback URL is the sender's choice, so it has to be a public
     * endpoint before anything is sent to it.
     *
     * @param rawBody         raw request body bytes (used for signature verification)
     * @param message         parsed body
     * @param signatureHeader value of {@code X-Ember-Discovery-Signature}
     */
    public void handleInboundPing(String rawBody, DiscoveryPingMessage message, @Nullable String signatureHeader) {
        if (!settingsService.isEnabled()) return;
        if (message == null || message.from() == null || message.nonce() == null) return;

        Instant now = Instant.now();
        if (!SignedRequests.withinDrift(message.issuedAt(), now, MAX_DRIFT)) {
            log.debug("Discarding ping from {} due to drift", message.from().baseUrl());
            return;
        }

        if (blocklistRepository.contains(
                        BlocklistKind.PUBLIC_KEY, message.from().publicKey())
                || blocklistRepository.contains(
                        BlocklistKind.BASE_URL, message.from().baseUrl())) {
            return;
        }

        if (signatureHeader == null
                || !signingService.verify(
                        rawBody, signatureHeader, message.from().publicKey())) {
            log.debug("Rejecting ping from {} - bad signature", message.from().baseUrl());
            reputationService.recordSignatureFailure(message.from().publicKey());
            return;
        }

        if (message.callbackUrl() == null || !urlValidator.isAllowed(message.callbackUrl())) {
            log.debug(
                    "Rejecting ping from {} - callback URL not permitted",
                    message.from().baseUrl());
            return;
        }

        if (keyService.publicKeyBase64().equals(message.from().publicKey())) {
            log.debug("Dropping a ping that came back to its sender");
            return;
        }
        if (!replayStore.firstSighting(REPLAY_SCOPE, message.nonce(), now.plus(NONCE_TTL))) {
            log.debug("Dropping replayed/looped ping nonce {}", message.nonce());
            return;
        }

        peerRepository.upsert(
                message.from().publicKey(),
                message.from().baseUrl(),
                message.from().instanceId(),
                PeerSource.GOSSIP,
                null);

        scheduler.later("discovery-callback", CALLBACK_DELAY, () -> sendCallback(message));
    }

    /**
     * Inbound callback handler. Validates the signature against {@code from.publicKey}, checks
     * that we actually sent a ping with that nonce and the window hasn't elapsed, and merges
     * the announced peers into the local registry.
     */
    public boolean handleCallback(String rawBody, DiscoveryCallbackMessage message, @Nullable String signatureHeader) {
        if (!settingsService.isEnabled()) return false;
        if (message == null || message.from() == null || message.inReplyTo() == null) return false;

        Instant now = Instant.now();
        if (!SignedRequests.withinDrift(message.issuedAt(), now, MAX_DRIFT)) {
            return false;
        }
        if (blocklistRepository.contains(
                        BlocklistKind.PUBLIC_KEY, message.from().publicKey())
                || blocklistRepository.contains(
                        BlocklistKind.BASE_URL, message.from().baseUrl())) {
            return false;
        }
        if (signatureHeader == null
                || !signingService.verify(
                        rawBody, signatureHeader, message.from().publicKey())) {
            reputationService.recordSignatureFailure(message.from().publicKey());
            return false;
        }

        var outboundPing = pingRepository.findByNonce(message.inReplyTo()).orElse(null);
        if (outboundPing == null || outboundPing.direction() != PingDirection.OUT) {
            log.debug("Callback nonce {} doesn't match an outbound ping", message.inReplyTo());
            return false;
        }
        if (now.isAfter(outboundPing.expiresAt())) {
            log.debug("Callback nonce {} is past the 60s window", message.inReplyTo());
            return false;
        }

        peerRepository.upsert(
                message.from().publicKey(),
                message.from().baseUrl(),
                message.from().instanceId(),
                PeerSource.GOSSIP,
                null);
        peerRepository.markReached(message.from().publicKey(), now);
        reputationService.recordSuccessfulCallback(message.from().publicKey());

        mergeAnnouncedPeers(message.from().publicKey(), message.peers());
        return true;
    }

    public DiscoveryIdentity selfIdentity() {
        return new DiscoveryIdentity(selfBaseUrl(), keyService.publicKeyBase64(), keyService.instanceId());
    }

    public String selfBaseUrl() {
        var base = conf.main().api().baseUrl();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * Stores the peers a callback announced, once each and never this instance itself. A peer whose
     * base URL is not a public endpoint is never stored, since the scheduler would later ping it; it
     * and an undecodable key both count against the announcer's reputation.
     */
    private void mergeAnnouncedPeers(String announcerKey, List<PeerAnnouncement> announcements) {
        if (announcements == null || announcements.isEmpty()) return;

        Set<String> seen = new HashSet<>();
        List<PeerAnnouncement> unique = new ArrayList<>();
        for (var ann : announcements) {
            if (ann == null || ann.publicKey() == null) continue;
            if (ann.publicKey().equals(keyService.publicKeyBase64())) continue;
            if (seen.add(ann.publicKey())) unique.add(ann);
        }

        for (var ann : unique) {
            if (blocklistRepository.contains(BlocklistKind.PUBLIC_KEY, ann.publicKey())
                    || blocklistRepository.contains(BlocklistKind.BASE_URL, ann.baseUrl())) {
                continue;
            }
            if (ann.baseUrl() == null || !urlValidator.isAllowed(ann.baseUrl())) {
                reputationService.recordInvalidAnnouncement(announcerKey);
                continue;
            }
            try {
                DiscoveryKeyService.decodePeerPublicKey(ann.publicKey());
            } catch (Exception e) {
                reputationService.recordInvalidAnnouncement(announcerKey);
                continue;
            }
            String instanceId = ann.instanceId() != null && !ann.instanceId().isBlank()
                    ? ann.instanceId()
                    : DiscoveryKeyService.fingerprintOf(ann.publicKey());
            peerRepository.upsert(ann.publicKey(), ann.baseUrl(), instanceId, PeerSource.GOSSIP, announcerKey);
        }
    }

    private void sendCallback(DiscoveryPingMessage ping) {
        try {
            var peers = peerRepository.findReachable();
            String announcerKey = keyService.publicKeyBase64();
            String announcerInstanceId = keyService.instanceId();
            List<PeerAnnouncement> announcements = new ArrayList<>(peers.size());
            for (var p : peers) {
                if (p.publicKey().equals(ping.from().publicKey())) continue;
                announcements.add(new PeerAnnouncement(
                        p.baseUrl(), p.publicKey(), p.instanceId(), announcerInstanceId, p.lastSeenAt()));
            }
            var message = new DiscoveryCallbackMessage(selfIdentity(), ping.nonce(), Instant.now(), announcements);
            String callbackUrl = ping.callbackUrl();
            String baseUrl = stripCallbackSuffix(callbackUrl);
            boolean ok = httpClient.signedPost(baseUrl, CALLBACK_PATH, message);
            if (!ok) {
                log.debug("Callback to {} failed", baseUrl);
                reputationService.recordTimeout(announcerKey);
            }
        } catch (Exception e) {
            log.warn("Failed to dispatch discovery callback: {}", e.getMessage());
        }
    }
}
