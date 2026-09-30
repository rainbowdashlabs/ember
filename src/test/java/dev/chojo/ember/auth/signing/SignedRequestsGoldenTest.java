/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth.signing;

import dev.chojo.ember.feature.discovery.service.DiscoveryKeyService;
import dev.chojo.ember.feature.discovery.service.DiscoverySigningService;
import dev.chojo.ember.feature.federation.service.FederationSigningService;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signatures produced before and after the signing code was brought together, compared byte for
 * byte against vectors computed outside Ember.
 *
 * <p>Both protocols sign deterministically: RSA PKCS#1 v1.5 and Ed25519 give the same signature for
 * the same key and bytes every time. The federation vector was computed with OpenSSL
 * ({@code openssl dgst -sha256 -sign}) over the envelope as laid out on the wire, and the Ed25519
 * vectors are the ones published in RFC 8032, section 7.1. An installation on an older version
 * computes exactly these, so matching them is what keeps the wire format unchanged.
 */
class SignedRequestsGoldenTest {

    private static final String RSA_PKCS8 = String.join(
            "",
            "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDCWMW7aWEBdJ1rH2aVzWRO7whZPkSTShBPczEplgoZw8zw",
            "jCmyPYmGx5bc4yrhnO64lqp/pxbRfXK9mDAlQziPqzDVQmlMS7rN2PIxF8yXS31dMXfbvpfMcMCtxHjXfgSZVX/i8Zk991OO",
            "bDI63R4a1LSrggOmhqaF/qFbaMRv8rxsy2rY8hSU8ePWPbNJvKL57F03D8rNrQXMHqgXVjioVM+3/IQ1/dKSi8W+iEbc02e2",
            "Jic2v6AsqOLKHXRIVEUM/9SJly7FI5XTU6OPezoyFdF9NwiWrLYEgTmoxrz77tC0DurOtZJNmNKi+VbS//x6nFQezSGylREy",
            "oEgGjnsrAgMBAAECggEAUhAcBkO781pjci1DKvQca/wg4VYarLr0Yif41V+GilSUP+pfXOUBIkk62bCOlc+nOYhdHCPYzcKT",
            "5ZzSj5lv07Jsn1A4mD/N9EesFLDA8g2tNOHDg6VaAbiGk5lHkW6j0H86zgBIPdMmlBf5qcNeh+PTGix/EB0BSck4Jxn5AG+Y",
            "mpB9ls/NXFAo+uuasAcmBEX8nu1zGnfl1rZUHMJvdS0KI078sEsvJ8N26Y6SgGKHMo6eaq2wPz1/iC4qj8cU66efk0DBvQqy",
            "ZRLNWkljfDqQOMzFeoT0qZEbJDefTmc1gWdEEHA4IxkZIsyQgOM1yDoQXHM7xQWP30WTJ9sm6QKBgQD0X4HtTe0uHgm592Vp",
            "vjQAYcCRqO/AtQXNzMDyOMHU9OtlDIunXs1PkpO4j0U9WBsrA4way4RFi7KkVxX1LGUhmY/rD+ynpfjlEtIEY5LgfXcVkhTt",
            "sOSfCeykKrvOxpjUHipWCgBvlu82JOVPt0Z+TYi9+GWmNRL/EltTuDVzHQKBgQDLl/BIWPyXYwM8+2pczw6596/AOT9fNuwP",
            "oVailvteT6egRCKNygIWKeEHbiw29Y/S5PcfO4FpZzFB6+Bi2SCiMMxFVBq7ZCMst1GBAOcRD9L8+gb4OPiLVO1BqGLfuKEK",
            "PlEZWt2OguITm6M6k3rlJyqnHuMSgj3RlYeQ6RNM5wKBgBiSmgAAu/7NKnE2vU3awGPhcHm1IRahnUnKcwRoTKVbU2g/0LMt",
            "xztfI9eFIJNAjsrOeoIfQNe1ams4Do8uvaSZSm16lnNtLw/pypCCaryEITtcCxqzlOmhF9iSK3xpX+jEd8FbFBwkcz1gjGZt",
            "qXr91Og1WBNUduqLL//FICrJAoGAAh5gp8AVUNX88KOFJdYZxyGmuI9f83O03SzGpAaCMycNDYPoACW8A9MHvnYNC71ec8li",
            "MPcDj789Kfx0Z1LvHgD3/ziy1oRiQVLfDEKgJPNPNMa9T9P38is+FNLVxJI9Ssf/PA5QA+2kwKN7/V4Ph1bzAK43mre0QPwX",
            "XxCt3L0CgYATfrPfD//dDLR+8tz6Pdau06G0NnAs39NLDoHlIsq2WHuppHL29AnjsKqvsDkGZ2a4kvmKzaGyDkr6BLWQWGxy",
            "g1hX489gwWNRUCLwaP/bBvrAYFcD3sxkv2PPt2l33yXkm3w8dnz0BVcm/DnwdWAz8w/cjlmPVCG0KvYJjcLA6Q==");

