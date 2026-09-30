/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.ConfigChanges;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.HibpSettings;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.util.RandomTokens;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Locale;
import java.util.Set;

/**
 * The instance's sign-in and security settings as the administration screens read and change them:
 * tokens and sessions, the password leak check, two-factor authentication, authenticator apps,
 * backup codes and security keys.
 *
 * <p>Every change is checked in full before anything is set, and then applied and saved as one
 * step through {@link ConfigChanges}, so a refused request changes nothing and a failed save is
 * taken back.
 */
@Singleton
public class SecuritySettingsService {
    private static final Set<String> TOTP_ALGORITHMS = Set.of("SHA1", "SHA256", "SHA512");
    private static final Set<String> WEBAUTHN_ATTESTATIONS = Set.of("none", "indirect", "direct");

    private final Conf conf;
    private final ConfigChanges changes;

    @Inject
    public SecuritySettingsService(Conf conf, ConfigChanges changes) {
        this.conf = conf;
        this.changes = changes;
    }

    /**
     * Refuses a number outside the range a setting allows.
     *
     * @param value the number given
     * @param min   the smallest allowed
     * @param max   the largest allowed
     * @param field the setting, named in the refusal
     */
    static void requireRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw Refusal.SETTING_OUT_OF_RANGE.raise(field);
        }
    }

    private static boolean isSet(String secret) {
        return secret != null && !secret.isBlank();
    }

    public TokensConfigResponse tokens() {
        return TokensConfigResponse.of(auth());
    }

    public TokensConfigResponse updateTokens(TokensConfigRequest request) {
        requireRange(request.tokenBytes(), 16, 256, "tokenBytes");
        requireRange(request.verifyTokenHours(), 1, 720, "verifyTokenHours");
        requireRange(request.passwordTokenHours(), 1, 720, "passwordTokenHours");
        requireRange(request.setupTokenDays(), 1, Auth.SETUP_TOKEN_MAX_DAYS, "setupTokenDays");
        requireRange(request.sessionMinutes(), 5, 43200, "sessionMinutes");
        requireRange(request.untrustedSessionMinutes(), 5, 43200, "untrustedSessionMinutes");
        if (request.untrustedSessionMinutes() > request.sessionMinutes()) {
            throw Refusal.UNTRUSTED_SESSION_OUTLASTS_TRUSTED.raise();
        }
        var auth = auth();
        var before = TokensConfigRequest.of(auth);
        changes.apply(() -> request.applyTo(auth), () -> before.applyTo(auth));
        return tokens();
    }

    /**
     * Generates the token pepper, once. A pepper already set is never replaced from here: every
     * token hashed with it would stop matching, which signs everybody out at once.
     */
    public TokensConfigResponse generateTokenPepper() {
        var auth = auth();
        if (isSet(auth.tokenPepper())) {
            throw Refusal.TOKEN_PEPPER_ALREADY_SET.raise();
        }
        String previous = auth.tokenPepper();
        String pepper = RandomTokens.urlSafe(48);
        changes.apply(() -> auth.tokenPepper(pepper), () -> auth.tokenPepper(previous));
        return tokens();
    }

    public HibpConfigResponse hibp() {
        return HibpConfigResponse.of(auth().hibp());
    }

    public HibpConfigResponse updateHibp(HibpConfigRequest request) {
        requireRange(request.staleAfterDays(), 1, 365, "staleAfterDays");
        requireRange(request.timeoutSeconds(), 1, 30, "timeoutSeconds");
        if (request.endpoint() == null || request.endpoint().isBlank()) {
            throw Refusal.PASSWORD_LEAK_CHECK_NEEDS_AN_ADDRESS.raise();
        }
        var hibp = auth().hibp();
        var before = HibpConfigRequest.of(hibp);
        changes.apply(() -> request.applyTo(hibp), () -> before.applyTo(hibp));
        return hibp();
    }

    public TwoFactorCoreConfigResponse twoFactorCore() {
        return TwoFactorCoreConfigResponse.of(auth().twoFactor());
    }

    public TwoFactorCoreConfigResponse updateTwoFactorCore(TwoFactorCoreConfigRequest request) {
        requireRange(request.stepUpFreshnessSeconds(), 60, 3600, "stepUpFreshnessSeconds");
        requireRange(request.trustedDeviceMaxDays(), 1, 30, "trustedDeviceMaxDays");
        requireRange(request.enrollmentGraceDays(), 1, 7, "enrollmentGraceDays");
        var twoFactor = auth().twoFactor();
        var before = TwoFactorCoreConfigRequest.of(twoFactor);
        changes.apply(() -> request.applyTo(twoFactor), () -> before.applyTo(twoFactor));
        return twoFactorCore();
    }

    /**
     * Generates the key second factors are encrypted with, once. A key already set is never
     * replaced from here: every enrolled authenticator would become unreadable.
     */
    public TwoFactorCoreConfigResponse generateTwoFactorSecretKey() {
        var twoFactor = auth().twoFactor();
        if (isSet(twoFactor.secretKey())) {
            throw Refusal.TWO_FACTOR_SECRET_KEY_ALREADY_SET.raise();
        }
        String previous = twoFactor.secretKey();
        String key = RandomTokens.base64(32);
        changes.apply(() -> twoFactor.secretKey(key), () -> twoFactor.secretKey(previous));
        return twoFactorCore();
    }

    public TotpConfig totp() {
        return TotpConfig.of(auth().twoFactor().totp());
    }

    public TotpConfig updateTotp(TotpConfig request) {
        requireRange(request.digits(), 4, 8, "digits");
        requireRange(request.periodSeconds(), 15, 60, "periodSeconds");
        requireRange(request.driftWindow(), 0, 3, "driftWindow");
        if (request.issuer() == null || request.issuer().isBlank()) {
            throw Refusal.AUTHENTICATOR_NEEDS_AN_ISSUER.raise();
        }
        String algorithm =
                request.algorithm() == null ? "" : request.algorithm().toUpperCase(Locale.ROOT);
        if (!TOTP_ALGORITHMS.contains(algorithm)) {
            throw Refusal.AUTHENTICATOR_ALGORITHM_UNKNOWN.raise();
        }
        var totp = auth().twoFactor().totp();
        var after = new TotpConfig(
                request.digits(), request.periodSeconds(), algorithm, request.driftWindow(), request.issuer());
        var before = TotpConfig.of(totp);
        changes.apply(() -> after.applyTo(totp), () -> before.applyTo(totp));
        return totp();
    }

    public BackupCodesConfig backupCodes() {
        return new BackupCodesConfig(auth().twoFactor().backupCodes().count());
    }

    public BackupCodesConfig updateBackupCodes(BackupCodesConfig request) {
        requireRange(request.count(), 5, 20, "count");
        var backup = auth().twoFactor().backupCodes();
        int before = backup.count();
        changes.apply(() -> backup.count(request.count()), () -> backup.count(before));
        return backupCodes();
    }

    /**
     * The security key settings actually in force, which an instance still carrying them under
     * their old place in the file reads from there.
     */
    public WebAuthnConfig webAuthn() {
        return WebAuthnConfig.of(WebAuthnSettings.resolvedFrom(auth()));
    }

    public WebAuthnConfig updateWebAuthn(WebAuthnConfig request) {
        requireRange(request.timeoutSeconds(), 10, 300, "timeoutSeconds");
        String attestation =
                request.attestation() == null ? "" : request.attestation().toLowerCase(Locale.ROOT);
        if (!WEBAUTHN_ATTESTATIONS.contains(attestation)) {
            throw Refusal.SECURITY_KEY_ATTESTATION_UNKNOWN.raise();
        }
        var webauthn = WebAuthnSettings.resolvedFrom(auth());
        var after = new WebAuthnConfig(
                request.rpId() == null ? "" : request.rpId(),
                request.rpName() == null ? "" : request.rpName(),
                attestation,
                request.timeoutSeconds());
        var before = WebAuthnConfig.of(webauthn);
        changes.apply(() -> after.applyTo(webauthn), () -> before.applyTo(webauthn));
        return webAuthn();
    }

    private Auth auth() {
        return conf.main().auth();
    }

    /**
     * @param setupTokenDays          how long the link that sets up a new account stays good for,
     *                                counted in days because an invitation waits for a holiday or a
     *                                term break rather than for the next hour
     * @param untrustedSessionMinutes how long a session lasts on a machine the person signing in
     *                                did not vouch for
     */
    public record TokensConfigResponse(
            int tokenBytes,
            int verifyTokenHours,
            int passwordTokenHours,
            int setupTokenDays,
            int sessionMinutes,
            int untrustedSessionMinutes,
            boolean tokenPepperConfigured) {

        static TokensConfigResponse of(Auth auth) {
            return new TokensConfigResponse(
                    auth.tokenBytes(),
                    auth.verifyTokenHours(),
                    auth.passwordTokenHours(),
                    auth.setupTokenDays(),
                    auth.sessionMinutes(),
                    auth.untrustedSessionMinutes(),
                    isSet(auth.tokenPepper()));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokensConfigRequest(
            int tokenBytes,
            int verifyTokenHours,
            int passwordTokenHours,
            int setupTokenDays,
            int sessionMinutes,
            int untrustedSessionMinutes) {

        static TokensConfigRequest of(Auth auth) {
            return new TokensConfigRequest(
                    auth.tokenBytes(),
                    auth.verifyTokenHours(),
                    auth.passwordTokenHours(),
                    auth.setupTokenDays(),
                    auth.sessionMinutes(),
                    auth.untrustedSessionMinutes());
        }

        void applyTo(Auth auth) {
            auth.tokenBytes(tokenBytes);
            auth.verifyTokenHours(verifyTokenHours);
            auth.passwordTokenHours(passwordTokenHours);
            auth.setupTokenDays(setupTokenDays);
            auth.sessionMinutes(sessionMinutes);
            auth.untrustedSessionMinutes(untrustedSessionMinutes);
        }
    }

    public record HibpConfigResponse(boolean enabled, String endpoint, int staleAfterDays, int timeoutSeconds) {
        static HibpConfigResponse of(HibpSettings hibp) {
            return new HibpConfigResponse(
                    hibp.enabled(), hibp.endpoint(), hibp.staleAfterDays(), hibp.timeoutSeconds());
        }
    }

    public record HibpConfigRequest(boolean enabled, String endpoint, int staleAfterDays, int timeoutSeconds) {
        static HibpConfigRequest of(HibpSettings hibp) {
            return new HibpConfigRequest(hibp.enabled(), hibp.endpoint(), hibp.staleAfterDays(), hibp.timeoutSeconds());
        }

        void applyTo(HibpSettings hibp) {
            hibp.enabled(enabled);
            hibp.endpoint(endpoint);
            hibp.staleAfterDays(staleAfterDays);
            hibp.timeoutSeconds(timeoutSeconds);
        }
    }

    public record TwoFactorCoreConfigResponse(
            boolean enabled,
            int stepUpFreshnessSeconds,
            int trustedDeviceMaxDays,
            int enrollmentGraceDays,
            boolean secretKeyConfigured) {

        static TwoFactorCoreConfigResponse of(TwoFactorSettings twoFactor) {
            return new TwoFactorCoreConfigResponse(
                    twoFactor.enabled(),
                    twoFactor.stepUpFreshnessSeconds(),
                    twoFactor.trustedDeviceMaxDays(),
                    twoFactor.enrollmentGraceDays(),
                    isSet(twoFactor.secretKey()));
        }
    }

    public record TwoFactorCoreConfigRequest(
            boolean enabled, int stepUpFreshnessSeconds, int trustedDeviceMaxDays, int enrollmentGraceDays) {

        static TwoFactorCoreConfigRequest of(TwoFactorSettings twoFactor) {
            return new TwoFactorCoreConfigRequest(
                    twoFactor.enabled(),
                    twoFactor.stepUpFreshnessSeconds(),
                    twoFactor.trustedDeviceMaxDays(),
                    twoFactor.enrollmentGraceDays());
        }

        void applyTo(TwoFactorSettings twoFactor) {
            twoFactor.enabled(enabled);
            twoFactor.stepUpFreshnessSeconds(stepUpFreshnessSeconds);
            twoFactor.trustedDeviceMaxDays(trustedDeviceMaxDays);
            twoFactor.enrollmentGraceDays(enrollmentGraceDays);
        }
    }

    /** The authenticator app settings, read and written in the same shape. */
    public record TotpConfig(int digits, int periodSeconds, String algorithm, int driftWindow, String issuer) {
        static TotpConfig of(TwoFactorSettings.TotpConfig totp) {
            return new TotpConfig(
                    totp.digits(), totp.periodSeconds(), totp.algorithm(), totp.driftWindow(), totp.issuer());
        }

        void applyTo(TwoFactorSettings.TotpConfig totp) {
            totp.digits(digits);
            totp.periodSeconds(periodSeconds);
            totp.algorithm(algorithm);
            totp.driftWindow(driftWindow);
            totp.issuer(issuer);
        }
    }

    /** How many backup codes an account is given, read and written in the same shape. */
    public record BackupCodesConfig(int count) {}

    /** The security key settings, read and written in the same shape. */
    public record WebAuthnConfig(String rpId, String rpName, String attestation, int timeoutSeconds) {
        static WebAuthnConfig of(WebAuthnSettings webauthn) {
            return new WebAuthnConfig(
                    webauthn.rpId(), webauthn.rpName(), webauthn.attestation(), webauthn.timeoutSeconds());
        }

        void applyTo(WebAuthnSettings webauthn) {
            webauthn.rpId(rpId);
            webauthn.rpName(rpName);
            webauthn.attestation(attestation);
            webauthn.timeoutSeconds(timeoutSeconds);
        }
    }
}
