/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.security.PrivateKey;
import java.util.UUID;

/**
 * Signs federation traffic on behalf of a station, named by its id.
 *
 * <p>This is the only way federation code reaches a station's private key: callers say which
 * station speaks and get a signature back, and the key itself never travels through a service or
 * route signature. The key is looked up in {@link StationKeyStore}, which keeps it encrypted at
 * rest and decoded in memory.
 */
@Singleton
public class StationSigner {
    private final StationKeyStore keys;
    private final FederationSigningService signing;

    @Inject
    public StationSigner(StationKeyStore keys, FederationSigningService signing) {
        this.keys = keys;
        this.signing = signing;
    }

    /**
     * Whether the station has a key to sign with.
     *
     * @param stationId the station
     * @return true when it can sign
     */
    public boolean canSign(int stationId) {
        return keys.hasKey(stationId);
    }

    /**
     * Signs a federation request envelope as the station.
     *
     * @param stationId     the station sending the request
     * @param method        the HTTP method
     * @param pathWithQuery the path with its canonical query string
     * @param recipientUuid the station the request is addressed to
     * @param nonce         the request nonce
     * @param body          the request body, {@code ""} for none
     * @param timestamp     the request timestamp
     * @return the Base64 signature
     * @throws IllegalStateException when the station has no key
     */
    public String signRequest(
            int stationId,
            String method,
            String pathWithQuery,
            UUID recipientUuid,
            String nonce,
            String body,
            String timestamp) {
        return signing.sign(method, pathWithQuery, recipientUuid, nonce, body, timestamp, requireKey(stationId));
    }

    /**
     * Signs a handshake payload as the station.
     *
     * @param stationId the station entering the partnership
     * @param payload   the enrollment payload
     * @return the Base64 signature
     * @throws IllegalStateException when the station has no key
     */
    public String signEnrollment(int stationId, String payload) {
        return signing.signEnrollmentPayload(payload, requireKey(stationId));
    }

    /**
     * The public key partners verify the station's signatures with, creating the station's key pair
     * first when it has none.
     *
     * @param stationId the station
     * @return the Base64 public key
     */
    public String ensurePublicKey(int stationId) {
        return keys.ensurePublicKey(stationId);
    }

    private PrivateKey requireKey(int stationId) {
        return keys.privateKey(stationId)
                .orElseThrow(() -> new IllegalStateException("Station " + stationId + " has no federation key"));
    }
}
