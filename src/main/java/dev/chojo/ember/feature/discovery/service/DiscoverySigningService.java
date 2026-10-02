/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.auth.signing.RawBodyEnvelope;
import dev.chojo.ember.auth.signing.SignatureAlgorithm;
import dev.chojo.ember.auth.signing.SignedRequests;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.PublicKey;

/**
 * Discovery's use of the shared signed-request module: Ed25519 over the body exactly as sent,
 * carried in {@value #SIGNATURE_HEADER}.
 *
 * <p>Discovery uses its own per-instance keypair (see {@link DiscoveryKeyService}), distinct
 * from federation's per-station RSA keys. The signature is over the request body bytes
 * exactly as transmitted - callers are responsible for serializing once and hashing the same
 * bytes.
 */
@Singleton
public class DiscoverySigningService {
    public static final String SIGNATURE_HEADER = "X-Ember-Discovery-Signature";

    /**
     * Where a delivery to a beacon carries the key its signature is to be checked against.
     *
     * <p>Beside the signature header because the two only mean anything together, and both sides of
     * a delivery read them from here rather than spelling the names out twice.
     */
    public static final String BEACON_KEY_HEADER = "X-Beacon-Key";

    private static final Logger log = LoggerFactory.getLogger(DiscoverySigningService.class);
    private static final SignatureAlgorithm ALGORITHM = SignatureAlgorithm.ED25519;
    private final DiscoveryKeyService keyService;

    @Inject
    public DiscoverySigningService(DiscoveryKeyService keyService) {
        this.keyService = keyService;
    }

    /**
     * The public half of the key this instance signs with, base64 as it travels.
     *
     * <p>A peer that already knows this instance looks the key up by the partner it belongs to. A
     * beacon cannot: anybody may report to one without being known to it beforehand, so a delivery
     * has to carry the key its signature is to be checked against.
     */
    public String publicKeyBase64() {
        return keyService.publicKeyBase64();
    }

    /**
     * Signs the given body with the local instance's private key. Returns the base64-encoded
     * raw Ed25519 signature.
     */
    public String sign(String body) {
        return SignedRequests.sign(ALGORITHM, keyService.privateKey(), new RawBodyEnvelope(body));
    }

    /**
     * Verifies a signature against the peer's raw public key (base64 wire format).
     */
    public boolean verify(String body, String signatureBase64, String peerPublicKeyBase64) {
        try {
            PublicKey peerKey = DiscoveryKeyService.decodePeerPublicKey(peerPublicKeyBase64);
            return verify(body, signatureBase64, peerKey);
        } catch (IOException e) {
            log.warn("Failed to decode peer public key: {}", e.getMessage());
            return false;
        }
    }

    public boolean verify(String body, String signatureBase64, PublicKey peerKey) {
        if (!SignedRequests.verify(ALGORITHM, peerKey, new RawBodyEnvelope(body), signatureBase64)) {
            log.warn("Discovery signature verification failed");
            return false;
        }
        return true;
    }
}
