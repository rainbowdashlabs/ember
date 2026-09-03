/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebAuthn relying-party configuration, shared by passkeys and second-factor security keys.
 *
 * <p>Lives at {@code auth.webauthn}. It used to live at {@code auth.twoFactor.webauthn} when the
 * only WebAuthn credentials were second factors; a passkey is not a two-factor detail, so the
 * settings moved up. {@link #resolvedFrom(Auth)} still reads the old location for one release.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
public class WebAuthnSettings {
    private static final Logger log = LoggerFactory.getLogger(WebAuthnSettings.class);

    @Overwrite(env = @Env)
    private String rpId = "";

    @Overwrite(env = @Env)
    private String rpName = "";

    @Overwrite(env = @Env)
    private String attestation = "none";

    @Overwrite(env = @Env)
    private int timeoutSeconds = 60;

    /**
     * The settings actually in force: the new location, unless it is untouched while the old
     * {@code auth.twoFactor.webauthn} still carries values. In that case the old values are
     * adopted into the new object, with a startup warning naming where they should move, so the
     * runtime and the admin screen agree on what applies and the next save writes the new
     * location.
     */
    public static WebAuthnSettings resolvedFrom(Auth auth) {
        WebAuthnSettings current = auth.webauthn();
        TwoFactorSettings.WebAuthnConfig legacy = auth.twoFactor().webauthn();
        if (current.isUntouched() && legacyCarriesValues(legacy)) {
            log.warn("WebAuthn settings were read from auth.twoFactor.webauthn, which is deprecated. "
                    + "Move them to auth.webauthn; the old location stops being read in the next release.");
            current.rpId = legacy.rpId() == null ? "" : legacy.rpId();
            current.rpName = legacy.rpName() == null ? "" : legacy.rpName();
            current.attestation = legacy.attestation() == null ? "none" : legacy.attestation();
            current.timeoutSeconds = legacy.timeoutSeconds();
        }
        return current;
    }

    private boolean isUntouched() {
        return rpId.isBlank() && rpName.isBlank() && "none".equals(attestation) && timeoutSeconds == 60;
    }

    private static boolean legacyCarriesValues(TwoFactorSettings.WebAuthnConfig legacy) {
        return !legacy.rpId().isBlank()
                || !legacy.rpName().isBlank()
                || !"none".equals(legacy.attestation())
                || legacy.timeoutSeconds() != 60;
    }

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
