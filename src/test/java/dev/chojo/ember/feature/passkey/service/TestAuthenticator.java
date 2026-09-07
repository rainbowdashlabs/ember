/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/**
 * An authenticator for tests: one P-256 key that registers against a real creation ceremony and
 * signs real assertions afterwards. The relying party accepts both, which is what lets the
 * success paths of every ceremony run in a unit test where no browser sits.
 *
 * <p>The origin is the one every test setup configures ({@code https://ember.test}); the rpId and
 * the challenge come from the ceremony itself.
 */
final class TestAuthenticator {

    private static final String ORIGIN = "https://ember.test";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KeyPair keyPair;
    private final byte[] credentialId = new byte[16];
    private byte[] userHandle;
    private int counter;

    TestAuthenticator() {
        try {
            var keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(new ECGenParameterSpec("secp256r1"));
            keyPair = keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("P-256 unavailable", e);
        }
        new SecureRandom().nextBytes(credentialId);
    }

    byte[] credentialId() {
        return credentialId.clone();
    }

    byte[] userHandle() {
        return userHandle == null ? null : userHandle.clone();
    }

    /** The credential's public key as the COSE bytes the store keeps, for tests planting rows. */
    byte[] coseKey() {
        try {
            return buildCoseKey();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Pins the user handle for a credential planted straight into the store rather than registered. */
    void useHandle(byte[] handle) {
        userHandle = handle.clone();
    }

    /**
     * Answers a creation ceremony: a registration response with this authenticator's key, the
     * ceremony's own challenge and a {@code none} attestation, flagged user-verified.
     */
    String register(String optionsJson) {
        return register(optionsJson, true);
    }

    /** The same, without the user-verified flag: a reader who skipped the fingerprint. */
    String register(String optionsJson, boolean userVerified) {
        try {
            var options = MAPPER.readTree(optionsJson).path("publicKey");
            String challenge = options.path("challenge").asText();
            String rpId = options.path("rp").path("id").asText();
            userHandle = Base64.getUrlDecoder()
                    .decode(options.path("user").path("id").asText());

            var authData = new ByteArrayOutputStream();
            authData.write(MessageDigest.getInstance("SHA-256").digest(rpId.getBytes(StandardCharsets.UTF_8)));
            authData.write(userVerified ? 0x45 : 0x41); // user present, attested data, verified or not
            authData.write(new byte[4]); // signature counter
            authData.write(new byte[16]); // aaguid
            authData.write(new byte[] {0, (byte) credentialId.length});
            authData.write(credentialId);
            authData.write(buildCoseKey());

            // Attestation object: {"fmt": "none", "attStmt": {}, "authData": bytes}
            var attestation = new ByteArrayOutputStream();
            attestation.write(0xA3);
            cborText(attestation, "fmt");
            cborText(attestation, "none");
            cborText(attestation, "attStmt");
            attestation.write(0xA0);
            cborText(attestation, "authData");
            cborBytes(attestation, authData.toByteArray());

            var b64 = Base64.getUrlEncoder().withoutPadding();
            String clientData = MAPPER.writeValueAsString(MAPPER.createObjectNode()
                    .put("type", "webauthn.create")
                    .put("challenge", challenge)
                    .put("origin", ORIGIN));
            var response = MAPPER.createObjectNode()
                    .put("id", b64.encodeToString(credentialId))
                    .put("rawId", b64.encodeToString(credentialId))
                    .put("type", "public-key");
            response.putObject("clientExtensionResults");
            response.putObject("response")
                    .put("clientDataJSON", b64.encodeToString(clientData.getBytes(StandardCharsets.UTF_8)))
                    .put("attestationObject", b64.encodeToString(attestation.toByteArray()));
            return MAPPER.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build the registration response", e);
        }
    }

    /**
     * Answers an assertion ceremony: authenticator data flagged user-verified, a counter one
     * higher than last time, and a real signature over what the relying party will verify.
     */
    String sign(String requestJson) {
        try {
            var options = MAPPER.readTree(requestJson).path("publicKey");
            String challenge = options.path("challenge").asText();
            String rpId = options.path("rpId").asText();

            counter++;
            var authData = new ByteArrayOutputStream();
            authData.write(MessageDigest.getInstance("SHA-256").digest(rpId.getBytes(StandardCharsets.UTF_8)));
            authData.write(0x05); // user present, user verified
            authData.write(ByteBuffer.allocate(4).putInt(counter).array());

            String clientData = MAPPER.writeValueAsString(MAPPER.createObjectNode()
                    .put("type", "webauthn.get")
                    .put("challenge", challenge)
                    .put("origin", ORIGIN));
            byte[] clientDataBytes = clientData.getBytes(StandardCharsets.UTF_8);

            var signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(authData.toByteArray());
            signature.update(MessageDigest.getInstance("SHA-256").digest(clientDataBytes));
            byte[] signed = signature.sign();

            var b64 = Base64.getUrlEncoder().withoutPadding();
            var response = MAPPER.createObjectNode()
                    .put("id", b64.encodeToString(credentialId))
                    .put("rawId", b64.encodeToString(credentialId))
                    .put("type", "public-key");
            response.putObject("clientExtensionResults");
            response.putObject("response")
                    .put("clientDataJSON", b64.encodeToString(clientDataBytes))
                    .put("authenticatorData", b64.encodeToString(authData.toByteArray()))
                    .put("signature", b64.encodeToString(signed))
                    .put("userHandle", userHandle == null ? null : b64.encodeToString(userHandle));
            return MAPPER.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build the assertion response", e);
        }
    }

    /** COSE key: {1: 2 (EC2), 3: -7 (ES256), -1: 1 (P-256), -2: x, -3: y}. */
    private byte[] buildCoseKey() throws IOException {
        var publicKey = (ECPublicKey) keyPair.getPublic();
        var coseKey = new ByteArrayOutputStream();
        coseKey.write(0xA5);
        cborInt(coseKey, 1);
        cborInt(coseKey, 2);
        cborInt(coseKey, 3);
        cborInt(coseKey, -7);
        cborInt(coseKey, -1);
        cborInt(coseKey, 1);
        cborInt(coseKey, -2);
        cborBytes(coseKey, fixedLength(publicKey.getW().getAffineX(), 32));
        cborInt(coseKey, -3);
        cborBytes(coseKey, fixedLength(publicKey.getW().getAffineY(), 32));
        return coseKey.toByteArray();
    }

    /** One CBOR integer, covering the small values a COSE key needs. */
    private static void cborInt(ByteArrayOutputStream out, int value) {
        if (value >= 0) {
            out.write(value); // all our positives are below 24
        } else {
            out.write(0x20 | (-1 - value)); // all our negatives are above -25
        }
    }

    private static void cborBytes(ByteArrayOutputStream out, byte[] data) throws IOException {
        if (data.length < 24) {
            out.write(0x40 | data.length);
        } else if (data.length < 256) {
            out.write(0x58);
            out.write(data.length);
        } else {
            out.write(0x59);
            out.write(data.length >> 8);
            out.write(data.length & 0xff);
        }
        out.write(data);
    }

    private static void cborText(ByteArrayOutputStream out, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        out.write(0x60 | data.length); // all our strings are short
        out.write(data);
    }

    private static byte[] fixedLength(BigInteger coordinate, int length) {
        byte[] raw = coordinate.toByteArray();
        byte[] out = new byte[length];
        if (raw.length >= length) {
            System.arraycopy(raw, raw.length - length, out, 0, length);
        } else {
            System.arraycopy(raw, 0, out, length - raw.length, raw.length);
        }
        return out;
    }
}
