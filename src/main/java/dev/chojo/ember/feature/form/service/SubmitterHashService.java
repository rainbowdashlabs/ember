/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Produces the pseudonymous {@code submitter_hash} stored alongside anonymous public
 * form responses (concept §4.4).
 *
 * <p>The hash is {@code SHA-256(realClientIp || ':' || form.id || ':' || instanceSalt)}.
 * Scoping the hash by form id means the same visitor on a different form yields a
 * different hash; hashes can't be cross-referenced between forms.
 *
 * <p>The instance-scoped salt is generated on first use, persisted as Base64 in the
 * {@code application_setting} table under {@value #SALT_KEY}, and reused for every
 * subsequent call. It is the only piece of state that would let an admin link a hash
 * back to a raw IP; rotating it invalidates every existing dedup/rate-limit anchor.
 *
 * <p>The raw IP is consumed by {@link #hash(InetAddress, int)} and immediately released
 * to the JVM heap — callers must not log or persist it elsewhere.
 */
@Singleton
public class SubmitterHashService {

    /** Application-setting key for the per-instance salt. */
    public static final String SALT_KEY = "form_response.submitter_hash_salt";

    /** Salt size — 32 bytes / 256 bits, matching SHA-256's output width. */
    private static final int SALT_BYTES = 32;

    private final ApplicationSettingRepository settings;
    private final SecureRandom random = new SecureRandom();
    private volatile byte[] cachedSalt;

    @Inject
    public SubmitterHashService(ApplicationSettingRepository settings) {
        this.settings = settings;
    }

    /**
     * Computes the {@code submitter_hash} for a public form submission.
     *
     * @param clientIp the resolved real client IP
     * @param formId   the form being submitted to
     * @return the 32-byte SHA-256 hash
     */
    public byte[] hash(InetAddress clientIp, int formId) {
        byte[] salt = salt();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(clientIp.getHostAddress().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(Integer.toString(formId).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(salt);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private byte[] salt() {
        byte[] local = cachedSalt;
        if (local != null) return local;
        synchronized (this) {
            if (cachedSalt != null) return cachedSalt;
            cachedSalt =
                    settings.get(SALT_KEY).map(Base64.getDecoder()::decode).orElseGet(this::generateAndPersistSalt);
            return cachedSalt;
        }
    }

    private byte[] generateAndPersistSalt() {
        byte[] fresh = new byte[SALT_BYTES];
        random.nextBytes(fresh);
        settings.set(SALT_KEY, Base64.getEncoder().encodeToString(fresh));
        return fresh;
    }
}