    private static final String RSA_SIGNATURE = String.join(
            "",
            "jQAFGs+cMzQjeTt10OyLnVIakCLJ7MSNJNI5Kck28Vv/Y702vET52OaCthVu9HH8I+/Lo/I2Ce6FhKHpdv1EErSfkuYnUVwI",
            "TpX09Kod4YWQGAXcQqR72Su4oGrxbW8RN4rDvA+hNzZhlTvtF8wOog5W71S3U2E+jKqb2X05dupaKNwl/7+ac+6yhnYPqk5/",
            "qkeBs1iZE53iKLvPBDY5yEddghboYktjkszvQDaqrk4YoaMXrytJnO39nVtLfc88xoy1mL13QaGgIiIlasZC8GOFRHnhTWwl",
            "r7IfIK7bNVS2ltf8AiFbsIDY95oBkbiTDqrUYXJVnuvVU+B1D7g9MA==");

    private static final UUID RECIPIENT = UUID.fromString("5b0f3c1e-8a4d-4d2b-9c7e-2f1a6b3d4e5f");
    private static final String NONCE = "9e8d7c6b-5a49-4382-a1b0-c9d8e7f6a5b4";
    private static final String TIMESTAMP = "2026-09-30T12:00:00Z";
    private static final String BODY = "{\"content\":\"Hallo\"}";

    /** RFC 8032, section 7.1, TEST 1 (empty message) and TEST 2 (one byte, 0x72). */
    private static final String[][] ED25519_VECTORS = {
        {
            "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60",
            "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",
            "",
            "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"
        },
        {
            "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb",
            "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
            "r",
            "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00"
        }
    };

    private static final byte[] ED25519_PKCS8_PREFIX = HexFormat.of().parseHex("302e020100300506032b657004220420");

