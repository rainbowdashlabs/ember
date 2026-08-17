/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

/**
 * Signs and verifies discovery payloads with Ed25519.
 *
 * <p>Discovery uses its own per-instance keypair (see {@link DiscoveryKeyService}), distinct
 * from federation's per-partner RSA keys. The signature is over the request body bytes
 * exactly as transmitted - callers are responsible for serializing once and hashing the same
 * bytes.
 */
@Singleton
public class DiscoverySigningService {
    public static final String SIGNATURE_HEADER = "X-Ember-Discovery-Signature";
    private static final Logger log = LoggerFactory.getLogger(DiscoverySigningService.class);
    private static final String ALGO = "Ed25519";
    private final DiscoveryKeyService keyService;

    @Inject
    public DiscoverySigningService(DiscoveryKeyService keyService) {
        this.keyService = keyService;
    }

    /**
     * Signs the given body with the local instance's private key. Returns the base64-encoded
     * raw Ed25519 signature.
     */
    public String sign(String body) {
        try {
            var signer = Signature.getInstance(ALGO);
            signer.initSign(keyService.privateKey());
            signer.update(body.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign discovery body", e);
        }
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
        try {
            var verifier = Signature.getInstance(ALGO);
            verifier.initVerify(peerKey);
            verifier.update(body.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            log.warn("Discovery signature verification failed: {}", e.getMessage());
            return false;
        }
    }
}
