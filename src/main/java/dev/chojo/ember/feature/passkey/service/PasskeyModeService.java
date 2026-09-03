/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers which passkey mode is actually in force. The configured mode is overridden to OFF in
 * two situations, and every reader goes through here so the two never have to be remembered at a
 * call site:
 *
 * <ul>
 *   <li>The public demo instance, whose accounts are shared by every visitor: offering a passkey
 *       there would let one visitor put a credential on an account the next visitor signs in as.
 *       A dev run is not held back, because its accounts are the developer's own.
 *   <li>An rpId that fell back to {@code localhost}, where every passkey would bind to the wrong
 *       effective domain and stop working the moment the configuration is fixed.
 * </ul>
 */
@Singleton
public class PasskeyModeService {
    private static final Logger log = LoggerFactory.getLogger(PasskeyModeService.class);

    private final PasskeySettings settings;
    private final Demo demo;
    private final RelyingParties relyingParties;

    @Inject
    public PasskeyModeService(PasskeySettings settings, Demo demo, RelyingParties relyingParties) {
        this.settings = settings;
        this.demo = demo;
        this.relyingParties = relyingParties;
        if (relyingParties.localhostFallback() && settings.mode() != PasskeySettings.Mode.OFF) {
            log.error(
                    "Passkeys are configured {} but the WebAuthn rpId fell back to 'localhost', "
                            + "so the effective mode is OFF. Set auth.webauthn.rpId or api.baseUrl to a real host.",
                    settings.mode());
        }
    }

    /**
     * The mode in force, which is the configured one unless the instance cannot honestly offer
     * passkeys at all.
     */
    public PasskeySettings.Mode effectiveMode() {
        if (demo.enabled()) return PasskeySettings.Mode.OFF;
        if (relyingParties.localhostFallback()) return PasskeySettings.Mode.OFF;
        return settings.mode();
    }
}
