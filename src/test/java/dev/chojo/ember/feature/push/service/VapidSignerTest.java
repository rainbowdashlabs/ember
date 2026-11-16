/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.service;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERSequence;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The token that says who sends a push is one the push service accepts: signed with ES256 over the
 * header and claims, naming the service's origin, a time it ends and the operator to contact.
 */
class VapidSignerTest {
    private static final Base64.Decoder URL = Base64.getUrlDecoder();
    private static final Instant NOW = Instant.parse("2026-11-17T08:00:00Z");

    @Test
    void theTokenNamesTheServiceAndVerifiesWithThePublicKey() throws Exception {
        var pair = EcP256.generate();
        byte[] publicKey = EcP256.uncompressed((ECPublicKey) pair.getPublic());

        String header = VapidSigner.authorization(
                URI.create("https://fcm.googleapis.com/fcm/send/abc:def"),
                (ECPrivateKey) pair.getPrivate(),
                publicKey,
                "https://ember.example",
                NOW);

        assertTrue(header.startsWith("vapid t="), header);
        String token = header.substring("vapid t=".length(), header.indexOf(", k="));
        assertEquals(
                Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey),
                header.substring(header.indexOf(", k=") + 4));

        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);
        assertEquals("{\"typ\":\"JWT\",\"alg\":\"ES256\"}", new String(URL.decode(parts[0]), StandardCharsets.UTF_8));
        assertEquals(
                "{\"aud\":\"https://fcm.googleapis.com\",\"exp\":"
                        + NOW.plus(VapidSigner.VALIDITY).getEpochSecond() + ",\"sub\":\"https://ember.example\"}",
                new String(URL.decode(parts[1]), StandardCharsets.UTF_8));

        byte[] raw = URL.decode(parts[2]);

        assertEquals(64, raw.length, "two 32-byte halves, not a DER sequence");
        var der = new ASN1EncodableVector();
        der.add(new ASN1Integer(new BigInteger(1, Arrays.copyOfRange(raw, 0, 32))));
        der.add(new ASN1Integer(new BigInteger(1, Arrays.copyOfRange(raw, 32, 64))));
        var verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(pair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(verifier.verify(new DERSequence(der).getEncoded()));
    }

    @Test
    void theAudienceIsTheEndpointsOrigin() {
        assertEquals("https://push.example", VapidSigner.audience(URI.create("https://push.example/a/b?c=d")));
        assertEquals("https://push.example:8443", VapidSigner.audience(URI.create("https://push.example:8443/x")));
        assertEquals("https://push.example", VapidSigner.audience(URI.create("HTTPS://Push.Example:443/x")));
    }
}
