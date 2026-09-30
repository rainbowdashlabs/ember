/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.ConfigChanges;
import dev.chojo.ember.conf.UnwritableConf;
import dev.chojo.ember.conf.file.File;
import dev.chojo.ember.feature.system.service.SecuritySettingsService.BackupCodesConfig;
import dev.chojo.ember.feature.system.service.SecuritySettingsService.HibpConfigRequest;
import dev.chojo.ember.feature.system.service.SecuritySettingsService.TokensConfigRequest;
import dev.chojo.ember.feature.system.service.SecuritySettingsService.TotpConfig;
import dev.chojo.ember.feature.system.service.SecuritySettingsService.TwoFactorCoreConfigRequest;
import dev.chojo.ember.feature.system.service.SecuritySettingsService.WebAuthnConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecuritySettingsServiceTest {
    private static final TokensConfigRequest TOKENS = new TokensConfigRequest(48, 12, 24, 14, 600, 30);

    @TempDir
    Path directory;

    private Conf conf;
    private SecuritySettingsService service;

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    @BeforeEach
    void setup() {
        conf = new Conf(directory);
        service = new SecuritySettingsService(conf, new ConfigChanges(conf));
    }

    private File reread() {
        return new Conf(directory).main();
    }

    @Test
    void tokenSettingsAreWrittenToTheFile() {
        var answer = service.updateTokens(TOKENS);

        var auth = reread().auth();
        assertEquals(48, auth.tokenBytes());
        assertEquals(12, auth.verifyTokenHours());
        assertEquals(24, auth.passwordTokenHours());
        assertEquals(14, auth.setupTokenDays());
        assertEquals(600, auth.sessionMinutes());
        assertEquals(30, auth.untrustedSessionMinutes());
        assertEquals(600, answer.sessionMinutes());
        assertFalse(answer.tokenPepperConfigured());
    }

    @Test
    void oneTokenSettingOutOfRangeChangesNoneOfThem() {
        var refusal = refusalOf(() -> service.updateTokens(new TokensConfigRequest(48, 12, 24, 14, 600, 1)));

        assertEquals(Refusal.SETTING_OUT_OF_RANGE, refusal);
        assertEquals(32, conf.main().auth().tokenBytes());
    }

    @Test
    void aSessionOnAnUnvouchedMachineMayNotOutlastOneOnAVouchedMachine() {
        var refusal = refusalOf(() -> service.updateTokens(new TokensConfigRequest(48, 12, 24, 14, 60, 120)));

        assertEquals(Refusal.UNTRUSTED_SESSION_OUTLASTS_TRUSTED, refusal);
    }

    @Test
    void thePepperIsGeneratedOnceAndNeverReplaced() {
        assertTrue(service.generateTokenPepper().tokenPepperConfigured());
        String pepper = reread().auth().tokenPepper();

        assertEquals(Refusal.TOKEN_PEPPER_ALREADY_SET, refusalOf(service::generateTokenPepper));
        assertEquals(pepper, reread().auth().tokenPepper());
        assertTrue(service.tokens().tokenPepperConfigured());
    }

    @Test
    void theLeakCheckIsWrittenToTheFile() {
        service.updateHibp(new HibpConfigRequest(false, "https://leaks.test/range/", 10, 3));

        var hibp = reread().auth().hibp();
        assertFalse(hibp.enabled());
        assertEquals("https://leaks.test/range/", hibp.endpoint());
        assertEquals(10, hibp.staleAfterDays());
        assertEquals(3, service.hibp().timeoutSeconds());
    }

    @Test
    void theLeakCheckNeedsAnAddressAndSaneNumbers() {
        assertEquals(
                Refusal.PASSWORD_LEAK_CHECK_NEEDS_AN_ADDRESS,
                refusalOf(() -> service.updateHibp(new HibpConfigRequest(true, " ", 10, 3))));
        assertEquals(
                Refusal.PASSWORD_LEAK_CHECK_NEEDS_AN_ADDRESS,
                refusalOf(() -> service.updateHibp(new HibpConfigRequest(true, null, 10, 3))));
        assertEquals(
                Refusal.SETTING_OUT_OF_RANGE,
                refusalOf(() -> service.updateHibp(new HibpConfigRequest(true, "https://x/", 0, 3))));
        assertEquals(
                Refusal.SETTING_OUT_OF_RANGE,
                refusalOf(() -> service.updateHibp(new HibpConfigRequest(true, "https://x/", 10, 31))));
    }

    @Test
    void twoFactorSettingsAreWrittenToTheFile() {
        service.updateTwoFactorCore(new TwoFactorCoreConfigRequest(false, 600, 14, 3));

        var twoFactor = reread().auth().twoFactor();
        assertFalse(twoFactor.enabled());
        assertEquals(600, twoFactor.stepUpFreshnessSeconds());
        assertEquals(14, twoFactor.trustedDeviceMaxDays());
        assertEquals(3, service.twoFactorCore().enrollmentGraceDays());
    }

    @Test
    void twoFactorNumbersOutOfRangeAreRefused() {
        assertEquals(
                Refusal.SETTING_OUT_OF_RANGE,
                refusalOf(() -> service.updateTwoFactorCore(new TwoFactorCoreConfigRequest(true, 30, 14, 3))));
        assertEquals(
                Refusal.SETTING_OUT_OF_RANGE,
                refusalOf(() -> service.updateTwoFactorCore(new TwoFactorCoreConfigRequest(true, 600, 31, 3))));
        assertEquals(
                Refusal.SETTING_OUT_OF_RANGE,
                refusalOf(() -> service.updateTwoFactorCore(new TwoFactorCoreConfigRequest(true, 600, 14, 8))));
    }

    @Test
    void theSecondFactorKeyIsGeneratedOnceAndNeverReplaced() {
        assertTrue(service.generateTwoFactorSecretKey().secretKeyConfigured());
        String key = reread().auth().twoFactor().secretKey();

        assertEquals(Refusal.TWO_FACTOR_SECRET_KEY_ALREADY_SET, refusalOf(service::generateTwoFactorSecretKey));
        assertEquals(key, reread().auth().twoFactor().secretKey());
    }

    @Test
    void authenticatorSettingsAreWrittenWithTheAlgorithmInCapitals() {
        var answer = service.updateTotp(new TotpConfig(8, 60, "sha256", 2, "Wache"));

        var totp = reread().auth().twoFactor().totp();
        assertEquals("SHA256", totp.algorithm());
        assertEquals(8, totp.digits());
        assertEquals(60, totp.periodSeconds());
        assertEquals(2, totp.driftWindow());
        assertEquals("Wache", totp.issuer());
        assertEquals("SHA256", answer.algorithm());
        assertEquals(answer, service.totp());
    }

    @Test
    void authenticatorSettingsNeedAnIssuerAndAKnownAlgorithm() {
        assertEquals(
                Refusal.AUTHENTICATOR_NEEDS_AN_ISSUER,
                refusalOf(() -> service.updateTotp(new TotpConfig(6, 30, "SHA1", 1, null))));
        assertEquals(
                Refusal.AUTHENTICATOR_NEEDS_AN_ISSUER,
                refusalOf(() -> service.updateTotp(new TotpConfig(6, 30, "SHA1", 1, ""))));
        assertEquals(
                Refusal.AUTHENTICATOR_ALGORITHM_UNKNOWN,
                refusalOf(() -> service.updateTotp(new TotpConfig(6, 30, "MD5", 1, "Ember"))));
        assertEquals(
                Refusal.AUTHENTICATOR_ALGORITHM_UNKNOWN,
                refusalOf(() -> service.updateTotp(new TotpConfig(6, 30, null, 1, "Ember"))));
        assertEquals(
                Refusal.SETTING_OUT_OF_RANGE,
                refusalOf(() -> service.updateTotp(new TotpConfig(3, 30, "SHA1", 1, "Ember"))));
        assertEquals(
                Refusal.SETTING_OUT_OF_RANGE,
                refusalOf(() -> service.updateTotp(new TotpConfig(6, 90, "SHA1", 1, "Ember"))));
        assertEquals(
                Refusal.SETTING_OUT_OF_RANGE,
                refusalOf(() -> service.updateTotp(new TotpConfig(6, 30, "SHA1", 4, "Ember"))));
    }

    @Test
    void theNumberOfBackupCodesIsWrittenToTheFile() {
        assertEquals(12, service.updateBackupCodes(new BackupCodesConfig(12)).count());

        assertEquals(12, reread().auth().twoFactor().backupCodes().count());
        assertEquals(
                Refusal.SETTING_OUT_OF_RANGE, refusalOf(() -> service.updateBackupCodes(new BackupCodesConfig(4))));
        assertEquals(12, service.backupCodes().count());
    }

    @Test
    void securityKeySettingsAreWrittenWithTheAttestationInSmallLetters() {
        var answer = service.updateWebAuthn(new WebAuthnConfig(null, "Wache", "DIRECT", 120));

        var webauthn = reread().auth().webauthn();
        assertEquals("", webauthn.rpId());
        assertEquals("Wache", webauthn.rpName());
        assertEquals("direct", webauthn.attestation());
        assertEquals(120, webauthn.timeoutSeconds());
        assertEquals(answer, service.webAuthn());

        service.updateWebAuthn(new WebAuthnConfig("ember.test", null, "none", 60));
        assertEquals("", reread().auth().webauthn().rpName());
    }

    @Test
    void securityKeySettingsNeedAKnownAttestation() {
        assertEquals(
                Refusal.SECURITY_KEY_ATTESTATION_UNKNOWN,
                refusalOf(() -> service.updateWebAuthn(new WebAuthnConfig("", "", "enterprise", 60))));
        assertEquals(
                Refusal.SECURITY_KEY_ATTESTATION_UNKNOWN,
                refusalOf(() -> service.updateWebAuthn(new WebAuthnConfig("", "", null, 60))));
        assertEquals(
                Refusal.SETTING_OUT_OF_RANGE,
                refusalOf(() -> service.updateWebAuthn(new WebAuthnConfig("", "", "none", 5))));
    }

    /**
     * A file that cannot be written refuses every change and takes it back, so the running
     * instance never works to values nobody could read back after a restart.
     */
    @Test
    void aChangeTheFileCannotTakeIsTakenBack() {
        var unwritable = UnwritableConf.create();
        var failing = new SecuritySettingsService(unwritable, new ConfigChanges(unwritable));
        var before = failing.tokens();

        assertEquals(Refusal.SETTINGS_NOT_SAVED, refusalOf(() -> failing.updateTokens(TOKENS)));
        assertEquals(Refusal.SETTINGS_NOT_SAVED, refusalOf(failing::generateTokenPepper));
        assertEquals(
                Refusal.SETTINGS_NOT_SAVED,
                refusalOf(() -> failing.updateHibp(new HibpConfigRequest(false, "https://x/", 10, 3))));
        assertEquals(
                Refusal.SETTINGS_NOT_SAVED,
                refusalOf(() -> failing.updateTwoFactorCore(new TwoFactorCoreConfigRequest(false, 600, 14, 3))));
        assertEquals(Refusal.SETTINGS_NOT_SAVED, refusalOf(failing::generateTwoFactorSecretKey));
        assertEquals(
                Refusal.SETTINGS_NOT_SAVED,
                refusalOf(() -> failing.updateTotp(new TotpConfig(8, 60, "SHA512", 2, "Wache"))));
        assertEquals(Refusal.SETTINGS_NOT_SAVED, refusalOf(() -> failing.updateBackupCodes(new BackupCodesConfig(12))));
        assertEquals(
                Refusal.SETTINGS_NOT_SAVED,
                refusalOf(() -> failing.updateWebAuthn(new WebAuthnConfig("x", "y", "direct", 120))));

        assertEquals(before, failing.tokens());
        assertTrue(failing.hibp().enabled());
        assertTrue(failing.twoFactorCore().enabled());
        assertFalse(failing.twoFactorCore().secretKeyConfigured());
        assertEquals("SHA1", failing.totp().algorithm());
        assertEquals(10, failing.backupCodes().count());
        assertEquals("none", failing.webAuthn().attestation());
    }
}
