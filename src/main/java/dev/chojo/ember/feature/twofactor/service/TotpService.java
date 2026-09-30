/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.codec.binary.Base32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.OptionalLong;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time passwords (RFC 6238) for the authenticator-app factor.
 *
 * <p>A secret is twenty random bytes, handed to the app as unpadded Base32 in an
 * {@code otpauth://} URI and stored encrypted with AES-GCM. Codes are HMAC-based one-time passwords
 * over the number of whole periods since the epoch, with the digits, period and HMAC algorithm from
 * the configuration (SHA1, six digits and thirty seconds unless an operator changed them). That is
 * what every authenticator app computes, so every secret enrolled so far keeps working.
 *
 * <p>{@link #matchStep(String, String)} is the one way a code is checked, for enrolment and sign-in
 * alike, so both accept exactly the same codes.
 */
@Singleton
public class TotpService {
    private static final Logger log = LoggerFactory.getLogger(TotpService.class);
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final int SECRET_BYTES = 20;
    private static final Base32 BASE32 = new Base32();

    private final TwoFactorSettings.TotpConfig config;
    private final byte[] encryptionKey;
    private final TimeBasedOneTimePasswordGenerator generator;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    @Inject
    public TotpService(TwoFactorSettings twoFactorSettings, Demo demo) {
        this(twoFactorSettings, demo, Clock.systemUTC());
    }

    TotpService(TwoFactorSettings twoFactorSettings, Demo demo, Clock clock) {
        this.config = twoFactorSettings.totp();
        this.encryptionKey = resolveEncryptionKey(twoFactorSettings, demo, twoFactorSettings.secretKey());
        this.generator = generatorFor(config);
        this.clock = clock;
    }

    private static TimeBasedOneTimePasswordGenerator generatorFor(TwoFactorSettings.TotpConfig config) {
        try {
            return new TimeBasedOneTimePasswordGenerator(
                    Duration.ofSeconds(config.periodSeconds()), config.digits(), "Hmac" + config.algorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unsupported TOTP algorithm: " + config.algorithm(), e);
        }
    }

    /**
     * Returns the TOTP time-step that the given code matches, searching the configured drift
     * window around the current step, or empty when the code is invalid. Callers can persist
     * the matched step and reject any step at or below it to make each code single-use.
     *
     * <p>A secret that is not Base32 or that the HMAC refuses as a key matches nothing, and is
     * logged, because a stored secret should never be either.
     *
     * @param secret the Base32 secret
     * @param code   the code the user typed
     * @return the matched step, or empty
     */
    public OptionalLong matchStep(String secret, String code) {
        if (secret == null || code == null) return OptionalLong.empty();
        byte[] keyBytes = BASE32.decode(secret);
        if (keyBytes.length == 0) {
            log.warn("A TOTP secret decoded to no key bytes");
            return OptionalLong.empty();
        }
        var key = new SecretKeySpec(keyBytes, generator.getAlgorithm());
        byte[] presented = code.getBytes(StandardCharsets.UTF_8);
        long period = config.periodSeconds();
        long currentStep = Math.floorDiv(clock.instant().getEpochSecond(), period);
        int drift = config.driftWindow();
        try {
            for (long step = currentStep - drift; step <= currentStep + drift; step++) {
                String candidate =
                        generator.generateOneTimePasswordString(key, Instant.ofEpochSecond(step * period), Locale.ROOT);
                if (MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), presented)) {
                    return OptionalLong.of(step);
                }
            }
        } catch (InvalidKeyException e) {
            log.error("A TOTP secret was refused as an HMAC key", e);
        }
        return OptionalLong.empty();
    }

    /**
     * Resolves the AES-GCM key used to encrypt TOTP secrets at rest. Production deployments
     * with {@code auth.twoFactor.enabled = true} (which is now the default) must provide a
     * real 32-byte base64 key via {@code TWO_FACTOR_SECRET_KEY}; the app refuses to boot
     * otherwise so a missing secret never silently degrades to a zero key that every install
     * shares. {@code demo.dev} / {@code demo.enabled} runs fall back to a fixed dev key, and with
     * 2FA disabled the key stays zeroed because nothing ever encrypts with it.
     */
    private static byte[] resolveEncryptionKey(TwoFactorSettings settings, Demo demo, String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            if (demo.dev() || demo.enabled()) {
                return new byte[32];
            }
            if (settings.enabled()) {
                throw new IllegalStateException(
                        "auth.twoFactor.secretKey (or TWO_FACTOR_SECRET_KEY) must be set in production deployments. "
                                + "Generate a 32-byte base64 random value and inject it via configuration.");
            }
            return new byte[32];
        }
        byte[] decoded = Base64.getDecoder().decode(keyBase64);
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "auth.twoFactor.secretKey must decode to exactly 32 bytes (got " + decoded.length + ").");
        }
        return decoded;
    }

    /**
     * A new secret: twenty random bytes as thirty-two Base32 characters, the length authenticator
     * apps expect.
     */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return BASE32.encodeToString(bytes);
    }

    public byte[] encryptSecret(String secretBase32) {
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[GCM_IV_BYTES];
            new SecureRandom().nextBytes(iv);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(secretBase32.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt TOTP secret", e);
        }
    }

    public String decryptSecret(byte[] encrypted) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            System.arraycopy(encrypted, 0, iv, 0, iv.length);
            byte[] ciphertext = new byte[encrypted.length - iv.length];
            System.arraycopy(encrypted, iv.length, ciphertext, 0, ciphertext.length);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt TOTP secret", e);
        }
    }

    public String buildOtpauthUri(String secret, String accountEmail) {
        String issuer = config.issuer();
        return "otpauth://totp/" + URLEncoder.encode(issuer, StandardCharsets.UTF_8)
                + ":" + URLEncoder.encode(accountEmail, StandardCharsets.UTF_8)
                + "?secret=" + secret
                + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8)
                + "&digits=" + config.digits()
                + "&period=" + config.periodSeconds()
                + "&algorithm=" + config.algorithm();
    }

    public byte[] generateQrPng(String otpauthUri, int size) {
        try {
            var matrix = new QRCodeWriter().encode(otpauthUri, BarcodeFormat.QR_CODE, size, size);
            var out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            log.error("Failed to generate QR code", e);
            throw new IllegalStateException("QR generation failed", e);
        }
    }

    public TwoFactorSettings.TotpConfig config() {
        return config;
    }
}
