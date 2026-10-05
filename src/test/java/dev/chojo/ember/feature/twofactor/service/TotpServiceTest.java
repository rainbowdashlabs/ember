/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class TotpServiceTest {

    private static TwoFactorSettings settingsWithKey(boolean enabled, String base64Key) throws Exception {
        var settings = new TwoFactorSettings();
        setField(settings, "enabled", enabled);
        setField(settings, "secretKey", base64Key);
        return settings;
    }

    private static Demo demoMode(boolean dev, boolean enabled) throws Exception {
        var demo = new Demo();
        setField(demo, "dev", dev);
        setField(demo, "enabled", enabled);
        return demo;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static String validKey() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) key[i] = (byte) i;
        return Base64.getEncoder().encodeToString(key);
    }

    /** Only a development instance knows the fixed code; a public demo has real visitors. */
    @Test
    void onlyADevelopmentInstanceKnowsTheFixedCode() throws Exception {
        var settings = settingsWithKey(true, validKey());

        assertTrue(new TotpService(settings, demoMode(true, false)).isDevCode(TotpService.DEV_CODE));
        assertFalse(new TotpService(settings, demoMode(true, false)).isDevCode("123456"));
        assertFalse(new TotpService(settings, demoMode(false, true)).isDevCode(TotpService.DEV_CODE));
        assertFalse(new TotpService(settings, demoMode(false, false)).isDevCode(TotpService.DEV_CODE));
    }

    @Test
    void generateSecretAndVerify() throws Exception {
        var service = new TotpService(settingsWithKey(true, validKey()), demoMode(false, false));
        String secret = service.generateSecret();
        assertNotNull(secret);
        assertFalse(secret.isBlank());

        String uri = service.buildOtpauthUri(secret, "user@test.com");
        assertTrue(uri.startsWith("otpauth://totp/"));

        byte[] qr = service.generateQrPng(uri, 128);
        assertNotNull(qr);
        assertTrue(qr.length > 0);

        assertEquals(32, secret.length());
        assertTrue(secret.matches("[A-Z2-7]{32}"));
        assertTrue(service.matchStep(secret, "abcdef").isEmpty());
    }

    /**
     * RFC 6238, appendix B: the published codes for all three algorithms at eight digits, with no
     * drift allowed so each code has to land on exactly its own step.
     */
    @ParameterizedTest
    @CsvSource({
        "SHA1,   12345678901234567890, 59, 94287082",
        "SHA1,   12345678901234567890, 1111111109, 07081804",
        "SHA1,   12345678901234567890, 1111111111, 14050471",
        "SHA1,   12345678901234567890, 1234567890, 89005924",
        "SHA1,   12345678901234567890, 2000000000, 69279037",
        "SHA1,   12345678901234567890, 20000000000, 65353130",
        "SHA256, 12345678901234567890123456789012, 59, 46119246",
        "SHA256, 12345678901234567890123456789012, 1111111109, 68084774",
        "SHA256, 12345678901234567890123456789012, 1111111111, 67062674",
        "SHA256, 12345678901234567890123456789012, 1234567890, 91819424",
        "SHA256, 12345678901234567890123456789012, 2000000000, 90698825",
        "SHA256, 12345678901234567890123456789012, 20000000000, 77737706",
        "SHA512, 1234567890123456789012345678901234567890123456789012345678901234, 59, 90693936",
        "SHA512, 1234567890123456789012345678901234567890123456789012345678901234, 1111111109, 25091201",
        "SHA512, 1234567890123456789012345678901234567890123456789012345678901234, 1111111111, 99943326",
        "SHA512, 1234567890123456789012345678901234567890123456789012345678901234, 1234567890, 93441116",
        "SHA512, 1234567890123456789012345678901234567890123456789012345678901234, 2000000000, 38618901",
        "SHA512, 1234567890123456789012345678901234567890123456789012345678901234, 20000000000, 47863826"
    })
    void rfc6238VectorsMatchTheirStep(String algorithm, String asciiSecret, long time, String code) throws Exception {
        var settings = settingsWithKey(true, validKey());
        setField(settings.totp(), "algorithm", algorithm);
        setField(settings.totp(), "digits", 8);
        setField(settings.totp(), "driftWindow", 0);
        var service = new TotpService(settings, demoMode(false, false), clockAt(time));
        String secret = new Base32().encodeToString(asciiSecret.getBytes(StandardCharsets.US_ASCII));

        assertEquals(OptionalLong.of(time / 30), service.matchStep(secret, code));
    }

    /**
     * Codes the previous TOTP library produced for a secret enrolled with it, at a fixed moment.
     * An account enrolled before the switch has to keep signing in with the codes its app shows.
     */
    @Test
    void aSecretEnrolledEarlierStillVerifies() throws Exception {
        var service = new TotpService(settingsWithKey(true, validKey()), demoMode(false, false), clockAt(1700000000L));
        long step = 1700000000L / 30;

        assertEquals(OptionalLong.of(step), service.matchStep("JBSWY3DPEHPK3PXP", "324550"));
        assertEquals(OptionalLong.of(step + 1), service.matchStep("JBSWY3DPEHPK3PXP", "367665"));
        assertEquals(OptionalLong.of(step), service.matchStep("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "921300"));
    }

    @Test
    void theDriftWindowReachesOneStepEitherSideAndNoFurther() throws Exception {
        long now = 1700000000L;
        var service = new TotpService(settingsWithKey(true, validKey()), demoMode(false, false), clockAt(now));
        String secret = "JBSWY3DPEHPK3PXP";

        assertTrue(service.matchStep(secret, TotpCodes.at(secret, now - 30)).isPresent());
        assertTrue(service.matchStep(secret, TotpCodes.at(secret, now + 30)).isPresent());
        assertTrue(service.matchStep(secret, TotpCodes.at(secret, now - 60)).isEmpty());
        assertTrue(service.matchStep(secret, TotpCodes.at(secret, now + 60)).isEmpty());
    }

    @Test
    void nothingMissingMatches() throws Exception {
        var service = new TotpService(settingsWithKey(true, validKey()), demoMode(false, false), clockAt(1700000000L));

        assertTrue(service.matchStep(null, "324550").isEmpty());
        assertTrue(service.matchStep("JBSWY3DPEHPK3PXP", null).isEmpty());
        assertTrue(service.matchStep("", "324550").isEmpty());
    }

    private static Clock clockAt(long epochSecond) {
        return Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC);
    }

    @Test
    void encryptionRoundTrip() throws Exception {
        var service = new TotpService(settingsWithKey(true, validKey()), demoMode(false, false));
        String secret = "JBSWY3DPEHPK3PXP";
        byte[] encrypted = service.encryptSecret(secret);
        assertNotNull(encrypted);
        assertTrue(encrypted.length > 0);
        assertEquals(secret, service.decryptSecret(encrypted));
    }

    @Test
    void demoBypassAllowsBlankKey() throws Exception {
        var service = new TotpService(settingsWithKey(true, ""), demoMode(true, false));
        assertNotNull(service.generateSecret());
    }

    @Test
    void productionRefusesBlankKeyWhenEnabled() {
        assertThrows(
                IllegalStateException.class, () -> new TotpService(settingsWithKey(true, ""), demoMode(false, false)));
    }

    @Test
    void productionAcceptsBlankKeyWhenDisabled() {
        assertDoesNotThrow(() -> new TotpService(settingsWithKey(false, ""), demoMode(false, false)));
    }

    @Test
    void rejectsWrongLengthKey() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(
                IllegalStateException.class,
                () -> new TotpService(settingsWithKey(true, tooShort), demoMode(false, false)));
    }

    @Test
    void exposesConfig() throws Exception {
        var service = new TotpService(settingsWithKey(true, validKey()), demoMode(false, false));
        assertNotNull(service.config());
    }

    @Test
    void aLiveCodeForAFreshSecretMatches() throws Exception {
        var service = new TotpService(settingsWithKey(true, validKey()), demoMode(false, false));
        String secret = service.generateSecret();
        assertTrue(service.matchStep(secret, TotpCodes.current(secret)).isPresent());
    }

    @Test
    void decryptOnCorruptInputThrows() throws Exception {
        var service = new TotpService(settingsWithKey(true, validKey()), demoMode(false, false));
        assertThrows(IllegalStateException.class, () -> service.decryptSecret(new byte[] {1, 2, 3}));
    }

    @Test
    void qrGenerationOnPayloadTooLargeThrows() throws Exception {
        var service = new TotpService(settingsWithKey(true, validKey()), demoMode(false, false));
        StringBuilder huge = new StringBuilder("otpauth://huge?data=");
        huge.repeat("A", 5000);
        assertThrows(IllegalStateException.class, () -> service.generateQrPng(huge.toString(), 64));
    }
}
