/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;

/**
 * Demo mode configuration controlling whether demo mode is active, the data reset interval,
 * and whether dev-mode features are enabled.
 */
@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
public class Demo {
    @Overwrite(env = @Env)
    private boolean dev = false;

    @Overwrite(env = @Env)
    private int idleResetMinutes = 15;

    @Overwrite(env = @Env)
    private boolean enabled = false;

    @Overwrite(env = @Env)
    private boolean federationForceHttp = false;

    @Overwrite(env = @Env)
    private boolean stableSessionTokens = true;

    public boolean enabled() {
        return enabled;
    }

    public boolean dev() {
        return dev;
    }

    /**
     * Whether a dev or demo login answers with the account's own address as its token, keeping one
     * session per account.
     *
     * <p>On for the demo, where a link that signs somebody in has to survive a restart, and where
     * one person is one browser. Off for anything driving several browsers as the same person at
     * once: with it on, a second login takes the first one's row over rather than standing beside
     * it, and it takes the freshness of that session with it. Switch it off and a dev instance
     * mints a token per login and keeps a row per session, the way a real one does.
     */
    public boolean stableSessionTokens() {
        return stableSessionTokens;
    }

    /**
     * Minutes of inactivity (no authenticated requests) before the demo data is reset.
     * Default: 15 minutes.
     */
    public int idleResetMinutes() {
        return idleResetMinutes;
    }

    /**
     * When true, forces same-instance federation to use HTTP instead of direct service calls.
     * Useful for dev/testing to exercise the remote federation HTTP endpoints.
     */
    public boolean federationForceHttp() {
        return federationForceHttp;
    }

    @Override
    public String toString() {
        return "Demo{" + "dev=" + dev + ", idleResetMinutes=" + idleResetMinutes + ", enabled=" + enabled
                + ", federationForceHttp=" + federationForceHttp + '}';
    }
}