    private static PrivateKey rsaKey() throws Exception {
        return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(RSA_PKCS8)));
    }

    private static PrivateKey ed25519Key(String seedHex) throws Exception {
        byte[] seed = HexFormat.of().parseHex(seedHex);
        byte[] encoded = new byte[ED25519_PKCS8_PREFIX.length + seed.length];
        System.arraycopy(ED25519_PKCS8_PREFIX, 0, encoded, 0, ED25519_PKCS8_PREFIX.length);
        System.arraycopy(seed, 0, encoded, ED25519_PKCS8_PREFIX.length, seed.length);
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    private static FederationEnvelope goldenEnvelope() {
        return new FederationEnvelope(
                "post", "/api/v1/remote/news/comments?a=1&b=2", RECIPIENT, NONCE, TIMESTAMP, BODY);
    }

    @Test
    void theFederationEnvelopeIsLaidOutAsOnTheWire() {
        String expected = "POST\n/api/v1/remote/news/comments?a=1&b=2\n" + RECIPIENT + "\n" + NONCE + "\n" + TIMESTAMP
                + "\n" + BODY;

        assertArrayEquals(
                expected.getBytes(StandardCharsets.UTF_8), goldenEnvelope().bytes());
    }

    @Test
    void aFederationSignatureMatchesTheVectorComputedWithOpenSsl() throws Exception {
        assertEquals(RSA_SIGNATURE, SignedRequests.sign(SignatureAlgorithm.RSA_SHA256, rsaKey(), goldenEnvelope()));
    }

    @Test
    void theFederationSigningServiceStillProducesAndAcceptsTheSameSignature() throws Exception {
        var signing = new FederationSigningService();
        var key = rsaKey();

        String signature =
                signing.sign("POST", "/api/v1/remote/news/comments?a=1&b=2", RECIPIENT, NONCE, BODY, TIMESTAMP, key);

        assertEquals(RSA_SIGNATURE, signature);
    }

    @Test
    void theRsaVectorVerifiesAndATamperedEnvelopeDoesNot() throws Exception {
        var crt = (RSAPrivateCrtKey) rsaKey();
        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));

        assertTrue(SignedRequests.verify(SignatureAlgorithm.RSA_SHA256, publicKey, goldenEnvelope(), RSA_SIGNATURE));
        var tampered = new FederationEnvelope(
                "POST", "/api/v1/remote/news/comments?a=1&b=3", RECIPIENT, NONCE, TIMESTAMP, BODY);
        assertFalse(SignedRequests.verify(SignatureAlgorithm.RSA_SHA256, publicKey, tampered, RSA_SIGNATURE));
        assertFalse(SignedRequests.verify(SignatureAlgorithm.RSA_SHA256, publicKey, goldenEnvelope(), "not base64!"));
    }

    @Test
    void ed25519SignaturesMatchTheRfcVectors() throws Exception {
        for (String[] vector : ED25519_VECTORS) {
            var key = ed25519Key(vector[0]);

            String signature = SignedRequests.sign(SignatureAlgorithm.ED25519, key, new RawBodyEnvelope(vector[2]));

            assertEquals(vector[3], HexFormat.of().formatHex(Base64.getDecoder().decode(signature)));
        }
    }

    @Test
    void rawEd25519KeysMatchTheRfcVectorsBothWays() {
        for (String[] vector : ED25519_VECTORS) {
            byte[] raw = HexFormat.of().parseHex(vector[1]);

            PublicKey key = Ed25519Keys.fromRaw(raw);

            assertArrayEquals(raw, Ed25519Keys.raw(key));
            assertTrue(SignedRequests.verify(
                    SignatureAlgorithm.ED25519,
                    key,
                    new RawBodyEnvelope(vector[2]),
                    Base64.getEncoder().encodeToString(HexFormat.of().parseHex(vector[3]))));
        }
    }

    @Test
    void theDiscoveryVerifierAcceptsTheRfcVectorsFromTheWireForm() {
        var discovery = new DiscoverySigningService(null);
        for (String[] vector : ED25519_VECTORS) {
            String publicKey = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(vector[1]));
            String signature = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(vector[3]));

            assertTrue(discovery.verify(vector[2], signature, publicKey));
            assertEquals(
                    DiscoveryKeyService.computeInstanceId(HexFormat.of().parseHex(vector[1])),
                    DiscoveryKeyService.fingerprintOf(publicKey));
        }
    }

    /**
     * The raw key form agrees with the point encoding the code used to compute by hand, for keys the
     * RFC does not list.
     */
    @RepeatedTest(20)
    void rawKeysAgreeWithTheHandWrittenPointEncoding() throws Exception {
        PublicKey key =
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();

        byte[] raw = Ed25519Keys.raw(key);

        assertArrayEquals(handWrittenRaw(key), raw);
        assertArrayEquals(
                KeyFactory.getInstance("Ed25519")
                        .generatePublic(handWrittenSpec(raw))
                        .getEncoded(),
                Ed25519Keys.fromRaw(raw).getEncoded());
    }

    /** The RFC 8032 encoding as it was written out by hand before: little-endian y, sign of x on top. */
    private static byte[] handWrittenRaw(PublicKey key) {
        EdECPoint point = ((EdECPublicKey) key).getPoint();
        byte[] yBytes = point.getY().toByteArray();
        byte[] le = new byte[32];
        for (int i = 0; i < yBytes.length && i < 32; i++) {
            le[i] = yBytes[yBytes.length - 1 - i];
        }
        if (point.isXOdd()) {
            le[31] |= (byte) 0x80;
        } else {
            le[31] &= (byte) 0x7f;
        }
        return le;
    }

    /** The decoding that went with it. */
    private static EdECPublicKeySpec handWrittenSpec(byte[] raw) {
        boolean xOdd = (raw[31] & 0x80) != 0;
        byte[] yBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            yBytes[i] = raw[31 - i];
        }
        yBytes[0] = (byte) (yBytes[0] & 0x7f);
        return new EdECPublicKeySpec(NamedParameterSpec.ED25519, new EdECPoint(xOdd, new BigInteger(1, yBytes)));
    }

    @Test
    void theDriftWindowIncludesItsEdgesAndNothingBeyond() {
        Instant now = Instant.parse(TIMESTAMP);
        var drift = Duration.ofMinutes(5);

        assertTrue(SignedRequests.withinDrift(now.minus(drift), now, drift));
        assertTrue(SignedRequests.withinDrift(now.plus(drift), now, drift));
        assertFalse(SignedRequests.withinDrift(now.minus(drift).minusSeconds(1), now, drift));
        assertFalse(SignedRequests.withinDrift(null, now, drift));
    }
}
