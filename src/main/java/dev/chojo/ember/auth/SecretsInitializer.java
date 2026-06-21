/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Ensures that boot-critical secrets are populated in {@code config.yaml} before
 * the Guice singletons that consume them are constructed.
 *
 * <p>{@link TokenHasher} and the TOTP encryption key are cached at service
 * construction; if their backing config field is blank the application either
 * refuses to boot (production) or substitutes a deterministic dev placeholder
 * (demo / dev). Running this initializer first means a fresh production install
 * never trips the boot guard — the first start generates a strong secret,
 * persists it to disk, and subsequent boots reuse it.
 *
 * <p>Demo / dev runs are skipped intentionally: tests and demo containers expect
 * the placeholder values so config files committed into images stay diff-free.
 */
public final class SecretsInitializer {
    private static final Logger log = LoggerFactory.getLogger(SecretsInitializer.class);

    private SecretsInitializer() {}

    /**
     * Generates and persists any missing secrets in {@code conf}.
     *
     * @param conf the configuration whose backing file will be mutated and saved
     */
    public static void ensure(Conf conf) {
        Auth auth = conf.main().auth();
        Demo demo = conf.main().demo();
        if (demo.dev() || demo.enabled()) {
            return;
        }
        boolean dirty = false;
        if (auth.tokenPepper() == null || auth.tokenPepper().isBlank()) {
            setField(Auth.class, auth, "tokenPepper", generateUrlSafeBase64(48));
            log.warn("auth.tokenPepper was empty — generated a fresh value and persisted it to config.yaml.");
            dirty = true;
        }
        TwoFactorSettings twoFactor = auth.twoFactor();
        if (twoFactor.secretKey() == null || twoFactor.secretKey().isBlank()) {
            setField(TwoFactorSettings.class, twoFactor, "secretKey", generateBase64(32));
            log.warn(
                    "auth.twoFactor.secretKey was empty — generated a fresh 32-byte value and persisted it to config.yaml.");
            dirty = true;
        }
        if (dirty) {
            conf.save();
        }
    }

    private static String generateUrlSafeBase64(int bytes) {
        byte[] buf = new byte[bytes];
        new SecureRandom().nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String generateBase64(int bytes) {
        byte[] buf = new byte[bytes];
        new SecureRandom().nextBytes(buf);
        return Base64.getEncoder().encodeToString(buf);
    }

    private static void setField(Class<?> clazz, Object target, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set " + clazz.getSimpleName() + "." + fieldName, e);
        }
    }
}
