/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.ChangeType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationCapability;
import dev.chojo.ember.feature.federation.entity.FederationChangeLog;
import dev.chojo.ember.feature.federation.entity.FederationMetadataCache;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationShare;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class FederationService {
    private static final Logger log = LoggerFactory.getLogger(FederationService.class);
    public static final String FEDERATION_VERSION = loadFederationVersion();

    private final FederationRepository repository;
    private final StationRepository stationRepository;
    private final String instanceHost;

    @Inject
    public FederationService(FederationRepository repository, StationRepository stationRepository, Api apiConfig) {
        this.repository = repository;
        this.stationRepository = stationRepository;
        this.instanceHost = extractHost(apiConfig.baseUrl());

        int updated = repository.backfillLocalPartnerVersions(FEDERATION_VERSION);
        if (updated > 0) {
            log.info("Updated federation version for {} local partner(s) to {}", updated, FEDERATION_VERSION);
        }
    }

    private static String loadFederationVersion() {
        try (var is = FederationService.class.getResourceAsStream("/federation_version")) {
            if (is == null) throw new IllegalStateException("federation_version resource not found");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load federation_version", e);
        }
    }

    private static String extractHost(String baseUrl) {
        try {
            return URI.create(baseUrl).getHost();
        } catch (Exception e) {
            return baseUrl;
        }
    }

    // -- Pairing Code --

    /**
     * Generates a discovery/pairing code: ember-BASE64(stationUid)-BASE64(host).
     * Stateless — entering this creates a PENDING request that the target station must accept.
     */
    public String generatePairingCode(UUID stationUid) {
        String encodedUid = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(stationUid.toString().getBytes(StandardCharsets.UTF_8));
        String encodedHost =
                Base64.getUrlEncoder().withoutPadding().encodeToString(instanceHost.getBytes(StandardCharsets.UTF_8));
        return "ember-" + encodedUid + "-" + encodedHost;
    }

    /**
     * Generates a station invite code: ember-BASE64(stationUid)-BASE64(host)-TOKEN.
     * The token proves the station consented — entering this auto-activates the federation.
     */
    public String generateStationInvite(int stationId, UUID stationUid) {
        String token = generateRandomToken();
        repository.createInviteToken(stationId, token);
        return generatePairingCode(stationUid) + "-" + token;
    }

    private String generateRandomToken() {
        var random = new SecureRandom();
        var chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        var sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }

    /**
     * Parses any pairing/invite code. Returns the parts including an optional token.
     * - 2 segments: discovery code (PENDING request)
     * - 3 segments: station invite (auto-activate if token is valid)
     */
    public Optional<PairingCodeParts> parsePairingCode(String code) {
        if (!code.startsWith("ember-")) return Optional.empty();
        String rest = code.substring("ember-".length());
        // Split into exactly 2 or 3 parts: encodedUid, encodedHost, [token]
        String[] segments = rest.split("-", 3);
        if (segments.length < 2) return Optional.empty();
        try {
            String uid = new String(Base64.getUrlDecoder().decode(segments[0]), StandardCharsets.UTF_8);
            String host = new String(Base64.getUrlDecoder().decode(segments[1]), StandardCharsets.UTF_8);
            String token = segments.length == 3 ? segments[2] : null;
            return Optional.of(new PairingCodeParts(UUID.fromString(uid), host, token));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Validates and consumes a station invite token. Returns true if valid (station consented).
     */
    public boolean consumeInviteToken(int stationId, String token) {
        return repository.deleteInviteToken(stationId, token);
    }

    public String getInstanceHost() {
        return instanceHost;
    }

    public record PairingCodeParts(UUID stationUid, String host, String token) {
        public boolean isStationInvite() {
            return token != null && !token.isBlank();
        }
    }

    // -- Keypair --

    public KeyPair generateKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate keypair", e);
        }
    }

    public String encodePublicKey(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    public String encodePrivateKey(KeyPair keyPair) {
        return Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
    }

    // -- Partner Management --

    public List<FederationPartner> findPartners(int stationId) {
        return repository.findPartners(stationId);
    }

    public Optional<FederationPartner> findPartner(int id) {
        return repository.findPartnerById(id);
    }

    /**
     * Creates a pending pair request from the requesting station to the target station.
     * This shows up on the target station's federation page for approval.
     */
    public FederationPartner createPairRequest(int requestingStationId, int targetStationId) {
        UUID targetUid = resolveStationUid(targetStationId);
        return repository.createPartner(requestingStationId, targetUid, null, null, null);
    }

    /**
     * Accepts a pending pair request, establishing the full bidirectional federation.
     * Generates keypairs for both sides.
     */
    public FederationPartner acceptPairRequest(int partnerId) {
        var partner = repository.findPartnerById(partnerId).orElseThrow();
        if (partner.status() != FederationPartner.FederationStatus.PENDING) {
            throw new IllegalStateException("Partner is not in PENDING status");
        }

        int requestingStationId = partner.stationId();
        // partner.partnerStationId() is now a UUID — resolve back to int for local station lookup
        int targetStationId = stationRepository
                .findByUid(partner.partnerStationId())
                .orElseThrow()
                .id();

        // Delete the pending request record
        repository.deletePartner(partnerId);

        // Create full bidirectional federation with fresh keypairs
        var keyPair = generateKeyPair();
        return acceptInvite(targetStationId, requestingStationId, encodePublicKey(keyPair), null, null);
    }

    /**
     * Declines a pending pair request.
     */
    public void declinePairRequest(int partnerId) {
        repository.deletePartner(partnerId);
    }

    /**
     * Finds pending pair requests targeting the given station.
     */
    public List<FederationPartner> findPendingRequests(int targetStationId) {
        UUID targetUid = resolveStationUid(targetStationId);
        return repository.findPendingRequestsForStation(targetUid);
    }

    /**
     * Accepts a federation invite on the same instance (or cross-instance).
     * Creates bidirectional partner records with optional remote host URLs.
     *
     * @param acceptingStationId    the station accepting the invite
     * @param initiatingStationId   the station that created the invite
     * @param initiatingPublicKey   the initiating station's public key
     * @param initiatingRemoteHost  the initiating station's base URL (null if same instance)
     * @param acceptingRemoteHost   the accepting station's base URL (null if same instance)
     */
    public FederationPartner acceptInvite(
            int acceptingStationId,
            int initiatingStationId,
            String initiatingPublicKey,
            String initiatingRemoteHost,
            String acceptingRemoteHost) {
        var keyPair = generateKeyPair();
        String acceptingPublicKey = encodePublicKey(keyPair);

        // Store private key on accepting station (if not already set)
        stationRepository.updateFederationPrivateKey(acceptingStationId, encodePrivateKey(keyPair));

        UUID acceptingUid = resolveStationUid(acceptingStationId);
        UUID initiatingUid = resolveStationUid(initiatingStationId);

        // Create partner record: initiating -> accepting (from initiating's POV, accepting may be remote)
        var partner = repository.createPartner(
                initiatingStationId, acceptingUid, null, initiatingPublicKey, acceptingRemoteHost);
        repository.activatePartner(partner.id(), acceptingPublicKey);

        // Create reverse partner record: accepting -> initiating (from accepting's POV, initiating may be remote)
        var reverse = repository.createPartner(
                acceptingStationId, initiatingUid, null, acceptingPublicKey, initiatingRemoteHost);
        repository.activatePartner(reverse.id(), initiatingPublicKey);

        // Initialize default capabilities (all enabled for both directions)
        for (var cap : CapabilityType.values()) {
            for (var dir : Direction.values()) {
                repository.upsertCapability(partner.id(), cap, dir, true);
                repository.upsertCapability(reverse.id(), cap, dir, true);
            }
        }

        return repository.findPartnerById(partner.id()).orElseThrow();
    }

    public boolean suspendPartner(int partnerId) {
        return repository.updatePartnerStatus(partnerId, "SUSPENDED");
    }

    public boolean resumePartner(int partnerId) {
        return repository.updatePartnerStatus(partnerId, "ACTIVE");
    }

    /**
     * Updates the remote host for all partner records pointing to the given station.
     * Called when a station announces it has moved to a new host.
     *
     * @param stationUid the UUID of the station that moved
     * @param newHost    the new base URL (null if the station moved to the same instance)
     */
    public void updateRemoteHost(UUID stationUid, String newHost) {
        // We need to find all records across ALL stations where partner_station_id = stationUid
        // and update their remote_host
        repository.updateRemoteHostForPartnerStation(stationUid, newHost);
    }

    public boolean endFederation(int partnerId) {
        // Find and delete the reverse partner too
        var partner = repository.findPartnerById(partnerId);
        if (partner.isPresent()) {
            var p = partner.get();
            // Find reverse: look up the partner station by UUID, then find its partners
            var partnerStation = stationRepository.findByUid(p.partnerStationId());
            if (partnerStation.isPresent()) {
                UUID ourUid = resolveStationUid(p.stationId());
                var all = repository.findPartners(partnerStation.get().id());
                for (var rev : all) {
                    if (rev.partnerStationId().equals(ourUid)) {
                        repository.deletePartner(rev.id());
                    }
                }
            }
        }
        return repository.deletePartner(partnerId);
    }

    // -- Capabilities --

    public List<FederationCapability> findCapabilities(int partnerId) {
        return repository.findCapabilities(partnerId);
    }

    public void setCapability(int partnerId, CapabilityType capability, Direction direction, boolean enabled) {
        repository.upsertCapability(partnerId, capability, direction, enabled);
    }

    public boolean hasCapability(int partnerId, CapabilityType capability, Direction direction) {
        return repository.findCapabilities(partnerId).stream()
                .anyMatch(
                        c -> c.capability().equals(capability) && c.direction().equals(direction) && c.enabled());
    }

    // -- Sharing --

    public List<FederationShare> findKbShares(int stationId) {
        return repository.findKbShares(stationId);
    }

    public FederationShare createKbShare(int stationId, Integer fileId, Integer folderId, ShareScope shareScope) {
        return repository.createKbShare(stationId, fileId, folderId, shareScope);
    }

    public boolean deleteKbShare(int id) {
        return repository.deleteKbShare(id);
    }

    public List<FederationShare> findQuizShares(int stationId) {
        return repository.findQuizShares(stationId);
    }

    public FederationShare createQuizShare(int stationId, int catalogId, ShareScope shareScope) {
        return repository.createQuizShare(stationId, catalogId, shareScope);
    }

    public boolean deleteQuizShare(int id) {
        return repository.deleteQuizShare(id);
    }

    public List<FederationShare> findProtocolShares(int stationId) {
        return repository.findProtocolShares(stationId);
    }

    public FederationShare createProtocolShare(int stationId, int protocolId, ShareScope shareScope) {
        return repository.createProtocolShare(stationId, protocolId, shareScope);
    }

    public boolean deleteProtocolShare(int id) {
        return repository.deleteProtocolShare(id);
    }

    // -- Metadata Cache --

    // Available for remote sync — not yet called from routes
    public List<FederationMetadataCache> getCachedMetadata(int partnerId, ContentType contentType) {
        return repository.findCachedMetadata(partnerId, contentType);
    }

    // Available for remote sync — not yet called from routes
    public void refreshMetadataCache(int partnerId, ContentType contentType, List<FederationMetadataCache> entries) {
        for (var entry : entries) {
            repository.upsertMetadataCache(
                    partnerId, contentType, entry.remoteId(), entry.title(), entry.description());
        }
    }

    /**
     * Returns the capabilities supported by this instance.
     */
    public List<CapabilityType> getSupportedCapabilities() {
        return List.of(
                CapabilityType.KB_SHARE,
                CapabilityType.QUIZ_SHARE,
                CapabilityType.PROTOCOL_SHARE,
                CapabilityType.INVENTORY_LEND,
                CapabilityType.EVENT_SHARE,
                CapabilityType.BOARD_SHARE);
    }

    // -- Change Tracking --

    /**
     * Logs a content change for federation sync polling.
     */
    public void logChange(int stationId, ContentType contentType, int contentId, ChangeType changeType) {
        repository.logChange(stationId, contentType, contentId, changeType);
    }

    /**
     * Returns content changes since the given timestamp for sync polling.
     */
    public List<FederationChangeLog> getChangesSince(int stationId, Instant since) {
        return repository.findChangesSince(stationId, since);
    }

    /**
     * Resolves an internal station ID to its UUID.
     */
    private UUID resolveStationUid(int stationId) {
        return stationRepository.resolveUid(stationId);
    }
}
