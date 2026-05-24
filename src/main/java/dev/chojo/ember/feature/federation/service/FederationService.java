/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.entity.FederationCapability;
import dev.chojo.ember.feature.federation.entity.FederationChangeLog;
import dev.chojo.ember.feature.federation.entity.FederationMetadataCache;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationShare;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Singleton
public class FederationService {
    private static final Logger log = LoggerFactory.getLogger(FederationService.class);
    private static final int FEDERATION_VERSION = 1;

    private final FederationRepository repository;
    private final StationRepository stationRepository;
    private final String instanceHost;

    @Inject
    public FederationService(FederationRepository repository, StationRepository stationRepository, Api apiConfig) {
        this.repository = repository;
        this.stationRepository = stationRepository;
        this.instanceHost = extractHost(apiConfig.baseUrl());
    }

    private static String extractHost(String baseUrl) {
        try {
            return URI.create(baseUrl).getHost();
        } catch (Exception e) {
            return baseUrl;
        }
    }

    // -- Invite Code --

    /**
     * Generates an invite code in the format: ember-CODE-BASE64(HOST)
     * where CODE is an 8-char random string and HOST is the base64-encoded instance hostname.
     */
    public String generateInviteCode() {
        var random = new SecureRandom();
        var chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        var code = new StringBuilder();
        for (int i = 0; i < 8; i++) code.append(chars.charAt(random.nextInt(chars.length())));
        String encodedHost =
                Base64.getUrlEncoder().withoutPadding().encodeToString(instanceHost.getBytes(StandardCharsets.UTF_8));
        return "ember-" + code + "-" + encodedHost;
    }

