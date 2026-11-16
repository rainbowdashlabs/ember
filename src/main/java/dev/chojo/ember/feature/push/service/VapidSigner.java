/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.service;

import dev.chojo.ember.util.Json;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Signs who is sending a push, as RFC 8292 lays it down.
 *
 * <p>A push service accepts a message for a subscription only from the key the browser subscribed
 * with. The proof is a short signed token naming the push service it is for and how long it holds,
 * sent with the installation's public key in the {@code Authorization} header.
 */
public final class VapidSigner {
    /** How long one token is good for. Push services refuse anything beyond a day. */
    static final Duration VALIDITY = Duration.ofHours(12);

    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();
    private static final String HEADER =
            URL.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));

    private VapidSigner() {}

    /**
     * The {@code Authorization} header for a push to one endpoint.
     *
     * @param endpoint   the subscription's endpoint, whose origin the token names
     * @param privateKey the installation's private key
     * @param publicKey  the installation's public key as an uncompressed point
     * @param subject    how the push service reaches the operator, a {@code mailto:} or {@code https:}
     *                   address; written as JSON, so any character is safe
     * @param now        the moment the token is made
     * @return the header value
     */
    public static String authorization(
            URI endpoint, ECPrivateKey privateKey, byte[] publicKey, String subject, Instant now) {
        var claims = new LinkedHashMap<String, Object>();
        claims.put("aud", audience(endpoint));
        claims.put("exp", now.plus(VALIDITY).getEpochSecond());
        claims.put("sub", subject);
        String claimsJson = Json.MAPPER.writeValueAsString(claims);
        String signingInput = HEADER + "." + URL.encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8));
        String token = signingInput + "." + URL.encodeToString(sign(privateKey, signingInput));
        return "vapid t=" + token + ", k=" + URL.encodeToString(publicKey);
    }

    /**
     * The origin of the endpoint as push services compare it: scheme and host in lower case, and a
     * port only where it is not the scheme's own.
     */
    static String audience(URI endpoint) {
        String scheme = endpoint.getScheme().toLowerCase(Locale.ROOT);
        String origin = scheme + "://" + endpoint.getHost().toLowerCase(Locale.ROOT);
        int port = endpoint.getPort();
        boolean ownPort =
                port == -1 || ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80);
        return ownPort ? origin : origin + ":" + port;
    }

    /** Signs with ES256 and returns the two 32-byte halves a token carries, not the DER of certificates. */
    private static byte[] sign(ECPrivateKey key, String signingInput) {
        try {
            var signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            signature.initSign(key);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("The push key could not sign", e);
        }
    }
}
