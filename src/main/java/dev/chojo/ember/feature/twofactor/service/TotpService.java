/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.OptionalLong;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@Singleton
public class TotpService {
    private static final Logger log = LoggerFactory.getLogger(TotpService.class);
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private final TwoFactorSettings.TotpConfig config;
    private final byte[] encryptionKey;
    private final SecretGenerator secretGenerator;
    private final CodeVerifier codeVerifier;
    private final CodeGenerator codeGenerator;
    private final TimeProvider timeProvider;

    @Inject
    public TotpService(TwoFactorSettings twoFactorSettings, Demo demo) {
        this.config = twoFactorSettings.totp();
        String keyBase64 = twoFactorSettings.secretKey();
        this.encryptionKey = resolveEncryptionKey(twoFactorSettings, demo, keyBase64);

        this.secretGenerator = new DefaultSecretGenerator();
        var algorithm = HashingAlgorithm.valueOf(config.algorithm());
        this.codeGenerator = new DefaultCodeGenerator(algorithm, config.digits());
        this.timeProvider = new SystemTimeProvider();
        var verifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
        verifier.setTimePeriod(config.periodSeconds());
        verifier.setAllowedTimePeriodDiscrepancy(config.driftWindow());
        this.codeVerifier = verifier;
    }

    /**
     * Returns the TOTP time-step that the given code matches, searching the configured drift
     * window around the current step, or empty when the code is invalid. Callers can persist
     * the matched step and reject any step at or below it to make each code single-use.
     */
    public OptionalLong matchStep(String secret, String code) {
        if (code == null) return OptionalLong.empty();
        long currentStep = timeProvider.getTime() / config.periodSeconds();
        int drift = config.driftWindow();
        for (long step = currentStep - drift; step <= currentStep + drift; step++) {
            try {
                String candidate = codeGenerator.generate(secret, step);
                if (MessageDigest.isEqual(
                        candidate.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8))) {
                    return OptionalLong.of(step);
                }
            } catch (Exception ignored) {
                // A generation failure for one step should not abort the window scan.
            }
        }
        return OptionalLong.empty();
    }

    /**
     * Resolves the AES-GCM key used to encrypt TOTP secrets at rest. Production deployments
     * with {@code auth.twoFactor.enabled = true} (which is now the default) must provide a
     * real 32-byte base64 key via {@code TWO_FACTOR_SECRET_KEY}; the app refuses to boot
     * otherwise so a missing secret never silently degrades to a zero key that every install
     * shares. {@code demo.dev} / {@code demo.enabled} runs fall back to a fixed dev key.
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
            // 2FA disabled — leave the key zeroed; the service will not be invoked.
            return new byte[32];
        }
        byte[] decoded = Base64.getDecoder().decode(keyBase64);
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "auth.twoFactor.secretKey must decode to exactly 32 bytes (got " + decoded.length + ").");
        }
        return decoded;
    }

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public boolean verifyCode(String secret, String code) {
        return codeVerifier.isValidCode(secret, code);
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
