/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.FederationCapability;
import dev.chojo.ember.feature.federation.entity.FederationMetadataCache;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationShare;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Singleton
public class FederationService {
    private static final Logger log = LoggerFactory.getLogger(FederationService.class);
    private static final int FEDERATION_VERSION = 1;

    private final FederationRepository repository;

    @Inject
    public FederationService(FederationRepository repository) {
        this.repository = repository;
    }

    // -- Invite Code --

    public String generateInviteCode() {
        var random = new SecureRandom();
        var sb = new StringBuilder("EMBER-");
        var chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < 4; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        sb.append("-");
        for (int i = 0; i < 4; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
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
        // We store the partner_station_id as the same station temporarily — it's updated on accept
        // Actually, we need a valid station ID for the FK. Let's use a different approach:
        // The invite is created with just the station_id. partner_station_id is set on accept.
        // But the FK constraint requires a valid reference. So we'll create the full record on accept.
        // For now, store the invite code + public key in a temporary way.
        // Simplest approach: return just the code and public key, store nothing in DB until accepted.
        // Actually, let's store the invite in the partner table with partner = station (self-ref blocked by CHECK).
        // Better: remove the CHECK constraint and allow pending records with partner=0.
        // Simplest: just return the data without DB storage. The accepting station creates the record.

        // For same-instance: we can look up the station by invite code from a temporary store.
        // Let's use a simple in-memory map for pending invites.
        return new FederationPartner(
                0,
                stationId,
                0,
                inviteCode,
                publicKey,
                null,
                FederationPartner.FederationStatus.PENDING,
                FEDERATION_VERSION,
                java.time.Instant.now(),
                java.time.Instant.now());
    }

    /**
     * Accepts a federation invite on the same instance.
     * Creates partner records for both stations.
     */
    public FederationPartner acceptInvite(int acceptingStationId, int initiatingStationId, String initiatingPublicKey) {
        var keyPair = generateKeyPair();
        String acceptingPublicKey = encodePublicKey(keyPair);

        // Create partner record: initiating -> accepting
        var partner = repository.createPartner(initiatingStationId, acceptingStationId, null, initiatingPublicKey);
        repository.activatePartner(partner.id(), acceptingPublicKey);

        // Create reverse partner record: accepting -> initiating
        var reverse = repository.createPartner(acceptingStationId, initiatingStationId, null, acceptingPublicKey);
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
}
