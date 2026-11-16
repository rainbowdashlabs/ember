/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.service;

import dev.chojo.ember.feature.push.repository.VapidKeyRepository;
import dev.chojo.ember.feature.signing.service.SigningKeyWrap;
import dev.chojo.ember.feature.signing.service.SigningKeyWrapException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * The key pair this installation signs every push with.
 *
 * <p>It is made the first time it is needed and kept in the database, its private half wrapped under
 * the at-rest secret like the signing keys. Browsers bind their subscriptions to the public half, so
 * a new key would silently end every subscription. That is why a stored key that does not open is an
 * error to fix, by restoring the at-rest secret from the same backup as the database, and never a
 * reason to make another one.
 */
@Singleton
public class VapidKeys {
    private static final Logger log = getLogger(VapidKeys.class);

    private final VapidKeyRepository repository;
    private final SigningKeyWrap wrap;
    private volatile Opened opened;

    /**
     * The key pair, ready to sign with.
     *
     * @param privateKey the private key
     * @param publicKey  the public key as the uncompressed point browsers subscribe with
     */
    public record Opened(ECPrivateKey privateKey, byte[] publicKey) {
        /** @return a copy, so no caller can change the installation's key for everybody else */
        @Override
        public byte[] publicKey() {
            return publicKey.clone();
        }
    }

    @Inject
    public VapidKeys(VapidKeyRepository repository, SigningKeyWrap wrap) {
        this.repository = repository;
        this.wrap = wrap;
    }

    /**
     * The key pair, made and stored on the first call.
     *
     * @throws SigningKeyWrapException when a stored key does not open under this installation's at-rest
     *     secret
     */
    public Opened keys() {
        var current = opened;
        if (current != null) return current;
        synchronized (this) {
            if (opened == null) opened = load();
            return opened;
        }
    }

    /** @return the public key as an uncompressed point, what a browser subscribes with */
    public byte[] publicKey() {
        return keys().publicKey();
    }

    private Opened load() {
        var stored = repository.find().orElseGet(this::create);
        try {
            return new Opened((ECPrivateKey) wrap.unwrap(stored.wrappedPrivateKey()), stored.publicKey());
        } catch (SigningKeyWrapException e) {
            throw new SigningKeyWrapException(
                    "The key push messages are signed with does not open, so no push can be sent. Restore the"
                            + " at-rest key from the same backup as the database; a new key would end every"
                            + " browser's subscription. " + e.getMessage(),
                    e);
        }
    }

    private VapidKeyRepository.StoredVapidKey create() {
        var pair = EcP256.generate();
        if (repository.storeIfAbsent(
                EcP256.uncompressed((ECPublicKey) pair.getPublic()), wrap.wrap(pair.getPrivate()))) {
            log.info("Made the key pair push messages are signed with");
        }
        return repository.find().orElseThrow(() -> new IllegalStateException("The push key pair was not stored"));
    }
}
