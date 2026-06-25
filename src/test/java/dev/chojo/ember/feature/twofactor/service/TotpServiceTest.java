/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Base64;

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

        // Verification with a clearly wrong code returns false; we don't have a clock to
        // produce a real code from this thread without depending on the same generator.
        assertFalse(service.verifyCode(secret, "000000"));
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
    void productionRefusesBlankKeyWhenEnabled() throws Exception {
        assertThrows(
                IllegalStateException.class, () -> new TotpService(settingsWithKey(true, ""), demoMode(false, false)));
    }

    @Test
    void productionAcceptsBlankKeyWhenDisabled() throws Exception {
        // Service constructs without throwing even with a blank key when 2FA is disabled —
        // the dead zero key is never actually invoked.
        assertDoesNotThrow(() -> new TotpService(settingsWithKey(false, ""), demoMode(false, false)));
    }

    @Test
    void rejectsWrongLengthKey() throws Exception {
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
    void verifyAcceptsLiveCode() throws Exception {
        var service = new TotpService(settingsWithKey(true, validKey()), demoMode(false, false));
        String secret = service.generateSecret();
        long timeBucket = java.time.Instant.now().getEpochSecond() / 30;
        String code = new dev.samstevens.totp.code.DefaultCodeGenerator(
                        dev.samstevens.totp.code.HashingAlgorithm.SHA1, 6)
                .generate(secret, timeBucket);
        assertTrue(service.verifyCode(secret, code));
    }

    @Test
    void decryptOnCorruptInputThrows() throws Exception {
        var service = new TotpService(settingsWithKey(true, validKey()), demoMode(false, false));
        assertThrows(IllegalStateException.class, () -> service.decryptSecret(new byte[] {1, 2, 3}));
    }

    @Test
    void qrGenerationOnPayloadTooLargeThrows() throws Exception {
        var service = new TotpService(settingsWithKey(true, validKey()), demoMode(false, false));
        // Forge a payload large enough that QR encoding can't fit it into the matrix,
        // exercising the WriterException catch branch.
        StringBuilder huge = new StringBuilder("otpauth://huge?data=");
        huge.repeat("A", 5000);
        assertThrows(IllegalStateException.class, () -> service.generateQrPng(huge.toString(), 64));
    }
}
