/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

/**
 * Loads or generates the local instance's Ed25519 discovery keypair.
 *
 * <p>Keys live under {@code data/discovery/}: {@code private.key} (PKCS#8 PEM-style raw
 * bytes, mode 0600 where the FS supports it) and {@code public.key} (raw 32-byte
 * Ed25519 point). The matching {@code instance_id} fingerprint is derived deterministically
 * from the public key.
 *
 * <p>Per the concept doc, keys are <em>never rotated</em> in v1 — if compromised, the
 * instance identity is treated as ephemeral and the admin re-creates it manually by deleting
 * the files.
 */
@Singleton
public class DiscoveryKeyService {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryKeyService.class);
    private static final Path DIR = Path.of("data", "discovery");
    private static final Path PRIVATE_PATH = DIR.resolve("private.key");
    private static final Path PUBLIC_PATH = DIR.resolve("public.key");
    private static final String ALGO = "Ed25519";

    private final KeyPair keyPair;
    private final String publicKeyBase64;
    private final String instanceId;

    public DiscoveryKeyService() {
        try {
            Files.createDirectories(DIR);
            this.keyPair = loadOrGenerate();
            this.publicKeyBase64 = Base64.getEncoder().encodeToString(rawPublicKey(keyPair.getPublic()));
            this.instanceId = computeInstanceId(rawPublicKey(keyPair.getPublic()));
            log.info("Discovery identity ready (instanceId={})", instanceId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize discovery keypair", e);
        }
    }

    /**
     * Decodes a peer's base64-encoded raw 32-byte Ed25519 public key.
     */
    public static PublicKey decodePeerPublicKey(String base64) throws IOException {
        try {
            byte[] raw = Base64.getDecoder().decode(base64);
            var kf = KeyFactory.getInstance(ALGO);
            return kf.generatePublic(decodeRawPublic(raw));
        } catch (Exception e) {
            throw new IOException("Failed to decode Ed25519 public key", e);
        }
    }

    /**
     * Computes the 16-character hex fingerprint of {@code sha256(publicKey)}.
     */
    public static String computeInstanceId(byte[] rawPublicKey) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(rawPublicKey);
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 not available", e);
        }
    }

    /**
     * Convenience: instance id for a peer's base64-encoded public key.
     */
    public static String fingerprintOf(String base64PublicKey) {
        try {
            byte[] raw = Base64.getDecoder().decode(base64PublicKey);
            return computeInstanceId(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("publicKey must be base64-encoded", e);
        }
    }

    private static void tightenPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(
                    path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (Exception ignored) {
            // Non-POSIX filesystems (Windows in CI). Skip silently — the file is still inside
            // data/discovery/ which is expected to be owner-owned by deployment.
        }
    }

    private static byte[] rawPublicKey(PublicKey key) {
        if (!(key instanceof EdECPublicKey edec)) {
            throw new IllegalStateException("Expected Ed25519 public key, got " + key.getClass());
        }
        // Encode point per RFC 8032: little-endian Y with the high bit of the last byte set
        // to the sign of X.
        var point = edec.getPoint();
        byte[] yBytes = point.getY().toByteArray();
        // toByteArray is big-endian; reverse to little-endian and pad to 32 bytes.
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

    private static EdECPublicKeySpec decodeRawPublic(byte[] raw) {
        if (raw.length != 32) {
            throw new IllegalArgumentException("Ed25519 public key must be 32 bytes, got " + raw.length);
        }
        // Reverse to big-endian and strip sign bit
        boolean xOdd = (raw[31] & 0x80) != 0;
        byte[] yBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            yBytes[i] = raw[31 - i];
        }
        yBytes[0] = (byte) (yBytes[0] & 0x7f);
        var y = new BigInteger(1, yBytes);
        return new EdECPublicKeySpec(NamedParameterSpec.ED25519, new EdECPoint(xOdd, y));
    }

    public PublicKey publicKey() {
        return keyPair.getPublic();
    }

    public PrivateKey privateKey() {
        return keyPair.getPrivate();
    }

    /**
     * Returns the base64-encoded raw 32-byte Ed25519 public key — the canonical wire format.
     */
    public String publicKeyBase64() {
        return publicKeyBase64;
    }

    /**
     * Returns the 16-character hex fingerprint of {@code sha256(publicKey)}, used as the
     * human-readable instance id.
     */
    public String instanceId() {
        return instanceId;
    }

    private KeyPair loadOrGenerate() throws Exception {
        if (Files.exists(PRIVATE_PATH) && Files.exists(PUBLIC_PATH)) {
            return loadExisting();
        }
        return generateAndPersist();
    }

    private KeyPair loadExisting() throws Exception {
        byte[] privateRaw = Files.readAllBytes(PRIVATE_PATH);
        byte[] publicRaw = Files.readAllBytes(PUBLIC_PATH);
        var kf = KeyFactory.getInstance(ALGO);
        var privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privateRaw));
        var publicKey = kf.generatePublic(decodeRawPublic(publicRaw));
        return new KeyPair(publicKey, privateKey);
    }

    private KeyPair generateAndPersist() throws Exception {
        log.info("No discovery keypair found, generating a fresh Ed25519 identity...");
        var generator = KeyPairGenerator.getInstance(ALGO);
        var pair = generator.generateKeyPair();
        Files.write(PRIVATE_PATH, pair.getPrivate().getEncoded());
        Files.write(PUBLIC_PATH, rawPublicKey(pair.getPublic()));
        tightenPermissions(PRIVATE_PATH);
        return pair;
    }
}
