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
    private boolean enabled = false;

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

    @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
    public static class WebAuthnConfig {
        private String rpId = "";
        private String rpName = "";
        private String attestation = "none";
        private int timeoutSeconds = 60;
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

        public boolean requireResidentKey() {
            return requireResidentKey;
        }
    }
}
