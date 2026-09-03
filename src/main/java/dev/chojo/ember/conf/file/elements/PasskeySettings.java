/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;

import java.util.Locale;

/**
 * How far this instance goes with passkeys, at {@code auth.passkeys}.
 *
 * <p>The configured mode is not always the mode in force: a demo instance and an instance whose
 * rpId fell back to {@code localhost} are held at {@link Mode#OFF} whatever this says. That
 * decision lives with the service that answers for the effective mode, not here.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
public class PasskeySettings {

    /**
     * The five steps an instance can take, in order. There is deliberately no step that makes a
     * passkey mandatory: an instance that wants to push pairs {@link #PREFERRED} with the
     * two-factor policy, which mandates strength rather than one technology.
     */
    public enum Mode {
        /** Passkeys do not exist on this instance. */
        OFF,
        /** Passkeys work from the account screen. Nothing is ever suggested to anybody. */
        OPTIONAL,
        /** The login screen offers the passkey path, and a member is offered one once. */
        ENCOURAGED,
        /** An account with a working passkey may switch its own password sign-in off. */
        PREFERRED,
        /** A new account is created with no password at all. */
        PASSWORDLESS
    }

    @Overwrite(env = @Env)
    private String mode = "OPTIONAL";

    /**
     * The configured mode. Unknown values read as {@link Mode#OPTIONAL}, the default: a typo in
     * a config file must not silently switch every member's login screen around.
     */
    public Mode mode() {
        try {
            return Mode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Mode.OPTIONAL;
        }
    }
}
