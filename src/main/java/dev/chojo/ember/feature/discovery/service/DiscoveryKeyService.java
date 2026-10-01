/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.auth.signing.Ed25519Keys;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Set;

/**
 * Loads or generates the local instance's Ed25519 discovery keypair.
 *
 * <p>Keys live under {@code data/discovery/} unless another directory is given: {@code private.key}
 * (PKCS#8 PEM-style raw bytes, mode 0600 where the FS supports it) and {@code public.key} (raw
 * 32-byte Ed25519 point). The matching {@code instance_id} fingerprint is derived deterministically
 * from the public key.
 *
 * <p>Keys are <em>never rotated</em> in v1 - if compromised, the
 * instance identity is treated as ephemeral and the admin re-creates it manually by deleting
 * the files.
 */
@Singleton
public class DiscoveryKeyService {
    private static final Logger log = LoggerFactory.getLogger(DiscoveryKeyService.class);
    private static final Path DEFAULT_DIR = Path.of("data", "discovery");
    private static final String ALGO = "Ed25519";

    private final Path privatePath;
    private final Path publicPath;
    private final KeyPair keyPair;
    private final String publicKeyBase64;
    private final String instanceId;

    /** Loads or generates the keypair in {@code data/discovery/}, where the running instance keeps it. */
    @Inject
    public DiscoveryKeyService() {
        this(DEFAULT_DIR);
    }

    /**
     * Loads or generates the keypair in the given directory, creating it when missing.
     *
     * @param dir the directory holding {@code private.key} and {@code public.key}
     */
    public DiscoveryKeyService(Path dir) {
        this.privatePath = dir.resolve("private.key");
        this.publicPath = dir.resolve("public.key");
        try {
            Files.createDirectories(dir);
            this.keyPair = loadOrGenerate();
            this.publicKeyBase64 = Base64.getEncoder().encodeToString(Ed25519Keys.raw(keyPair.getPublic()));
            this.instanceId = computeInstanceId(Ed25519Keys.raw(keyPair.getPublic()));
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
            return Ed25519Keys.fromRaw(Base64.getDecoder().decode(base64));
        } catch (IllegalArgumentException e) {
            throw new IOException("Failed to decode Ed25519 public key", e);
        }
    }

    /**
     * Computes the 16-character hex fingerprint of {@code sha256(publicKey)}.
     */
    public static String computeInstanceId(byte[] rawPublicKey) {
        return Sha256.hex(rawPublicKey).substring(0, 16);
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

    /**
     * Restricts the key file to its owner. A filesystem without POSIX permissions is skipped
     * silently: the file still sits in the discovery data directory, which the deployment is
     * expected to keep owner-only.
     */
    private static void tightenPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(
                    path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (Exception ignored) {
        }
    }

    public PublicKey publicKey() {
        return keyPair.getPublic();
    }

    public PrivateKey privateKey() {
        return keyPair.getPrivate();
    }

    /**
     * Returns the base64-encoded raw 32-byte Ed25519 public key - the canonical wire format.
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
        if (Files.exists(privatePath) && Files.exists(publicPath)) {
            return loadExisting();
        }
        return generateAndPersist();
    }

    private KeyPair loadExisting() throws Exception {
        byte[] privateRaw = Files.readAllBytes(privatePath);
        byte[] publicRaw = Files.readAllBytes(publicPath);
        var privateKey = KeyFactory.getInstance(ALGO).generatePrivate(new PKCS8EncodedKeySpec(privateRaw));
        var publicKey = Ed25519Keys.fromRaw(publicRaw);
        return new KeyPair(publicKey, privateKey);
    }

    private KeyPair generateAndPersist() throws Exception {
        log.info("No discovery keypair found, generating a fresh Ed25519 identity...");
        var generator = KeyPairGenerator.getInstance(ALGO);
        var pair = generator.generateKeyPair();
        Files.write(privatePath, pair.getPrivate().getEncoded());
        Files.write(publicPath, Ed25519Keys.raw(pair.getPublic()));
        tightenPermissions(privatePath);
        return pair;
    }
}