    /**
     * Parses an invite code in the format ember-CODE-BASE64(HOST).
     * Returns the decoded parts [code, host] or empty if invalid.
     */
    public Optional<InviteCodeParts> parseInviteCode(String inviteCode) {
        if (!inviteCode.startsWith("ember-")) return Optional.empty();
        String rest = inviteCode.substring("ember-".length());
        int dashIdx = rest.indexOf('-');
        if (dashIdx < 1) return Optional.empty();
        String code = rest.substring(0, dashIdx);
        String encodedHost = rest.substring(dashIdx + 1);
        try {
            String host = new String(Base64.getUrlDecoder().decode(encodedHost), StandardCharsets.UTF_8);
            return Optional.of(new InviteCodeParts(code, host));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public String getInstanceHost() {
        return instanceHost;
    }

    public record InviteCodeParts(String code, String host) {}

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
     * Creates a federation invite. The initiating station generates a keypair and invite code.
     * The partner station ID is set to 0 initially (placeholder) until the invite is accepted.
     */
    public FederationPartner createInvite(int stationId) {
        var keyPair = generateKeyPair();
        String publicKey = encodePublicKey(keyPair);
        String inviteCode = generateInviteCode();
        return new FederationPartner(
                0,
                stationId,
                0,
                inviteCode,
                publicKey,
                null,
                FederationPartner.FederationStatus.PENDING,
                FEDERATION_VERSION,
                Instant.now(),
                Instant.now(),
                null);
    }

    /**
     * Accepts a federation invite on the same instance.
     * Creates partner records for both stations.
     */
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

        // Create partner record: initiating -> accepting (from initiating's POV, accepting may be remote)
        var partner = repository.createPartner(
                initiatingStationId, acceptingStationId, null, initiatingPublicKey, acceptingRemoteHost);
        repository.activatePartner(partner.id(), acceptingPublicKey);

        // Create reverse partner record: accepting -> initiating (from accepting's POV, initiating may be remote)
        var reverse = repository.createPartner(
                acceptingStationId, initiatingStationId, null, acceptingPublicKey, initiatingRemoteHost);
        repository.activatePartner(reverse.id(), initiatingPublicKey);

        // Initialize default capabilities (all enabled for both directions)
        for (var cap : FederationCapability.CapabilityType.values()) {
            for (var dir : FederationCapability.Direction.values()) {
                repository.upsertCapability(partner.id(), cap.name(), dir.name(), true);
                repository.upsertCapability(reverse.id(), cap.name(), dir.name(), true);
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
     * @param stationId the station that moved
     * @param newHost   the new base URL (null if the station moved to the same instance)
     */
    public void updateRemoteHost(int stationId, String newHost) {
        // Find all partner records where this station is the partner
        // and update the remote_host on each
        var allPartners = repository.findPartners(stationId);
        for (var partner : allPartners) {
            if (partner.partnerStationId() == stationId) {
                // This is a record where stationId is the remote partner — should not happen
                // in normal findPartners (which filters by station_id), but guard anyway
                continue;
            }
        }
        // We need to find all records across ALL stations where partner_station_id = stationId
        // and update their remote_host
        repository.updateRemoteHostForPartnerStation(stationId, newHost);
    }

    public boolean endFederation(int partnerId) {
        // Find and delete the reverse partner too
        var partner = repository.findPartnerById(partnerId);
        if (partner.isPresent()) {
            var p = partner.get();
            // Find reverse
            var all = repository.findPartners(p.partnerStationId());
            for (var rev : all) {
                if (rev.partnerStationId() == p.stationId()) {
                    repository.deletePartner(rev.id());
                }
            }
        }
        return repository.deletePartner(partnerId);
    }

    // -- Capabilities --

    public List<FederationCapability> findCapabilities(int partnerId) {
        return repository.findCapabilities(partnerId);
    }

    public void setCapability(int partnerId, String capability, String direction, boolean enabled) {
        repository.upsertCapability(partnerId, capability, direction, enabled);
    }

    public boolean hasCapability(int partnerId, String capability, String direction) {
        return repository.findCapabilities(partnerId).stream()
                .anyMatch(
                        c -> c.capability().equals(capability) && c.direction().equals(direction) && c.enabled());
    }

    // -- Sharing --

    public List<FederationShare> findKbShares(int stationId) {
        return repository.findKbShares(stationId);
    }

    public FederationShare createKbShare(int stationId, Integer fileId, Integer folderId, String shareScope) {
        return repository.createKbShare(stationId, fileId, folderId, shareScope);
    }

    public boolean deleteKbShare(int id) {
        return repository.deleteKbShare(id);
    }

    public List<FederationShare> findQuizShares(int stationId) {
        return repository.findQuizShares(stationId);
    }

    public FederationShare createQuizShare(int stationId, int catalogId, String shareScope) {
        return repository.createQuizShare(stationId, catalogId, shareScope);
    }

    public boolean deleteQuizShare(int id) {
        return repository.deleteQuizShare(id);
    }

    public List<FederationShare> findProtocolShares(int stationId) {
        return repository.findProtocolShares(stationId);
    }

    public FederationShare createProtocolShare(int stationId, int protocolId, String shareScope) {
        return repository.createProtocolShare(stationId, protocolId, shareScope);
    }

    public boolean deleteProtocolShare(int id) {
        return repository.deleteProtocolShare(id);
    }

    // -- Metadata Cache --

    public List<FederationMetadataCache> getCachedMetadata(int partnerId, String contentType) {
        return repository.findCachedMetadata(partnerId, contentType);
    }

    public void refreshMetadataCache(int partnerId, String contentType, List<FederationMetadataCache> entries) {
        for (var entry : entries) {
            repository.upsertMetadataCache(
                    partnerId, contentType, entry.remoteId(), entry.title(), entry.description());
        }
    }

    // -- Federation Version --

    public int getFederationVersion() {
        return FEDERATION_VERSION;
    }

    /**
     * Returns the capabilities supported by this instance.
     */
    public List<String> getSupportedCapabilities() {
        return List.of(
                FederationCapability.CapabilityType.KB_SHARE.name(),
                FederationCapability.CapabilityType.QUIZ_SHARE.name(),
                FederationCapability.CapabilityType.PROTOCOL_SHARE.name(),
                FederationCapability.CapabilityType.INVENTORY_LEND.name());
    }

    // -- Change Tracking --

    /**
     * Logs a content change for federation sync polling.
     */
    public void logChange(int stationId, String contentType, int contentId, String changeType) {
        repository.logChange(stationId, contentType, contentId, changeType);
    }

    /**
     * Returns content changes since the given timestamp for sync polling.
     */
    public List<FederationChangeLog> getChangesSince(int stationId, Instant since) {
        return repository.findChangesSince(stationId, since);
    }
}
