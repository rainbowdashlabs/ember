/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.chojo.ember.feature.federation.repository.StationKeyRepository;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Keeps each station's federation signing key, encrypted at rest.
 *
 * <p>The key is stored sealed by {@link CredentialCipher}, so the database holds nothing a dump
 * could sign with; the cipher's key lives in the configuration or the data directory, never beside
 * the ciphertext. A value written before encryption existed is still read, and
 * {@link #sealLegacyKeys()} converts those rows once at start-up.
 *
 * <p>Decoding a key costs a decryption and an RSA key parse, and every signed request needs one,
 * so decoded keys are kept in memory for a while. Every write through this class drops the cached
 * entry of the station it wrote.
 *
 * <p>A station keeps one key pair for all of its partners, and each partner holds the matching
 * public half. Nothing here replaces an existing key except {@link #adopt(int, PrivateKey)}, which
 * a station transfer uses to carry the station's identity over from its source instance.
 */
@Singleton
public class StationKeyStore {
    private static final Logger log = LoggerFactory.getLogger(StationKeyStore.class);
    private static final String ALGORITHM = "RSA";
    private static final int KEY_BITS = 2048;

    private final StationKeyRepository repository;
    private final CredentialCipher cipher;
    private final Cache<Integer, PrivateKey> decoded = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(10_000)
            .build();

    @Inject
    public StationKeyStore(StationKeyRepository repository, CredentialCipher cipher) {
        this.repository = repository;
        this.cipher = cipher;
    }

    /**
     * Encodes a private key the way it is sealed and carried: the Base64 of its PKCS#8 form.
     *
     * @param key the key
     * @return the encoded key
     */
    static String encode(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Reads a private key from the Base64 of its PKCS#8 form.
     *
     * @param encoded the encoded key
     * @return the key
     */
    static PrivateKey decode(String encoded) {
        try {
            return KeyFactory.getInstance(ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        } catch (Exception e) {
            throw new IllegalStateException("Stored federation key could not be read", e);
        }
    }

    /**
     * The Base64 X.509 public key belonging to a private key.
     *
     * @param key the private key, which carries its public exponent
     * @return the encoded public key
     */
    static String publicKeyOf(PrivateKey key) {
        if (!(key instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalArgumentException("Private key carries no public exponent");
        }
        try {
            var publicKey = KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
            return Base64.getEncoder().encodeToString(publicKey.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Public key could not be derived", e);
        }
    }

    /**
     * The key a station signs with.
     *
     * @param stationId the station
     * @return the key, or empty when the station has none yet
     */
    public Optional<PrivateKey> privateKey(int stationId) {
        var cached = decoded.getIfPresent(stationId);
        if (cached != null) return Optional.of(cached);
        var key = repository.find(stationId).map(this::open);
        key.ifPresent(k -> decoded.put(stationId, k));
        return key;
    }

    /**
     * Whether a station has a key to sign with.
     *
     * @param stationId the station
     * @return true when a key is stored
     */
    public boolean hasKey(int stationId) {
        return privateKey(stationId).isPresent();
    }

    /**
     * The public half of a station's key, generating and storing a key pair first when the station
     * has none.
     *
     * <p>Generating a fresh pair for a station that already has one would leave every existing
     * partner verifying against a key the station no longer signs with, so an existing key is kept.
     * Two callers racing to create the first key both end up with the one that was stored.
     *
     * @param stationId the station
     * @return the Base64 public key
     */
    public String ensurePublicKey(int stationId) {
        var existing = privateKey(stationId);
        if (existing.isPresent()) return publicKeyOf(existing.get());
        var generated = generate();
        if (repository.storeIfAbsent(stationId, cipher.seal(encode(generated)))) {
            decoded.put(stationId, generated);
            log.info("Generated the federation key of station {}", stationId);
            return publicKeyOf(generated);
        }
        decoded.invalidate(stationId);
        return publicKeyOf(privateKey(stationId).orElseThrow());
    }

    /**
     * Makes a key the station's own, replacing any key it held.
     *
     * @param stationId the station
     * @param key       the key to sign with from now on
     */
    public void adopt(int stationId, PrivateKey key) {
        repository.replace(stationId, cipher.seal(encode(key)));
        decoded.put(stationId, key);
    }

    /**
     * Encrypts every key still stored in plaintext.
     *
     * <p>Each row is converted on its own and only while it still holds the plaintext that was read,
     * so the conversion can be interrupted at any point and simply run again: converted rows are
     * skipped, the others are picked up, and a key another writer replaced meanwhile is left alone.
     *
     * @return how many keys were encrypted
     */
    public int sealLegacyKeys() {
        int sealed = 0;
        for (var row : repository.findWithoutPrefix(CredentialCipher.SEALED_PREFIX)) {
            try {
                decode(row.stored());
            } catch (IllegalStateException e) {
                log.error("The federation key of station {} is unreadable and was left as it is", row.stationId());
                continue;
            }
            if (repository.replaceIfUnchanged(row.stationId(), row.stored(), cipher.seal(row.stored()))) {
                decoded.invalidate(row.stationId());
                sealed++;
            }
        }
        if (sealed > 0) log.info("Encrypted {} federation key(s) that were stored in plaintext", sealed);
        return sealed;
    }

    private PrivateKey open(String stored) {
        return decode(CredentialCipher.isSealed(stored) ? cipher.unseal(stored) : stored);
    }

    private static PrivateKey generate() {
        try {
            var generator = KeyPairGenerator.getInstance(ALGORITHM);
            generator.initialize(KEY_BITS);
            return generator.generateKeyPair().getPrivate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate a federation key", e);
        }
    }
}
