/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
public class TwoFactorSettings {
    @Overwrite(env = @Env)
    private boolean enabled = true;

    @Overwrite(env = @Env)
    private String secretKey = "";

    @Overwrite(env = @Env)
    private int stepUpFreshnessSeconds = 300;

    @Overwrite(env = @Env)
    private int trustedDeviceMaxDays = 30;

    @Overwrite(env = @Env)
    private int enrollmentGraceDays = 7;

    private TotpConfig totp = new TotpConfig();
    private BackupCodesConfig backupCodes = new BackupCodesConfig();
    private WebAuthnConfig webauthn = new WebAuthnConfig();

    public boolean enabled() {
        return enabled;
    }

    public String secretKey() {
        return secretKey;
    }

    public int stepUpFreshnessSeconds() {
        return stepUpFreshnessSeconds;
    }

    public int trustedDeviceMaxDays() {
        return Math.min(trustedDeviceMaxDays, 30);
    }

    public int enrollmentGraceDays() {
        return Math.min(enrollmentGraceDays, 7);
    }

    public TotpConfig totp() {
        return totp;
    }

    public BackupCodesConfig backupCodes() {
        return backupCodes;
    }

    public WebAuthnConfig webauthn() {
        return webauthn;
    }

    @Override
    public String toString() {
        return "TwoFactorSettings{enabled=" + enabled
                + ", secretKeyConfigured=" + !secretKey.isBlank()
                + ", stepUpFreshnessSeconds=" + stepUpFreshnessSeconds
                + ", trustedDeviceMaxDays=" + trustedDeviceMaxDays
                + ", enrollmentGraceDays=" + enrollmentGraceDays + '}';
    }

    @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
    public static class TotpConfig {
        private int digits = 6;
        private int periodSeconds = 30;
        private String algorithm = "SHA1";
        private int driftWindow = 1;
        private String issuer = "Ember";

        public int digits() {
            return digits;
        }

        public int periodSeconds() {
            return periodSeconds;
        }

        public String algorithm() {
            return algorithm;
        }

        public int driftWindow() {
            return driftWindow;
        }

        public String issuer() {
            return issuer;
        }
    }

    @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
    public static class BackupCodesConfig {
        private int count = 10;

        public int count() {
            return count;
        }
    }

    /**
     * The old home of the relying-party settings, kept so a config file written before the move
     * still works for one release. {@link WebAuthnSettings#resolvedFrom(Auth)} is the only
     * reader. {@code requireResidentKey} is gone entirely: the passkey ceremony always requires
     * a resident key and the second-factor ceremony never does, so there was nothing left for a
     * knob to decide.
     *
     * @deprecated moved to {@code auth.webauthn}; stops being read in the next release
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal", "unused"})
    public static class WebAuthnConfig {
        private String rpId = "";
        private String rpName = "";
        private String attestation = "none";
        private int timeoutSeconds = 60;

        /** Dead, but a config file written before the move may still carry it; it must parse. */
        private boolean requireResidentKey = false;

        public String rpId() {
            return rpId;
        }

        public String rpName() {
            return rpName;
        }

        public String attestation() {
            return attestation;
        }

        public int timeoutSeconds() {
            return timeoutSeconds;
        }
    }
}
